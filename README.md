# BOSS Session Recap

End-of-session summary combining tab, project, MCP and file events into a single readable recap.

## What it does

Session Recap is a left-bottom panel that watches the host's application event
bus and renders a human-readable summary of what happened in BOSS over a
configurable time window. It also contributes three MCP tools so in-terminal
agents can query the same recap.

- **Window changes** - tabs opened, closed, selected and renamed, with
  timestamps, from `applicationEventBus.tabEvents()`.
- **Project activity** - projects touched and how often the user switched,
  from `applicationEventBus.projectChanges()`.
- **MCP calls** - top tools by call count, error rate, total time. The
  recap renders this section only when the host's MCP ledger is reachable;
  on a host without it the section is hidden, never broken.
- **File activity** - files created, modified, deleted, grouped by
  top-level directory, from `applicationEventBus.fileChanges()`.
- **Agent notes** - notes attached by a page-memory plugin (if loaded).
  Hidden when page-memory is absent.

## Window selector

The toolbar has a dropdown for Today / Yesterday / Last 7 days / Custom. A
Custom row opens two text fields where the user types start and end epoch
milliseconds; the recap filters everything by that range.

## MCP tools

| Tool | Purpose |
|---|---|
| `session_recap_sections` | List the recap sections this build can currently show |
| `session_recap_summary` | Plain-text summary of the recap (accepts a `window` argument) |
| `session_recap_markdown` | Markdown version of the recap (same `window` argument) |

Every tool is `readOnly = true` and asks for nothing destructive.

## Why this plugin

Nothing in BOSS combines tab, project, MCP, and file events into one view
today. Each source is reachable independently, and the user has to open
four panels and correlate timestamps by hand. Session Recap is the first
plugin to fold them into one card, and to make the same data available
to agents over MCP so a recap can be requested programmatically.

## Buffer caps

The event bus streams forever. Each collector drops the oldest event on
overflow:

- 5,000 tab events
- 5,000 project changes
- 5,000 file events
- 1,000 MCP call records
- 50 notes per page

The summary also drops anything older than seven days (168 hours), so a
user who never closes BOSS still gets a sensible window.

## Requirements

- BOSS desktop, version 9.4.2 or newer
- boss-plugin-api 1.0.93 or newer
- `applicationEventBus` from the host (provided by BossConsole today; the
  plugin degrades to "no panel" without it).

## Build

```bash
./gradlew buildPluginJar
ls build/libs/
```

The output jar is named `boss-plugin-session-recap-<version>.jar` and
ships the compiled classes plus the manifest at
`META-INF/boss-plugin/plugin.json`.

## Install

Copy the jar into `~/.boss/plugins/` and restart BOSS, or install it
through the Toolbox by pointing it at the GitHub repository.

## License

Proprietary - see the LICENSE file.
