package ai.rever.boss.plugin.dynamic.sessionrecap

/**
 * Summary data classes produced by [SessionRecapViewModel].
 *
 * A [SessionSummary] is what the panel and the MCP tools hand back. Every
 * section is independent - a host without MCP exposes [mcpCalls] == null and
 * the renderer hides it. That is the "graceful degrade" contract described in
 * AGENTS.md: anything the plugin cannot reach is null, never an exception.
 */

/** A single tab event captured during the window. */
data class TabEventRecord(
    val tabId: String,
    val kind: String,
    val timestamp: Long,
)

/** Aggregated window-level tab activity over the chosen window. */
data class WindowChanges(
    val opened: Int,
    val closed: Int,
    val selected: Int,
    val titleChanges: Int,
    val recent: List<TabEventRecord>,
    val isReachable: Boolean,
)

/** A project that was selected during the window. */
data class ProjectRecord(
    val projectPath: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

/** Aggregated project activity. */
data class ProjectActivity(
    val projects: List<ProjectRecord>,
    val switches: Int,
    val isReachable: Boolean,
)

/** A single MCP tool call row. */
data class McpCallRecord(
    val toolName: String,
    val success: Boolean,
    val durationMs: Long,
)

/** Top tools + error rate + total time. */
data class McpSummary(
    val topTools: List<ToolUsage>,
    val totalCalls: Int,
    val errorCount: Int,
    val totalDurationMs: Long,
    val isReachable: Boolean,
)

/** Aggregated tool usage. */
data class ToolUsage(
    val toolName: String,
    val callCount: Int,
    val errorCount: Int,
)

/** A single file-change event. */
data class FileEventRecord(
    val filePath: String,
    val changeType: String,
    val projectPath: String?,
    val timestamp: Long,
)

/** Aggregated file activity. */
data class FileActivity(
    val created: Int,
    val modified: Int,
    val deleted: Int,
    val recent: List<FileEventRecord>,
    val byDirectory: List<DirectoryCount>,
    val isReachable: Boolean,
)

/** File change count per top-level directory. */
data class DirectoryCount(
    val directory: String,
    val count: Int,
)

/** A note attached to a page by the page-memory plugin (if loaded). */
data class AgentNote(
    val pageId: String,
    val note: String,
    val timestamp: Long,
)

/** Aggregated agent notes over the window. */
data class AgentNotes(
    val notes: List<AgentNote>,
    val pages: Int,
    val isReachable: Boolean,
)

/** The full recap. Each section is independently nullable. */
data class SessionSummary(
    val windowLabel: String,
    val windowStart: Long,
    val windowEnd: Long,
    val windowChanges: WindowChanges?,
    val projectActivity: ProjectActivity?,
    val mcpSummary: McpSummary?,
    val fileActivity: FileActivity?,
    val agentNotes: AgentNotes?,
)

/** Named time windows the user can pick from the panel. */
enum class RecapWindow(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    CUSTOM("Custom"),
}

/** A resolved window with concrete start/end timestamps. */
data class TimeWindow(
    val kind: RecapWindow,
    val start: Long,
    val end: Long,
) {
    val label: String get() = if (kind == RecapWindow.CUSTOM) "Custom" else kind.label

    companion object {
        fun today(now: Long = System.currentTimeMillis()): TimeWindow {
            val dayMs = 24L * 60L * 60L * 1000L
            val startOfToday = now - (now % dayMs)
            return TimeWindow(RecapWindow.TODAY, startOfToday, now)
        }

        fun yesterday(now: Long = System.currentTimeMillis()): TimeWindow {
            val dayMs = 24L * 60L * 60L * 1000L
            val startOfToday = now - (now % dayMs)
            return TimeWindow(RecapWindow.YESTERDAY, startOfToday - dayMs, startOfToday)
        }

        fun last7Days(now: Long = System.currentTimeMillis()): TimeWindow {
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            return TimeWindow(RecapWindow.LAST_7_DAYS, now - sevenDaysMs, now)
        }

        fun custom(start: Long, end: Long): TimeWindow =
            TimeWindow(RecapWindow.CUSTOM, start, end)
    }
}
