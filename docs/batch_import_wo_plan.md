# Implementation Plan: Batch Import + Two-Table Scheduler Architecture

**Date:** 2026-07-12
**Author:** alami-ziezat
**Status:** IMPLEMENTED

> **Revision history**
> - v1 (07-07): batch import → scheduler log.
> - v2 (07-12 am): batch import reinterpreted as *direct* "Migrate All".
> - **v3 (07-12 pm, current): reverted to a scheduler model with TWO tables and TWO
>   independent runs.** Batch import queues into `drm_scheduler_logs`; single "Schedule WO"
>   queues into the new `drm_etl_scheduler_log`. Neither UI action migrates directly.

---

## Architecture Overview

Two separate scheduler pipelines, run **independently**:

| Pipeline | Queued by (UI) | Table | Processed by (run) |
|---|---|---|---|
| **Manual** | Batch Import **"Add All"** (Toolbar 4) | `smallworld.drm_scheduler_logs` | `migrate_scheduled_objects()` |
| **Automated ETL** | Single **"Schedule WO"** (Toolbar 3) | `smallworld.drm_etl_scheduler_log` | `migrate_etl_scheduled_objects()` |

- `drm_etl_scheduler_log` has the **same columns** as `drm_scheduler_logs` **plus a `subject`** text column.
- Both runs share the exact same per-type migration logic and both write the ETL **summary
  `.txt` log** to TEMP on completion. The source table is swapped via a `.scheduler_table` slot.

```
                 UI                         TABLE                          RUN
Batch Import --> Add All      --> drm_scheduler_logs      --> migrate_scheduled_objects()   (manual)
Schedule WO  --> (single WO)  --> drm_etl_scheduler_log   --> migrate_etl_scheduled_objects() (automated)
```

---

## How to Run the Schedulers

Both runs are Magik CLI methods on `astri_data_migrator`. Create the migrator once, then call
whichever run you need (they are independent and can be run in any order):

```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])

# MANUAL run — processes smallworld.drm_scheduler_logs (queued by Batch Import "Add All")
migrator.migrate_scheduled_objects()

# AUTOMATED ETL run — processes smallworld.drm_etl_scheduler_log (queued by "Schedule WO")
migrator.migrate_etl_scheduled_objects()
```

Or a fresh migrator per run:

```magik
astri_data_migrator.new(gis_program_manager.databases[:gis]).migrate_scheduled_objects()
astri_data_migrator.new(gis_program_manager.databases[:gis]).migrate_etl_scheduled_objects()
```

Per-type only (manual table), if you need to run a single infra type:

```magik
migrator.migrate_feeder_scheduled_objects()
migrator.migrate_subfeeder_scheduled_objects()
migrator.migrate_cluster_scheduled_objects()
```

Each run processes `status='scheduled'` rows in the order **FEEDER → SUBFEEDER → CLUSTER**,
updates each row `scheduled → processing → migrated`/`failed`, and writes a summary `.txt`
to `%TEMP%` on completion.

---

## Part 1 — Batch Import (Toolbar 4) → `drm_scheduler_logs`

### Access control (root/admin only)

Toolbar 4 is **only shown to `root` or `admin`** users. `activate_in()` calls
`batch_import_allowed?()` (compares the current GIS user name, case-insensitive, to
`root`/`admin`; any lookup failure denies). When not allowed, the toolbar **row is omitted
entirely** — the outer container is built with **9 rows** instead of 10 and `build_toolbar4()`
is skipped, so the batch UI is fully hidden (not just disabled). No batch item keys exist for
unauthorized users; all other toolbars and behaviour are unchanged.


### TXT File Format (multi-type, bracketed headers)

```
[FEEDER]
BDG001511
BDG001402
[SUBFEEDER]
BBS000366
[CLUSTER]
JKT00001
```

Rules:
- A **section header** starts with `[` and ends with `]`; inner text (case-insensitive) must be
  `FEEDER`, `SUBFEEDER`, or `CLUSTER`.
- Code lines follow their header, one per line. Blank lines ignored.
- **1, 2, or 3 sections**, any order. Only present types are loaded.
- A code before any header, or an unknown header type, is a parse error.

### Toolbar 4 Components

| Item Key | Label | Initial | Callback |
|---|---|---|---|
| `:batch_browse_btn` | (browse icon) | enabled | `browse_batch_list()` |
| `:batch_list_path` | — (read-only) | — | — |
| `:batch_clear_btn` | "Clear" | disabled | `clear_batch_list()` |
| `:load_wo_list_btn` | "Load WO List" | disabled | `load_wo_list()` |
| `:add_all_btn` | **"Add All"** | disabled | **`add_all_to_scheduler()`** |

