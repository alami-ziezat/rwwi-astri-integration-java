# Implementation Plan: "Schedule WO" Button — Insert to `drm_scheduler_logs`

**Date:** 2026-05-24  
**Author:** alami-ziezat  
**Status:** Ready for implementation — awaiting "go" confirmation

---

## Overview

Add a **"Schedule WO"** button to the ASTRI Work Order Manager dialog (Toolbar 3 — Local KMZ row).  
When clicked, it automatically inserts the currently selected work order into the PostgreSQL table  
`smallworld.drm_scheduler_logs` using values derived from the selected row — no manual user input needed.

Before inserting, two validations pop up a warning dialog if triggered:
1. A Smallworld design already exists for this WO.
2. A scheduler log record already exists for this `infra_code` with an active status.

---

## Field Mapping (All Auto-Populated)

| DB Column | Source | Example |
|---|---|---|
| `infra_code` | `selected_wo[:infra_code]` | `"JKT00001"` |
| `name` | `selected_wo[:infra_name]` | `"PRE ABD_KEBON PALA RW 09"` |
| `infra_type` | `.filters[:infrastructure_type]` | `"cluster"` |
| `username` | `system.getenv("USERNAME")` | `"alami-ziezat"` |
| `scheduler_username` | `system.getenv("USERNAME")` | `"alami-ziezat"` |
| `schedule_date` | `date_time.now()` formatted as date | `"2026-05-24"` |
| `start_time` | `date_time.now()` formatted as time | `"09:30"` |
| `end_time` | `""` (empty — unknown at schedule time) | `""` |
| `status` | `"scheduled"` (hardcoded initial) | `"scheduled"` |
| `description` | `<topology>_<kmz_uuid>` from `selected_wo` | `"AE_4ea5751b-ba8b-4771"` |
| `created_at` | `NOW()` via SQL | — |
| `updated_at` | `NOW()` via SQL | — |

**`description` composition:**
```
description = wo[:topology] + "_" + wo[:kmz_uuid]
# e.g. "AE_4ea5751b-ba8b-4771-86df-1d0225fddc0f"
# If topology is empty: "_4ea5751b-..."
```

---

## Validation Logic (Before Insert)

Two checks run in sequence. Each shows a popup via `_self.show_alert(message)`.  
The insert is **blocked** if either condition is true.

### Check 1 — Design already exists in Smallworld

Reuses the existing engine method `check_project_and_design_exist(wo_number)`:

```
(has_project, has_design) = engine.check_project_and_design_exist(wo[:wo_number])
```

If `has_design` is `_true`:
```
Popup: "Design already created for WO: <wo_number>. Scheduling blocked."
→ abort insert
```

If `has_project` is `_true` but `has_design` is `_false`:
```
Popup: "Project exists but has no design yet for WO: <wo_number>. Scheduling blocked."
→ abort insert
```

### Check 2 — Scheduler log already exists for this `infra_code`

New engine query against `smallworld.drm_scheduler_logs`:

```sql
SELECT status FROM smallworld.drm_scheduler_logs
WHERE infra_code = ?
ORDER BY created_at DESC
LIMIT 1
```

If a row is found:
```
Popup: "WO <infra_code> is already in the scheduler with status: <existing_status>. Scheduling blocked."
→ abort insert
```

If no row is found → proceed with insert.

---

## Files to Modify / Create

### 1. `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_dialog.magik`

**Change A — Add button at end of `build_toolbar3()`**

Current last line of toolbar3 is the `migration_scope` dropdown. Add immediately after:

```magik
sw_label_item.new(a_toolbar, "  ")

.items[:schedule_wo_btn] << sw_button_item.new(a_toolbar,
    :label, "Schedule WO",
    :model, _self,
    :selector, :schedule_wo|()|,
    :tooltip, "Insert selected WO into scheduler log (drm_scheduler_logs)")
.items[:schedule_wo_btn].enabled? << _false
```

**Change B — Enable/disable in `update_detail_panel(wo)`**

In the `_if wo _is _unset` branch, add:
```magik
.items[:schedule_wo_btn].enabled? << _false
```

In the `_else` branch (after existing button enables), add:
```magik
.items[:schedule_wo_btn].enabled? << _true
```

---

### 2. NEW FILE: `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_dialog_schedule.magik`

Full content:

```magik
#% text_encoding = iso8859_1

_package user
$

## Schedule WO dialog handler
## Inserts selected work order into smallworld.drm_scheduler_logs

_pragma(classify_level=basic, topic={astri_integration})
_method rwwi_astri_workorder_dialog.schedule_wo()
    ## Button handler: validate then insert selected WO into drm_scheduler_logs.
    ## All fields are auto-populated from the selected row — no user input required.

    _if .selected_wo _is _unset
    _then
        _self.log_warning("No work order selected")
        _return
    _endif

    _local wo          << .selected_wo
    _local wo_number   << wo[:wo_number].default("")
    _local infra_code  << wo[:infra_code].default("")

    # --- Validation 1: Design already exists in Smallworld ---
    _local (has_project, has_design) << _self.check_project_and_design_exist(wo)

    _if has_design
    _then
        _self.show_alert(
            write_string("Design already created for WO: ", wo_number,
                         %newline, "Scheduling is blocked."))
        _self.log_warning("Schedule blocked - design exists for: " + wo_number)
        _return
    _endif

    _if has_project
    _then
        _self.show_alert(
            write_string("Project exists but has no design yet for WO: ", wo_number,
                         %newline, "Scheduling is blocked."))
        _self.log_warning("Schedule blocked - project exists (no design) for: " + wo_number)
        _return
    _endif

    # --- Validation 2: Already in scheduler log ---
    _local existing_status << .engine.get_scheduler_log_status(infra_code)

    _if existing_status _isnt _unset _andif existing_status <> ""
    _then
        _self.show_alert(
            write_string("WO ", infra_code, " is already in the scheduler",
                         %newline, "with status: ", existing_status,
                         %newline, "Scheduling is blocked."))
        _self.log_warning(
            write_string("Schedule blocked - existing record for: ",
                         infra_code, " (status: ", existing_status, ")"))
        _return
    _endif

    # --- Build params from selected row ---
    _local username    << system.getenv("USERNAME").default("unknown")
    _local now_dt      << date_time.now()
    _local topology    << wo[:topology].default("")
    _local kmz_uuid    << wo[:kmz_uuid].default("")
    _local description << topology + "_" + kmz_uuid

    _local params << property_list.new_with(
        :infra_code,         infra_code,
        :name,               wo[:infra_name].default(""),
        :infra_type,         .filters[:infrastructure_type].default(""),
        :username,           username,
        :scheduler_username, username,
        :schedule_date,      now_dt.date.write_string,
        :start_time,         now_dt.time.write_string,
        :end_time,           "",
        :status,             "scheduled",
        :description,        description)

    # --- Insert ---
    _local ok? << .engine.insert_scheduler_log(params)

    _if ok?
    _then
        _self.log_success(
            write_string("WO scheduled: ", wo_number,
                         " [", infra_code, "] desc=", description))
    _else
        _self.log_error("Failed to schedule WO: " + wo_number)
    _endif
_endmethod
$
```

---

### 3. `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_engine.magik`

**Add two methods:**

#### Method A — `insert_scheduler_log(params)`

Uses the same connection pattern as `get_kmz_uuid_from_db()` with `rwwi_external_ds_manager`.

```magik
_pragma(classify_level=basic, topic={astri_integration})
_method rwwi_astri_workorder_engine.insert_scheduler_log(params)
    ## Insert WO record into smallworld.drm_scheduler_logs.
    ##
    ## Parameters:
    ##   params (property_list) — :infra_code, :name, :infra_type, :username,
    ##     :scheduler_username, :schedule_date, :start_time, :end_time,
    ##     :status, :description
    ##
    ## Returns:
    ##   boolean — _true on success

    _local sql << "INSERT INTO smallworld.drm_scheduler_logs " +
        "(infra_code, name, infra_type, username, scheduler_username, " +
        "schedule_date, start_time, end_time, status, description, " +
        "created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())"

    _local conn << _unset

    _try _with cond
        _local is_connect?
        (is_connect?, conn) << user:rwwi_external_ds_manager.open_connection_for(
            "[POSTGRESQL_ASTRI_DB]")

        _if _not is_connect?
        _then
            write("ERROR: Cannot connect to POSTGRESQL_ASTRI_DB")
            _return _false
        _endif

        conn.sql_execute(sql,
            params[:infra_code],
            params[:name],
            params[:infra_type],
            params[:username],
            params[:scheduler_username],
            params[:schedule_date],
            params[:start_time],
            params[:end_time],
            params[:status],
            params[:description])

        conn.commit()
        extdb_java_acp.close_all()
        write("Scheduler log inserted for infra_code:", params[:infra_code])
        _return _true

    _when error
        write("ERROR in insert_scheduler_log():", cond.report_contents_string)
        _try
            _if conn _isnt _unset _then conn.rollback() _endif
        _when error
        _endtry
        extdb_java_acp.close_all()
        _return _false
    _endtry
_endmethod
$
```

