# NISA Cluster Outage Dialog - Implementation Plan

## 1. Overview

A new SWIFT dialog for querying NISA mass problem (outage) status per cluster.
Users search cluster codes, see results from the local PostgreSQL table, trigger NISA API calls to check outage status, then visualise impacted clusters on the map with tooltips.

---

## 2. Architecture

```
rwwi_nisa_dialog        (model — dialog UI, table, log)
        |
        v
rwwi_nisa_plugin        (plugin — map rendering, tooltip, databus)
        |
        v
nisa_massproblem_*      (existing global procs in test_nisa_procs.magik)
        |
        v
PostgreSQL              smallworld.dim_cluster_stella_master_smallworld
        |
        v
NISA REST API           (via Java NisaMassProblemProcs)
```

---

## 3. New Files

| File | Purpose |
|------|---------|
| `source/rwwi_nisa_dialog.magik` | Dialog model — search UI, table, logging, outage check actions |
| `source/rwwi_nisa_plugin.magik` | Plugin — map rendering, tooltip, databus producer/consumer |
| `source/load_list.txt` | **Updated** — add both new files after `test_nisa_procs` |

No changes to `module.def` (already requires `base` and `rwwi.nisa.integration`).

---

## 4. Database Query

**Table:** `smallworld.dim_cluster_stella_master_smallworld`
**Columns used:**

| Column | Display label | Notes |
|--------|--------------|-------|
| cluster_id_stella | Stella ID | Hidden / used for API call |
| cluster_code | Cluster Code | Primary search field |
| olt_code | OLT Code | Display |
| fdt_code | FDT Code | Display |
| cluster_langitude | Latitude | Stored as longitude-latitude (check naming) |
| cluster_longitude | Longitude | |
| olt_hostname | OLT Hostname | Display |
| homepass_total | Homepass | Display |

**Search query pattern:**
```sql
SELECT cluster_id_stella, cluster_code, olt_code, fdt_code,
       cluster_langitude, cluster_longitude, olt_hostname, homepass_total
FROM smallworld.dim_cluster_stella_master_smallworld
WHERE cluster_code ILIKE '%<search_term>%'
ORDER BY cluster_code
LIMIT 200
```

Uses existing `rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")` pattern from `test_nisa_procs.magik`.

---

## 5. Dialog Design (`rwwi_nisa_dialog`)

### 5.1 Slots

```magik
def_slotted_exemplar(:rwwi_nisa_dialog,
{
    {:owner,            _unset, :writable},   # Parent plugin
    {:items,            _unset, :writable},   # UI widget cache (property_list)
    {:cluster_cache,    _unset, :writable},   # Row -> cluster property_list map
    {:result_rows,      _unset, :writable},   # Current search results (rope of pl)
    {:outage_cache,     _unset, :writable},   # cluster_code -> outage result map
    {:selected_cluster, _unset, :writable}    # Currently selected cluster pl
}, :model)
```

### 5.2 Layout

```
┌─────────────────────────────────────────────────────────────┐
│ NISA Cluster Outage Monitor                                 │
├─────────────────────────────────────────────────────────────┤
│ [Toolbar] Cluster Code: [___________] [Search] [Reset]      │
│           Status: 0 clusters found                          │
├─────────────────────────────────────────────────────────────┤
│ [Table - flexible height]                                   │
│  # | Cluster Code | OLT Code | FDT Code | Homepass |        │
│    | OLT Hostname | Lat | Lon | Is Outage?                  │
├─────────────────────────────────────────────────────────────┤
│ [Action bar]                                                │
│  [Check Outage (Selected)] [Check Outage (All)] [Show Map]  │
├─────────────────────────────────────────────────────────────┤
│ [Log window - sw_text_window, read-only]                    │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Table Columns

| # | Label | Width | Notes |
|---|-------|-------|-------|
| 1 | # | 0 (seq num) | Row number |
| 2 | Cluster Code | 2 | Filterable |
| 3 | OLT Code | 1.5 | Filterable |
| 4 | FDT Code | 1.5 | |
| 5 | OLT Hostname | 2 | |
| 6 | Homepass | 1 | |
| 7 | Latitude | 1 | |
| 8 | Longitude | 1 | |
| 9 | Is Outage? | 1 | Icon cell: red icon = outage, green = ok, grey = unchecked |

### 5.4 Key Methods

```
activate_in(frame)          Build full GUI (toolbar + table + actions + log)
build_toolbar(parent)       Search field + Search + Reset buttons + count label
build_table(parent)         sw_table with 9 columns, filterable, sortable
build_actions(parent)       Check Outage (Selected), Check Outage (All), Show Map
build_log(parent)           sw_text_window for status messages

