# FMS Module Analysis
**Path:** `pni_custom/rwwi_astri_integration_java/magik/fms`  
**Author:** Realworld Systems (yudo.ariyanto@gmail.com)  
**Year:** 2024

---

## Overview

The `fms` directory contains two Smallworld GIS modules that together implement a **Field Management System (FMS)** for real-time fiber optic network fault visualization:

| Module | Package | Purpose |
|---|---|---|
| `animator_demo` | `:mapani` | Map animation engine (blinking markers, moving objects) |
| `rwwi_fms` | `sw` | FMS plugin — polls MySQL, traces OTDR faults, animates results |

The system connects to an external MySQL database containing alarm data, performs OTDR (Optical Time Domain Reflectometer) traces along the fiber path to locate the fault, and renders animated blinking markers on the GIS map at the fault coordinates. Field technicians can click faults in a dialog table to navigate the map and generate Word work orders.

---

## Module 1: `animator_demo`

### Purpose
Provides the animation engine used by `rwwi_fms`. Manages background-threaded animations drawn directly on the map view canvas.

### Load Order
```
def_package
command/          → command pattern classes
map_animation_base
map_animator
map_blink_animation
map_move_over_chain_animation
map_view_mods
test
```

### Classes

#### `map_animation_base` (abstract)
Base class for all animations.

| Slot | Description |
|---|---|
| `map_view` | The map view to draw on |
| `index` | Current frame index |
| `dir` | Direction of animation (forward/backward) |

**Contract for subclasses:** Implement `draw()` which must return:
- A list of `pixel_bounding_box` areas drawn (for undraw/restore)
- Time in seconds to wait before next frame, or `_unset` to stop

---

#### `map_animator`
Central controller; manages multiple concurrent animations in a single background thread.

**Responsibilities:**
- Maintains a collection of active animations via an atomic command queue
- Runs a background thread that repeatedly calls each animation's `draw()`
- Saves and restores the underlying canvas pixels between frames
- Pauses automatically during map re-renders (via `:ani_start_render` / `:ani_stop_render` notifications)
- Pauses during pan actions via `document_gui_framework.handle_pan_action()` hook

**Key Methods:**

| Method | Description |
|---|---|
| `new(map_view)` | Creates animator for a map view |
| `run()` | Starts the background animation thread |
| `add(ani)` | Queues an animation to start |
| `remove(ani)` | Queues an animation to stop |
| `stop()` | Halts all animations |
| `draw_animations()` | Iterates animations and calls their `draw()` |
| `pauze_rendering()` / `continue_rendering()` | Pause/resume during map rendering |

---

#### `map_blink_animation`
Subclass of `map_animation_base`. Renders a pulsing concentric-circle effect at a world coordinate.

**Visual:** Three concentric rings — light blue wash (50% transparent) → bright blue fill → white/grey outlines. Cycles through 3 frames at 0.2-second intervals (5 fps).

**Used by `rwwi_fms`** to mark fault locations on the map.

---

#### `map_move_over_chain_animation`
Subclass of `map_animation_base`. Renders a car icon that moves along a network chain (route/cable).

- Reads a raster `:car` bitmap from module resources
- Calculates position based on chain length and current map scale
- Moves bidirectionally at 0.1-second intervals (10 fps)

---

#### `map_view_mods`
Patches two existing Smallworld classes to support animation:

| Patched Class | Method | Purpose |
|---|---|---|
| `map_view` | `run_controlled_render()` | Fires `:ani_start_render` / `:ani_stop_render` around primary and transient render phases |
| `document_gui_framework` | `handle_pan_action()` | Pauses animations during pan to prevent pixel corruption |

---

#### Command Pattern Classes (`command/`)
Four simple command objects for the animator's atomic queue:

| Class | Calls on Animator |
|---|---|
| `add_command` | `execute_add()` |
| `remove_command` | `execute_remove()` |
| `run_command` | `execute_run()` |
| `stop_command` | `execute_stop()` |

---

## Module 2: `rwwi_fms`

### Purpose
Main FMS plugin. Polls a MySQL database for critical alarms, performs OTDR traces to find physical fault positions on the fiber network, and displays animated markers and an info dialog.

### Load Order
```
timer
rwwi_fms_plugin
rwwi_configure_external_db
rwwi_fms_dialog
rwwi_fms_info_box
```

### Classes

#### `timed_event` (`timer.magik`)
Generic interval-based background scheduler (original: Mark Cederholm, 2000).

