package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult

/**
 * MCP tools contributed by the Session Recap plugin.
 *
 * The recap is a read-only view over already-captured events, so every tool
 * here is `readOnly = true`. Each handler pulls a fresh [SessionSummary] from
 * the view model - the view model is the single source of truth for "what is
 * in the recap right now", which keeps the panel and the MCP tools in sync.
 */
internal class SessionRecapMcpToolProvider(
    override val providerId: String,
    private val viewModelProvider: () -> SessionRecapViewModel?,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "session_recap_sections",
            description = "List the recap sections this build can currently show, " +
                "based on which host services are reachable.",
            handler = McpToolHandler { sectionsListing() },
        ),
        McpToolDefinition(
            name = "session_recap_summary",
            description = "Return the current Session Recap as a short plain-text " +
                "summary. Accepts a window argument (today, yesterday, last_7_days, custom).",
            inputSchema = WINDOW_SCHEMA,
            handler = McpToolHandler { args -> summaryFor(args) },
        ),
        McpToolDefinition(
            name = "session_recap_markdown",
            description = "Return the current Session Recap as a Markdown document " +
                "suitable for pasting into a note, ticket, or chat.",
            inputSchema = WINDOW_SCHEMA,
            handler = McpToolHandler { args -> markdownFor(args) },
        ),
    )

    private fun sectionsListing(): McpToolResult {
        val vm = viewModelProvider() ?: return unavailable()
        val summary = vm.summary.value ?: return McpToolResult("No recap captured yet.")
        val sections = buildList {
            if (summary.windowChanges?.isReachable == true) add("window_changes")
            if (summary.projectActivity?.isReachable == true) add("project_activity")
            if (summary.mcpSummary?.isReachable == true) add("mcp_calls")
            if (summary.fileActivity?.isReachable == true) add("file_activity")
            if (summary.agentNotes?.isReachable == true) add("agent_notes")
        }
        return McpToolResult(sections.joinToString("\n"))
    }

    private suspend fun summaryFor(args: McpToolArgs): McpToolResult {
        val vm = viewModelProvider() ?: return unavailable()
        applyWindowIfPresent(args, vm)
        val summary = vm.summary.value ?: return McpToolResult("No recap captured yet.")
        return McpToolResult(renderPlain(summary))
    }

    private suspend fun markdownFor(args: McpToolArgs): McpToolResult {
        val vm = viewModelProvider() ?: return unavailable()
        applyWindowIfPresent(args, vm)
        val summary = vm.summary.value ?: return McpToolResult("No recap captured yet.")
        return McpToolResult(MarkdownExporter.render(summary))
    }

    private fun applyWindowIfPresent(args: McpToolArgs, vm: SessionRecapViewModel) {
        val windowArg = args.string("window") ?: return
        val now = System.currentTimeMillis()
        val target = when (windowArg.lowercase()) {
            "today" -> TimeWindow.today(now)
            "yesterday" -> TimeWindow.yesterday(now)
            "last_7_days", "last7days", "week" -> TimeWindow.last7Days(now)
            "custom" -> TimeWindow.custom(now - 86_400_000L, now)
            else -> return
        }
        vm.setWindow(target)
    }

    private fun renderPlain(summary: SessionSummary): String = buildString {
        appendLine("Session Recap - ${summary.windowLabel}")
        appendLine("Window changes: opened=${summary.windowChanges?.opened}, closed=${summary.windowChanges?.closed}")
        appendLine("Project switches: ${summary.projectActivity?.switches}")
        summary.mcpSummary?.let { mcp ->
            appendLine("MCP calls: total=${mcp.totalCalls}, errors=${mcp.errorCount}, time=${mcp.totalDurationMs}ms")
        }
        summary.fileActivity?.let { files ->
            appendLine("File activity: created=${files.created}, modified=${files.modified}, deleted=${files.deleted}")
        }
        summary.agentNotes?.let { notes ->
            appendLine("Agent notes: pages=${notes.pages}, notes=${notes.notes.size}")
        }
    }

    private fun unavailable(): McpToolResult =
        McpToolResult("Session Recap view model not available.", isError = true)

    private companion object {
        const val WINDOW_SCHEMA =
            """{"type":"object","properties":{"window":{"type":"string",""" +
                """"description":"today, yesterday, last_7_days, or custom."}}}"""
    }
}
