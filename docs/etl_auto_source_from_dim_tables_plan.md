# Implementation Plan: Automated ETL Source from Dim Master Tables

**Date:** 2026-07-16
**Author:** alami-ziezat
**Status:** IMPLEMENTED (Magik + `.bat` files) — pending live test + Task Scheduler registration

> **Implementation note (2026-07-16).** Parts 1–4 are coded:
> - `astri_data_migrator.magik` — `load_etl_source(infra_type)`,
>   `migrate_etl_scheduled_objects_for(infra_type, …)`, `get_scheduled_records` ETL branch
>   (no `created_at` cap, `ORDER BY schedule_date ASC`), and `write_batch_summary_log` gained an
>   optional `name_tag` so each per-type report is named `… - <TYPE> - <datetime>.html`.
> - `admin_drm_scheduler.magik` — `run_etl_for_type(infra_type)`.
> - `admin_drm_batch.magik` — dispatches on `DRM_ETL_INFRA_TYPE` when `DRM_RUN_TYPE=etl`.
> - `resources/base/bin/` — `ADMIN_DRM_etl_cluster.bat` / `_subfeeder.bat` / `_feeder.bat` (CRLF).
> - **Part 6 done (2026-07-16):** the dead single-WO ETL source was removed —
>   `rwwi_astri_workorder_dialog_schedule.magik` deleted (+ unregistered from `load_list.txt`),
>   and engine methods `insert_etl_scheduler_log` / `get_etl_scheduler_log_status` /
>   `apd_kmz_recent?` removed. No source references remain.
>
> **Operational caveat — failed rows are not auto-retried.** Dedup (D1) is `NOT EXISTS` on
> `(infra_code, infra_type)` against **all** statuses, and the run only picks `status='scheduled'`.
> So a row that ends `failed` is neither re-inserted by the loader nor re-processed by the run —
> it stays `failed` until someone resets it to `scheduled` (or deletes it). This is consistent
> with D1 ("once queued, never re-queued") but worth an explicit retry decision later.

> **Supersedes** the user-triggered ETL source. The "Schedule WO" button (removed
> 2026-07-16) previously fed `smallworld.drm_etl_scheduler_log` one row at a time via
> `insert_etl_scheduler_log()`. This plan replaces that manual source with an **automated
> loader** that reads the three `dim_*_master_smallworld` tables directly, run
> **independently per infra type**.

---

## 1. Goal (client requirement)

The ETL queue (`smallworld.drm_etl_scheduler_log`) must be populated automatically from
three source tables instead of a UI action:

1. `smallworld.dim_cluster_master_smallworld`
2. `smallworld.dim_subfeeder_master_smallworld`
3. `smallworld.dim_feeder_master_smallworld`

Rules:

1. Read each source table and insert into the target (ETL scheduler log) every record whose
   `apd_kmz_upload_date` is **within the last month** (H … H-30, i.e. `>= CURRENT_DATE - INTERVAL '1 month'`).
2. The ETL runs **independently for each infra type** — **3 separate scheduler runs**.
3. **Oldest data first** — process earliest `apd_kmz_upload_date` first.
4. Each of the 3 runs uses the **same processing time window** as the existing ETL run
   (default 22:00 → 09:00, via `DRM_ETL_START` / `DRM_ETL_END`).
5. **Re-runnable** — every run re-reads the source; only genuinely new records are inserted;
   **no duplicates**.

### Decisions (confirmed)

| # | Decision | Choice |
|---|---|---|
| D1 | Duplicate key | **`(infra_code, infra_type)`** — once queued, never re-queued (even on KMZ re-upload). |
| D2 | Run topology | **3 separate OS scheduled jobs** (`cluster` / `subfeeder` / `feeder`), each loads + processes + e-mails its own type. |
| D3 | Backlog handling | **Drop the `created_at >= yesterday` cap** for the ETL path; select **all** `status='scheduled'` rows for the type, `ORDER BY schedule_date ASC` (oldest KMZ first). Dedup (D1) prevents re-insert, so backlog always drains. |

---

## 2. Architecture: before vs after

**Before (user-driven source):**

```
UI "Schedule WO" ──insert_etl_scheduler_log()──► drm_etl_scheduler_log
                                                        │
                    migrate_etl_scheduled_objects() ────┘  (all 3 types, 1 combined run + 1 email)
```

