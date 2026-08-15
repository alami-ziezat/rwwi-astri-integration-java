# `rwi_stela_integration` Module Assessment

**Date:** 2026-08-15
**Location:** `pni_custom/rwwi_astri_integration_java/magik/rwi_stela_integration/rwi_stela_integration/`
**Status at time of review:** Untracked in git (`git status` shows `??`), **not referenced by `pni_custom/product.def`** or any other module's `requires` — this module does not appear to be wired into the loaded product yet.

## 1. What it is

A Magik/SWAF plugin ("Stela Integration") that adds a "Stela Integration" action to the PNI application. It opens a dialog that:

1. Summarizes the **current design job** (`swg_dsn_admin_engine.get_current_job()`) by counting FTTH object types that are missing/incomplete (see §3).
2. Lets the user drill into the detail records for each summary row in a second table.
3. Exports the current selection to one of three pre-built Excel templates (Cluster / FAT / Homepass) using Excel OLE automation, for BOQ-style reporting.
4. Looks up an OLT code from an external ASTRI PostgreSQL database (`POSTGRESQL_ASTRI_DB`) to fill in the Excel export.

It is functionally a **validation/reporting tool for FTTH design QA**, not a data-editing tool — its main job is to flag objects in the current design that fail proximity/coverage checks against "Micro Cell" zones (see §3.2), and to help export that data.

## 2. Module layout

```
rwi_stela_integration/
├── module.def                  # name only: "rwi_stela_integration 1", description "Stela Integration", NO requires section
├── load_list.txt               # "source/"
├── source/
│   ├── load_list.txt           # dialog, engine, plugin (in that order)
│   ├── rwi_stela_integration_plugin.magik   # plugin/action registration
│   ├── rwi_stela_integration_dialog.magik   # ~2400 lines — all UI + business logic
│   └── rwi_stela_integration_engine.magik   # separate exemplar, effectively dead code (see §4.1)
└── resources/
    └── cluster-template*.xlsx, fat-template*.xlsx, homepass-template*.xlsx  (+ "- Copy" duplicates)
```

`module.def` has **no `requires` block** — unusual for this codebase, where nearly everything requires `base`. Combined with not being referenced by `product.def`, this strongly suggests the module was scaffolded/copy-pasted from another module and never finished being integrated into the build.

## 3. `rwi_stela_integration_plugin.magik`

Standard `plugin` subclass. Registers one action, `activate_get_fat`, captioned **"Stela Integration"**, which lazily creates/caches and activates `rwi_stela_integration_dialog`. Also declares (but barely uses) databus producer/consumer plumbing (`goto_request`, `geometry_to_draw`, `post_render_sets`) and some unused line/point/text style constants — these look like leftovers copied from a KML-drawing plugin template and aren't exercised by the dialog logic.

## 4. `rwi_stela_integration_dialog.magik` (the real logic)

### 4.1 Dead `.engine` integration
The dialog has an `.engine` slot, initialized to `""` (not `_unset`, not an instance of `rwi_stela_integration_engine`). The only two places that would call `.engine.get_ur1(...)` / `.engine.get_ur2(...)` are **commented out**. `rwi_stela_integration_engine.new()` is never called anywhere in the module.

`rwi_stela_integration_engine.magik` itself implements a completely different workflow: selecting **two Underground Routes** on the map and checking whether they can be merged (`underground_route.can_merge_all_cables?`), with goto/highlight helpers. This looks like an earlier "UR merge checker" feature (possibly ported from `rwwi_astri_integration`/another module) that predates the current "Design Summary" dialog and was left in the tree but is **not reachable from the UI at all**. Treat `rwi_stela_integration_engine.magik` as legacy/dead code unless you find another caller.

### 4.2 Main UI (`activate_in`)
Builds a two-table layout inside `sw_border_container`s:
- **Table 1 ("Design Summary", `table_ur1`)** — 6 fixed rows, populated by `populate_ur1`:
  | Row | Label | Source method |
  |---|---|---|
  | 1 | Cluster | hardcoded `"1"` (not real data — see §5.1) |
  | 2 | FAT | `get_data_odp()` |
  | 3 | Homepass | `get_data_homepass()` |
  | 4 | FAT Need To Check | `get_data_fat_not_valid()` |
  | 5 | Homepass Need To Check | `get_data_homepass_not_valid()` |
  | 6 | Boundary FAT Need To Check | `get_data_boundary_fat_not_valid()` |
