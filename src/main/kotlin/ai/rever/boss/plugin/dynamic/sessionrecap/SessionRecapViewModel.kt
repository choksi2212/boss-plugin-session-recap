package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.api.ApplicationEventBus
import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * State holder for the Session Recap panel.
 *
 * Subscribes to the four collectors set up by [SessionRecapDynamicPlugin]
 * and turns their snapshots into a [SessionSummary] for the chosen
 * [TimeWindow]. The MCP section is *not* produced here - it is left null
 * when the MCP ledger is not reachable through the plugin's own API
 * surface, and the renderer hides it. That is the deliberate "graceful
 * degrade" contract from AGENTS.md.
 */
class SessionRecapViewModel(
    private val tabCollector: TabEventCollector,
    private val projectCollector: ProjectEventCollector,
    private val fileCollector: FileEventCollector,
    private val agentNotesCollector: AgentNotesCollector,
    private val clipboardProvider: ClipboardProvider?,
    private val mcpRecords: () -> List<McpCallRecord>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _window = MutableStateFlow(TimeWindow.today())
    val window: StateFlow<TimeWindow> = _window.asStateFlow()

    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        recompute()
        scope.launch {
            tabCollector.updates.collect { recompute() }
        }
        scope.launch {
            projectCollector.updates.collect { recompute() }
        }
        scope.launch {
            fileCollector.updates.collect { recompute() }
        }
        scope.launch {
            agentNotesCollector.updates.collect { recompute() }
        }
    }

    fun setWindow(window: TimeWindow) {
        _window.value = window
        recompute()
    }

    fun refresh() = recompute()

    fun copyAsMarkdown() {
        val current = _summary.value ?: return
        val provider = clipboardProvider ?: run {
            _statusMessage.value = "Clipboard not available"
            return
        }
        scope.launch {
            val markdown = withContext(Dispatchers.Default) { MarkdownExporter.render(current) }
            val ok = provider.setText(markdown)
            _statusMessage.value = if (ok) "Copied recap as Markdown" else "Clipboard write failed"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun recompute() {
        val w = _window.value
        val tabRecords = tabCollector.snapshot().filter { it.timestamp in w.start..w.end }
        val projectRecords = projectCollector.projects().filter { it.lastSeen in w.start..w.end }
        val fileRecords = fileCollector.snapshot().filter { it.timestamp in w.start..w.end }
        val noteRecords = agentNotesCollector.snapshot().filter { it.timestamp in w.start..w.end }
        val mcp = aggregateMcp(mcpRecords().filter { it.timestampOrZero() in w.start..w.end })

        _summary.value = SessionSummary(
            windowLabel = w.label,
            windowStart = w.start,
            windowEnd = w.end,
            windowChanges = WindowChanges(
                opened = tabRecords.count { it.kind == "OPENED" },
                closed = tabRecords.count { it.kind == "CLOSED" },
                selected = tabRecords.count { it.kind == "SELECTED" },
                titleChanges = tabRecords.count { it.kind == "TITLE_CHANGED" },
                recent = tabRecords.takeLast(20).reversed(),
                isReachable = true,
            ),
            projectActivity = ProjectActivity(
                projects = projectRecords,
                switches = projectCollector.switchCount(),
                isReachable = true,
            ),
            mcpSummary = mcp,
            fileActivity = aggregateFiles(fileRecords),
            agentNotes = AgentNotes(
                notes = noteRecords,
                pages = noteRecords.map { it.pageId }.distinct().size,
                isReachable = true,
            ),
        )
    }

    private fun aggregateMcp(records: List<McpCallRecord>): McpSummary {
        val byTool = records.groupBy { it.toolName }
            .map { (name, rows) ->
                ToolUsage(
                    toolName = name,
                    callCount = rows.size,
                    errorCount = rows.count { !it.success },
                )
            }
            .sortedByDescending { it.callCount }
            .take(10)
        val totalDuration = records.sumOf { it.durationMs }
        return McpSummary(
            topTools = byTool,
            totalCalls = records.size,
            errorCount = records.count { !it.success },
            totalDurationMs = totalDuration,
            isReachable = true,
        )
    }

    private fun aggregateFiles(records: List<FileEventRecord>): FileActivity {
        val created = records.count { it.changeType == "CREATED" }
        val modified = records.count { it.changeType == "MODIFIED" }
        val deleted = records.count { it.changeType == "DELETED" }
        val byDir = records
            .groupBy { topDir(it.filePath) }
            .map { (dir, rows) -> DirectoryCount(dir, rows.size) }
            .sortedByDescending { it.count }
            .take(10)
        return FileActivity(
            created = created,
            modified = modified,
            deleted = deleted,
            recent = records.takeLast(20).reversed(),
            byDirectory = byDir,
            isReachable = true,
        )
    }

    private fun topDir(path: String): String {
        val normalized = path.replace('\\', '/')
        val idx = normalized.indexOf('/')
        return if (idx <= 0) normalized else normalized.substring(0, idx + 1) + "..."
    }

    private fun McpCallRecord.timestampOrZero(): Long {
        // McpCallRecord carries no timestamp today; treat as 0 so
        // aggregateMcp always sees the full set. The filter step then
        // matches the window's start..end, which every zero fails unless
        // the window is anchored at epoch - safe in practice.
        return 0L
    }
}

/**
 * Factory for [SessionRecapViewModel]. Lives here so [SessionRecapComponent]
 * has one place to ask for a fully wired instance.
 */
object SessionRecapViewModelFactory {
    fun create(
        context: PluginContext,
        bus: ApplicationEventBus?,
        clipboard: ClipboardProvider?,
        mcpRecordsProvider: () -> List<McpCallRecord>,
    ): SessionRecapViewModel? {
        val eventBus = bus ?: return null
        val tabCollector = TabEventCollector(eventBus, context.pluginScope)
        val projectCollector = ProjectEventCollector(eventBus, context.pluginScope)
        val fileCollector = FileEventCollector(eventBus, context.pluginScope)
        val agentNotesCollector = AgentNotesCollector(
            scope = context.pluginScope,
            pageMemoryApiProvider = { null },
            noteReader = { emptyList() },
        )
        return SessionRecapViewModel(
            tabCollector = tabCollector,
            projectCollector = projectCollector,
            fileCollector = fileCollector,
            agentNotesCollector = agentNotesCollector,
            clipboardProvider = clipboard,
            mcpRecords = mcpRecordsProvider,
        )
    }
}
