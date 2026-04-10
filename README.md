# Gatherly

A client-side task tracker for Minecraft. Create to-do lists, track item collection progress automatically, and bookmark coordinates — all from a clean in-game UI with a compact HUD overlay.

## Features

- **To-do lists** — create, rename, reorder (drag-and-drop), pin, and color-code tasks
- **Sub-tasks** — nest tasks under a parent, each with their own targets and waypoints
- **Auto-complete** — tasks mark themselves done when all collection targets are met
- **Inventory scanning** — progress updates in real time as you collect items
- **Coordinate bookmarks** — save locations with custom labels, see distance in HUD
- **3D world markers** — billboard waypoints rendered in-world for bookmarked locations
- **HUD overlay** — compact, always-visible display of active tasks and progress
- **12 theme presets** — Vanilla, Dark, Transparent, Retro, Ocean, Sunset, Forest, Rose, Midnight, Neon, Monochrome, or fully custom
- **Undo** — Ctrl/Cmd+Z to undo changes (50-deep stack)
- **World filtering** — show only tasks relevant to the current world/server
- **Auto-purge** — completed tasks are automatically deleted after a configurable time
- **Toast notifications** — get notified when a task is auto-completed

## Controls

| Key | Action |
|-----|--------|
| **K** | Open Gatherly screen |
| **Shift+K** | Toggle HUD overlay |

## Requirements

- Minecraft **26.1.1**
- [Fabric Loader](https://fabricmc.net/) 0.18.4+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config) 26.1.154+
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for settings access)
- Java **25**

## Installation

1. Install Fabric Loader for Minecraft 26.1.1
2. Download and place the required dependencies (Fabric API, Cloth Config) in your `mods/` folder
3. Download `gatherly-x.x.x.jar` from Releases and place it in your `mods/` folder
4. Launch the game

## Building from source

```bash
./gradlew build
```

The jar will be in `build/libs/`. Requires Java 25.

## License

[LGPL-3.0](LICENSE)