### Loading (`load_wo_list` → `fetch_batch_workorders`)

- **Per-type cap: max 25 codes / type (75 total).** If exceeded, a **blocking popup** lists the
  offending types and their counts; the load aborts (nothing fetched).
- WOs are resolved **one infra_code at a time** via the API's server-side code filter
  (feeder → `target_osp_route_code`; cluster/subfeeder → `target_<type>_code`). Page limit 50.
- **Progress footer** updates on every code (`Fetching [type] i/N: <code>`).
- Per-section log line `Section [type]: matched X / N`; unmatched codes logged `Not found`.
- **Error isolation** — each per-code fetch is wrapped in `_try/_when error`, so an API or
  XML-parse failure on one code is logged (`Fetch error [type]: <code> - skipped`) and the loop
  continues with the remaining codes. Empty results (`count=0`) also return cleanly:
  `parse_xml_response` guards the workorder loop so a no-`<data>` response yields an empty list
  instead of raising.
- Each WO is tagged with its **own** `:infrastructure_type`; deduped by infra_code.
- **Donation values are auto-fetched** as part of Load WO List (see below).

### Donation auto-fetch (`apply_batch_donations`)

Load WO List also fetches donation values so the **Donation column is populated automatically**
— the Admin no longer has to click "Get Donation WO" for a batch list.

- **Where:** invoked inside the batch branch of `workorder_list_data()`, immediately after
  `fetch_batch_workorders()` returns the WOs and **before** the table rows are built, so the
  Donation column renders in the **same single pass** (no extra `:renew`, no re-fetch of the
  WO list).
- **Per-WO type:** each WO uses its **own** `:infrastructure_type` (mixed-type files work),
  unlike the existing `get_donation_work_orders_for_list()` button which uses one filter type
  and blocks entirely on feeder.
- **Feeder skipped** — donation is not applicable to feeder WOs.
- Reuses the existing private `fetch_donation_value_for_wo(wo, infra_type)` per WO; sets
  `wo[:donation_value]` in place (rendered via `format_donation_display`).
- **Progress + log:** footer shows `Donation i/N: <code>`; a summary line logs
  `Batch donation: matched X / N (feeder skipped: K)`.
- **Cost:** batch Load WO List now issues the per-code WO fetches **plus** one donation call
  per non-feeder WO. This is the expected trade-off for auto-populating donations; progress is
  shown live.
- The standalone **"Get Donation WO"** button is unchanged for the normal (non-batch) flow.

### `add_all_to_scheduler()`

Loops the displayed WOs and inserts each into `drm_scheduler_logs` via
`engine.insert_scheduler_log()`. Per-WO validations (skip, never abort):
1. Skip if a Smallworld project/design already exists.
2. Skip if already present in `drm_scheduler_logs`.

Each inserted row sets **`description = "BULK"`**.
Reports a summary popup: `Added / Skipped (design) / Skipped (already scheduled) / Failed`.
**No apd_kmz H-1 check on this path** (that rule is ETL-only).

---

## Part 2 — Single "Schedule WO" (Toolbar 3) → `drm_etl_scheduler_log`

`schedule_wo()` (in `rwwi_astri_workorder_dialog_schedule.magik`) now targets the **ETL table**
with an extra condition and the `subject` column.

### Validations (block with popup)
1. Smallworld project/design already exists → block.
2. **`apd_kmz_upload_date` must be newer than H-1 (yesterday)** → else block.
   Checked via `engine.apd_kmz_recent?(infra_type, infra_code)`. The **block popup shows the
   WO's current `apd_kmz_upload_date`** (`(none)` when empty) so the user sees why it's blocked.
3. Already in `drm_etl_scheduler_log` (`engine.get_etl_scheduler_log_status`) → block.

### `subject` value
Built by `build_etl_subject(infra_type)`:
```
[SMALLWORLD] - SUMMARY ETL [<TYPE>] [<YYYY-MM-DD>]
```
Type label: cluster → `CLUSTER`, subfeeder → `SUB-FEEDER`, feeder → `FEEDER`.
(Multi-type would join with `|`, but a single Schedule WO is always one type.)

### Insert
`engine.insert_etl_scheduler_log(params)` — same columns as `insert_scheduler_log` plus
`:subject`. Each inserted row sets **`description = "SINGLE"`** (vs `"BULK"` for batch).