search_clusters()           Query PostgreSQL, populate result_rows + table
reset()                     Clear results, clear outage_cache, clear table

check_outage_selected()     Call NISA for currently selected cluster row
check_outage_all()          Call NISA for every row in result_rows (with progress)
show_map()                  Fire :goto_request + enable plugin rendering

cluster_list_data(table)    Data selector: populate table from result_rows
cluster_selected(sel)       Row selection handler: update selected_cluster, enable actions

_get_outage_icon(cluster_code)  Return icon based on outage_cache status:
                                  :outage_red_icon  -> red  (is outage)
                                  :ok_green_icon    -> green (no outage)
                                  :unknown_icon     -> grey  (not checked)

log_info/log_success/log_error/log_warning/log_separator/clear_log
manage_actions()            Enable/disable buttons based on state
```

---

## 6. Plugin Design (`rwwi_nisa_plugin`)

Mirrors `rwwi_bulk_search_plugin` pattern.

### 6.1 Slots

```magik
def_slotted_exemplar(:rwwi_nisa_plugin,
{
    {:dialog,          _unset},   # rwwi_nisa_dialog instance
    {:current_doc_gui, _unset},   # Current map document GUI
    {:current_doc,     _unset},   # Current map document
    {:geoms,           _unset},   # Rope of {coord, label, is_outage?} tuples
    {:render_status,   _unset}    # Boolean: rendering enabled?
}, :plugin)
```

### 6.2 Databus

- **Consumes:** `:current_document` (to get map document for rendering)
- **Produces:** `:goto_request` (navigate map to cluster location), `:geometry_to_draw`, `:set_map_selection`

### 6.3 Rendering Pattern

Coordinate flow:
```
cluster_langitude / cluster_longitude   (decimal degrees WGS84 from DB)
        ↓  transform.convert() WGS84 → local CS
Local coordinate (for world display)
        ↓  line_gc.transform.convert() world → pixel
Pixel coordinate (for screen drawing)
```

Tooltip content per cluster point (from `nisa_parse_massproblem_response` result):
```
Cluster Code: XXX
OLT: YYY
Homepass: NNN
── MASS PROBLEM ACTIVE ──        ← only if outage detected (result[:data].size > 0)
  MP No    : <mp_no>
  Event    : <event>
  Area     : <area>
  Category : <category>
  Start    : <start_date> <start_time>
  ETA      : <estimation>
  Sites    : <cluster_name> (<olt_name>)  ← first site; "+ N more" if >1