**After (automated per-type source):**

```
                     ┌─ load_etl_source(:cluster)   ─► INSERT…SELECT from dim_cluster_master_smallworld
drm_etl_scheduler_log┼─ load_etl_source(:subfeeder) ─► INSERT…SELECT from dim_subfeeder_master_smallworld
                     └─ load_etl_source(:feeder)    ─► INSERT…SELECT from dim_feeder_master_smallworld

3 independent jobs (each: load its type ► process its type ► write summary ► e-mail):
  ADMIN_DRM_etl_cluster.bat    → migrate_etl_scheduled_objects_for(:cluster)
  ADMIN_DRM_etl_subfeeder.bat  → migrate_etl_scheduled_objects_for(:subfeeder)
  ADMIN_DRM_etl_feeder.bat     → migrate_etl_scheduled_objects_for(:feeder)
```

The loader is **set-based SQL** — the dim tables and `drm_etl_scheduler_log` both live in the
same PostgreSQL `smallworld` schema, so a single `INSERT … SELECT … WHERE NOT EXISTS` does the
whole load+dedup with no row-by-row Magik.

---

## 3. Part 1 — The ETL source loader (new)

### Method

`astri_data_migrator.load_etl_source(infra_type)` — **new private method** on the migrator
(kept off the dialog engine so the batch/CLI run has zero UI dependency). Returns the number
of rows inserted.

Runs **one** `INSERT … SELECT` against `[POSTGRESQL_ASTRI_DB]`, reusing the migrator's existing
`.db_connection` open/commit/`extdb_java_acp.close_all()` pattern (identical to
`get_scheduled_records()` / `update_scheduled_status()`).

### Per-type source config

Columns confirmed against the live dim-table schema (2026-07-16).

| infra_type | Source table | Code col | Name col | UUID | Date col |
|---|---|---|---|---|---|
| cluster | `dim_cluster_master_smallworld` | `cluster_code` | `cluster_name` | `apd_kmz_uuid` | `apd_kmz_upload_date` |
| subfeeder | `dim_subfeeder_master_smallworld` | `subfeeder_code` | `subfeeder_name` | `apd_kmz_uuid` | `apd_kmz_upload_date` |
| feeder | `dim_feeder_master_smallworld` | `feeder_code` | `feeder_name` | `apd_kmz_uuid` | `apd_kmz_upload_date` |

- **UUID = `apd_kmz_uuid`** (not the ABD-preferring `COALESCE` used by the interactive
  `sql_for_kmz_uuid`). This is the APD-driven pipeline: the source window is `apd_kmz_upload_date`
  and the filter requires `apd_kmz_uuid IS NOT NULL`, so the APD KMZ is the one to migrate.
- **NOT-NULL guards (required):** every loaded row must have `<type>_code IS NOT NULL`
  **and** `apd_kmz_uuid IS NOT NULL`.

### The SQL (cluster shown; subfeeder/feeder identical with substitutions)

```sql
INSERT INTO smallworld.drm_etl_scheduler_log
  (infra_code, name, infra_type, username, scheduler_username,
   schedule_date, start_time, end_time, status,
   topology, uuid, subject, description, created_at, updated_at)
SELECT
  src.cluster_code,                                  -- infra_code
  COALESCE(src.cluster_name, src.cluster_code),      -- name (null-safe)
  'cluster',                                         -- infra_type
  'ETL_AUTO', 'ETL_AUTO',                            -- username / scheduler_username
  src.apd_kmz_upload_date,                           -- schedule_date  (drives old-first order)
  CURRENT_TIME, CURRENT_TIME,                        -- start_time / end_time
  'scheduled',                                       -- status
  src.topology,                                      -- topology
  src.apd_kmz_uuid,                                  -- uuid (APD kmz)
  '[SMALLWORLD] - SUMMARY ETL [CLUSTER] [' || TO_CHAR(CURRENT_DATE,'YYYY-MM-DD') || ']',
  'ETL_AUTO',                                        -- description (distinguishes source)
  NOW(), NOW()
FROM smallworld.dim_cluster_master_smallworld src
WHERE src.apd_kmz_upload_date >= (CURRENT_DATE - INTERVAL '1 month')   -- H … H-30 (rule 1)
  AND src.apd_kmz_upload_date <= CURRENT_DATE                          -- ignore future-dated
  AND src.cluster_code  IS NOT NULL                                   -- infra_code required
  AND src.apd_kmz_uuid  IS NOT NULL                                   -- APD KMZ required
  AND NOT EXISTS (                                                     -- dedup D1 (rule 5)
      SELECT 1 FROM smallworld.drm_etl_scheduler_log t
      WHERE t.infra_code = src.cluster_code
        AND t.infra_type = 'cluster'
  );
```

