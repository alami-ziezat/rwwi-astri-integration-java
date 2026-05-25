# Implementation Plan: Schedule WO + Batch Migration Runner

**Date:** 2026-05-24  
**Author:** alami-ziezat  
**Status:** FULLY IMPLEMENTED

---

## PART 1 — "Schedule WO" Button

### Overview

A **"Schedule WO"** button in Toolbar 3 (Local KMZ row) auto-inserts the selected WO into
`smallworld.drm_scheduler_logs`. Two validations block the insert if a Smallworld design
already exists or the `infra_code` already has a scheduler record.

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

### Validation (Before Insert)

1. **Design exists** — `engine.check_project_and_design_exist(wo_number)` → popup + block
2. **Already scheduled** — `engine.get_scheduler_log_status(infra_code)` → popup + block if status found

### Files Changed

| File | Change |
|---|---|
| `rwwi_astri_workorder_dialog.magik` | Add `:schedule_wo_btn` in `build_toolbar3()`; enable/disable in `update_detail_panel()` |
| `rwwi_astri_workorder_dialog_schedule.magik` *(new)* | `schedule_wo()` handler |
| `rwwi_astri_workorder_engine.magik` | `insert_scheduler_log(params)`, `get_scheduler_log_status(infra_code)` |
| `load_list.txt` | Register `rwwi_astri_workorder_dialog_schedule` |

---

---

## PART 2 — Batch Migration Runner

### Overview

Ten new methods appended to **`astri_data_migrator.magik`**, following its exact
`migrate_<infra_type>_objects` / `process_<infra_type>_kml_migration` pattern.

The runner is **standalone** — no dialog dependency, no new files, all output via `write()`.
It is invoked directly from the Magik CLI or any OS-level command.

### Status Lifecycle

```
scheduled  →  processing  →  migrated
                           →  failed
```

### CLI Usage

```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])

migrator.migrate_scheduled_objects()              # all infra types (feeder → subfeeder → cluster)
migrator.migrate_feeder_scheduled_objects()       # feeder only
migrator.migrate_subfeeder_scheduled_objects()    # subfeeder only
migrator.migrate_cluster_scheduled_objects()      # cluster only
```

### Method Map

```
EXISTING                                    NEW (scheduled)
──────────────────────────────────────────  ────────────────────────────────────────────
migrate_all()                           ←→  migrate_scheduled_objects()
migrate_cluster_objects()               ←→  migrate_cluster_scheduled_objects()
  └─ process_cluster_kml_migration()    ←→    └─ process_cluster_scheduled_migration()
migrate_subfeeder_objects()             ←→  migrate_subfeeder_scheduled_objects()
  └─ process_subfeeder_kml_migration()  ←→    └─ process_subfeeder_scheduled_migration()
migrate_feeder_objects()                ←→  migrate_feeder_scheduled_objects()
  └─ process_feeder_kml_migration()     ←→    └─ process_feeder_scheduled_migration()
migrate_to_sw_alternative()             ←→  migrate_to_sw_design()   (new private sink)
```

### Key Differences vs Existing Pattern

| Aspect | Existing `_objects` methods | New `_scheduled_objects` methods |
|---|---|---|
| Record source | SW collection (`master_cluster` etc.) | PostgreSQL `drm_scheduler_logs` |
| KMZ UUID | `rec.abd_kmz_uuid` | parsed from `rec[:description]` → `topology\|kmz_uuid` |
| Status write-back | SW record field (`rec.cluster_status << "MIGRATED"`) | SQL `UPDATE drm_scheduler_logs SET status=?` |
| Migration target | `migrate_to_sw_alternative()` → existing SW alternative | `migrate_to_sw_design()` → new project + design |

### `description` Parsing

```
description = "AE|4ea5751b-ba8b-4771-86df-1d0225fddc0f"

pipe_pos     = description.index_of(%|)
topology_str = description.subseq(1, pipe_pos - 1)    → "AE"
kmz_uuid     = description.subseq(pipe_pos + 1)        → "4ea5751b-..."
```

### `migrate_to_sw_design()` vs `migrate_to_sw_alternative()`

| | `migrate_to_sw_alternative()` | `migrate_to_sw_design()` |
|---|---|---|
| Navigation | `gv.go_to_alternative(alt_name)` + `gv.switch(:write)` | none |
| Construction status | `"In Service"` | default (none passed) |
| Creates project/design | no | yes — `create_project_and_design()` |
| Commits | caller responsibility | `database.commit()` inside method |

### UPDATE SQL (no `id` dependency)

```sql
UPDATE smallworld.drm_scheduler_logs
SET    status = ?, updated_at = NOW()
WHERE  infra_code = ? AND infra_type = ?
```

### Files Changed

| File | Change |
|---|---|
| `astri_data_migrator.magik` | 10 new methods appended (see table below) |

### All New Methods

| # | Method | Visibility |
|---|---|---|
| 1 | `migrate_scheduled_objects()` | public |
| 2 | `migrate_cluster_scheduled_objects()` | public |
| 3 | `migrate_subfeeder_scheduled_objects()` | public |
| 4 | `migrate_feeder_scheduled_objects()` | public |
| 5 | `process_cluster_scheduled_migration(rec, obj_stats)` | private |
| 6 | `process_subfeeder_scheduled_migration(rec, obj_stats)` | private |
| 7 | `process_feeder_scheduled_migration(rec, obj_stats)` | private |
| 8 | `migrate_to_sw_design(wo, placemarks, infra_type, topology)` | private |
| 9 | `get_scheduled_records(infra_type)` | private |
| 10 | `update_scheduled_status(infra_code, infra_type, new_status)` | private |

### Notes

- **No dialog involvement** — the batch runner has zero dependency on `rwwi_astri_workorder_dialog`.
- **`wo_number` = `infra_code`** — project name fallback since `drm_scheduler_logs` has no `wo_number` column.
- **One failure never aborts the loop** — each `process_*` call wraps its own `_try/_when error`.
- **DB connection** managed per-call via the existing `.db_connection` slot + `extdb_java_acp.close_all()`.