| Slot | Description |
|---|---|
| `interval` | Seconds between executions |
| `proc` | Procedure to call |
| `args` | Arguments to pass |
| `thread` | Background thread reference |
| `go_status` | Running flag (`_true`/`_false`) |

**Key Methods:**

| Method | Description |
|---|---|
| `new(interval, proc, args)` | Creates event |
| `go(priority)` | Starts background thread |
| `stop()` | Gracefully halts thread |
| `return_status()` | Returns `:stopped` or `:running` |
| `run_event()` | Internal loop — sleeps 1s at a time, fires `proc` when interval elapsed |

**Used by `rwwi_fms_dialog`** to trigger periodic database polling.

---

#### `rwwi_fms_plugin` (`rwwi_fms_plugin.magik`)
Smallworld plugin class that integrates FMS into the GIS toolbar.

- Declares databus data types: `:goto_request`, `:geometry_to_draw`, `:post_render_sets`
- Registers toolbar action `:activate_fms` (icon from MIT base GUI)
- Creates and caches the `rwwi_fms_dialog` when activated
- Responds to databus `:post_render_sets` requests with geometry for map overlay

---

#### `rwwi_configure_external_db` (`rwwi_configure_external_db.magik`)
Configures and establishes the JDBC connection to the MySQL FMS database.

**Database parameters:**

| Parameter | Value |
|---|---|
| Driver | `com.mysql.jdbc.Driver` |
| JDBC URL | `jdbc:mysql://localhost:3306/rest` |
| User | `root` |
| Password | _(none)_ |
| Extra JARs | `extern.jar`, `mysql-connector-j-8.0.31.jar` |

**Tables configured** (primary key validation disabled):

| Table | Role |
|---|---|
| `input_parse2` | Alarm input records |
| `fms_point` | Fault point coordinates |
| `fms_cable_route` | Cable routing segments |
| `fms_structure_point` | Structure locations (poles, buildings) |

**Global procedures:**

| Procedure | Description |
|---|---|
| `configure_oracle_objects_ext_db(db_type, db_name)` | Sets connection parameters |
| `get_user_connection(db_type, db_name)` | Opens and returns a JDBC user connection |

---

#### `rwwi_fms_dialog` (`rwwi_fms_dialog.magik`)
The central UI and business logic class. Extends `:engine_model`.

**UI Layout:**
- 2-row rowcol container
- **Top:** Tree table (240×770px) with columns: `CircuitID | Distance | CableID | Alseverity | Latitude | Longitude`
- **Buttons:** Activate FMS, Refresh, Print Work Order, Email (disabled), Run, Stop
- **Interval selector:** 1/4, 1/2, 1, 2, 5 minutes
- **Status bar:** Operation messages

**Key Slots (26 total):**

| Slot | Description |
|---|---|
| `timer` | `timed_event` for polling |
| `interval` | Update interval (seconds) |
| `an` | `map_animator` instance |
| `mv` | Current map view |
| `lo_manager` | Low-level network manager for OTDR traces |
| `v` / `gv` | Dataset views |
| `list` | Rope of display tree items |
| `sel_record` | Currently selected fault record |
| `info_box` | Info box widget |

**Key Methods:**

| Method | Description |
|---|---|
| `go()` | Starts monitoring — creates `timed_event`, sets interval, calls `check_for_update()` |
| `stop()` | Stops timer, disconnects DB, stops animations, clears map |
| `check_for_update()` | Timer callback — calls `int!do_process_fms(_true)` |
| `int!do_process_fms(enabled?)` | **Core loop** — connects to DB, queries alarms, runs OTDR trace, starts blink animations |
| `get_fault_by_range(port, distance)` | OTDR trace on fiber path; returns pseudo-nodes, last link, full results |
| `connect_to_fms()` | Opens MySQL collections via `get_user_connection()` |
| `disconnect_all()` | Closes all external DB connections via `extdb_java_acp.close_all()` |
| `int!tab_list()` | Queries `input_parse2` for critical alarms; builds tree table rows with OTDR-derived coordinates |
| `convert_coord(p_coord)` / `convert_coord2(p_coord)` | Coordinate transforms: world ↔ WGS84 lat/long |
| `tab_list_double_clicked(selection)` | Zooms map to fault (800m extent), draws green point marker |
| `refresh()` | Clears table, reconnects, rebuilds data, clears animations |
| `do_snap()` | Captures map snapshot → clipboard → inserts into Word document via OLE |
| `sheath(ob, reqid)` | Extracts structure/cable route points, inserts into MySQL FMS tables, converts to WGS84 |