- **Subject label** per type: cluster → `CLUSTER`, subfeeder → `SUB-FEEDER`, feeder → `FEEDER`
  (matches the removed `build_etl_subject`).
- **`schedule_date = apd_kmz_upload_date`** is the key detail — it carries the source upload
  date into the target so the run can order oldest-first (rule 3) without a schema change.
- **`description = 'ETL_AUTO'`** distinguishes loader rows from the old `SINGLE` / `BULK` rows.

---

## 4. Part 2 — Per-type ETL run (new entry point)

`astri_data_migrator.migrate_etl_scheduled_objects_for(infra_type, _optional window_start, window_end)`
— **new public method**. One infra type end-to-end:

```
1. resolve time window   (args → DRM_ETL_START/END → 22:00/09:00)   [reuse parse_hhmm]
2. .scheduler_table  << "smallworld.drm_etl_scheduler_log"
3. _self.load_etl_source(infra_type)          # populate the queue for THIS type (Part 1)
4. run just this type:
     :cluster   -> s << migrate_cluster_scheduled_objects()
     :subfeeder -> s << migrate_subfeeder_scheduled_objects()
     :feeder    -> s << migrate_feeder_scheduled_objects()
5. fold s into a single-type overall_stats  (only this type's keys populated)
6. write_batch_summary_log(overall_stats, start, end)   # one card, this type
7. restore .scheduler_table / clear window  (in _protection)
8. >> result   (incl. :log_file / :log_name for the e-mail)
```

Notes:
- Wrap steps 2–6 in `_protect … _protection` and capture `result` in a local before the
  `_protection` block (the existing `migrate_etl_scheduled_objects` has a comment documenting
  that `>>` inside `_protect` returns `_unset` — same trap applies here).
- `migrate_*_scheduled_objects()` already return `s` with `:total, :migrated, :errors,
  :succeeded_codes, :failed_codes, :features_written`; map those into an `overall_stats`
  shaped exactly like `migrate_scheduled_objects()` builds, leaving the other two types at 0.
  `write_batch_summary_log` then renders a single card for the present type (absent types
  produce no card) — no change needed to the summary writer.
- The existing combined `migrate_etl_scheduled_objects()` (all 3 types, one report) may be
  **kept** for manual/ad-hoc use; the 3 jobs use the new per-type method.

---

## 5. Part 3 — `get_scheduled_records` ordering (backlog + old-first)

Per D3, the ETL path selects **all** pending rows oldest-first, with **no** `created_at` cap.
Branch on the active table so the **manual** run (`drm_scheduler_logs`) keeps its current
behaviour:

```magik
_local etl? << .scheduler_table.default("").matches?("*drm_etl_scheduler_log*")

_local sql << _if etl?
_then
    >> "SELECT infra_code, name, infra_type, topology, uuid, status, " +
       "scheduler_username, schedule_date " +
       "FROM " + table + " " +
       "WHERE status = 'scheduled' AND infra_type = ? " +
       "ORDER BY schedule_date ASC, infra_code ASC"          # rule 3: oldest KMZ first
_else
    >> "SELECT infra_code, name, infra_type, topology, uuid, status, " +
       "scheduler_username, schedule_date " +
       "FROM " + table + " " +
       "WHERE status = 'scheduled' AND infra_type = ? " +
       "AND created_at >= (CURRENT_DATE - INTERVAL '1 day') " +   # manual path unchanged
       "ORDER BY created_at ASC"
_endif
```

