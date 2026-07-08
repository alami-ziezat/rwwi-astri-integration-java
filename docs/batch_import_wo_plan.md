# Implementation Plan: Batch Import WO from TXT File

**Date:** 2026-07-07  
**Author:** alami-ziezat  
**Status:** PLANNED

---

## Overview

A new **Toolbar 4** (Batch Import row) is added between Toolbar 3 (Local KMZ) and the table.
It allows the user to:

1. **Browse** — pick a `.txt` file containing an infra_type header and a list of infra_codes
2. **Load WO List** — fetch WO data for each infra_code and populate the dialog table
3. **Schedule All** — insert every displayed WO into `drm_scheduler_logs` in one operation

---

## TXT File Format

```
CLUSTER
JKT00001
JKT00002
JKT00003
```

Rules:
- Line 1: infra_type identifier — `CLUSTER`, `SUBFEEDER`, or `FEEDER` (case-insensitive)
- Lines 2+: one `infra_code` per line, blank lines ignored
- Encoding: UTF-8 or ANSI (standard Windows text file)

---

## Part 1 — Toolbar 4 (Batch Import Row)

### Layout Change (`activate_in()`)

Current: 9 rows `{0, 0, 0, 1, 1, 0, 0, 0, 0}`  
New: **10 rows** `{0, 0, 0, 0, 1, 1, 0, 0, 0, 0}`

```magik
# Before
.items[:outer] << outer << sw_container.new(top_c, 9, 1,
    :row_resize_values, {0, 0, 0, 1, 1, 0, 0, 0, 0})

# After
.items[:outer] << outer << sw_container.new(top_c, 10, 1,
    :row_resize_values, {0, 0, 0, 0, 1, 1, 0, 0, 0, 0})
```

Add call after `build_toolbar3`:
```magik
_self.build_toolbar4(outer)
```

### Toolbar 4 Components

| Item Key | Type | Label | Initial State | Callback |
|---|---|---|---|---|
| `:batch_browse_btn` | `sw_button_item` | "Browse List" | always enabled | `browse_batch_list()` |
| `:batch_list_path` | `sw_text_item` | — | read-only display | — |
| `:batch_clear_btn` | `sw_button_item` | "Clear" | disabled | `clear_batch_list()` |
| `:load_wo_list_btn` | `sw_button_item` | "Load WO List" | disabled | `load_wo_list()` |
| `:schedule_all_btn` | `sw_button_item` | "Schedule All" | disabled | `schedule_all_wo()` |

### Button Enable/Disable Logic

```
browse_batch_list()  →  path set  →  enable: batch_clear_btn, load_wo_list_btn
clear_batch_list()   →  path cleared  →  disable: batch_clear_btn, load_wo_list_btn, schedule_all_btn
load_wo_list()       →  WOs loaded  →  enable: schedule_all_btn
```

`schedule_all_btn` is also disabled in `update_detail_panel()` when no list is loaded.

---

## Part 2 — New File: `rwwi_astri_workorder_dialog_batch_import.magik`

All batch import methods are isolated in this new file, following the same split pattern
as `rwwi_astri_workorder_dialog_schedule.magik`.

### Method List

| # | Method | Visibility | Description |
|---|---|---|---|
| 1 | `browse_batch_list()` | public | Open file dialog for `.txt` selection |
| 2 | `ok_batch_list(path)` | public | Callback from file dialog — stores path, enables buttons |
| 3 | `clear_batch_list()` | public | Resets path field and loaded list |
| 4 | `load_wo_list()` | public | Reads file, fetches WOs, populates table |
| 5 | `schedule_all_wo()` | public | Schedules every row in the current WO table |
| 6 | `_parse_batch_file(path)` | private | Parses TXT → returns `(infra_type, infra_codes_rope)` |
| 7 | `_set_infra_type_filter(infra_type)` | private | Sets `.filters[:infrastructure_type]` and syncs dropdown |
| 8 | `_filter_wo_cache_by_codes(infra_codes)` | private | Filters `.wo_cache` to only matching infra_codes |

---

### Method Details

#### `browse_batch_list()`
Follows the same pattern as `browse_kmz()`:
```magik
_method rwwi_astri_workorder_dialog.browse_batch_list()
    ## Open file dialog to select a .txt infra_code list
    _local fd << file_dialog.new(_self, :ok_batch_list|()|)
    fd.filter << {"Text files (*.txt)", "*.txt"}
    fd.directory << system.getenv("TEMP").default("C:\")
    fd.activate()
_endmethod
```