**Core Fault Processing Flow (`int!do_process_fms`):**
```
1. Stop existing animations & disconnect DB
2. Connect to MySQL (input_parse2, fms_point, fms_cable_route, fms_structure_point)
3. Query input_parse2 WHERE alseverity = 'critical'
4. For each critical alarm:
   a. Extract circuitid, device port, distance
   b. Run OTDR trace from port along fiber path
   c. Find pseudo-node at fault distance
   d. Get world coordinate of fault position
   e. Create map_blink_animation at that coordinate
   f. Add to map_animator
5. Start map_animator (blinking markers appear on map)
```

---

#### `rwwi_fms_info_box` (`rwwi_fms_info_box.magik`)
Visual flag/annotation drawn on the map at a fault location. Extends `:nf_start_flag_style`.

**Renders:**
- A flag polygon pointing upward (filled red)
- Four text labels:
  - X Position (longitude)
  - Y Position (latitude)
  - Distance from source
  - Request ID

**Key Methods:**

| Method | Description |
|---|---|
| `flag_and_symbol(x, y)` | Builds flag polygon coordinates and symbol |
| `paint(draw_undraw?, window, geometry, jarak, reqid, link)` | Draws or undraws the flag + text at world coordinate |

---

## External Database Schema

MySQL database `rest` on `localhost:3306`:

| Table | Key Fields | Role |
|---|---|---|
| `input_parse2` | `circuitid`, `dist`, `cableid`, `alseverity`, `reqid` | Alarm input; filtered by `alseverity = 'critical'` |
| `fms_point` | `circuitid`, `distance`, `latitude`, `longitude`, `reqid` | Computed fault point coordinates |
| `fms_cable_route` | `cableid`, `reqid`, `sequence`, `latitude`, `longitude` | Cable segment geometry |
| `fms_structure_point` | `structureid`, `reqid`, `cableid`, `latitude`, `longitude`, `swobject` | Structure (pole, building, access point) locations |

---

## Data Flow Diagram

```
MySQL (localhost:3306/rest)
        │
        │  JDBC (mysql-connector-j-8.0.31.jar)
        ▼
 rwwi_configure_external_db
        │
        ▼
 rwwi_fms_dialog.int!do_process_fms()
        │
        ├─── Query input_parse2 (critical alarms)
        │
        ├─── For each alarm:
        │        lo_manager.otdr_trace(port, distance)
        │              │
        │              └── pseudo_node → world coordinate
        │
        ├─── map_blink_animation.new(map_view, coordinate)
        │
        └─── map_animator.add(animation) → background thread → GIS map
```

---

## Key Technical Integrations

| Integration | Technology |
|---|---|
| Database | JDBC → MySQL 8.0.31 via `extdb_java_acp` framework |
| Fiber trace | Smallworld `lo_manager.otdr_trace()` (OTDR algorithm) |
| Coordinate transform | Smallworld transform API (world ↔ WGS84) |
| Map rendering | `map_view` pixel canvas with background save/restore |
| Animation threading | Smallworld background threads + atomic command queue |
| Work order | OLE automation → Microsoft Word |
| UI framework | Smallworld engine_model + SWAF plugin + databus |

---

## File Index

```
fms/
├── animator_demo/
│   ├── module.def
│   ├── load_list.txt
│   └── source/
│       ├── load_list.txt
│       ├── def_package.magik
│       ├── map_animation_base.magik     ← abstract animation base
│       ├── map_animator.magik           ← animation controller (background thread)
│       ├── map_blink_animation.magik    ← pulsing circle effect
│       ├── map_move_over_chain_animation.magik  ← car moving along cable
│       ├── map_view_mods.magik          ← hooks into map_view render cycle
│       ├── test.magik                   ← demo/test script
│       └── command/
│           ├── command.magik            ← command factory + abstract base
│           ├── add_command.magik
│           ├── remove_command.magik
│           ├── run_command.magik
│           └── stop_command.magik
└── rwwi_fms/
    ├── module.def
    ├── load_list.txt
    ├── resources/en_gb/messages/rwi_fms_plugin.msg
    └── source/
        ├── timer.magik                  ← interval-based background scheduler
        ├── rwwi_fms_plugin.magik        ← GIS plugin / toolbar integration
        ├── rwwi_configure_external_db.magik  ← MySQL JDBC connection setup
        ├── rwwi_fms_dialog.magik        ← main UI dialog + all business logic
        └── rwwi_fms_info_box.magik      ← flag/annotation renderer on map
```
