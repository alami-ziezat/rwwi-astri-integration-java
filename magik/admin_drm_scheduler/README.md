# admin_drm_scheduler

Smallworld module that runs the DRM scheduler migrations and **e-mails their HTML summary
report** (via the bundled `cmail.exe`).

There are **four scheduler jobs** plus a reusable e-mail helper. Two audiences use this module:

- **Admins / operators** run the jobs — see **[Section A](#a-running-the-jobs-from-admin-operations)**.
- **Developers** call it from another Magik module — see **[Section B](#b-using-the-drm-batch-from-another-module-integration)**.

> **ETL source changed (2026-07-16).** The automated ETL queue is no longer fed by a UI action
> ("Schedule WO", removed). Each per-type ETL job now **auto-loads its own queue** from the
> `smallworld.dim_<type>_master_smallworld` table (records with `apd_kmz_upload_date` within the
> last month), then processes it oldest-first. Full design:
> `docs/etl_auto_source_from_dim_tables_plan.md`.

---

## The jobs at a glance

| Job | `.bat` | Queue table | Fed by | Migrator method |
|---|---|---|---|---|
| **Manual DRM** | `ADMIN_DRM_scheduler.bat` | `smallworld.drm_scheduler_logs` | Work Order dialog "Add All" (UI) | `migrate_scheduled_objects()` |
| **ETL — cluster** | `ADMIN_DRM_etl_cluster.bat` | `smallworld.drm_etl_scheduler_log` | auto-load `dim_cluster_master_smallworld` | `migrate_etl_scheduled_objects_for("cluster")` |
| **ETL — subfeeder** | `ADMIN_DRM_etl_subfeeder.bat` | `smallworld.drm_etl_scheduler_log` | auto-load `dim_subfeeder_master_smallworld` | `migrate_etl_scheduled_objects_for("subfeeder")` |
| **ETL — feeder** | `ADMIN_DRM_etl_feeder.bat` | `smallworld.drm_etl_scheduler_log` | auto-load `dim_feeder_master_smallworld` | `migrate_etl_scheduled_objects_for("feeder")` |

- The **manual DRM** job processes rows queued by the UI. It does **not** auto-load from any dim table.
- Each **ETL** job is **independent**: it loads its own queue, processes only its type, and
  e-mails its own report. Run them on separate schedules if you like.
- A **combined all-types ETL run** still exists but is **CLI-only** (no `.bat`) — see
  [A.6](#a6-running-directly-from-the-magik-cli-no-bat).

**Selection / ordering:**
- Manual DRM: `status='scheduled' AND infra_type=? AND created_at >= yesterday`, oldest `created_at` first.
- ETL: after loading, `status='scheduled' AND infra_type=?` with **no date cap**, ordered by
  `schedule_date ASC` (= **oldest `apd_kmz_upload_date` first**).

All jobs write a new HTML report to `%TEMP%` and e-mail it. ETL reports are named
`SMALLWORLD - SUMMARY ETL - <TYPE> - <datetime>.html`; the manual report omits the `<TYPE>`.

---

## Module layout

```
admin_drm_scheduler/
├── module.def                         # requires: base, rwwi_astri_integration
├── load_list.txt                      # -> source/
├── source/
│   ├── load_list.txt
│   └── admin_drm_scheduler.magik      # the loadable object (email_report / run_and_email / run_etl_for_type)
└── resources/base/
    ├── data/                          # located at runtime via get_resource_file(:data)
    │   ├── cmail.exe
    │   ├── cmail-nossl.exe
    │   └── recipients.txt
    └── bin/                           # OS-scheduler artifacts
        ├── ADMIN_DRM_scheduler.bat        # manual DRM run
        ├── ADMIN_DRM_etl_cluster.bat      # per-type ETL run: CLUSTER
        ├── ADMIN_DRM_etl_subfeeder.bat    # per-type ETL run: SUBFEEDER
        ├── ADMIN_DRM_etl_feeder.bat       # per-type ETL run: FEEDER
        └── admin_drm_batch.magik          # stdin caller piped by all .bat files
```

- `cmail.exe` + `recipients.txt` are **module resources** under `resources/base/data/` and are
  located at runtime with
  `smallworld_product.get_resource_file("cmail.exe", :data, :admin_drm_scheduler)`.
- Edit **`resources/base/data/recipients.txt`** to change the mailing list (one recipient per
  line: `user@example.com` or `Name <user@example.com>`).

---

# A. Running the jobs from admin (operations)

This section is for the person who **operates** the scheduler on the server. You have three ways
to run any job: **on demand** (double-click / `CALL`), **scheduled** (Windows Task Scheduler),
or **directly from the Magik CLI**.

## A.1 How a `.bat` job works (read this once)

Every `.bat` in `resources/base/bin/` does the same five things:

1. Sets the GIS environment (`SW_GIS_ENVIRONMENT_FILE`, `SW_GIS_ALIAS_FILES`).
2. Sets **`DRM_RUN_TYPE`** (`manual` or `etl`) and, for ETL, **`DRM_ETL_INFRA_TYPE`**
   (`cluster` | `subfeeder` | `feeder`).
3. Sets **`DRM_SCHEDULER_DIR`** — the folder holding the `.bat`, the stdin caller
   `admin_drm_batch.magik`, and a writable `logs\`.
4. Builds a timestamped log path under `%DRM_SCHEDULER_DIR%logs\`.
5. Boots a **headless GIS session** and pipes `admin_drm_batch.magik` into it:

   ```bat
   CALL %SW_GIS_ENVIRONMENT_FILE%
   CALL <runalias.exe path> <alias> -noiteractive -cli -login "root/" < %DRM_SCHEDULER_DIR%admin_drm_batch.magik > %JOB_SERVER_LOG%
   ```

Inside that session, **`admin_drm_batch.magik`** reads the env vars and dispatches:

```
DRM_RUN_TYPE = manual  ->  admin_drm_scheduler.run_and_email(:manual)
DRM_RUN_TYPE = etl     ->  admin_drm_scheduler.run_etl_for_type( DRM_ETL_INFRA_TYPE )
```

So the **only** difference between the four jobs is which env vars the `.bat` sets. The Magik
does the rest (load queue → migrate → write HTML report → e-mail it).

## A.2 The manual DRM job

**File:** `ADMIN_DRM_scheduler.bat` — key lines:

```bat
SET DRM_RUN_TYPE=manual
SET DRM_SCHEDULER_DIR=F:\SW5\Scheduler\admin_drm_scheduler\resources\base\bin\
```

- Processes `smallworld.drm_scheduler_logs` (rows queued by the Work Order dialog's **"Add All"**).
- Order **FEEDER → SUBFEEDER → CLUSTER**. Not time-boxed.
- Log file prefix: `drm_`. Report: `%TEMP%\SMALLWORLD - SUMMARY ETL - <datetime>.html`.

## A.3 The three ETL jobs (one per infra type)

Each type has its **own** `.bat`. They are fully independent — schedule them together or apart.

| `.bat` | `DRM_ETL_INFRA_TYPE` | Source table (auto-loaded) | Log prefix |
|---|---|---|---|
| `ADMIN_DRM_etl_cluster.bat` | `cluster` | `dim_cluster_master_smallworld` | `drm_etl_cluster_` |
| `ADMIN_DRM_etl_subfeeder.bat` | `subfeeder` | `dim_subfeeder_master_smallworld` | `drm_etl_subfeeder_` |
| `ADMIN_DRM_etl_feeder.bat` | `feeder` | `dim_feeder_master_smallworld` | `drm_etl_feeder_` |

Key lines (cluster shown):

```bat
SET DRM_RUN_TYPE=etl
SET DRM_ETL_INFRA_TYPE=cluster
SET DRM_SCHEDULER_DIR=F:\SW5\Scheduler\admin_drm_scheduler\resources\base\bin\
REM Optional processing time window (HH:MM). Default 22:00 - 09:00.
REM SET DRM_ETL_START=22:00
REM SET DRM_ETL_END=09:00
```

**What one ETL job does, in order:**
1. **Load** — inserts into `drm_etl_scheduler_log` every row from its dim table where
   `apd_kmz_upload_date >= CURRENT_DATE - 1 month`, the code is not null, and `apd_kmz_uuid` is
   not null — **skipping any `(infra_code, infra_type)` already in the table** (no duplicates).
2. **Process** — migrates the `status='scheduled'` rows for that type, **oldest APD KMZ first**,
   updating each `scheduled → processing → migrated`/`failed`.
3. **Report + e-mail** — writes `SMALLWORLD - SUMMARY ETL - <TYPE> - <datetime>.html` to `%TEMP%`
   and e-mails it.

**Processing time window:** an ETL job only processes while the current clock time is within
`[DRM_ETL_START, DRM_ETL_END]` (default **22:00 → 09:00**, crossing midnight). When the window
ends, it **stops** — unprocessed rows stay `scheduled` and are picked up on the next run — and
the report is **still e-mailed**. Override per `.bat` by uncommenting `DRM_ETL_START` /
`DRM_ETL_END`.

> **Re-running is safe.** Because the load step dedups on `(infra_code, infra_type)`, running an
> ETL job again re-reads the source, inserts only genuinely new rows, and drains any backlog
> oldest-first. A second run with nothing new inserts 0 rows (expected).

## A.4 Environment variables (full reference)

Edit these at the top of each `.bat` for your server:

| Variable | Set in | Meaning |
|---|---|---|
| `SW_GIS_ENVIRONMENT_FILE` | every `.bat` | path to the GIS `environment.bat` (e.g. `F:\SW5\PNI_FTTH536\core\config\environment.bat`) |
| `SW_GIS_ALIAS_FILES` | every `.bat` | path to the `gis_aliases` file |
| `DRM_RUN_TYPE` | every `.bat` | `manual` or `etl` (already set per file) |
| `DRM_ETL_INFRA_TYPE` | ETL `.bat`s | `cluster` \| `subfeeder` \| `feeder` (already set per file). **Required** for ETL; if blank/invalid the run errors out and does nothing. |
| `DRM_SCHEDULER_DIR` | every `.bat` | folder holding the `.bat`, `admin_drm_batch.magik`, and `logs\` — **trailing `\` required** |
| `DRM_ETL_START` / `DRM_ETL_END` | ETL `.bat`s (optional) | processing time window `HH:MM`; default `22:00` / `09:00` |
| the `runalias.exe` path + alias name | every `.bat` | the product/alias to boot — **must load this module** (default alias `dev_ftth_myrep_custom_open`) |

> `DRM_SCHEDULER_DIR` only locates the stdin caller + the `logs\` output. `cmail.exe` /
> `recipients.txt` are located from the **loaded module** resources, not from here.

## A.5 Running on demand

- Double-click the `.bat`, **or** from a command prompt: `CALL ADMIN_DRM_etl_cluster.bat`.
- The console window closes when the headless GIS session finishes; read the log (A.7) for results.

## A.6 Running directly from the Magik CLI (no `.bat`)

If you already have a Magik session on the server, you can run any job by hand. This is also how
you run the **combined all-types ETL** (there is no `.bat` for it):

```magik
# --- via the module object (runs the migration AND e-mails the report) ---
admin_drm_scheduler.run_and_email(:manual)        $   # manual DRM (drm_scheduler_logs)
admin_drm_scheduler.run_etl_for_type("cluster")   $   # one ETL type (auto-load + process + e-mail)
admin_drm_scheduler.run_etl_for_type("subfeeder") $
admin_drm_scheduler.run_etl_for_type("feeder")    $
admin_drm_scheduler.run_and_email(:etl)           $   # combined ETL, CLI-only: processes whatever
                                                      # is already queued; does NOT auto-load dims
```

You can also override the ETL window from the CLI (per-type run passes it straight through):

```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])   $
migrator.migrate_etl_scheduled_objects_for("cluster", "20:00", "07:00")    $
```

## A.7 Setting up Windows Task Scheduler

For each job you want automated (typically the 3 ETL `.bat`s, and optionally the manual DRM):

1. Open **Task Scheduler** → **Create Task** (not "Basic Task").
2. **General:** name it (e.g. `DRM ETL Cluster`); select **Run whether user is logged on or
   not**; tick **Run with highest privileges**.
3. **Triggers:** New → e.g. **Daily** at the start of your ETL window (e.g. `22:00`). Stagger the
   three ETL jobs by a few minutes if you don't want them hitting the DB at the same instant
   (they are independent, so overlap is safe — staggering just keeps reports tidy).
4. **Actions:** New → **Start a program** → Program/script = the full path to the `.bat`
   (e.g. `F:\SW5\Scheduler\admin_drm_scheduler\resources\base\bin\ADMIN_DRM_etl_cluster.bat`).
   Set **Start in** to that `bin\` folder.
5. **Settings:** allow the task to run on demand; your choice on "stop if runs longer than".
6. Repeat for `ADMIN_DRM_etl_subfeeder.bat` and `ADMIN_DRM_etl_feeder.bat`.

## A.8 Where the output goes

- **Session log** (console output of the run): `%DRM_SCHEDULER_DIR%logs\<prefix><timestamp>.log`
  — prefixes: `drm_` (manual), `drm_etl_cluster_`, `drm_etl_subfeeder_`, `drm_etl_feeder_`.
- **HTML report** (what gets e-mailed): `%TEMP%\SMALLWORLD - SUMMARY ETL[ - <TYPE>] - <datetime>.html`.
- **E-mail:** sent to everyone in `recipients.txt`; subject = the report base name.

## A.9 Deployment checklist (server)

1. The booted product/alias **loads `admin_drm_scheduler`** (it's a `requires` of
   `rwwi_astri_workorder`, so booting that product pulls it in) — needed so `get_resource_file`
   can find `cmail.exe`/`recipients.txt`.
2. Copy the `.bat` files + `admin_drm_batch.magik` into `DRM_SCHEDULER_DIR`, with a writable
   `logs\` subfolder. This copy can live outside the product tree.
3. Edit each `.bat`'s paths (`SW_GIS_ENVIRONMENT_FILE`, `SW_GIS_ALIAS_FILES`, `DRM_SCHEDULER_DIR`,
   `runalias.exe` path, alias name) for your server.
4. Edit `resources/base/data/recipients.txt` for the mailing list.
5. Register the Task Scheduler entries (A.7).

---

# B. Using the DRM batch from another module (integration)

This section is for a **developer** calling the scheduler from another Magik module (the way the
Work Order dialog does for its "Migrate All" button).

## B.1 Make the module available

Add `admin_drm_scheduler` to your product/alias so it loads. It is already declared as a
**`requires`** of `rwwi_astri_workorder`, so any product that boots the Work Order dialog already
has it. The loaded object is the exemplar **`admin_drm_scheduler`** itself (call methods on the
class, not on an instance).

## B.2 Public API

```magik
## E-mail an already-produced HTML report. Subject = log_name.
## Returns _true if the mail command was issued, _false otherwise.
admin_drm_scheduler.email_report(log_path, log_name)

## Run a scheduler migration AND e-mail its report.
##   :manual (default) -> migrate_scheduled_objects()      (drm_scheduler_logs)
##   :etl              -> migrate_etl_scheduled_objects()   (drm_etl_scheduler_log, combined; does
##                                                           NOT auto-load the dim tables)
## Returns _true if a report was produced and e-mailed.
admin_drm_scheduler.run_and_email(:manual)
admin_drm_scheduler.run_and_email(:etl)

## Run ONE per-type automated ETL job end-to-end:
##   auto-load the dim source -> process that type -> e-mail its report.
##   arg: "cluster" | "subfeeder" | "feeder"  (invalid -> logs error, returns _false)
## Returns _true if a report was produced and e-mailed.
admin_drm_scheduler.run_etl_for_type("cluster")
```

## B.3 Pattern 1 — run a job and e-mail it, in one call

The simplest integration. Let the module do everything:

```magik
_if admin_drm_scheduler.run_etl_for_type("cluster")
_then
    # report was generated and mailed
_else
    # bad type, no result, or no report produced (see the session log)
_endif
```

## B.4 Pattern 2 — run the migrator yourself, then e-mail

Use this when you want the stats / log path before (or without) mailing. Both scheduler methods
return an `overall_stats` property_list carrying `:log_file` and `:log_name`:

```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])