The `scheduled → processing → migrated/failed` lifecycle and the per-type time-window
`_leave` (rule 4, via `in_time_window?`) are unchanged. A row left as `scheduled` when the
window closes is simply picked up by the **next** run (dedup means the loader won't duplicate
it), so the backlog drains oldest-first over successive nights.

---

## 6. Part 4 — OS wiring: 3 independent jobs

### `admin_drm_batch.magik` (stdin caller) — extend

Read a new `DRM_ETL_INFRA_TYPE` env var and dispatch per type when `DRM_RUN_TYPE=etl`:

```magik
_local mode_str  << system.getenv("DRM_RUN_TYPE").default("manual").lowercase
_if mode_str = "etl"
_then
    _local it_str << system.getenv("DRM_ETL_INFRA_TYPE").default("").lowercase   # cluster|subfeeder|feeder
    admin_drm_scheduler.run_etl_for_type(it_str)
_else
    admin_drm_scheduler.run_and_email(:manual)
_endif
```

### `admin_drm_scheduler.magik` — add `run_etl_for_type(infra_type)`

Mirror `run_and_email`, but call the per-type run and e-mail its report:

```magik
_method admin_drm_scheduler.run_etl_for_type(infra_type_str)
    _local it << infra_type_str.default("").as_symbol()
    _if {:cluster, :subfeeder, :feeder}.includes?(it).not
    _then
        write("admin_drm_scheduler: invalid DRM_ETL_INFRA_TYPE=", infra_type_str)
        _return _false
    _endif
    _local migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])
    _local result   << migrator.migrate_etl_scheduled_objects_for(it)
    _if result _is _unset _then _return _false _endif
    _local log_path << result[:log_file]
    _if log_path _is _unset _then _return _false _endif
    >> _self.email_report(log_path, result[:log_name])
_endmethod
```

### Three `.bat` files (`resources/base/bin/`)

