package ai.rever.boss.plugin.dynamic.sessionrecap

import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material.Icon

/**
 * Compose UI for the Session Recap panel.
 *
 * Layout: a toolbar with the window selector, refresh, and copy buttons
 * over a scrolling body of section cards. Every section is rendered only
 * if its model is non-null; that is how the panel "gracefully degrades"
 * for hosts without MCP or page-memory wired up.
 */
@Composable
fun SessionRecapContent(viewModel: SessionRecapViewModel) {
    BossTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Toolbar(viewModel)
                Divider()
                StatusRow(viewModel)
                Body(viewModel)
            }
        }
    }
}

@Composable
private fun Toolbar(viewModel: SessionRecapViewModel) {
    val window by viewModel.window.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }
    var customStartText by remember { mutableStateOf("") }
    var customEndText by remember { mutableStateOf("") }
    var showingCustom by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colors.background)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = window.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "Window",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
            // Dropdown hit area sits invisibly over the label so taps anywhere
            // on the row open the menu - the IconButton below is the
            // explicit anchor but a plain Row also needs to respond.
            TextButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.size(0.dp),
            ) {
                Text("")
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                RecapWindow.entries.forEach { kind ->
                    DropdownMenuItem(
                        onClick = {
                            dropdownExpanded = false
                            showingCustom = false
                            val now = System.currentTimeMillis()
                            viewModel.setWindow(
                                when (kind) {
                                    RecapWindow.TODAY -> TimeWindow.today(now)
                                    RecapWindow.YESTERDAY -> TimeWindow.yesterday(now)
                                    RecapWindow.LAST_7_DAYS -> TimeWindow.last7Days(now)
                                    RecapWindow.CUSTOM -> TimeWindow.custom(now - 86_400_000L, now)
                                },
                            )
                        },
                    ) {
                        Text(kind.label)
                    }
                }
                DropdownMenuItem(
                    onClick = {
                        dropdownExpanded = false
                        showingCustom = true
                    },
                ) {
                    Text("Custom range...")
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { viewModel.refresh() },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
        IconButton(
            onClick = { viewModel.copyAsMarkdown() },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy as Markdown",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }

    if (showingCustom) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = customStartText,
                onValueChange = { customStartText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Start (epoch ms)") },
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextField(
                value = customEndText,
                onValueChange = { customEndText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("End (epoch ms)") },
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = {
                    val start = customStartText.toLongOrNull() ?: return@TextButton
                    val end = customEndText.toLongOrNull() ?: return@TextButton
                    viewModel.setWindow(TimeWindow.custom(start, end))
                },
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun StatusRow(viewModel: SessionRecapViewModel) {
    val message by viewModel.statusMessage.collectAsState()
    LaunchedEffect(message) {
        if (message != null) {
            delay(2500)
            viewModel.clearStatusMessage()
        }
    }
    val current = message ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.primary.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = current,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Body(viewModel: SessionRecapViewModel) {
    val summary by viewModel.summary.collectAsState()
    val current = summary
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (current == null) {
            EmptyState()
            return
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(
                    listState = listState,
                    direction = Orientation.Vertical,
                    config = getPanelScrollbarConfig(),
                ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val changes = current.windowChanges
            if (changes != null && changes.isReachable) {
                item {
                    SectionCard(title = "Window changes") {
                        StatRowInt("Opened", changes.opened)
                        StatRowInt("Closed", changes.closed)
                        StatRowInt("Selected", changes.selected)
                        StatRowInt("Title changes", changes.titleChanges)
                    }
                }
            }
            val activity = current.projectActivity
            if (activity != null && activity.isReachable) {
                item {
                    SectionCard(title = "Project activity") {
                        StatRowInt("Switches", activity.switches)
                        StatRowInt("Projects touched", activity.projects.size)
                    }
                }
            }
            val mcp = current.mcpSummary
            if (mcp != null && mcp.isReachable) {
                item {
                    SectionCard(title = "MCP calls") {
                        StatRowInt("Total calls", mcp.totalCalls)
                        StatRowInt("Errors", mcp.errorCount)
                        StatRowLong("Total time (ms)", mcp.totalDurationMs)
                        if (mcp.topTools.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Top tools",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.onSurface,
                            )
                            mcp.topTools.forEach { tool ->
                                Text(
                                    text = "${tool.toolName} - ${tool.callCount} calls, ${tool.errorCount} errors",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
            val files = current.fileActivity
            if (files != null && files.isReachable) {
                item {
                    SectionCard(title = "File activity") {
                        StatRowInt("Created", files.created)
                        StatRowInt("Modified", files.modified)
                        StatRowInt("Deleted", files.deleted)
                        if (files.byDirectory.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "By directory",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.onSurface,
                            )
                            files.byDirectory.forEach { dir ->
                                Text(
                                    text = "${dir.directory} - ${dir.count}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
            val notes = current.agentNotes
            if (notes != null && notes.isReachable) {
                item {
                    SectionCard(title = "Agent notes") {
                        StatRowInt("Pages", notes.pages)
                        StatRowInt("Notes", notes.notes.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.surface)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        body()
    }
}

@Composable
private fun StatRowInt(label: String, value: Int) {
    StatRow(label, value.toString())
}

@Composable
private fun StatRowLong(label: String, value: Long) {
    StatRow(label, value.toString())
}

@Composable
private fun StatRow(label: String, valueText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.08f)),
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No activity captured yet",
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }
}
