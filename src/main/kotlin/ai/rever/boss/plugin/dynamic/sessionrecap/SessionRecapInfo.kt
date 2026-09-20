package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Summarize

/**
 * Panel info for the Session Recap panel.
 *
 * Slot position matches the manifest's `panel.position` of `left_bottom`.
 * Priority 76 sits between high-priority infrastructure (0-50) and regular
 * user panels (100+) so it groups with reference plugins like Git Status
 * (14) but below system-built-in rows.
 */
object SessionRecapInfo : PanelInfo {
    override val id = PanelId("session-recap", 76, "ai.rever.boss.plugin.dynamic.sessionrecap")
    override val displayName = "Session Recap"
    override val icon = Icons.Outlined.Summarize
    override val defaultSlotPosition = left.bottom
}
