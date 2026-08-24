# Plan: "Upload Homepass Database Stella" API caller

**Date:** 2026-08-15
**Status:** Proposed — not yet implemented

## Context

`rwi_stela_integration_dialog.magik` already has a "Browse Excel" / "Upload Excel" toolbar section (built in `build_toolbar_second`) that is pure dead UI today: the buttons are wired to selectors `:browse_excel|()|` and `:upload_excel|()|` that don't exist anywhere in the module, and `upload_excel_btn` is created with `enabled? << _false` and never re-enabled. This was flagged in `docs/stela_integration_module_assessment_2026-08-15.md` as a stub for a feature that was never finished.

The user supplied the real API spec for that missing piece: **"Upload Homepass Database Stella"** — `POST /v4/osp/cluster/document/homepass-database/stella/upload`, `Content-Type: application/json`, Basic Auth, body `{cluster_code, file_name, file_base64}`, JSON success/error response. This plan wires that spec into the existing stub buttons, following the same Java-caller-plus-Magik-dialog-method pattern already used for the workorder dialog's KMZ/BOQ uploads (`upload_kmz_to_astri` / `upload_boq_to_astri` in `rwwi_astri_workorder_dialog_upload.magik`, backed by `AstriDocumentUploadProcs.java` / `DocumentUploadClient.java`).

The user then asked for two extra considerations, informed by what `generate_boq_excel()` in the Stela dialog actually does today (see §0 below) and what already exists for this exact "auto-upload" idea in the workorder dialog (see §0.2):
1. An **Auto-upload ON/OFF toggle** next to Export/Browse/Upload.
2. A **document-type selector** (Cluster / Homepass / FAT) — because the three Excel exports (and, eventually, their uploads) should be triggerable **independently, one at a time**, not as a single combined action.

Confirmed with user:
- Integration point: fill in Stela's existing stub buttons (not the workorder dialog).
- `cluster_code`: auto-derived from the current design job, the same way `generate_boq_excel()` already does it (scan the change set for a `sheath_with_loc` record, read `.cluster_code`) — no new input field for that.
- **API routing for all 3 doc types**: for now, assume Cluster, Homepass and FAT all upload through the *same* Homepass Stella route given in the spec — this is provisional, still to be confirmed with the API developer, and may change per-type later.
- **Auto-upload mechanics**: keep `generate_boq_excel()`'s current manual-save behavior (Excel stays open/visible, user saves it themselves) — "Auto Upload ON" should guide the user through a follow-up prompt + file picker right after export, rather than trying to detect when Excel is saved/closed.

---

## 0. What `generate_boq_excel()` currently does (read before changing anything)

`rwi_stela_integration_dialog.magik`, method `generate_boq_excel()` (~line 1698 onward). It branches on `.current_selection_ur1` — the currently-selected row in the "Design Summary" table, set by `table_selected_ur1` — into **three independent, mutually exclusive branches**:

| `.current_selection_ur1` | Summary row | Template used | What gets written |
|---|---|---|---|
| `1` | Cluster | `cluster-template (4).xlsx` | One row: job name, first FDT name/OLT code (`get_data_fdt()`), province/regency/district (boundary polygon lookup on the job's centroid), lat/long, project name, cluster name (`nm_cls`, parsed out of `job_title`) |
| `2` | FAT | `fat-template (9).xlsx` | One row per `Sheath Splice` in `.table_list2` with a location: name, cluster, ring name, lat/long |
| `3` | Homepass | `homepass-template (5).xlsx` | One row per `Demand point` in `.table_list2` with a location: province/regency/district (via boundary lookup), lat/long, OLT code (via `datalake_stella_by_cluster_code`), micro-cell/FAT lookup (`get_data_micro_cell`), cluster code, UG/AE/AE&UG flag (`cek_ug`/`cek_ae`), etc. |

Common to all three branches:
- Opens the template via **OLE automation** (`OLE_Client.Create_Object("Excel.Application")`, `Excel_Object.Visible << _True`), writes values into specific cell ranges (`Sheet.Range("A"+row)` etc.), then calls `.entirecolumn.autofit`.
- **Never calls `WorkBook.SaveAs`/`.Save`.** The method returns immediately after populating cells; Excel stays open and visible, and the user is expected to review and manually save (and pick the destination path) themselves in Excel. This is exactly why "Browse Excel" exists as a separate manual step afterwards — there is no `boq_file_path` return value the way the workorder dialog's `create_boq(...)` produces one.
- Both branches share the same trailing `WorkBook.Release_Object()` / `Excel_Object.Release_Object()` cleanup, regardless of which branch ran.

This is architecturally different from the workorder dialog's `generate_boq_excel()` (`rwwi_astri_workorder_dialog_boq.magik`), which calls the global proc `create_boq(...)` — a non-interactive generator that **returns a saved file path synchronously** — which is what lets *that* dialog auto-upload immediately after generation. Stela's OLE-based export can't do that without changing the save behavior, which the user asked to leave alone.

### 0.1 Existing "Auto-upload" precedent already implemented in the workorder dialog

`rwwi_astri_workorder_dialog.magik` already has this exact toggle pattern, twice:

```magik
# build_bottom_toolbar2 (~line 423)
sw_label_item.new(a_toolbar, "  Auto-upload KMZ:  ")
.items[:auto_upload_kmz] << sw_text_item.new(a_toolbar, :model, _self, :display_length, 5, :editable?, _false)
.items[:auto_upload_kmz].text_items << {"Off", "On"}
.items[:auto_upload_kmz].value << "On"

# build_bottom_toolbar3 (~line 501)
sw_label_item.new(a_toolbar, "  Auto-upload Excel:  ")
.items[:auto_upload_excel] << sw_text_item.new(a_toolbar, :model, _self, :display_length, 5, :editable?, _false)
.items[:auto_upload_excel].text_items << {"Off", "On"}
.items[:auto_upload_excel].value << "Off"
```

And it's checked right after a successful export, e.g. in `generate_boq_excel()` (`rwwi_astri_workorder_dialog_boq.magik` line ~81):

```magik
_if boq_file_path _isnt _unset _andif boq_file_path <> ""
_then
	_if .items[:auto_upload_excel] _isnt _unset _andif .items[:auto_upload_excel].value = "On"
	_then
		_self.upload_boq_to_astri(boq_file_path)
	_endif
_endif
```

This only works there because `create_boq(...)` already handed back a saved path with no user interaction needed. **Stela's Excel export can't reuse this exact shot — there's no `boq_file_path` — so the toggle needs a guided-prompt variant instead (§3 below).** The `sw_text_item` + `{"Off","On"}` dropdown widget itself, though, is exactly what Stela should reuse for visual/behavioral consistency.

---

## 1. Java layer — new API caller, built into the existing `rwwi.astri.integration` jar

Source tree: `pni_custom/rwwi_astri_integration_java/src/main/java/com/rwi/myrepublic/astri/...` — backs the `RWI ASTRI Integration` OSGi bundle (`pni_custom.rwwi.astri.integration.1.jar`, symbolic name `rwwi.astri.integration`), the same jar behind `AstriDocumentUploadProcs`/`AstriWorkOrderUpdateProcs`. No new Maven module — add two files to this existing tree, following its established per-feature class-pair convention (`AstriXProcs.java` + `internal/XClient.java`).

Since all three document types currently share one route (per user confirmation, pending API-developer sign-off), the Java layer takes a `docType` parameter now but **only implements one route today** — this keeps the seam ready for when Cluster/FAT get their own routes, without the Magik call site having to change later.

**1. `src/main/java/com/rwi/myrepublic/astri/internal/StellaDocumentUploadClient.java`** (new)
Modeled on `internal/DocumentUploadClient.java`'s response/XML conventions, but JSON-body like `internal/WorkOrderUpdateClient.java`:
- Reuses `AstriConfig.getInstance()` for base URL / credentials / timeouts and builds the same `Basic` auth header the other clients build.
- `uploadDocument(String filePath, String clusterCode, String docType, String fileName)`:
  - Reads the file at `filePath`, 404s → `buildErrorXml("File not found: ...", 0)` (same guard as `DocumentUploadClient`).
  - Base64-encodes the bytes (`java.util.Base64`).
  - Falls back to the path's own file name if `fileName` is blank.
  - `resolveRoute(String docType)` — **today, returns `"/osp/cluster/document/homepass-database/stella/upload"` for every value** (`"cluster"`, `"fat"`, `"homepass"`), with a comment noting this is provisional per the API spec we have (only Homepass documented so far) and should branch to per-type routes once ASTRI confirms them.
  - Builds the JSON body manually (`{"cluster_code":"...","file_name":"...","file_base64":"..."}` — `docType` is **not** sent to ASTRI, it's Magik/Java-side routing only, since the spec doesn't define a type field), same manual-escaping style as `WorkOrderUpdateClient.buildJsonBody`/`escapeJson` — no JSON library in this project.
  - POSTs to `config.getDmBaseUrl() + resolveRoute(docType)` with `Content-Type: application/json` and the Basic Auth header.
  - Normalizes the response into the same XML envelope `DocumentUploadClient.buildSuccessXml`/`buildErrorXml` already produce, so Magik-side parsing stays 1:1 with `upload_boq_to_astri`'s existing `simple_xml.read_element_string(...)` handling.
  - `close()` no-op, same as the other clients.

**2. `src/main/java/com/rwi/myrepublic/astri/AstriStellaDocumentUploadProcs.java`** (new)
Modeled on `AstriDocumentUploadProcs.java`:
- `@MagikProc(@Name("astri_upload_stella_document"))` exposing global proc `astri_upload_stella_document(file_path, cluster_code, doc_type, _optional file_name)`.
- Converts Magik args via `MagikInteropUtils.fromMagikString` (required args) and the existing `toJavaStringOrNull` pattern for the optional `file_name`.
- Calls `StellaDocumentUploadClient.uploadDocument(...)`, returns `MagikInteropUtils.toMagikString(xmlResponse)`.
- Catch-all builds the same inline error-XML fallback `AstriDocumentUploadProcs` already builds.

No `pom.xml` changes — same package, same jar/build output, picked up automatically on the next `mvn package` (`Export-Package` already covers `com.rwi.myrepublic.astri`).

---

## 2. Magik layer — wire the existing stub buttons + new toggle/type selector

**`pni_custom/rwwi_astri_integration_java/magik/rwi_stela_integration/source/rwi_stela_integration_dialog.magik`**

### 2.1 New toolbar controls in `build_toolbar_second`

Add, next to the existing "Export Data into Excel" / "Browse Excel" / "Upload Excel" controls:

```magik
# --- Auto-upload Excel toggle (same widget as rwwi_astri_workorder_dialog) ---
sw_label_item.new(file_toolbar, "  Auto-upload:  ")
.items[:auto_upload_excel] << sw_text_item.new(file_toolbar, :model, _self, :display_length, 5, :editable?, _false)
.items[:auto_upload_excel].text_items << {"Off", "On"}
.items[:auto_upload_excel].value << "Off"

# --- Document type selector (defaults to the selected summary row, overridable) ---
sw_label_item.new(file_toolbar, "  Doc Type:  ")
.items[:upload_doc_type] << sw_text_item.new(file_toolbar, :model, _self, :display_length, 10, :editable?, _false)
.items[:upload_doc_type].text_items << {"Cluster", "FAT", "Homepass"}
.items[:upload_doc_type].value << "Homepass"
```

New writable slot: `{:pending_auto_upload?, _false}` — set right before opening the file dialog from the auto-upload guided flow (§2.3), consumed in `ok_browse_excel`.

### 2.2 Keep `.current_selection_ur1` and the type dropdown in sync

In `table_selected_ur1` (existing method — already sets `.current_selection_ur1` from the row clicked), add at the end:

```magik
_if .items[:upload_doc_type] _isnt _unset
_then
	_if .current_selection_ur1 = 1 _then .items[:upload_doc_type].value << "Cluster"   _endif
	_if .current_selection_ur1 = 2 _then .items[:upload_doc_type].value << "FAT"       _endif
	_if .current_selection_ur1 = 3 _then .items[:upload_doc_type].value << "Homepass"  _endif
_endif
```

This keeps the dropdown defaulted to whatever row is selected (so exporting FAT and then uploading naturally uploads "as FAT"), while still letting the user manually change it before Browse/Upload — which is what makes uploading "independently, one by one" possible (pick a doc type, browse an existing file, upload it, without needing a live table selection at all).

### 2.3 `browse_excel()` / `ok_browse_excel(path)` (new — replaces dead `:browse_excel|()|` selector)

Same `file_dialog` pattern as `rwwi_astri_workorder_dialog_batch_import.magik#browse_batch_list` / `#ok_batch_list`:

```magik
_method rwi_stela_integration_dialog.browse_excel()
	_local fd << file_dialog.new(_self, :ok_browse_excel|()|)
	fd.filter << {"Excel files (*.xlsx)", "*.xlsx"}
	_local current_path << .items[:local_excel_path].value.default("")
	fd.directory << _if current_path <> "" _then >> current_path _else >> system.getenv("TEMP").default("C:\") _endif
	fd.activate()
_endmethod
$

_method rwi_stela_integration_dialog.ok_browse_excel(path)
	## Callback from file_dialog — store selected Excel path and enable Upload.
	.items[:local_excel_path].value << path
	.items[:upload_excel_btn].enabled? << _true
	_if .pending_auto_upload?
	_then
		.pending_auto_upload? << _false
		_self.upload_excel()
	_endif
_endmethod
$
```

### 2.4 `get_current_cluster_code()` (new small private helper)

Factors out the change-set scan `generate_boq_excel()` already does inline (find the record whose `source_collection.name _is :sheath_with_loc` in the current job's change set, read `.cluster_code`) so `upload_excel()` can reuse it without duplicating `generate_boq_excel`'s logic. `generate_boq_excel()` itself is left untouched.

### 2.5 `upload_excel()` (new — replaces dead `:upload_excel|()|` selector)

Same shape as `upload_boq_to_astri` in `rwwi_astri_workorder_dialog_upload.magik`:

- Validate `.items[:local_excel_path].value` is set/non-empty → `.owner.show_alert(...)` + bail if not (this dialog's existing feedback convention, already used in `get_ur1`/`get_ur2`).
- Resolve `cluster_code` via `_self.get_current_cluster_code()` → alert + bail if `_unset`.
- Read `doc_type << .items[:upload_doc_type].value.default("Homepass")`.
- Derive `file_name` from the browsed path's basename.
- Call `astri_upload_stella_document(path, cluster_code, doc_type, file_name)` inside `_try _with errCond ... _when error ...`.
- Parse the XML response with `simple_xml.read_element_string(...)` + `element_matching_name(:success)`/`:error` — identical extraction code to `upload_boq_to_astri`'s existing block.
- Feedback via `.owner.show_alert(...)`, including `doc_type` in the message (e.g. `"Homepass document uploaded successfully."`) so it's clear which type just went out.

### 2.6 Auto-upload guided flow

New private helper, called from `generate_boq_excel()` right after each branch finishes populating cells (all three branches, same call — reuse the doc type already implied by `.current_selection_ur1`, keep in sync via §2.2):

```magik
_if .items[:auto_upload_excel] _isnt _unset _andif .items[:auto_upload_excel].value = "On"
_then
	_self.prompt_and_upload_after_export()
_endif
```

```magik
_method rwi_stela_integration_dialog.prompt_and_upload_after_export()
	## Auto-upload is ON, but generate_boq_excel() leaves the workbook open/unsaved
	## (OLE Excel automation, no SaveAs call) — so, unlike the workorder dialog's
	## create_boq() flow, we can't upload immediately. Guide the user through the
	## remaining manual step instead of trying to detect Excel save/close.
	.owner.show_alert("Save the Excel file in Excel, then click OK to select it for upload.")
	.pending_auto_upload? << _true
	_self.browse_excel()
_endmethod
$
```

When Auto-upload is **Off** (default), behavior is unchanged: user clicks "Browse Excel" then "Upload Excel" manually, exactly as originally planned.

### 2.7 `rwi_stela_integration/module.def`

Add a `requires` block (currently missing entirely) listing `base` and `rwwi_astri_integration` — mirrors `rwwi_astri_workorder/module.def`, which requires `rwwi_astri_integration` for the same reason (calls global procs the `rwwi.astri.integration` OSGi bundle registers). Not strictly load-bearing today (that bundle is already pulled in transitively via `rwwi_astri_workorder`, already in `pni_custom`'s product requires), but makes the dependency explicit.

---

## 3. Known limitation to flag back to the user

All three document types currently resolve to the **same** upload route (`resolveRoute()` in `StellaDocumentUploadClient` always returns the Homepass Stella path) because that's the only spec we have. If the API developer confirms separate routes for Cluster/FAT uploads later, the only change needed is inside `resolveRoute(String docType)` — the Magik call site, UI, and proc signature (`astri_upload_stella_document(file_path, cluster_code, doc_type, _optional file_name)`) already carry `doc_type` end-to-end and won't need to change.

---

## 4. Verification

1. `mvn package` in `pni_custom/rwwi_astri_integration_java/` — confirm it compiles clean and the two new classes land in the rebuilt jar.
2. Restart `gis.exe` (or reload the module) so the updated `rwwi.astri.integration` bundle and the edited `rwi_stela_integration` Magik module are picked up.
3. **Manual flow (Auto-upload Off, default):** open Stela Integration → select a design job → select "Homepass" summary row (confirm Doc Type dropdown auto-switches to "Homepass") → "Export Data into Excel" → manually save the .xlsx in Excel → "Browse Excel" and pick that file → confirm "Upload Excel" becomes enabled → click it → confirm success alert and, separately, that the file arrived at ASTRI with correct `cluster_code`/`file_name`/`file_base64`.
4. **Guided auto-upload flow (Auto-upload On):** same as above but flip the toggle to "On" before exporting → after "Export Data into Excel", confirm the "Save the file..." alert appears → save in Excel → confirm the file dialog opens automatically → pick the saved file → confirm upload fires immediately without a separate "Upload Excel" click.
5. **Independent/one-by-one flow:** with no summary row selected (or after switching), manually change the Doc Type dropdown and Browse to an older, already-saved Excel file, then Upload — confirm it uploads under the manually chosen type without needing a fresh export.
6. Failure paths: no file browsed yet (alert, no call made), current job has no resolvable `cluster_code` (alert, no call made), deliberately bad path/unreachable API (confirm `_when error` surfaces a readable message).