Clone the existing `ADMIN_DRM_etl_scheduler.bat` into three, each pinning both env vars
(so a leaked var can't flip type/mode):

| Job | New `.bat` | `DRM_RUN_TYPE` | `DRM_ETL_INFRA_TYPE` |
|---|---|---|---|
| Cluster ETL | `ADMIN_DRM_etl_cluster.bat` | `etl` | `cluster` |
| Sub-Feeder ETL | `ADMIN_DRM_etl_subfeeder.bat` | `etl` | `subfeeder` |
| Feeder ETL | `ADMIN_DRM_etl_feeder.bat` | `etl` | `feeder` |

All three keep the shared `DRM_ETL_START`/`DRM_ETL_END` (22:00/09:00 — rule 4),
`DRM_SCHEDULER_DIR`, and pipe the same `admin_drm_batch.magik` (single source of truth).
Register each as its own Windows Task Scheduler entry (independent — rule 2).

> **Legacy combined bat removed (2026-07-16).** The old `ADMIN_DRM_etl_scheduler.bat`
> (`DRM_RUN_TYPE=etl`, no `DRM_ETL_INFRA_TYPE`) was deleted — under the new dispatch it would call
> `run_etl_for_type("")` and error out. The combined all-types run survives as a **CLI-only**
> call (`migrate_etl_scheduled_objects()` / `run_and_email(:etl)`), not wired to any `.bat`.

---

## 7. Part 5 — Per-type summary log + e-mail

- Each job writes its **own** HTML summary (one card, its type) and e-mails it — subject =
  `last_log_name()`, as today.
- **Recommend** adding the infra type to the log file name so the three don't look identical:
  `SMALLWORLD - SUMMARY ETL - <TYPE> - <YYYYMMDD_HHMMSS>.html`. Small tweak in the file-name
  build inside `write_batch_summary_log` (or pass the type through). Optional but advised.

---

## 8. Part 6 — Retire the dead UI/source path (optional cleanup)

The loader supersedes the old single-row source. Once Parts 1–5 are in, these become dead:

| Symbol | File | Note |
|---|---|---|
| `schedule_wo()`, `build_etl_subject()` | `rwwi_astri_workorder_dialog_schedule.magik` | whole file; unregister from `load_list.txt` |
| `insert_etl_scheduler_log()` | `rwwi_astri_workorder_engine.magik` | replaced by loader `INSERT…SELECT` |
| `get_etl_scheduler_log_status()` | `rwwi_astri_workorder_engine.magik` | replaced by loader `NOT EXISTS` |
| `apd_kmz_recent?()` | `rwwi_astri_workorder_engine.magik` | date filter now in loader SQL |

Keep for now if a manual single-WO schedule is still wanted; otherwise remove in a follow-up.

---

## 9. Field mapping (source dim → target `drm_etl_scheduler_log`)

| Target column | Source / value |
|---|---|
| `infra_code` | `<type>_code` |
| `name` | `<type>_name` (null-safe fallback `<type>_code`) |
| `infra_type` | literal `'cluster'` / `'subfeeder'` / `'feeder'` |
| `username`, `scheduler_username` | `'ETL_AUTO'` |
| `schedule_date` | `apd_kmz_upload_date` ← **enables old-first ordering** |
| `start_time`, `end_time` | `CURRENT_TIME` |
| `status` | `'scheduled'` |
| `topology` | `topology` |
| `uuid` | `apd_kmz_uuid` (APD pipeline; guaranteed non-null by filter) |
| `subject` | `[SMALLWORLD] - SUMMARY ETL [<LABEL>] [YYYY-MM-DD]` |
| `description` | `'ETL_AUTO'` |
| `created_at`, `updated_at` | `NOW()` |

---

## 10. Files changed

| File | Change |
|---|---|
| `astri_data_migrator.magik` | **New** `load_etl_source(infra_type)` (INSERT…SELECT + dedup); **new** `migrate_etl_scheduled_objects_for(infra_type, …)`; `get_scheduled_records` branches on ETL table (no `created_at` cap, `ORDER BY schedule_date ASC`); optional log-name includes type |
| `admin_drm_scheduler.magik` | **New** `run_etl_for_type(infra_type)` |
| `admin_drm_batch.magik` | Read `DRM_ETL_INFRA_TYPE`; dispatch to `run_etl_for_type` when `DRM_RUN_TYPE=etl` |
| `resources/base/bin/ADMIN_DRM_etl_cluster.bat` *(new)* | job: cluster |
| `resources/base/bin/ADMIN_DRM_etl_subfeeder.bat` *(new)* | job: subfeeder |
| `resources/base/bin/ADMIN_DRM_etl_feeder.bat` *(new)* | job: feeder |
| *(optional)* `rwwi_astri_workorder_dialog_schedule.magik`, `_engine.magik`, `load_list.txt` | retire dead single-WO source (Part 6) |

---

## 11. Test / rollout

1. **Loader (SQL only), per type:** run `load_etl_source(:cluster)` at the Magik prompt; confirm
   N rows inserted with `status='scheduled'`, `description='ETL_AUTO'`, `schedule_date` = the
   source `apd_kmz_upload_date`. Run it **again** → **0 inserted** (dedup proof, rule 5).
2. **Date window:** verify only rows with `apd_kmz_upload_date >= CURRENT_DATE - 1 month` were
   picked up; temporarily back-date a source row beyond a month and confirm it's excluded.
3. **Old-first:** `migrate_etl_scheduled_objects_for(:cluster)` and confirm the processing log
   order follows ascending `apd_kmz_upload_date`.
4. **Independence:** run the three per-type calls separately; confirm 3 distinct summary logs
   and 3 e-mails; a failure in one type never touches the others.
5. **Time window:** confirm processing stops at window end and the backlog resumes oldest-first
   on the next run.
6. **End-to-end:** trigger each new `.bat` via Task Scheduler; confirm load → migrate → HTML
   report → e-mail.

---

## 12. Open items to confirm

- **Schedule cadence/stagger** — do the 3 jobs fire at the same clock time or staggered? The
  22:00–09:00 window bounds *processing*; overlapping DB writes are fine (independent rows),
  but staggering keeps report timing clean.

### Resolved (2026-07-16)

- **`name` column** — ✅ confirmed present: `cluster_name` / `subfeeder_name` / `feeder_name`.
- **UUID choice** — ✅ store `apd_kmz_uuid`; filter requires it `IS NOT NULL`.
- **NOT-NULL guards** — ✅ `<type>_code IS NOT NULL` and `apd_kmz_uuid IS NOT NULL` on every row
  (KMZ-less / code-less rows are skipped — they can't be migrated).
