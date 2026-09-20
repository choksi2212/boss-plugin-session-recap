package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

/**
 * Decompose component for the Session Recap panel.
 *
 * The component holds the panel's view model and forwards composition to
 * the [SessionRecapContent] composable. It does NOT own the collectors -
 * those live for the lifetime of the plugin, not the lifetime of one
 * panel instance, so the plugin wires them in once at register time and
 * passes the same view model into every component that opens.
 */
class SessionRecapComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val viewModel: SessionRecapViewModel,
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        SessionRecapContent(viewModel)
    }
}