- **Table 2 ("Detail", `table_ur2`)** — populated via `populate_ur2`, showing Name/Lat/Long for whichever row of Table 1 is selected (`table_selected_ur1` dispatches to the matching `get_data_*` method and stores results in `.table_list2`).
- A toolbar with Clear/Refresh, a read-only "Type" selector (`Design` / `Existing` — only cosmetic, not actually branching logic anywhere obvious), and a second toolbar with:
  - **"Export Data into Excel"** → `generate_boq_excel()` (wired, works)
  - **"Browse Excel"** → selector `:browse_excel|()|` — **method does not exist anywhere in the module**
  - **"Upload Excel"** → selector `:upload_excel|()|` — **method does not exist anywhere in the module**, button is also created with `enabled? << _false` (permanently disabled)

  This "Browse/Upload Excel" section is UI-only scaffolding for a feature that was never implemented. See `pni_custom/rwwi_astri_integration_java/docs/astri_document_upload_plan.md` — that plan describes an ASTRI Document Upload API integration (KMZ + "BOQ Excel upload — future") that matches this stub almost exactly. If you're asked to "finish" the Excel upload button, that plan doc is the design to follow.

### 4.3 Data-gathering methods — the real business logic
All of `get_data_odp`, `get_data_homepass`, `get_data_fat_not_valid`, `get_data_homepass_not_valid`, `get_data_boundary_fat_not_valid`, `get_data_fdt`, `get_data_cell`, `get_data_micro_cell`, `get_fat_cell` follow the same pattern:

1. `s << swg_dsn_admin_engine.get_current_job()` — bail out (`write("No Design")`, `_leave`) if there's no active design job.
2. `change_set << mit_scheme_record_change_set.new(s)` — iterate every record touched by the current design change set.
3. Filter by `ob.source_collection.name` / `ob.external_name` (e.g. `"ftth!demand_point"`, `"sheath_splice"` + `sheath_splice_object_type = "FAT"`/`"FDT"`, `"ftth!zone"` + `type = "Micro Cell"`), explicitly skipping `"Conduit Route Face"`.
4. The `*_not_valid` variants additionally do a **spatial proximity check**: build a `pseudo_point`/buffer from the record's location and `predicate.interacts(:location, ...)` against the `ftth!zone` (Micro Cell) collection — if no interacting Micro Cell zone is found, the object is considered "not valid" (i.e. not yet covered by a cell boundary) and added to the result.
5. Most wrap the loop body in `_try _with err ... _when error _continue` — **errors on individual records are silently swallowed**, which will hide bugs during future changes unless you temporarily remove/log inside these handlers while debugging.

`get_data_boundary_fat_not_valid()` is the exception — its `_try/_when error` is commented out, so an error on any single Micro Cell zone record will abort the whole scan.

`get_fat_cell(cell)` has a subtle bug for future-you to be aware of: in the `p.size > 0` branch it iterates and does `_return j` / `_leave` inside a `_for` loop when a match is found — the `_return` exits the method immediately (correct), but the trailing `_leave` after it is unreachable dead code. If no match is found in the loop, `j` is never set and the method falls through to `>> j` with `j` `_unset` implicitly (relies on `_unset` default) unless `p.size = 1` branch (`_elif`) sets it — but note `_elif p.size = 1` can never be reached because it's mutually exclusive with the preceding `_if p.size > 0` (a size of `1` is `> 0`), so that branch is unreachable too.

### 4.4 `datalake_stella_by_cluster_code` (global proc, not a method)
Defined as a `_global` proc at the bottom of the dialog file (line ~1615) — unusual placement (global procs are normally in their own file), and it opens a **direct JDBC connection** to `POSTGRESQL_ASTRI_DB` (via `rwwi_external_ds_manager` in `rwi_external_databases`, config-driven — see `rwi_external_databases/source/rwwi_external_ds_manager.magik` and its `.cfg` for the actual DB host/user) to run:
```sql
SELECT olt_code FROM smallworld.dim_cluster_stella_master_smallworld WHERE cluster_code_astri = ? LIMIT 1
```
Connection is always closed/rolled back in a `_protect`/`_endprotect` block — that part is solid. Called from `generate_boq_excel()` to resolve the OLT code for the Cluster template export.

