# Implementation Plan: Admin Migrate-All + admin_drm_scheduler Module

**Date:** 2026-07-13
**Author:** alami-ziezat
**Status:** IMPLEMENTED

> **As-built notes**
> - Toolbar visibility: `activate_in` always builds all 10 rows; each toolbar method now
>   **returns its container**, stored in `.items[:toolbar1_con..:bottom3_con]`.
>   `apply_role_visibility()` toggles `managed?` (guarded) via `set_toolbar_visible()`.
> - Resource layout: `cmail.exe`, `cmail-nossl.exe`, `recipients.txt` → `resources/base/data/`
>   (located via `get_resource_file(name, :data, :admin_drm_scheduler)`). `.bat`s + the stdin
>   caller `admin_drm_batch.magik` → `resources/base/bin/`.
> - No `product.def` edit — modules are directory-scan discovered. `admin_drm_scheduler` is a
>   **requires** of `rwwi_astri_workorder`.
> - `.bat` `DRM_SCHEDULER_DIR` now points at the `resources/base/bin/` location (holds the .bat,
>   the stdin caller, and logs); cmail is located from module resources at runtime.

---

## Goals

1. **Role-based toolbars** in the Work Order dialog:
   - **root** — sees everything (unchanged).
   - **admin** — sees **only the Batch Import toolbar** + the table, log window, and progress
     footer. All other action toolbars (filters, KMZ, migrate/upload/BOQ) are **hidden**.
   - **other users** — see everything **except** Batch Import (unchanged).
2. **New "Migrate All" button** on the Batch Import toolbar so admin can migrate the displayed
   WOs **directly** (no scheduler), then **e-mail the HTML report** (like `admin_drm_batch`).
3. **Convert `admin_drm_scheduler` into a proper SW module** so `cmail.exe` / `recipients.txt`
   and the e-mail logic are locatable at runtime by the dialog.

---

## Roles

Current: `batch_import_allowed?()` → `root`/`admin`. Add a role classifier:

```magik
user_role()  ->  :root | :admin | :user     # lowercased current GIS user name
```

| Role | Batch Import toolbar | Other action toolbars | Table/log/footer |
|---|---|---|---|
| root | shown | shown | shown |
| admin | shown | **hidden** | shown |
| user | hidden | shown | shown |

---

## Part 1 — Role-based toolbar visibility (`rwwi_astri_workorder_dialog.magik`)

The Batch Import load path uses the filter items (`apply_filters` reads `:filter_limit`,
`:filter_offset`, `:filter_infrastructure`; `set_infra_type_filter` → `infrastructure_type_changed`
touches `:label_code`, `:filter_topology`, `:label_osp_area`, …). So for **admin** the filter
toolbars must still be **built** (items must exist) — just **hidden**.

Approach: **always build every toolbar** (all 10 rows), then hide the ones a role shouldn't see.

- Each `build_*` toolbar method stores its top container in `.items[:<key>_con]`
  (e.g. `:toolbar1_con`, `:toolbar2_con`, `:toolbar3_con`, `:bottom1_con`, `:bottom2_con`,
  `:bottom3_con`, `:toolbar4_con`).
- New `apply_role_visibility(role)` sets `managed? << _false` on the containers to hide:
  - **admin** → hide `toolbar1, toolbar2, toolbar3, bottom1, bottom2, bottom3` (keep `toolbar4`,
    table, text, footer).
  - **user** → hide `toolbar4`.
  - **root** → hide nothing.
- `activate_in` always builds 10 rows (`{0,0,0,0,1,1,0,0,0,0}`) and calls
  `apply_role_visibility(_self.user_role())` after building.

> This replaces the current "omit the row" trick. Hidden fixed-height rows collapse to minimal
> height when their container is unmanaged.

---

## Part 2 — "Migrate All" button (Batch Import toolbar)

Add to `build_toolbar4`:

| Item Key | Label | Callback |
|---|---|---|
| `:migrate_all_btn` | **"Migrate All"** | `migrate_all_wo()` |

Enable/disable alongside `:add_all_btn` (enabled once a WO list is loaded).

### `migrate_all_wo()` (in `rwwi_astri_workorder_dialog_batch_import.magik`)

```
confirm popup
build wo_list from .wo_cache (each WO carries its own :infrastructure_type)
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])
result   << migrator.migrate_wo_list(wo_list)     # DIRECT migration (re-added) + HTML report
log_path << result[:log_file]; log_name << result[:log_name]
# e-mail the report via the new module
admin_drm_scheduler.email_report(log_path, log_name)   # or _self... see Part 4
show summary popup (processed / migrated / failed) + "report emailed"
refresh_button_states()
```

---

## Part 3 — Re-add direct migration (`astri_data_migrator.magik`)

Re-add the two methods removed in the earlier revision (they now also benefit from the HTML
report + return the log path/name via the shared `write_batch_summary_log`):

- `migrate_wo_list(wo_list)` (public) — group by type, process **FEEDER → SUBFEEDER → CLUSTER**,
  no scheduler writes, calls `write_batch_summary_log`, returns `overall_stats`
  (with `:log_file` and `:log_name`).
