# Implementation Plan: Schedule WO + Batch Migration Runner

**Date:** 2026-05-24  
**Author:** alami-ziezat  
**Status:** Part 1 — IMPLEMENTED. Part 2 — Ready for implementation, awaiting "go" confirmation.

---

## PART 1 — "Schedule WO" Button (IMPLEMENTED)

### Overview

Adds a **"Schedule WO"** button to Toolbar 3 (Local KMZ row). When clicked, it auto-inserts
the selected WO into `smallworld.drm_scheduler_logs`. Two validations block the insert
if a design already exists in Smallworld or the `infra_code` already has a scheduler record.

### Field Mapping (All Auto-Populated)

| DB Column | Source | Example |
|---|---|---|
| `infra_code` | `selected_wo[:infra_code]` | `"JKT00001"` |
| `name` | `selected_wo[:infra_name]` | `"PRE ABD_KEBON PALA RW 09"` |
| `infra_type` | `.filters[:infrastructure_type]` | `"cluster"` |
| `username` | `system.getenv("USERNAME")` | `"alami-ziezat"` |
| `scheduler_username` | `system.getenv("USERNAME")` | `"alami-ziezat"` |
| `schedule_date` | `date_time.now().date.write_string` | `"2026-05-24"` |
| `start_time` | `date_time.now().time.write_string` | `"09:30"` |
| `end_time` | `""` (unknown at schedule time) | `""` |
| `status` | `"scheduled"` | `"scheduled"` |
| `description` | `topology + "|" + kmz_uuid` | `"AE|4ea5751b-ba8b-4771"` |
| `created_at` | `NOW()` via SQL | — |
| `updated_at` | `NOW()` via SQL | — |

### Files Changed (Part 1)

| # | Action | File |
|---|---|---|
| 1 | Add `:schedule_wo_btn` at end of `build_toolbar3()` | `rwwi_astri_workorder_dialog.magik` |
| 2 | Enable/disable `:schedule_wo_btn` in `update_detail_panel()` | `rwwi_astri_workorder_dialog.magik` |
| 3 | `schedule_wo()` handler with both validations | `rwwi_astri_workorder_dialog_schedule.magik` (NEW) |
| 4 | `insert_scheduler_log(params)` | `rwwi_astri_workorder_engine.magik` |
| 5 | `get_scheduler_log_status(infra_code)` | `rwwi_astri_workorder_engine.magik` |
| 6 | Register new file | `load_list.txt` |

---

---

## PART 2 — Batch Migration Runner (PLANNED)

### Overview

New methods added directly to **`astri_data_migrator`**, following its exact
`migrate_<infra_type>_objects` / `process_<infra_type>_kml_migration` pattern.

The new methods read scheduled records from `smallworld.drm_scheduler_logs` (PostgreSQL)
instead of Smallworld collections, and migrate to a **new SW design** (via
`create_project_and_design` + `migrate_placemarks`) instead of an SW alternative.

Status lifecycle in `drm_scheduler_logs`:
```
scheduled  →  processing  →  migrated
                           →  failed
```

---

### CLI Usage (identical style to existing migrator)

```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])

# Run all infra types
migrator.migrate_scheduled_objects()

# Run one type only
migrator.migrate_cluster_scheduled_objects()
migrator.migrate_subfeeder_scheduled_objects()
migrator.migrate_feeder_scheduled_objects()
```

---

### Method Structure (mirrors existing pattern)

```
EXISTING PATTERN                        NEW PATTERN (scheduled from DB)
─────────────────────────────────────   ─────────────────────────────────────────
migrate_feeder_objects()            →   migrate_feeder_scheduled_objects()
  └─ process_feeder_kml_migration()  →    └─ process_feeder_scheduled_migration()
                                              reads from: drm_scheduler_logs
                                              migrates to: SW design (not alternative)

migrate_subfeeder_objects()         →   migrate_subfeeder_scheduled_objects()
  └─ process_subfeeder_kml_migration →    └─ process_subfeeder_scheduled_migration()

migrate_cluster_objects()           →   migrate_cluster_scheduled_objects()
  └─ process_cluster_kml_migration   →    └─ process_cluster_scheduled_migration()

migrate_to_sw_alternative()         →   migrate_to_sw_design()        (NEW shared sink)
  uses: gv.go_to_alternative()           uses: create_project_and_design()
         migrate_placemarks()                   migrate_placemarks()
```

Additionally:
- **`migrate_scheduled_objects()`** — combined entry point calling all three types in sequence
- **`get_scheduled_records(infra_type)`** — `SELECT` from `drm_scheduler_logs` (private)
- **`update_scheduled_status(infra_code, infra_type, new_status)`** — `UPDATE` (private)

---

### Key Differences vs Existing `process_<infra_type>_kml_migration`