# pick ONE:
result << migrator.migrate_scheduled_objects()                  # manual DRM
# result << migrator.migrate_etl_scheduled_objects_for("feeder")  # one ETL type (auto-loads)
# result << migrator.migrate_etl_scheduled_objects()             # combined ETL (no auto-load)

log_path << result[:log_file]     # full .html path  (or _unset if the file failed to write)
log_name << result[:log_name]     # base name, no extension  (or _unset)

_if log_path _isnt _unset
_then
    admin_drm_scheduler.email_report(log_path, log_name)
_endif
```

The same values are also available afterwards via `migrator.last_log_file()` /
`migrator.last_log_name()`.

## B.5 Pattern 3 — e-mail a report you already produced (the "Migrate All" case)

The Work Order dialog migrates the displayed WOs directly (its own path, not a scheduler queue),
then just mails the resulting report:

```magik
## after the dialog's own migration produced result[:log_file] / [:log_name]
admin_drm_scheduler.email_report(result[:log_file], result[:log_name])
```

## B.6 Return values & failure behaviour

- `run_and_email` / `run_etl_for_type` return **`_true`** only if a report was produced **and**
  the mail command was issued; **`_false`** on a bad type, a `_unset` result, a missing report,
  or a cmail failure. They **never raise** — check the boolean and the session log.
- `email_report` returns `_false` (and logs) if `log_path` is `_unset`/empty or the cmail
  resources can't be located; `_true` if the mail command was issued.
- A report is always attempted even if some rows failed — a WO that ends `failed` is recorded in
  the report's "List failed" section, not raised.

## B.7 How the pieces connect

```
.bat  ──sets env──►  admin_drm_batch.magik  ──dispatch──►  admin_drm_scheduler.<method>
                                                                 │
                                    run_and_email(:manual)  ─────┤─►  astri_data_migrator.migrate_scheduled_objects()
                                    run_etl_for_type(type)  ─────┤─►  astri_data_migrator.migrate_etl_scheduled_objects_for(type)
                                    run_and_email(:etl)     ─────┘─►  astri_data_migrator.migrate_etl_scheduled_objects()
                                                                              │
                                                       (each) writes HTML report ──►  admin_drm_scheduler.email_report()