- `migrate_one_wo(wo, infra_type, obj_stats)` (private) — download → parse → `migrate_to_sw_design`,
  feature counting, `_try/_when error` per WO.

(Same code as the prior version; reuses the now-HTML `write_batch_summary_log`.)

---

## Part 4 — New module `admin_drm_scheduler`

Convert the loose folder into a module so `cmail.exe` / `recipients.txt` and the e-mail proc
are part of the product and locatable at runtime.

### Layout

```
admin_drm_scheduler/
├── module.def                 # requires base, rwwi_astri_integration
├── load_list.txt              # -> source
├── source/
│   ├── load_list.txt
│   └── admin_drm_scheduler.magik      # loadable: defines the e-mail proc/object (NO _block)
└── resources/
    └── base/
        ├── data/              # runtime-located via get_resource_file(:data)
        │   ├── cmail.exe
        │   ├── cmail-nossl.exe
        │   └── recipients.txt
        └── bin/               # OS-scheduler artifacts (deployment)
            ├── ADMIN_DRM_scheduler.bat
            ├── ADMIN_DRM_etl_scheduler.bat
            └── admin_drm_batch.magik   # stdin caller: just calls the loaded proc
```

> **Resource type:** `smallworld_product.get_resource_file(filename, :data, :admin_drm_scheduler)`
> is the confirmed API (returns the on-disk path). So the runtime-located files (`cmail.exe`,
> `recipients.txt`) live under **`resources/base/data/`**. The `.bat`s live under `bin/` for the
> OS scheduler (they don't use `get_resource_file`).

### `admin_drm_scheduler.magik` (loadable)

Defines a shared object/proc with:

```magik
# Runs the requested scheduler migration and e-mails its HTML report.
admin_drm_scheduler.run_and_email(run_type)      # :manual | :etl

# E-mails an already-produced report (used by the UI Migrate All).
admin_drm_scheduler.email_report(log_path, log_name)
```

`email_report`:
```
cmail  << smallworld_product.get_resource_file("cmail.exe",     :data, :admin_drm_scheduler)
recips << smallworld_product.get_resource_file("recipients.txt",:data, :admin_drm_scheduler)
host   << " -host:172.17.12.165 -from:no-reply@morarepublic.co.id -to:@" + recips
body   << " \"-body-html:" + log_path + "\""
subj   << " \"-subject:" + log_name + "\""
system.do_command(cmail + host + subj + body)
```

`run_and_email(run_type)`:
```
migrator << astri_data_migrator.new(gis_program_manager.databases[:gis])
run manual/etl -> result
_self.email_report(result[:log_file], result[:log_name])   # if log_file present
```

### stdin caller (`resources/base/bin/admin_drm_batch.magik`)

```magik
_block
    mode << system.getenv("DRM_RUN_TYPE").default("manual").lowercase = "etl" ?? :etl :: :manual
    admin_drm_scheduler.run_and_email(mode)
_endblock
$
```

The `.bat` files are unchanged except they now pipe the copy under `resources/base/bin/`
(via `DRM_SCHEDULER_DIR`).

### Product wiring

- Add `admin_drm_scheduler` to `rwwi_astri_workorder`'s `module.def` **requires** (so the dialog
  can call the loaded proc), and to the product definition / alias module list so it loads and
  its resources resolve.

---

## Files Changed / Added

| File | Change |
|---|---|
| `rwwi_astri_workorder_dialog.magik` | Always build all toolbars; store containers in `.items`; new `user_role()` + `apply_role_visibility()`; `activate_in` calls it |
| `rwwi_astri_workorder_dialog_batch_import.magik` | New `migrate_all_wo()`; `:migrate_all_btn` enable/disable; keep `batch_import_allowed?` (or fold into `user_role`) |
| `astri_data_migrator.magik` | Re-add `migrate_wo_list` / `migrate_one_wo` |
| `admin_drm_scheduler/module.def` *(new)* | Module definition |
| `admin_drm_scheduler/load_list.txt`, `source/load_list.txt` *(new)* | Load lists |
| `admin_drm_scheduler/source/admin_drm_scheduler.magik` *(new)* | `run_and_email` + `email_report` |
| `admin_drm_scheduler/resources/base/data/*` | `cmail.exe`, `cmail-nossl.exe`, `recipients.txt` (moved) |
| `admin_drm_scheduler/resources/base/bin/*` | `.bat`s + stdin caller (moved/updated) |
| product/alias config | add `admin_drm_scheduler` module |

---

## Notes / Risks

- **Deployment:** the module must be part of the loaded product (alias `dev_ftth_myrep_custom_open`)
  for `get_resource_file` to resolve. Update the product definition + copy the module to the
  deployment tree.
- **Toolbar hide:** relies on `managed? << _false` collapsing hidden fixed-height rows; verify
  visually.
- **`migrate_all_wo` is admin-only** by virtue of living on the Batch Import toolbar, which only
  root/admin see.
- The scheduler `.bat` flow keeps working via the stdin caller that calls the loaded proc.
