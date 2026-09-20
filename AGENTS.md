# AGENTS.md

## Project Overview

**Session Recap** (`ai.rever.boss.plugin.dynamic.sessionrecap`) is a dynamic
plugin for the BOSS desktop application.

End-of-session summary combining tab, project, MCP and file events into a
single readable recap.

- **Plugin ID**: `ai.rever.boss.plugin.dynamic.sessionrecap`
- **Main Class**: `ai.rever.boss.plugin.dynamic.sessionrecap.SessionRecapDynamicPlugin`
- **API Version**: 1.0.93

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build
./gradlew processResources   # Process resources (syncs version into plugin.json)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user will test manually.
- After building, copy the JAR to `~/.boss/plugins/` for local testing.

## Architecture

### Plugin Structure

```
src/main/kotlin/   -> Plugin source code (package: ai.rever.boss.plugin.dynamic.sessionrecap)
src/main/resources/META-INF/boss-plugin/plugin.json -> Plugin manifest
build.gradle.kts   -> Build config + version (single source of truth)
```

### Files

- `SessionRecapDynamicPlugin.kt` - entry point. Registers the panel and the
  MCP tool provider on `context.applicationEventBus`.
- `SessionRecapInfo.kt` - `PanelInfo` (id `session-recap`, slot left-bottom,
  priority 76).
- `SessionRecapComponent.kt` - Decompose component that hosts the panel UI.
- `SessionRecapViewModel.kt` - holds the four collectors and exposes a
  `StateFlow<SessionSummary?>` for the panel and the MCP tools to read.
- `SessionRecapContent.kt` - Compose UI: toolbar with window selector,
  refresh and copy buttons, plus section cards.
- `EventCollectors.kt` - bounded ring buffers for tab, project, file and
  agent-note events. Each collector is launched once on the host's
  `pluginScope`.
- `MarkdownExporter.kt` - turns a `SessionSummary` into a Markdown document.
- `SessionRecapMcpTools.kt` - three MCP tools (`session_recap_sections`,
  `session_recap_summary`, `session_recap_markdown`).
- `SessionRecap.kt` - the data classes (`SessionSummary`, `WindowChanges`,
  `ProjectActivity`, `McpSummary`, `FileActivity`, `AgentNotes`, the
  `TimeWindow` enum and its `today` / `yesterday` / `last7Days` / `custom`
  factories).

### Key Patterns

- Entry point: `DynamicPlugin` interface with `register(context)` and
  `dispose()`.
- UI: `PanelComponentWithUI` with `@Composable Content()`.
- State: ViewModel pattern with `StateFlow`.
- Providers from `PluginContext`: `applicationEventBus`, `clipboardProvider`.
  Both are nullable; the plugin must handle null gracefully and skip
  registration when the bus is missing.
- Bounded buffers on every collector so a long-running session cannot
  exhaust memory.

### Dependencies

- **boss-plugin-api 1.0.93** - compileOnly (provided by host app at runtime).
- **Compose Desktop** - UI framework.
- **Decompose** - navigation and component lifecycle.
- **Coroutines** - async event collection.
- **kotlinx-serialization-json** - present in the dependency block so a
  future plugin internal RPC can serialize data classes; the recap itself
  does not require serialization today.

## Graceful degradation

Three sections are nullable by design. They are not "missing data" so much
as "the host does not expose the source":

- `mcpSummary` is null when the host's MCP ledger is not reachable. The
  plugin does not import or reference any host-internal MCP type; it asks
  for a list of records through a `mcpRecordsProvider` lambda supplied by
  the view model factory.
- `agentNotes` is null when no page-memory plugin is loaded. The
  `AgentNotesCollector` takes a `pageMemoryApiProvider` lambda and is
  silently a no-op when it returns null.
- The whole panel is skipped when `applicationEventBus` is null. The
  plugin still loads but registers nothing.

## Buffer caps

Set in `EventCollectors.kt` under `object Caps`:

- `MAX_EVENTS_PER_KIND = 5_000`
- `MAX_RECORDS_PER_MCP_CALL = 1_000`
- `MAX_FILE_ACTIVITY = 5_000`
- `MAX_AGENT_NOTES_PER_PAGE = 50`
- `MAX_DURATION_HOURS = 24 * 7`

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task syncs the version into `plugin.json` at build
time. Never edit the version in `plugin.json` directly - only in
`build.gradle.kts`.

## Code Quality

- Use Compose Multiplatform APIs (not Android-specific).
- All Kotlin files end with a newline.
- Handle null providers gracefully - skip work, do not throw.
- No mention of AI, automation, or any tooling of the authors.

## CI/CD

- Pull requests run the Tests workflow (`.github/workflows/test.yml`).
- Pushes to `main` run the Release workflow
  (`.github/workflows/build.yml`), which delegates to the shared
  `risa-labs-inc/BossConsole-Releases` reusable workflow. The Release
  workflow requires `permissions: contents: write` (already declared) so
  it can create a GitHub release and publish to the store.