```

`admin_drm_batch.magik` is the **single stdin caller** piped by all `.bat` files — it only reads
`DRM_RUN_TYPE` / `DRM_ETL_INFRA_TYPE` and calls the right method. There is no per-job Magik to
maintain.

---

## E-mail (cmail) settings

The SMTP host / from address are set in `source/admin_drm_scheduler.magik` (`email_report`):

```
-host:172.17.12.165  -from:no-reply@morarepublic.co.id  -to:@<recipients.txt>
-subject:<report base name>  -body-html:<report .html>
```

- HTML body is sent with cmail's `-body-html:` flag (UTF-8 HTML).
- To change host/sender, edit `email_report` in `source/admin_drm_scheduler.magik`.
- To change recipients, edit `resources/base/data/recipients.txt`.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| No e-mail, log says "cannot locate cmail resources" | Module not loaded / not in the booted product → `get_resource_file` can't resolve. Add `admin_drm_scheduler` to the alias/product. |
| ETL `.bat` runs but logs "invalid infra type ''" | `DRM_ETL_INFRA_TYPE` is missing/blank. Set it to `cluster`, `subfeeder`, or `feeder` in that `.bat`. |
| `.bat` runs but nothing migrates | Confirm `DRM_RUN_TYPE` (+ `DRM_ETL_INFRA_TYPE`) and that the alias boots this module. For ETL, check the dim table has rows with `apd_kmz_upload_date` within the last month and non-null `apd_kmz_uuid`; if all such codes are already in `drm_etl_scheduler_log`, the load inserts 0 (expected). |
| Report generated but empty | No matching `scheduled` rows for the window, or all skipped. A per-type ETL run also reports empty if every eligible code was already migrated (dedup) and nothing new was loaded. |
| A failed WO never retries | By design: dedup is `(infra_code, infra_type)` and the run only picks `status='scheduled'`. A `failed` row is neither re-loaded nor re-processed until it's manually reset to `scheduled` (or deleted). |
| ETL stops partway through | Expected if the processing time window (`DRM_ETL_START`–`DRM_ETL_END`) ended. Remaining rows stay `scheduled` for the next run; the report is still e-mailed. |
| cmail runs but no mail arrives | Verify SMTP `-host` reachability and `recipients.txt` contents. |