```

When `result[:data]` is empty (no active mass problems) but `result[:success]` is `_true`,
show only the cluster header lines without the MASS PROBLEM section.
If the API was never called (`:unknown`), tooltip shows only cluster header lines.

### 6.4 Key Methods

```
init(name, a_framework)         Initialise plugin, create dialog
build_gui(container)            Build GUI panel (delegates to dialog.activate_in)
sw_databus_data_available(...)  Handle :current_document
show_mode(enable?)              Register/unregister post_renderer
show_records()                  Build geoms list from dialog.result_rows, enable show_mode
build_rwo_cache()               Spatial index filtered to current view bounds
map_damage_notify(gc, view)     Post-renderer callback → draw_all_clusters
draw_all_clusters(gc)           Iterate geoms, draw point + tooltip
draw_cluster_point(...)         Draw circle + tooltip box with outage-aware colour
description(cluster_pl)         Return tooltip string
note_change(who, what, data)    Handle :show_mode, :goto_request
```

### 6.5 Visual Style

| State | Point colour | Tooltip colour |
|-------|-------------|----------------|
| Outage | Red fill | Red header |
| No outage | Green fill | Green header |
| Unchecked | Grey fill | Grey header |

---

## 7. Coordinate Conversion Detail

Clusters in DB have `cluster_langitude` / `cluster_longitude` as **WGS84 decimal degrees**.
To display on SW map:

```magik
# Build transform: WGS84 degrees -> local CS
cs_local  << database.world.coordinate_system
cs_wgs84  << ace_view.collections[:sw_gis!coordinate_system].at(:world_longlat_wgs84_degree)
transform << transform.new_converting_cs_to_cs(cs_wgs84, cs_local)

# Convert each cluster coord
wgs84_coord  << coordinate.new(lon.as_number(), lat.as_number())
local_coord  << transform.convert(wgs84_coord)
```

For tooltip (world → pixel), use the existing pattern from `rwwi_bulk_search_plugin`:
```magik
pixel_coord << line_gc.transform.convert(local_coord)
```

---

## 8. Outage Icon

Reference: mancore uses `image_index` in sw_table cells.
Use `sw_table` with an image column for column 9:

```magik
# In populate table, for each row:
outage_status << .outage_cache[cluster_code].default(:unknown)
_if outage_status = :outage
_then    row_icon << smallworld_icon(:error_status)    # or red_ball
_elif outage_status = :ok
_then    row_icon << smallworld_icon(:ok_status)
_else    row_icon << smallworld_icon(:unknown_status)  # grey
_endif
# Set icon in table cell via label with image
```

---

## 9. load_list.txt (Updated)

```
test_nisa_procs
rwwi_nisa_dialog
rwwi_nisa_plugin
```

---

## 10. Implementation Steps

### Step 1 — `rwwi_nisa_dialog.magik`
1. Define exemplar + slots
2. `new(owner)` / `init(owner)` constructor
3. `activate_in(frame)` — top-level layout
4. `build_toolbar` — search field + buttons + count label
5. `build_table` — 9-column sw_table with filterable columns
6. `build_actions` — 3 action buttons
7. `build_log` — text window
8. `search_clusters()` — PostgreSQL query via rwwi_external_ds_manager
9. `cluster_list_data(table)` — data selector
10. `cluster_selected(sel)` — selection handler
11. `check_outage_selected()` / `check_outage_all()` — call nisa_massproblem_parsed
12. `show_map()` — fire change to plugin
13. `_get_outage_icon()` — icon logic
14. Logging helpers (reuse pattern from rwwi_astri_workorder_dialog)
15. `manage_actions()` — button state control

### Step 2 — `rwwi_nisa_plugin.magik`
1. Define exemplar + slots + style constants
2. `init` / `build_gui` methods
3. `sw_databus_data_available` for `:current_document`
4. `show_mode` — post_renderer registration
5. `show_records` — convert DB coords to local CS, build geoms rope
6. `map_damage_notify` / `draw_all_clusters` / `draw_cluster_point`
7. Tooltip rendering (adapted from rwwi_bulk_search_plugin)
8. `note_change` + `description`

### Step 3 — Update `load_list.txt`
Add two new entries after `test_nisa_procs`.

---

## 11. Dependencies & Assumptions

- PostgreSQL connection alias `[POSTGRESQL_ASTRI_DB]` already configured (same as astri integration)
- `rwwi_external_ds_manager` is available (already used in test_nisa_procs)
- `nisa_massproblem_parsed(cluster_code)` global proc already exists and returns structured data
- `cluster_langitude` / `cluster_longitude` are stored as WGS84 decimal degree strings or numbers
- Map document available via databus `:current_document`
- Icon identifiers (`error_status`, `ok_status`) available via `smallworld_icon()` or `image_manager`