#### `ok_batch_list(path)`
```magik
_method rwwi_astri_workorder_dialog.ok_batch_list(path)
    ## Stores selected path and enables Load WO List button
    .items[:batch_list_path].value << path
    .items[:batch_clear_btn].enabled? << _true
    .items[:load_wo_list_btn].enabled? << _true
    .items[:schedule_all_btn].enabled? << _false
_endmethod
```

#### `clear_batch_list()`
```magik
_method rwwi_astri_workorder_dialog.clear_batch_list()
    ## Resets the batch import state
    .items[:batch_list_path].value << ""
    .items[:batch_clear_btn].enabled?    << _false
    .items[:load_wo_list_btn].enabled?   << _false
    .items[:schedule_all_btn].enabled?   << _false
_endmethod
```

#### `load_wo_list()`
Flow:
1. Call `_parse_batch_file(path)` → get `(infra_type, infra_codes)`
2. Validate infra_type is one of `cluster`, `subfeeder`, `feeder`
3. Call `_set_infra_type_filter(infra_type)` to update filter dropdown
4. If `.wo_cache` is populated, call `_filter_wo_cache_by_codes(infra_codes)` to filter in-memory
5. If `.wo_cache` is empty or infra_type changed, call `apply_filters()` first, then filter
6. Refresh table display
7. Enable `schedule_all_btn` if result list is non-empty
8. Show progress: `"Loaded N work orders from file."`

#### `schedule_all_wo()`
Flow:
1. Get current WO list from table (`.wo_cache` after filtering)
2. Validate list is not empty
3. Loop over each WO:
   - Run same validations as `schedule_wo()`:
     - `engine.check_project_and_design_exist(wo[:wo_number])` → skip if design exists
     - `engine.get_scheduler_log_status(wo[:infra_code])` → skip if already scheduled
   - On pass: call `engine.insert_scheduler_log(params)` 
   - Track counts: `scheduled_count`, `skipped_design`, `skipped_duplicate`
4. Show summary popup:
   ```
   Schedule All complete.
   Scheduled : 5
   Skipped (design exists) : 2
   Skipped (already scheduled) : 1
   ```

#### `_parse_batch_file(path)` (private)
```
Read file line by line:
  Line 1  → infra_type_str (trim, uppercase, map to lowercase: "CLUSTER" → "cluster")
  Line 2+ → collect non-blank lines into infra_codes rope

Return: (infra_type, infra_codes)
Error if: file unreadable, line 1 not in {CLUSTER, SUBFEEDER, FEEDER}
```

#### `_set_infra_type_filter(infra_type)` (private)
```
.filters[:infrastructure_type] << infra_type
.items[:infra_type_selector].value << infra_type   # sync dropdown UI
```

#### `_filter_wo_cache_by_codes(infra_codes)` (private)
```
Build a set from infra_codes for O(1) lookup
Filter .wo_cache keeping only entries where wo[:infra_code] is in the set
Assign result back to .wo_cache
Refresh table
```

---

## Part 3 — Engine Changes (`rwwi_astri_workorder_engine.magik`)

No new methods required — existing methods are sufficient:
- `insert_scheduler_log(params)` — reused as-is by `schedule_all_wo()`
- `get_scheduler_log_status(infra_code)` — reused for duplicate check
- `check_project_and_design_exist(wo_number)` — reused for design check

---

## Files Changed

| File | Change |
|---|---|
| `rwwi_astri_workorder_dialog.magik` | `activate_in()`: 9→10 rows, add `build_toolbar4()` call; add `build_toolbar4()` method; `update_detail_panel()`: disable `schedule_all_btn` when no row |
| `rwwi_astri_workorder_dialog_batch_import.magik` | **New file** — 8 methods (see table above) |
| `load_list.txt` | Add `rwwi_astri_workorder_dialog_batch_import` before `rwwi_astri_workorder_plugin` |

---

## Notes

- **No engine changes** — all new methods reuse existing engine API
- **No new DB table** — `schedule_all_wo()` writes to the same `drm_scheduler_logs` table
- **Infra_type drives filter** — loading from file auto-sets the infrastructure_type dropdown so subsequent manual filters are consistent
- **Partial success** — `schedule_all_wo()` never aborts on single failure; always shows full summary
- **WO display reuses existing table** — no new list/grid widget needed; the existing `wo_cache` + table refresh handles the display