### 4.5 `generate_boq_excel()`
Branches on `.current_selection_ur1` (1 = Cluster, 2 = FAT, 3 = Homepass) and drives Excel via `OLE_Client.Create_Object("Excel.Application")`, writing into cell ranges of one of three templates. **Hardcoded absolute file paths**, e.g.:
```
C:\SW5\PNI_FTTH536\pni_custom\modules\rwi_stela_integration\resources\cluster-template (4).xlsx
```
This path does not match the module's actual current location (`pni_custom/rwwi_astri_integration_java/magik/rwi_stela_integration/rwi_stela_integration/resources/`) and hardcodes a specific machine's `C:\SW5\PNI_FTTH536` install root. **This will only work on whatever machine originally hosted that path** — worth fixing to use `smallworld_product.pni_get_resource(...)` (the pattern already used correctly in `rwwi_external_ds_manager.config_file()`) before relying on this in another environment.

The Homepass branch (`.current_selection_ur1 = 3`) contains ~200 lines of commented-out `Cell_x.Borders(...)` styling code (dead, harmless, but adds noise).

`.current_selection_ur1 = 1` (Cluster) path never iterates `.table_list2` — it writes a single row using the design job's area centroid, `get_data_fdt()`'s first element, and a boundary-polygon lookup (`pol_boundary` collection filtered by `type = "Town"`) for province/regency/district names.

### 4.6 Table 2 selection (`table_selected_ur2`)
Routes to the embedded editor / map based on `external_name` ("Fibre Figure Eight" vs "Sheath (LOC)" vs default), each with slightly different goto/highlight call sequences — mostly consistent, though the "Fibre Figure Eight" branch uses `.grs.databus.make_data_available(:goto_request, ...)` while the others call `ef.goto_in_current_world()` / `ef.highlight_rwo()` directly. Not obviously wrong, just inconsistent style between branches.

## 5. Things to check/fix before extending this module

1. **Row 1 ("Cluster") of the summary table is fake data** — `populate_ur1` hardcodes `pl_pole << "1"` and displays `pl_pole.size` (always `1`), it does not call any `get_data_*` method for a real cluster count. If "Cluster" is supposed to mean something (count of clusters, or just a static label), decide/fix this before shipping.
2. **"Browse Excel" / "Upload Excel" buttons are non-functional stubs.** Either wire them up (see `docs/astri_document_upload_plan.md` for the intended ASTRI Document Upload API contract) or remove them so users aren't shown dead UI.
3. **Hardcoded `C:\SW5\PNI_FTTH536\...` template paths** in `generate_boq_excel()` — will break on any machine without that exact path. Switch to `smallworld_product.pni_get_resource("rwi_stela_integration", "resources", "<file>.xlsx")`.
4. **Module isn't wired into `product.def` and has no `requires` in `module.def`.** Before relying on it loading at all, add a `requires` block (at minimum `base`, plus whatever gives you `mit_scheme_record_change_set`, `swg_dsn_admin_engine`, `smallworld_product.pni_application`, FTTH collections, and `rwwi_external_ds_manager`/`rwi_external_databases`) and register the module path with the product.
5. **`rwi_stela_integration_engine.magik` is dead code** (UR-merge feature, never instantiated by the dialog). Confirm with whoever wrote it whether it's meant to be finished/reconnected or deleted — don't assume its logic is exercised by any test or user path today.
6. **Swallowed errors** (`_try _with err ... _when error _continue`) in most `get_data_*` methods will hide record-level exceptions. When debugging data-count discrepancies, temporarily log `err` inside these handlers.
7. **`get_fat_cell`** has the unreachable-branch issue described in §4.3 — worth a real review/rewrite if this method's return value (used for `d_fat` in the Homepass Excel export, i.e. cell `AE`/`AF` "ring_name"/"name") is ever wrong or `_unset` on the Excel sheet.
8. The `.xlsx` files each have a `" - Copy"` duplicate in `resources/` — confirm which one is actually the template in use (`generate_boq_excel` reads by exact machine path, not from this `resources/` folder, per point 3) and delete the stray copies once paths are fixed.

## 6. Suggested reading order if picking this up

1. `rwi_stela_integration_plugin.magik` (small, orientation only).
2. `rwi_stela_integration_dialog.magik` §`activate_in` → `build_toolbar*` → `populate_ur1`/`populate_ur2` to see the UI shape.
3. One representative `get_data_*_not_valid` method (e.g. `get_data_fat_not_valid`) to understand the "not valid" spatial-proximity pattern — the rest are copy-paste variants of it.
4. `generate_boq_excel` to see how table selection feeds the Excel export.
5. `docs/astri_document_upload_plan.md` if your task involves the Browse/Upload Excel buttons.
