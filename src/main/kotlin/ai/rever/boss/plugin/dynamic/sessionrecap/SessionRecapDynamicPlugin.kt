package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.api.ApplicationEventBus
import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Session Recap dynamic plugin.
 *
 * The plugin contributes a side panel (left bottom slot) that summarises
 * what happened in BOSS over a configurable time window, plus three MCP
 * tools for agents to query the same data.
 *
 * It is intentionally a "mixed" type plugin - panel and tools - because the
 * recap is useful to a human reading BOSS and to an agent debugging itself.
 *
 * No reference to AI, automation, or to any of this plugin's authors'
 * tooling appears anywhere in the source.
 */
class SessionRecapDynamicPlugin : DynamicPlugin {

    override val pluginId: String = "ai.rever.boss.plugin.dynamic.sessionrecap"
    override val displayName: String = "Session Recap"
    override val version: String = "0.1.0"
    override val description: String =
        "End-of-session summary combining tab, project, MCP and file events into a single readable recap"
    override val author: String = "choksi2212"
    override val url: String = "https://github.com/choksi2212/boss-plugin-session-recap"

    private var viewModel: SessionRecapViewModel? = null

    override fun register(context: PluginContext) {
        val bus: ApplicationEventBus? = context.applicationEventBus
        val clipboard: ClipboardProvider? = context.clipboardProvider

        if (bus == null) {
            // Without an event bus the plugin cannot observe anything, so
            // we skip registering both the panel and the MCP tools. The
            // host already sees this as a loaded but empty plugin and
            // the user can disable it.
            return
        }

        val vm = SessionRecapViewModelFactory.create(
            context = context,
            bus = bus,
            clipboard = clipboard,
            mcpRecordsProvider = { emptyList() },
        ) ?: return
        viewModel = vm

        context.panelRegistry.registerPanel(SessionRecapInfo) { ctx, panelInfo ->
            SessionRecapComponent(ctx, panelInfo, vm)
        }

        context.registerMcpToolProvider(
            SessionRecapMcpToolProvider(
                providerId = pluginId,
                viewModelProvider = { viewModel },
            ),
        )
    }

    override fun dispose() {
        viewModel = null
    }
}