| Aspect | Existing | New (scheduled) |
|---|---|---|
| Record source | SW collection (`master_feeder` etc.) | PostgreSQL `drm_scheduler_logs` |
| KMZ UUID source | `rec.abd_kmz_uuid` | parsed from `rec[:description]` (`topology\|kmz_uuid`) |
| WO name field | `feeder_code` etc. | `infra_code` from scheduler row |
| Migration target | `migrate_to_sw_alternative()` — goes to `\|Engineering Design\|migration` alt | `migrate_to_sw_design()` — creates new project + design |
| Status write-back | SW record field (`rec.feeder_status << "MIGRATED"`) | SQL `UPDATE drm_scheduler_logs SET status=?` |
| Status values | `NULL → PROCESS → MIGRATED/ERROR` | `scheduled → processing → migrated/failed` |

---

### `description` Parsing (topology\|kmz_uuid)

```
description = "AE|4ea5751b-ba8b-4771-86df-1d0225fddc0f"

pipe_pos     = description.index_of(%|)
topology_str = description.subseq(1, pipe_pos - 1)    # "AE"
kmz_uuid     = description.subseq(pipe_pos + 1)        # "4ea5751b-..."
```

If no `|` found: `topology_str = ""`, `kmz_uuid = description` (full string).

---

### WO Property-List Built Inside Each Processor

```magik
wo << property_list.new_with(
    :wo_number,           infra_code,     # project name (infra_code is fallback)
    :infra_code,          infra_code,
    :infra_name,          rec[:name],
    :topology,            topology_str,   # parsed from description
    :kmz_uuid,            kmz_uuid,       # parsed from description
    :kmz_source,          "APD",
    :infrastructure_type, infra_type,
    :olt_label,           "",
    :olt_name,            "",
    :area,                "",
    :area_plant_code,     "")
```

---

### New Private Method: `migrate_to_sw_design(wo, placemarks, infra_type, kml_topology)`

Parallel to the existing `migrate_to_sw_alternative()` but targets a **new Design Manager
project and design** instead of navigating to an existing alternative.

```magik
_private _method astri_data_migrator.migrate_to_sw_design(wo, placemarks, infra_type, _optional kml_topology)
    ## Migrate placemarks into a new Smallworld Design Manager project+design.
    ## Uses astri_design_migrator.create_project_and_design() + migrate_placemarks().
    ## Does NOT navigate to an alternative — creates a fresh design.

    _local database   << .database
    _local infra_code << wo[:infra_code].default("")
    _local wo_number  << wo[:wo_number].default(infra_code)
    _local infra_name << wo[:infra_name].default(infra_code)

    # Trim to Design Manager 64-char limit
    _if infra_name.size > 64
    _then
        infra_name << infra_name.subseq(infra_name.size - 64, 64)
    _endif

    wo[:infrastructure_type] << infra_type

    # Resolve topology: WO field takes priority over KML-detected
    _local final_topology << kml_topology
    _local topology_str << wo[:topology].default("")
    _if topology_str = "AE"      _then final_topology << :aerial
    _elif topology_str = "UG"    _then final_topology << :underground
    _elif topology_str = "AE&UG" _then final_topology << :both
    _endif

    _local migrator << astri_design_migrator.new(database, wo)
    migrator.migration_scope << "All"
    migrator.topology        << final_topology

    write("  Creating project [", wo_number, "] / design [", infra_name, "]...")
    _local (project, scheme) << migrator.create_project_and_design(
        placemarks, wo_number, infra_code, infra_name, infra_type)
    write("  Project ID: ", project.id, "  Design ID: ", scheme.id)

    write("  Migrating ", placemarks.size, " placemarks...")
    _local stats << migrator.migrate_placemarks(placemarks)

    _try _with commitErr
        database.commit()
        write("  Database committed OK")
    _when error
        write("  WARNING: Commit failed - ", commitErr.report_contents_string)
    _endtry

    write("  Stats: aerial=", stats[:aerial_routes],
          " UG=", stats[:underground_routes].default(0),
          " poles=", stats[:new_poles] + stats[:existing_poles],
          " HP=", stats[:demand_points],
          " errors=", stats[:errors])
_endmethod
```

---

### Files to Modify (Part 2)

#### 1. `magik/rwwi_astri_integration/source/astri_data_migrator.magik`

Append the following methods **at the end of the file** (before the final `$`):

**Group A — Public entry points (3 per infra type + 1 combined):**

```
migrate_scheduled_objects()               ← combined, calls all three
migrate_cluster_scheduled_objects()       ← public
migrate_subfeeder_scheduled_objects()     ← public
migrate_feeder_scheduled_objects()        ← public
```

**Group B — Private processors (one per infra type):**

```
process_cluster_scheduled_migration(rec, obj_stats)
process_subfeeder_scheduled_migration(rec, obj_stats)
process_feeder_scheduled_migration(rec, obj_stats)
```

**Group C — New shared migration sink:**

```
migrate_to_sw_design(wo, placemarks, infra_type, _optional kml_topology)
```

**Group D — PostgreSQL helpers:**

