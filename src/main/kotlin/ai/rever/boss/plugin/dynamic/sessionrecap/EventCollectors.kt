package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.api.ApplicationEventBus
import ai.rever.boss.plugin.api.FileChangeEvent
import ai.rever.boss.plugin.api.FileChangeType
import ai.rever.boss.plugin.api.ProjectChangeEvent
import ai.rever.boss.plugin.api.TabEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Bounded in-memory event collectors for the Session Recap plugin.
 *
 * The application event bus streams events for the whole process lifetime.
 * Without bounds the plugin would grow memory monotonically - and a user
 * who leaves BOSS open for a week would hand the recap an unbounded list to
 * filter. Caps below ensure a long-running session still produces a finite,
 * useful summary.
 *
 * Cap rationale (drop oldest on overflow):
 * - MAX_EVENTS_PER_KIND = 5,000 per collector. A heavy day produces on the
 *   order of a few hundred tab events; 5,000 is comfortably above that and
 *   keeps each collector's memory under ~1 MB.
 * - MAX_RECORDS_PER_MCP_CALL = 1,000. The recap aggregates tool usage into
 *   top-tools rather than per-row, so 1,000 rows is well above any plausible
 *   agent session.
 * - MAX_FILE_ACTIVITY = 5,000. File changes during a build can spike; 5,000
 *   holds the by-directory rollup meaningful.
 * - MAX_AGENT_NOTES_PER_PAGE = 50. Notes are a per-page cap, applied across
 *   every page the plugin sees.
 * - MAX_DURATION_HOURS = 168 (7 days). Anything older is dropped at summary
 *   time, so a user who never closes BOSS still gets a sensible window.
 *
 * Every collector is launched from [SessionRecapDynamicPlugin.register] on
 * the host's pluginScope and cancelled by the host when the plugin is
 * disposed - no manual cancellation needed here.
 */
object Caps {
    const val MAX_EVENTS_PER_KIND = 5_000
    const val MAX_RECORDS_PER_MCP_CALL = 1_000
    const val MAX_FILE_ACTIVITY = 5_000
    const val MAX_AGENT_NOTES_PER_PAGE = 50
    const val MAX_DURATION_HOURS = 24 * 7
}

/** A bounded ring buffer with `drop-oldest` semantics on overflow. */
internal class BoundedBuffer<T>(private val capacity: Int) {
    private val backing = ArrayDeque<T>(capacity)

    fun add(value: T) {
        if (backing.size >= capacity) {
            backing.removeFirst()
        }
        backing.addLast(value)
    }

    fun snapshot(): List<T> = backing.toList()

    fun size(): Int = backing.size
}

/** Convert a [FileChangeType] enum to a stable string for the recap model. */
internal fun FileChangeType.asString(): String = name

/**
 * Collector for tab events. Backs the [SessionSummary.windowChanges] section.
 */
class TabEventCollector(
    bus: ApplicationEventBus,
    scope: CoroutineScope,
) {
    private val backing = BoundedBuffer<TabEventRecord>(Caps.MAX_EVENTS_PER_KIND)
    private val _flow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val updates: SharedFlow<Unit> = _flow.asSharedFlow()

    init {
        scope.launch {
            bus.tabEvents().collect { event -> onTabEvent(event) }
        }
    }

    private fun onTabEvent(event: TabEvent) {
        backing.add(TabEventRecord(event.tabId, event.tabType.name, event.timestamp))
        _flow.tryEmit(Unit)
    }

    fun snapshot(): List<TabEventRecord> = backing.snapshot()
}

/**
 * Collector for project change events. Backs the
 * [SessionSummary.projectActivity] section.
 */
class ProjectEventCollector(
    bus: ApplicationEventBus,
    scope: CoroutineScope,
) {
    private val paths = LinkedHashMap<String, ProjectRecord>()
    private val switches = BoundedBuffer<Pair<String?, String?>>(Caps.MAX_EVENTS_PER_KIND)
    private val _flow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val updates: SharedFlow<Unit> = _flow.asSharedFlow()

    init {
        scope.launch {
            bus.projectChanges().collect { event -> onProjectEvent(event) }
        }
    }

    private fun onProjectEvent(event: ProjectChangeEvent) {
        val newPath = event.projectPath
        val oldPath = event.previousProjectPath
        switches.add(newPath to oldPath)
        if (newPath != null) {
            val existing = paths[newPath]
            paths[newPath] = if (existing == null) {
                ProjectRecord(newPath, event.timestamp, event.timestamp)
            } else {
                existing.copy(lastSeen = event.timestamp)
            }
        }
        _flow.tryEmit(Unit)
    }

    fun projects(): List<ProjectRecord> = paths.values.toList()
    fun switchCount(): Int = switches.size()
}

/**
 * Collector for file change events. Backs the [SessionSummary.fileActivity]
 * section.
 */
class FileEventCollector(
    bus: ApplicationEventBus,
    scope: CoroutineScope,
) {
    private val backing = BoundedBuffer<FileEventRecord>(Caps.MAX_FILE_ACTIVITY)
    private val _flow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val updates: SharedFlow<Unit> = _flow.asSharedFlow()

    init {
        scope.launch {
            bus.fileChanges().collect { event -> onFileEvent(event) }
        }
    }

    private fun onFileEvent(event: FileChangeEvent) {
        backing.add(
            FileEventRecord(
                filePath = event.filePath,
                changeType = event.changeType.asString(),
                projectPath = event.projectPath,
                timestamp = event.timestamp,
            ),
        )
        _flow.tryEmit(Unit)
    }

    fun snapshot(): List<FileEventRecord> = backing.snapshot()
}

/**
 * Optional collector for agent notes contributed by a page-memory plugin.
 *
 * `pageMemoryApiProvider` is invoked at registration time; if it returns
 * non-null the collector subscribes. `noteReader` is a suspend lambda the
 * host provides - kept as a function so this plugin does not import any
 * other plugin's API class. The collector is fully isolated: with no
 * reader, [snapshot] is always empty.
 */
class AgentNotesCollector(
    scope: CoroutineScope,
    pageMemoryApiProvider: () -> Any?,
    noteReader: suspend (Any) -> List<AgentNote>,
) {
    private val backing = BoundedBuffer<AgentNote>(Caps.MAX_AGENT_NOTES_PER_PAGE * 20)
    private val _flow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val updates: SharedFlow<Unit> = _flow.asSharedFlow()

    @Suppress("unused")
    private val job: Job? = run {
        val api = pageMemoryApiProvider() ?: return@run null
        scope.launch {
            try {
                val notes = noteReader(api)
                notes.forEach { backing.add(it) }
                _flow.tryEmit(Unit)
            } catch (_: Throwable) {
                // Graceful degrade - a misbehaving page-memory plugin
                // must not break the recap.
            }
        }
    }

    fun snapshot(): List<AgentNote> = backing.snapshot()
}
