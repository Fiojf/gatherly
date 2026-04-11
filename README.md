# Gatherly

A client-side task tracker for Minecraft. Create to-do lists, track item collection progress automatically, and bookmark coordinates — all from a clean in-game UI with a compact HUD overlay.

## Features

### Task Management
- Create, rename, and delete to-do items
- Drag-and-drop reordering
- Pin important tasks to the top
- Color-code to-dos (9 color presets)
- Per-to-do notes field
- Search and filter tasks by title
- Undo/redo (Ctrl+Z / Ctrl+Y, 50-deep stack)

### Sub-Tasks
- Nest tasks under a parent to-do
- Each sub-task has its own targets and waypoints
- Auto-complete or manual completion per sub-task 

### Auto-Complete & Collection Tracking
- Add item/block collection targets with a required count
- Automatic inventory scanning every second — progress updates in real time
- Autocomplete block ID picker from the item registry
- Tasks auto-complete when all targets are met (toggleable per to-do)
- Toast notification + levelup sound on completion
- Auto-delete completed tasks after a configurable time (0–1440 minutes)

### Coordinate Bookmarks
- Save X/Y/Z locations with custom labels on any task or sub-task
- Global bookmarks (independent of tasks) with their own tab
- Per-bookmark color, HUD visibility, and world marker toggles
- Custom marker letter per bookmark
- Real-time distance calculation from player position

### 3D World Markers
- Billboard-style waypoint markers rendered in the world
- Label, colored letter, and distance displayed at the bookmark position
- Distance-based scaling and alpha fading
- No distance limit — visible at any range
- Toggle all markers on/off with **O**

### HUD Overlay
- Compact always-visible overlay showing active tasks and progress
- Status dot, title, progress bar, and item count per to-do
- Optional nearest-waypoint distance per task
- Bookmarks shown below tasks with label, coordinates, and distance
- Configurable position, size, scale (25–400%), opacity, border, and max rows
- Alternating row tints

### Death Waypoints
- Automatically creates a bookmark at your death location (toggleable)

### World Filtering
- Tasks and bookmarks can be scoped to a specific world or server
- Toggle between "This World" and "Global" per task/bookmark
- World filter toggle in settings

### Customization
- **12 theme presets:** Vanilla, Dark, Transparent, Retro, Ocean, Sunset, Forest, Rose, Midnight, Neon, Monochrome, or fully Custom
- **25+ individual color settings** — panel, text, progress bars, checkboxes, buttons, HUD, row tints
- Adjustable panel size (30–100% width/height)
- All settings accessible via Mod Menu

### Timer
- Per-to-do countdown timer with configurable duration
- Start/stop from the settings panel
- Displays remaining time (H:MM:SS)

## Controls

| Key | Action |
|-----|--------|
| **K** | Open Gatherly screen |
| **Shift+K** | Toggle HUD overlay |
| **O** | Toggle world markers |
| **Ctrl+Z** | Undo |
| **Ctrl+Y** | Redo |

## Requirements

- Minecraft **26.1.x**
- [Fabric Loader](https://fabricmc.net/) 0.18.4+
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.145.4+
- [Cloth Config](https://modrinth.com/mod/cloth-config) 26.1.154+
- [Mod Menu](https://modrinth.com/mod/modmenu) 18.0.0+ (optional, for settings access)
- Java **25**

## Installation

1. Install Fabric Loader for Minecraft 26.1.x
2. Download and place the required dependencies (Fabric API, Cloth Config) in your `mods/` folder
3. Download `gatherly-x.x.x.jar` from Releases and place it in your `mods/` folder
4. Launch the game

## Building from source

```bash
./gradlew build
```

The jar will be in `build/libs/`. Requires Java 25.

## Notes

- **Client-side only** — no server installation needed, works on any server
- Config saved to `config/gatherly.json`

## License

[LGPL-3.0](LICENSE)