```
get_scheduled_records(infra_type)
update_scheduled_status(infra_code, infra_type, new_status)
```

---

#### 2. `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_dialog.magik`

Add **"Run Scheduled"** button in `build_toolbar3()` after `Schedule WO` button:

```magik
.items[:run_scheduled_btn] << sw_button_item.new(a_toolbar,
    :label, "Run Scheduled",
    :model, _self,
    :selector, :run_scheduled_migrations|()|,
    :tooltip, "Migrate all scheduled records from drm_scheduler_logs")
.items[:run_scheduled_btn].enabled? << _true
```

**Add handler in `rwwi_astri_workorder_dialog_schedule.magik`** (one-liner delegate):

```magik
_method rwwi_astri_workorder_dialog.run_scheduled_migrations()
    ## Delegates to astri_data_migrator — no dialog logic here.
    _local database << gis_program_manager.databases[:gis]
    _if database _is _unset
    _then
        _self.log_error("GIS database not available")
        _return
    _endif
    _self.log_info("Starting scheduled batch migration (see Magik console for details)...")
    astri_data_migrator.new(database).migrate_scheduled_objects()
    _self.log_info("Batch migration call complete.")
_endmethod
$
```

---

### Execution Flow

```
CLI: astri_data_migrator.new(gis_program_manager.databases[:gis]).migrate_scheduled_objects()

migrate_scheduled_objects()
  Phase 1 → migrate_cluster_scheduled_objects()
  Phase 2 → migrate_subfeeder_scheduled_objects()
  Phase 3 → migrate_feeder_scheduled_objects()
  → print combined summary

migrate_cluster_scheduled_objects()
  → get_scheduled_records("cluster")
      SELECT infra_code, name, infra_type, description, ...
      FROM smallworld.drm_scheduler_logs
      WHERE status = 'scheduled' AND infra_type = 'cluster'
      ORDER BY created_at ASC
  → _for each rec
      → process_cluster_scheduled_migration(rec, obj_stats)
          → update_scheduled_status(infra_code, "cluster", "processing")
          → parse description → topology_str, kmz_uuid
          → astri_download_cluster_kmz(kmz_uuid, TEMP)
          → parse XML → kml_file_path
          → astri_kml_parser.new(kml_file_path).parse() → placemarks, topology
          → build wo property_list
          → migrate_to_sw_design(wo, placemarks, "cluster", topology)
              → astri_design_migrator.new(database, wo)
              → migrator.create_project_and_design(...)
              → migrator.migrate_placemarks(placemarks)
              → database.commit()
          → update_scheduled_status(infra_code, "cluster", "migrated")  ← success
          [_when error]
          → update_scheduled_status(infra_code, "cluster", "failed")    ← failure
          → continue loop (never abort)
```

---

### Summary of Part 2 Changes

| # | Action | File |
|---|---|---|
| 1 | `migrate_scheduled_objects()` — combined entry point | `astri_data_migrator.magik` |
| 2 | `migrate_cluster_scheduled_objects()` — public | `astri_data_migrator.magik` |
| 3 | `migrate_subfeeder_scheduled_objects()` — public | `astri_data_migrator.magik` |
| 4 | `migrate_feeder_scheduled_objects()` — public | `astri_data_migrator.magik` |
| 5 | `process_cluster_scheduled_migration(rec, obj_stats)` — private | `astri_data_migrator.magik` |
| 6 | `process_subfeeder_scheduled_migration(rec, obj_stats)` — private | `astri_data_migrator.magik` |
| 7 | `process_feeder_scheduled_migration(rec, obj_stats)` — private | `astri_data_migrator.magik` |
| 8 | `migrate_to_sw_design(wo, placemarks, infra_type, topology)` — private | `astri_data_migrator.magik` |
| 9 | `get_scheduled_records(infra_type)` — private | `astri_data_migrator.magik` |
| 10 | `update_scheduled_status(infra_code, infra_type, new_status)` — private | `astri_data_migrator.magik` |
| 11 | `Run Scheduled` button in `build_toolbar3()` | `rwwi_astri_workorder_dialog.magik` |
| 12 | `run_scheduled_migrations()` one-liner handler | `rwwi_astri_workorder_dialog_schedule.magik` |
| No new files | No new standalone exemplar file needed | — |

---

### Notes

- **No new file** — all migration logic lives in `astri_data_migrator.magik` where it belongs.
- **`wo_number` = `infra_code`** — project name fallback since `drm_scheduler_logs` has no `wo_number` column.
- **UPDATE uses `infra_code + infra_type`** — no `id` column dependency.
- **`migrate_to_sw_design()`** is the new parallel to `migrate_to_sw_alternative()` — same shape, different migration target (new project/design vs existing alternative).
- **One failure never aborts remaining records** — each `process_*` call is wrapped in `_try/_when error`.
- **`migrate_scheduled_objects()`** follows the same phase-logging style as `migrate_all()`.
