# admin_drm_scheduler

Smallworld module that runs the DRM scheduler migrations and **e-mails their HTML summary
report** (via bundled `cmail.exe`). It is used two ways:

1. **Scheduled `.bat` jobs** (Windows Task Scheduler / cron) — manual and automated ETL runs.
2. **As a loaded module** — the Work Order dialog's **"Migrate All"** button e-mails its report
   through this module.

---

## Module layout

```
admin_drm_scheduler/
├── module.def                         # requires: base, rwwi_astri_integration
├── load_list.txt                      # -> source/
├── source/
│   ├── load_list.txt
│   └── admin_drm_scheduler.magik      # the loadable object (email_report / run_and_email)
└── resources/base/
    ├── data/                          # located at runtime via get_resource_file(:data)
    │   ├── cmail.exe
    │   ├── cmail-nossl.exe
    │   └── recipients.txt
    └── bin/                           # OS-scheduler artifacts
        ├── ADMIN_DRM_scheduler.bat        # manual run
        ├── ADMIN_DRM_etl_scheduler.bat    # automated ETL run
        └── admin_drm_batch.magik          # stdin caller piped by both .bat files
```

- `cmail.exe` + `recipients.txt` are **module resources** under `resources/base/data/` and are
  found at runtime with
  `smallworld_product.get_resource_file("cmail.exe", :data, :admin_drm_scheduler)`.
- Edit **`resources/base/data/recipients.txt`** to change the mailing list (one recipient per
  line: `user@example.com` or `Name <user@example.com>`).

---

## Two scheduler runs

| Run | Table read | Migrator method |
|---|---|---|
| **Manual** | `smallworld.drm_scheduler_logs` (fed by Batch Import "Add All") | `migrate_scheduled_objects()` |
| **Automated ETL** | `smallworld.drm_etl_scheduler_log` (fed by single "Schedule WO") | `migrate_etl_scheduled_objects()` |

Both select `status='scheduled' AND infra_type=? AND created_at >= yesterday`, process in the
order **FEEDER → SUBFEEDER → CLUSTER**, write a new HTML report to `%TEMP%`, and e-mail it.

---

## 1. Using the `.bat` jobs (manual + automated)

Both `.bat` files live in `resources/base/bin/`. They select the run type via the
**`DRM_RUN_TYPE`** environment variable and pipe the shared caller `admin_drm_batch.magik`
into a headless GIS session (`runalias ... -cli -login "root/" < admin_drm_batch.magik`).

### Manual — `ADMIN_DRM_scheduler.bat`
```bat
SET DRM_RUN_TYPE=manual
SET DRM_SCHEDULER_DIR=F:\SW5\Scheduler\admin_drm_scheduler\resources\base\bin\
```
Processes `drm_scheduler_logs`. Log file prefix: `drm_`.

### Automated ETL — `ADMIN_DRM_etl_scheduler.bat`
```bat
SET DRM_RUN_TYPE=etl
SET DRM_SCHEDULER_DIR=F:\SW5\Scheduler\admin_drm_scheduler\resources\base\bin\
REM Optional processing time window (HH:MM). Default 22:00 - 09:00.
REM SET DRM_ETL_START=22:00
REM SET DRM_ETL_END=09:00
```
Processes `drm_etl_scheduler_log`. Log file prefix: `drm_etl_`.

**Time window:** the automated ETL run only processes WOs while the current time is within
`[DRM_ETL_START, DRM_ETL_END]` (default **22:00 → 09:00**, crossing midnight). When the window
ends it **stops** — remaining rows are left for the next run — and the report is **still e-mailed**.
Set `DRM_ETL_START` / `DRM_ETL_END` in the `.bat` to change the window.

### What you must set before running (edit each `.bat`)
| Variable | Meaning |
|---|---|
| `SW_GIS_ENVIRONMENT_FILE` | path to the GIS `environment.bat` |
| `SW_GIS_ALIAS_FILES` | path to the `gis_aliases` file |
| `DRM_RUN_TYPE` | `manual` or `etl` (already set per file) |
| `DRM_SCHEDULER_DIR` | folder holding the `.bat`, `admin_drm_batch.magik`, and `logs\` (trailing `\` required) |
| `DRM_ETL_START` / `DRM_ETL_END` | *(ETL only, optional)* processing time window `HH:MM`; default `22:00` / `09:00` |
| the `runalias.exe` path + alias name | the product/alias to boot (must load this module) |

> `DRM_SCHEDULER_DIR` only locates the stdin caller + the `logs\` output. `cmail.exe` /
> `recipients.txt` are located from the **loaded module** resources, not from here.

### Run it
- On demand: double-click the `.bat`, or `CALL ADMIN_DRM_scheduler.bat`.
- Scheduled: point Windows Task Scheduler at the `.bat` (run whether or not a user is logged on).
- Output/logs: `%DRM_SCHEDULER_DIR%logs\drm[_etl]_<timestamp>.log`.

### Deployment note
The module's resources must be reachable by the booted product so `get_resource_file` resolves
(the module loads as a prerequisite of `rwwi_astri_workorder`). The `.bat` copy under
`DRM_SCHEDULER_DIR` can live independently of the loaded product tree — it only needs the `.bat`
+ `admin_drm_batch.magik` + a writable `logs\`.

---

## 2. Using it from the Work Order dialog (as a module)

Add `admin_drm_scheduler` to the product/alias so it loads. It is already declared as a
**`requires`** of `rwwi_astri_workorder`, so it loads automatically with the Work Order dialog.

The **"Migrate All"** button (Batch Import toolbar, root/admin only) migrates the displayed WOs
directly and then calls:

```magik
admin_drm_scheduler.email_report(log_path, log_name)
```

where `log_path` / `log_name` come from the migrator's return value
(`result[:log_file]` / `result[:log_name]`).

---

## Public API (`admin_drm_scheduler` object)

```magik
# E-mail an already-produced HTML report. Subject = log_name.
# Returns _true if the mail command was issued.
admin_drm_scheduler.email_report(log_path, log_name)

# Run a scheduler migration and e-mail its report.
#   :manual (default) -> migrate_scheduled_objects()     (drm_scheduler_logs)
#   :etl              -> migrate_etl_scheduled_objects()  (drm_etl_scheduler_log)
admin_drm_scheduler.run_and_email(:manual)
admin_drm_scheduler.run_and_email(:etl)
```

### Run a scheduler + e-mail from the Magik CLI directly
```magik
admin_drm_scheduler.run_and_email(:manual)   $
admin_drm_scheduler.run_and_email(:etl)      $
```

### Just run a migration (no e-mail) and inspect the result
```magik
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])
result   << migrator.migrate_scheduled_objects()     # or migrate_etl_scheduled_objects()
log_path << result[:log_file]                         # full .html path
log_name << result[:log_name]                         # base name, no extension
admin_drm_scheduler.email_report(log_path, log_name)  # e-mail it separately if desired
```

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
| No e-mail, log says "cannot locate cmail resources" | Module not loaded / not in the booted product → `get_resource_file` can't resolve. Add the module to the alias/product. |
| `.bat` runs but nothing migrates | Confirm `DRM_RUN_TYPE`, the alias boots this module, and there are `status='scheduled'` rows created since yesterday. |
| Report generated but empty | No matching scheduled rows for the window, or all skipped. |
| cmail runs but no mail arrives | Verify SMTP `-host` reachability and `recipients.txt` contents. |