### H-1 check detail (`apd_kmz_recent?`)
Queries the dim master table for the type
(`dim_cluster_master_smallworld` / `dim_subfeeder_master_smallworld` / `dim_feeder_master_smallworld`):
```sql
SELECT COUNT(*) AS cnt FROM smallworld.dim_<type>_master_smallworld
WHERE <type>_code = ? AND apd_kmz_upload_date > (CURRENT_DATE - INTERVAL '1 day')
```
`cnt > 0` ⇒ recent (allow). NULL / missing / older upload dates ⇒ `cnt = 0` ⇒ blocked.

`apd_kmz_upload_date` is a **new column** in the three `dim_<type>_master_smallworld` tables.
It is now also added to the `sql_for_kmz_uuid` SELECTs and mapped through
`get_kmz_uuid_from_db` → `parse_xml_response` into each WO as `:apd_kmz_upload_date`, so the
value is available on the loaded work order (in addition to the fresh `apd_kmz_recent?` check).

---

## Part 3 — The Two Scheduler Runs (`astri_data_migrator.magik`)

A `.scheduler_table` slot selects the source table; it defaults to
`"smallworld.drm_scheduler_logs"` (set in `init`).

| Method | Table | Notes |
|---|---|---|
| `migrate_scheduled_objects()` (existing) | `.scheduler_table` (default `drm_scheduler_logs`) | **manual** run |
| `migrate_etl_scheduled_objects()` (**new**) | `drm_etl_scheduler_log` | **automated** run |

`get_scheduled_records()` and `update_scheduled_status()` now build their SQL from
`.scheduler_table`, so both runs reuse the identical per-type migration + summary-log code.

**Row selection is identical for BOTH runs** (in `get_scheduled_records`):

```sql
WHERE status = 'scheduled' AND infra_type = ?
  AND created_at >= (CURRENT_DATE - INTERVAL '1 day')
ORDER BY created_at ASC
```

Both jobs process rows with **`status = 'scheduled'`** for the given infra_type, created
**within the last day** (≥ yesterday); older rows are ignored. Infra types are always
processed in the fixed order **FEEDER → SUBFEEDER → CLUSTER** (the phase order of
`migrate_scheduled_objects()`, inherited by the ETL run).

```magik
_method astri_data_migrator.migrate_etl_scheduled_objects()
    .scheduler_table << "smallworld.drm_etl_scheduler_log"
    _protect
        >> _self.migrate_scheduled_objects()
    _protection
        .scheduler_table << "smallworld.drm_scheduler_logs"   # restore default
    _endprotect
_endmethod
```

CLI:
```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])
migrator.migrate_scheduled_objects()       # manual   -> drm_scheduler_logs
migrator.migrate_etl_scheduled_objects()   # automated -> drm_etl_scheduler_log
```

> The direct "Migrate All" engine methods from v2 (`migrate_wo_list` / `migrate_one_wo`) were
> **removed** — no UI path migrates directly anymore.

---

## Part 4 — Summary ETL Log (written by BOTH runs)

`write_batch_summary_log()` + `sum_*` helpers are called at the end of **every** scheduler run —
both `migrate_scheduled_objects()` (manual) and `migrate_etl_scheduled_objects()` (automated,
which delegates to it). Each run writes its own `.txt` to TEMP.

> **Date handling fix.** `date_time.now()` on this platform does **not** understand `.date` /
> `.time` (it raised `does not understand message date`). The helpers now parse
> `date_time.write_string` (`DD/MM/YYYY HH:MM:SS`) via `sum_parse_dt()` instead, so both reports
> generate correctly.

Output:

```
+=== [SMALLWORLD] - SUMMARY ETL [SUB-FEEDER] [2026-05-29] ===+
| Asset Level       : Sub-Feeder                       |
| Datetime          : 20260529_100545                  |
| Updated Timestamp : 2026-05-25 -- 2026-05-29         |
+------------------------------------------------------+
| Total processed   : 723                              |
| Total successful  : 659                              |
| Total failed      : 64                               |
| Success percentage: 91.15%                           |
| Total features written : 67106                       |
+======================================================+
+== SUB-FEEDER ========================================+
| Processed : 571 | Success : 518 | Failed : 53        |
| List succeed: BBS000366, BBS000404, ...              |
+------------------------------------------------------+
```

- File: `%TEMP%\SMALLWORLD - SUMMARY ETL - <YYYYMMDD_HHMMSS>.txt`
- New file per run (datetime in name); existing logs never overwritten.
- Fixed processing order **FEEDER → SUBFEEDER → CLUSTER**; absent types produce no section.

---

## Part 5 — Feeder Filter Enhancement (search by code AND area)

Previously feeder could only be filtered by **OSP Route Area** (`target_osp_route_area`), and
the single code field mapped there. Now feeder supports **both**:

- The main code field (`:filter_cluster`) is relabelled **"Feeder Code"** and maps to
  **`target_osp_route_code`** (the infra_code).
- A **new field** `:filter_osp_area` ("OSP Route Area") is shown **only for feeder** and maps
  to `target_osp_route_area`.
- Either or both may be supplied; both keys are forwarded to the API (pass-through).

Because `build_filter_params` / `infrastructure_type_changed` are **redefined in
`rwwi_astri_workorder_dialog_filters.magik`** (loaded after `dialog.magik`), that file holds
the **active** versions — both copies are kept in sync.

Batch import **Load WO List** now resolves feeder codes via `target_osp_route_code`
(infra_code), not the area field.

---

## Files Changed

| File | Change |
|---|---|
| `rwwi_astri_workorder_engine.magik` | **New:** `insert_etl_scheduler_log`, `get_etl_scheduler_log_status`, `apd_kmz_recent?`; both inserts now write a `description` column (`BULK`/`SINGLE`) |
| `astri_data_migrator.magik` | Fixed summary-log date handling (parse `date_time.write_string` via `sum_parse_dt`); `get_scheduled_records` selection for both runs (`status = 'scheduled'` AND `infra_type = ?` AND `created_at >= yesterday`) |
| `rwwi_astri_workorder_dialog_schedule.magik` | `schedule_wo()` → ETL table + H-1 check + `subject`; **new** `build_etl_subject()` |
| `rwwi_astri_workorder_dialog_batch_import.magik` | `migrate_all_wo` → **`add_all_to_scheduler`** (writes `drm_scheduler_logs`); per-code fetch; 25/type cap; progress footer |
| `rwwi_astri_workorder_dialog.magik` | Toolbar 4 button `:migrate_all_btn`/"Migrate All" → `:add_all_btn`/**"Add All"** (`add_all_to_scheduler`); slot `:batch_sections`; multi-section `workorder_list_data()` |
| `astri_data_migrator.magik` | **New slot** `:scheduler_table`; `get_scheduled_records`/`update_scheduled_status` use it; **new** `migrate_etl_scheduled_objects()`; **removed** `migrate_wo_list`/`migrate_one_wo` |
| `rwwi_astri_workorder_dialog.magik` | Toolbar 2: new `:filter_osp_area` field (feeder only); synced `infrastructure_type_changed`/`build_filter_params` duplicates |
| `rwwi_astri_workorder_dialog_filters.magik` | **Active** `infrastructure_type_changed` (show OSP-area field for feeder, relabel code → "Feeder Code") + `build_filter_params` (feeder code → `target_osp_route_code`, add `target_osp_route_area`) |
| `rwwi_astri_workorder_dialog_donation.magik` | **New** `apply_batch_donations(workorders)` — per-WO-type donation fetch for the batch list (feeder skipped) |
| `rwwi_astri_workorder_dialog.magik` | `workorder_list_data()` batch branch calls `apply_batch_donations()` after the WO fetch |
| `rwwi_astri_workorder_dialog.magik` | `activate_in()` gates Toolbar 4 on `batch_import_allowed?()` (9 vs 10 rows; skip `build_toolbar4`) |
| `rwwi_astri_workorder_dialog_batch_import.magik` | **New** `batch_import_allowed?()` — `root`/`admin` check |

---

## Notes

- **Two independent runs** — manual (`drm_scheduler_logs`) and automated
  (`drm_etl_scheduler_log`); a single migrator instance can run either via `.scheduler_table`.
- **Batch Import = queue only** — "Add All" inserts into `drm_scheduler_logs`; nothing migrates
  until the manual run executes.
- **Schedule WO = ETL queue** — inserts into `drm_etl_scheduler_log` only when
  `apd_kmz_upload_date > H-1`; stores the formatted `subject`.
- **Access control** — Toolbar 4 (batch import) is hidden entirely unless the current user is
  `root` or `admin` (row omitted, not just disabled).
- **Per-type cap 25 / 75 total** enforced at batch load with a blocking popup.
- **Donation auto-fetch** — Load WO List populates the Donation column in the same pass
  (per-WO type, feeder skipped); no separate "Get Donation WO" click needed for the batch list.
- **Summary log** written by **both** runs (manual + automated); new file per run, existing
  logs untouched. Date parts are parsed from `date_time.write_string` (platform has no
  `date_time.date`/`.time`).
- **`description` column** — batch/manual rows = `"BULK"`; single Schedule WO rows = `"SINGLE"`.
- **Run selection (both runs identical)** — `status = 'scheduled'` AND `infra_type = ?` AND
  `created_at >= yesterday`; infra types processed in order FEEDER → SUBFEEDER → CLUSTER.
