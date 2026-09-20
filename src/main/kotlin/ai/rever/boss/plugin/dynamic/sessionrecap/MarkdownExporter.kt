package ai.rever.boss.plugin.dynamic.sessionrecap

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders a [SessionSummary] as a Markdown document suitable for
 * pasting into a note, ticket, or chat.
 *
 * The output is intentionally simple: section headings, bullet lists,
 * `inline code` for paths and tool names. No HTML. A summary is what
 * the user wants to read, not a wall of formatting.
 */
object MarkdownExporter {

    private val timestampFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    private fun fmt(epoch: Long): String = timestampFormat.format(Date(epoch))

    fun render(summary: SessionSummary): String = buildString {
        appendLine("# Session Recap")
        appendLine()
        appendLine("Window: **${summary.windowLabel}**")
        appendLine("From: ${fmt(summary.windowStart)}")
        appendLine("To:   ${fmt(summary.windowEnd)}")
        appendLine()

        summary.windowChanges?.let { changes ->
            if (!changes.isReachable) return@let
            appendLine("## Window changes")
            appendLine()
            appendLine("- Tabs opened: ${changes.opened}")
            appendLine("- Tabs closed: ${changes.closed}")
            appendLine("- Tab selections: ${changes.selected}")
            appendLine("- Title changes: ${changes.titleChanges}")
            if (changes.recent.isNotEmpty()) {
                appendLine()
                appendLine("Recent events:")
                changes.recent.take(10).forEach { record ->
                    appendLine("- ${fmt(record.timestamp)} `${record.kind}` ${record.tabId}")
                }
            }
            appendLine()
        }

        summary.projectActivity?.let { activity ->
            if (!activity.isReachable) return@let
            appendLine("## Project activity")
            appendLine()
            appendLine("- Switches: ${activity.switches}")
            if (activity.projects.isNotEmpty()) {
                appendLine()
                appendLine("Projects touched:")
                activity.projects.forEach { project ->
                    appendLine("- `${project.projectPath}` (${fmt(project.firstSeen)} - ${fmt(project.lastSeen)})")
                }
            }
            appendLine()
        }

        summary.mcpSummary?.let { mcp ->
            if (!mcp.isReachable) return@let
            appendLine("## MCP calls")
            appendLine()
            appendLine("- Total calls: ${mcp.totalCalls}")
            appendLine("- Errors: ${mcp.errorCount}")
            appendLine("- Total time: ${mcp.totalDurationMs} ms")
            if (mcp.topTools.isNotEmpty()) {
                appendLine()
                appendLine("Top tools:")
                mcp.topTools.forEach { tool ->
                    appendLine("- `${tool.toolName}` - ${tool.callCount} calls, ${tool.errorCount} errors")
                }
            }
            appendLine()
        }

        summary.fileActivity?.let { files ->
            if (!files.isReachable) return@let
            appendLine("## File activity")
            appendLine()
            appendLine("- Created: ${files.created}")
            appendLine("- Modified: ${files.modified}")
            appendLine("- Deleted: ${files.deleted}")
            if (files.byDirectory.isNotEmpty()) {
                appendLine()
                appendLine("By directory:")
                files.byDirectory.forEach { dir ->
                    appendLine("- `${dir.directory}` - ${dir.count} changes")
                }
            }
            appendLine()
        }

        summary.agentNotes?.let { notes ->
            if (!notes.isReachable) return@let
            appendLine("## Agent notes")
            appendLine()
            appendLine("- Pages with notes: ${notes.pages}")
            if (notes.notes.isNotEmpty()) {
                appendLine()
                notes.notes.take(20).forEach { note ->
                    appendLine("- ${fmt(note.timestamp)} (${note.pageId}) ${note.note}")
                }
            }
            appendLine()
        }
    }
}