#### Method B — `get_scheduler_log_status(infra_code)`

```magik
_pragma(classify_level=basic, topic={astri_integration})
_method rwwi_astri_workorder_engine.get_scheduler_log_status(infra_code)
    ## Check if a scheduler log record already exists for the given infra_code.
    ##
    ## Parameters:
    ##   infra_code (string)
    ##
    ## Returns:
    ##   string — existing status value, or _unset if no record found

    _local sql << "SELECT status FROM smallworld.drm_scheduler_logs " +
        "WHERE infra_code = ? ORDER BY created_at DESC LIMIT 1"

    _local conn << _unset

    _try _with cond
        _local is_connect?
        (is_connect?, conn) << user:rwwi_external_ds_manager.open_connection_for(
            "[POSTGRESQL_ASTRI_DB]")

        _if _not is_connect?
        _then
            write("WARNING: Cannot connect to POSTGRESQL_ASTRI_DB for scheduler check")
            _return _unset
        _endif

        _local recs << conn.sql_select(sql, infra_code)
        _local rec  << recs.get()
        recs.close()

        _try
            conn.commit()
        _when error
            conn.rollback()
        _endtry

        extdb_java_acp.close_all()

        _if rec _isnt _unset
        _then
            _return rec.status.default("")
        _endif

        _return _unset

    _when error
        write("ERROR in get_scheduler_log_status():", cond.report_contents_string)
        _try
            _if conn _isnt _unset _then conn.rollback() _endif
        _when error
        _endtry
        extdb_java_acp.close_all()
        _return _unset
    _endtry
_endmethod
$
```

---

### 4. `magik/rwwi_astri_workorder/source/load_list.txt`

Add the new file after `rwwi_astri_workorder_dialog_upload`:

```
rwwi_astri_workorder_dialog_schedule
```

---

## Execution Flow

```
User selects WO row
  → update_detail_panel(wo) called
  → :schedule_wo_btn enabled

User clicks "Schedule WO"
  → schedule_wo() on dialog

  [Validation 1 — Smallworld design check]
  → engine.check_project_and_design_exist(wo_number)
  → has_design = true  → show_alert("Design already created...") → STOP
  → has_project = true → show_alert("Project exists, no design...") → STOP
  → both false         → continue

  [Validation 2 — Scheduler log check]
  → engine.get_scheduler_log_status(infra_code)
  → status found       → show_alert("Already in scheduler with status: <status>") → STOP
  → not found          → continue

  [Build params from selected_wo]
  description = topology + "_" + kmz_uuid
  schedule_date = date_time.now().date.write_string
  start_time    = date_time.now().time.write_string
  username / scheduler_username = system.getenv("USERNAME")
  status = "scheduled"

  [Insert]
  → engine.insert_scheduler_log(params)
    → open_connection_for("[POSTGRESQL_ASTRI_DB]")
    → conn.sql_execute(INSERT ... VALUES (?, ..., NOW(), NOW()))
    → conn.commit()
    → extdb_java_acp.close_all()
    → return _true
  → log_success("WO scheduled: <wo_number> [<infra_code>] desc=<description>")

  On any failure:
  → conn.rollback() + close_all()
  → return _false
  → log_error("Failed to schedule WO: <wo_number>")
```

---

## Summary of Changes

| # | Action | File |
|---|---|---|
| 1 | Add `:schedule_wo_btn` at end of `build_toolbar3()` | `rwwi_astri_workorder_dialog.magik` |
| 2 | Enable/disable `:schedule_wo_btn` in `update_detail_panel()` | `rwwi_astri_workorder_dialog.magik` |
| 3 | Add `schedule_wo()` handler | NEW `rwwi_astri_workorder_dialog_schedule.magik` |
| 4 | Add `insert_scheduler_log(params)` | `rwwi_astri_workorder_engine.magik` |
| 5 | Add `get_scheduler_log_status(infra_code)` | `rwwi_astri_workorder_engine.magik` |
| 6 | Register new file | `load_list.txt` |

---

## No Open Questions

All design decisions resolved per user guidance:
- Auto-populate all fields from selected row (no form dialog)
- `description` = `topology + "_" + kmz_uuid`
- Button location: Toolbar 3 (Local KMZ row), after Migrate scope dropdown
- Validation: two popup blocks (design exists, scheduler record exists)
- DB connection: `[POSTGRESQL_ASTRI_DB]` via `rwwi_external_ds_manager` (same as engine)
- Initial status: `"scheduled"`
