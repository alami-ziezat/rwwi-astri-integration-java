# Segment Problem Detail — Equipment Tab UI Plan

**Date:** 2026-09-01
**Author:** Claude Code
**Status:** DRAFT - Awaiting Approval
**Related:** [fat_loss_detection_nisa_api_plan_2026-08-24.md](./fat_loss_detection_nisa_api_plan_2026-08-24.md)
(that plan added the `nisa_segment_problem_detail(area, hostname, fat)` Java caller and the
`nisa_parse_segment_problem_detail_response(json_string)` Magik parser this UI consumes — both are
already implemented in `test_nisa_procs.magik`. This plan is the "wire into a dialog later" step that
doc explicitly deferred.)

---

## 1. Overview

Add a **map-driven UI** for the Segment Problem Detail API to the existing NISA dialog
(`rwwi_nisa_dialog`, opened via *NISA Mass Problem Monitor...*), as a second tab alongside the
current mass-problem/outage UI.

- Tab 1 — **"Cluster"**: the dialog's current content (search by cluster code/area, outage check,
  map highlight, blink), unchanged, just re-parented under a tab.
- Tab 2 — **"Equipment"** (new): pick a FAT or Cable object on the map, auto-derive Area / OLT Code /
  Hostname / FAT Code, call `nisa_segment_problem_detail`, show results in a table, navigate the map
  to a selected ticket's FAT, and blink all resolved FAT locations.

Both tabs live in the **same `rwwi_nisa_dialog` model** (see §3 for why) and the same
`rwwi_nisa_plugin`. No new module, no `module.def` change.

---

## 2. Equipment Tab — Functional Spec

### 2a. Object-type selector + Get Selected Object

A dropdown (`sw_text_item`, non-editable, `:text_items` = `{"FAT", "Cable"}`) plus a "Get Selected
Object" button — same pattern as `rwwi_mancore_plan_dialog.build_toolbar` (`:kmlsource` selector +
`:get_rwo` button) and the same map-selection access pattern as its `get_rwo()`/`selected_str()`:

```magik
grs    << smallworld_product.application(:pni)
mapman << grs.plugin(:map_plugin)
sel    << mapman.current_map.current_selection
rwo    << sel.an_element().rwo   # when sel.size > 0
```

Dispatch on the dropdown value and `rwo.external_name`:

| Mode | Expected selection | Detection |
|------|--------------------|-----------|
| FAT | Pole or UUB structure | `rwo.external_name = "Pole"` (confirmed in `astri_splice_migrator.magik:310`) or `rwo.external_name = "UUB"` (**not confirmed in source — verify interactively at implementation time**, `pni_custom` only shows the internal table name `:uub`) |
| Cable | Aerial Route or Underground Route | `rwo.external_name = "Aerial Route" _orif rwo.external_name = "Underground Route"` — confirmed pattern, copied verbatim from `rwwi_mancore_plan_dialog.get_rwo()` (`rwwi_mancore_plan_dialog.magik:494`) |

**FAT mode** — from the selected Pole/UUB, find its FAT `sheath_splice`:

```magik
equipment << rwo.get_equipment()
splices   << equipment.select(predicate.navigate({:source_collection}, predicate.eq(:name, :sheath_splice)))
fat_splice << splices.select(predicate.eq(:sheath_splice_object_type, "FAT")).an_element()
```
(pattern copied from `rwi_export_to_aerial_kmz.magik:1446-1489`; if more than one FAT splice is found
on the structure, take `an_element()` and log a warning — edge case, not expected in practice).

**Cable mode** — from the selected route, find its cable (`sheath_with_loc`):

```magik
cable << rwo.cables().an_element()   # same call as rwwi_mancore_plan_dialog.get_rwo() line 496
```

Either branch ends by populating the Area and OLT Code fields from the resolved object, then calling
`_self.eq_lookup_hostname()` immediately (see §2b/2c) so Hostname is already resolved by the time the
user looks at the dialog — Get Selected Object never leaves Hostname stale.

### 2b/2c. Area / OLT Code / Hostname / FAT Code fields

Four `sw_text_item` fields, populated from the resolved object (FAT splice or cable — both expose
the same field names per `astri_splice_migrator.magik:261-286` and `rwwi_mancore_plan_query.magik`):

| Field | Editable? | Source | Notes |
|-------|-----------|--------|-------|
| **Area** | Yes | `.region` on the FAT splice or the cable | direct copy, editable afterwards in case of mismatch |
| **OLT Code** | Yes | `.olt_code` on the FAT splice or the cable | **new field** — also fully manual-entry capable, so the user can run a query for an OLT code that has no map object selected at all |
| **Hostname** | **No** (`:editable?, _false`) | `dim_olt_master_smallworld.olt_hostname` where `olt_code = <OLT Code field value>` | read-only — always derived, never typed; see lookup trigger below |
| **FAT Code** | Depends on mode | see below | FAT mode: editable text. Cable mode: dropdown (unchanged from before) |

**Hostname is looked up eagerly, not deferred to Run** — it must already be resolved (and is
read-only, so there's nothing else to fill it) by the time the user can press Run. The lookup
(mirrors `rwwi_stella_integration_dialog.magik:2150-2177` exactly):
```magik
sql << "SELECT olt_hostname FROM smallworld.dim_olt_master_smallworld WHERE olt_code = ? LIMIT 1"
recs << db_conn.sql_select(sql, olt_code)
rec  << recs.get()
hostname << _if rec _is _unset _then >> "" _else >> rec.olt_hostname.default("").write_string _endif
```
Uses the existing `rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")` connection
pattern already used throughout this dialog and `test_nisa_procs.magik`. Wrapped in a dialog method
`eq_lookup_hostname()` that reads `.items[:eq_olt_code_field].value`, runs the query, and writes
`.items[:eq_hostname_field].value`. This method fires from **two** places, both *before* Run is ever
reachable:
1. **Get Selected Object** — after populating the OLT Code field from the resolved splice/cable,
   call `_self.eq_lookup_hostname()` directly (setting a field's `.value` programmatically does not
   itself fire `:change_selector`, so this explicit call is required).
2. **Manual OLT Code edits** — the OLT Code field gets `:change_selector, :eq_olt_code_changed|()|`,
   which calls the same `eq_lookup_hostname()`. This is what lets the user type/overwrite an OLT code
   by hand and still get Hostname populated without touching the map at all.

If the lookup finds no row, Hostname is cleared to `""` and a log warning is shown — Run is expected
to fail its blank-field validation in that case rather than silently sending an empty hostname.

**FAT Code field — FAT mode**: editable text field, pre-filled by splitting `fat_splice.name` on `"."`
and taking the **last** segment (pattern confirmed at `rwwi_stella_integration_dialog.magik:2567-2569`,
e.g. name `"WNMLT4-5.040"` → `"040"`... **caveat**: the sample name in that file and the sample
response the user gave (`"fdt_code": "WNMLT4-5.040", "fat_code": "A09"`) don't share the same shape —
the *fdt_code* looks like it already contains a dot-separated segment, and *fat_code* looks
independent (e.g. `"A09"`), not derived from splitting `fdt_code`. This plan assumes the FAT
splice's own `.name` field is what gets dot-split (as the working code at line 2567 does), and that
its last segment is the value the Segment Problem Detail API expects as `fat`. **Verify this against
a real FAT splice record's `.name` value during implementation** before trusting the split logic —
either way, same as OLT Code, the field stays editable so the user can type/correct the FAT code by
hand when the selected object is a FAT (mirrors the OLT Code field's manual-entry behaviour, per the
user's explicit request).

**FAT Code field — Cable mode**: becomes a non-editable dropdown (`.text_items`, mirroring the
existing `:mode_selector` toggle pattern in this same file at `rwwi_nisa_dialog.magik:97-104`),
populated from all FAT splices sharing the cable's `ring_name` (unchanged from the previous version
of this plan — a single cable has no one obvious FAT code, hence a pick-list instead of free text):

```magik
sc_col  << .database.collections[:sheath_splice]
pred    << predicate.eq(:sheath_splice_object_type, "FAT") _and predicate.eq(:ring_name, cable.ring_name)
fats    << sc_col.select(pred)
fat_names << fats.fast_elements().map(_proc(s) >> s.name.split_by(".").last.default(s.name) _endproc)
```
This mirrors the ring_name-grouping query already used in `rwwi_astri_boq_generator.magik:1359-1389`
(FAT/FDT counted per `ring_name`), just narrowed to a name list instead of a count. Distinct/sort the
list before assigning to `.text_items`.

### 2d/2e. Run / Reset

- **Run**: reads the current **Area**, **Hostname**, and **FAT Code** field values (OLT Code is not
  sent to the API — it only drives the Hostname lookup) and validates all three are non-blank (mirrors
  the `size < 3` guard style in `search_clusters()`). Since Hostname is now always resolved eagerly
  (§2b/2c), Run itself does **no** database work — it only calls the NISA API:
  ```magik
  json    << nisa_segment_problem_detail(area, hostname, fat_code)
  result  << nisa_parse_segment_problem_detail_response(json)
  ```
  Populate the result table from `result[:data]`; show `result[:msg]` in the log on failure (this is
  the same "let the API's own validation message surface" behaviour already documented in the
  2026-08-24 plan §2.2).
- **Reset**: clear all four fields (Area, OLT Code, Hostname, FAT Code), clear the result table/cache,
  clear the selected object, disable Run/Navigate/Start — same shape as the existing `reset()` for the
  Cluster tab.

**Required fix while wiring this up**: `nisa_parse_segment_problem_detail_response`
(`test_nisa_procs.magik:358-427`) currently reads `item[:tlop_area_name]` and
`item[:tlop_cluster_name]` from each result row. The sample response the user provided uses plain
`"area"` and `"cluster_name"` keys, not `tlop_area_name`/`tlop_cluster_name` — as written, those two
columns will always come back empty. Fix the two field lookups (`item[:area]`, `item[:cluster_name]`)
as part of this work; everything else in that parser already matches the sample payload
(`ticket_number`, `tlop_status`, `hostname`, `fdt_code`, `fat_code`, `status_name`,
`tlop_created_date` all match as-is).

### 2f. Result table

New `sw_table`, same style as the Cluster tab's table (`enable_filter?`, `enable_sort?`,
`enable_manage_columns?`, `selection_type :row`, `selection_mode :one` since Navigate needs exactly
one row):

| # | Label | Source field |
|---|-------|--------------|
| 1 | # | row number |
| 2 | Ticket Number | `ticket_number` |
| 3 | Status | `tlop_status` |
| 4 | Status Name | `status_name` |
| 5 | Area | `area` (post-fix, see above) |
| 6 | Hostname | `hostname` |
| 7 | FDT Code | `fdt_code` |
| 8 | FAT Code | `fat_code` |
| 9 | Cluster Name | `cluster_name` (post-fix, see above) |
| 10 | Created Date | `tlop_created_date` |

### 2g. Navigate button

Enabled only when exactly one row is selected (mirrors `goto_selected()` / `single_selected?` in
`manage_actions()`). Resolve the row's FAT splice by name and go to it — **no WGS84↔local coordinate
transform needed here**, unlike the Cluster tab: `sheath_splice.location` is already a native GIS
geometry in the database's local coordinate system.

```magik
splice_name << row[:fdt_code] + "." + row[:fat_code]
splice << .database.collections[:sheath_splice].select(predicate.eq(:name, splice_name)).an_element()
_if splice _isnt _unset
_then
    pp << pseudo_point.new(splice.location.coord)
    pp.world << .database.world
    _self.changed(:goto_request, pp)   # reuses the existing plugin handler verbatim
_endif
```
Because this reuses the `:goto_request` databus message the plugin already handles
(`rwwi_nisa_plugin.note_change`, `.current_doc.goto(data.bounds)`), **no plugin change is required**
for Navigate.

### 2h. Blink (start/stop)

Reuse the animation mechanics from the Cluster tab (`map_animator` + `map_blink_animation` per
coordinate, `.animator` slot, Start/Stop buttons enabled per `manage_actions()`-style logic) but
sourced from the equipment result rows instead of outage clusters:

- New dialog method `equipment_blink_records()` — same shape as `all_outage_records()` but iterates
  the equipment result rows, resolving each `fdt_code + "." + fat_code` to a `sheath_splice`, builds
  `pseudo_point`s the same way as Navigate above, dedupes by splice name.
- New plugin methods `start_equipment_blink_animations()` / `stop_equipment_blink_animations()` —
  copy of `start_blink_animations()` / `stop_blink_animations()` with the record source swapped.
- New `note_change` branch: `:eq_blink_mode` (`_true`/`_false`) → calls the two methods above.
  (Kept separate from `:blink_mode` so Cluster-tab blink and Equipment-tab blink don't collide.)
- **Scope decision**: both tabs share the single `.animator` slot on the plugin — only one blink
  session (Cluster or Equipment) can run at a time. Starting one implicitly stops the other via the
  existing `stop_blink_animations()` call at the top of each start method. This is called out
  explicitly since it's a shared-resource simplification, not an oversight.

---

## 3. Why one model, two source files (not two dialog classes)

The ask is "existing UI becomes a tab, add a new tab" — i.e. one dialog, two panels — not two
independently-owned dialogs. Keeping a single `rwwi_nisa_dialog` model means:
- One `add_dependent(.owner)` wiring to the plugin (already exists) — no second databus consumer to
  register.
- `sw_table`/`sw_text_item` `:model` references stay uniform across both tabs.
- The plugin's `note_change` dispatch just grows two new `what` branches (`:eq_blink_mode`, and no new
  branch at all for Navigate since it reuses `:goto_request`).

To avoid bloating the already-1600-line `rwwi_nisa_dialog.magik`, **new equipment-tab slots are added
to the existing `def_slotted_exemplar(:rwwi_nisa_dialog, ...)` call** (Magik requires all slots
declared at exemplar definition), but **all new equipment-tab `_method` bodies go in a new file**,
`rwwi_nisa_equipment_dialog.magik` — Magik allows methods for one exemplar to be spread across
multiple source files/modules, so this is purely an organisational split, not a new class.

New slots to add to `rwwi_nisa_dialog`'s `def_slotted_exemplar`:
```magik
{:eq_mode,            _unset, :writable},   # :fat or :cable
{:eq_selected_object, _unset, :writable},   # resolved sheath_splice (FAT mode) or cable (Cable mode)
{:eq_result_rows,     _unset, :writable},   # rope of pl from nisa_parse_segment_problem_detail_response[:data]
{:eq_splice_cache,    _unset, :writable},   # splice_name -> sheath_splice rwo (resolved lazily for goto/blink)
{:eq_blink_active,    _false}
```

---

## 4. Tab container restructuring (`activate_in`)

Per `tab_container_example.magik` (`core/sw_core/modules/sw_swift/magik_gui_components_examples`):

```magik
_method rwwi_nisa_dialog.activate_in(frame)
    frame.title << "NISA Integration"

    .items[:top] << top_c << sw_canvas_container.new(frame, 4, 1, ...)
    .items[:outer] << outer << sw_container.new(top_c, 4, 1, :row_resize_values, {0, 1, 0, 1})

    .items[:tabs] << tab_con << sw_tab_container.new(outer, :min_height, 500)

    cluster_tab << tab_con.new_tab("Cluster")
    _self.build_toolbar(cluster_tab)      # existing method, unchanged
    _self.build_table(cluster_tab)        # existing method, unchanged

    equipment_tab << tab_con.new_tab("Equipment")
    _self.build_equipment_toolbar(equipment_tab)   # new
    _self.build_equipment_table(equipment_tab)     # new

    _self.build_log(outer)   # shared log window below both tabs, unchanged
    ...
_endmethod
```
Existing `build_toolbar`/`build_table`/`build_log` bodies are otherwise untouched — only their
parent container changes from `outer` to `cluster_tab`. The shared log window stays outside the tab
container so both tabs log to the same place, consistent with how the Cluster tab already works.

---

## 5. Files touched

| File | Change |
|------|--------|
| `rwwi_nisa_dialog.magik` | Add 5 new slots to `def_slotted_exemplar`; rewrite `activate_in` to build a `sw_tab_container` with Cluster + Equipment tabs (§4) |
| `rwwi_nisa_equipment_dialog.magik` (new) | All Equipment-tab UI build + logic methods (§2), including the OLT Code field's `:eq_olt_code_changed()` change_selector and the eager `eq_lookup_hostname()` |
| `rwwi_nisa_plugin.magik` | Add `:eq_blink_mode` branch to `note_change`; add `start_equipment_blink_animations()` / `stop_equipment_blink_animations()` / `equipment_blink_records()`-consumer wiring (no change needed for Navigate — reuses `:goto_request`) |
| `test_nisa_procs.magik` | Fix `nisa_parse_segment_problem_detail_response`'s `:tlop_area_name`/`:tlop_cluster_name` → `:area`/`:cluster_name` field lookups (§2e) |
| `source/load_list.txt` | Add `rwwi_nisa_equipment_dialog` after `rwwi_nisa_dialog` |

No `module.def` change — same module, same Java jar, no new dependencies.

---

## 6. Regression safety — the Cluster tab must keep working exactly as-is

This is a refactor of `activate_in` plus additive changes everywhere else, not a rewrite — the risk
surface is entirely in §4 (re-parenting the existing toolbar/table/log under a tab). To keep that risk
near zero:

- **No existing method body changes.** `build_toolbar`, `build_table`, `build_log`,
  `search_clusters`, `cluster_list_data`, `cluster_selected`, `check_outage_selected/all`,
  `show_map`, `go`/`stop`, `manage_actions`, `records`/`all_outage_records`, the logging helpers, the
  area-search flow — none of these are touched. Only the *parent container argument* passed to
  `build_toolbar`/`build_table` changes (from `outer` to the new `cluster_tab` container returned by
  `tab_con.new_tab("Cluster")`); `build_log` keeps its existing parent (`outer`, outside the tab
  container) so the log stays shared and visible regardless of which tab is active.
- **No existing `.items` keys renamed or removed.** `:search_field`, `:table`, `:log`,
  `:highlight_btn`, `:start_btn`, `:stop_btn`, etc. all keep their current keys — every existing
  method that reads `.items[:...]` keeps working unmodified. Only new `:eq_*`-prefixed keys are added
  for the Equipment tab (§3), so there's no collision risk.
- **`rwwi_nisa_plugin.note_change` keeps its existing `_if .../ _elif ...` branches
  (`:show_map`, `:blink_mode`, `:goto_request`) completely intact** — the Equipment tab's
  `:eq_blink_mode` is added as one more `_elif` at the end, and Navigate reuses `:goto_request`
  verbatim rather than adding a parallel path that could diverge from it.
- **The `.animator` slot sharing (§2h)** is the one place Cluster-tab behaviour is *observably*
  affected by the new tab: starting an Equipment blink while a Cluster blink is running will stop the
  Cluster one (and vice versa), because both go through the same `stop_blink_animations()` /
  `.animator` slot. This is called out here again because it's the only behavioural interaction
  between the two tabs — everything else in this plan is purely additive.

**Manual verification checklist before calling this done** (run in order, on the actual `gis.exe`
session, not just a code read-through):
1. Open *NISA Mass Problem Monitor...* — dialog opens with **Cluster** tab active by default, title
   and layout look the same as today.
2. Cluster tab, unchanged flows, exactly as before this change:
   - Search by cluster code and by area (toggle the `Search by:` dropdown) both still return results.
   - `Check All Outages` / per-row outage check still populates the traffic-light column.
   - Select rows → `Go To Selected` still navigates the map; `Show on Map` toggle still
     highlights/tooltips selected clusters.
   - `Start`/`Stop` blink still animates outage clusters on the map.
   - Reset still clears the table, cache, and log as before.
3. Switch to the **Equipment** tab and back to **Cluster** — confirm the Cluster table's data,
   selection, and log content are still intact (i.e. switching tabs doesn't rebuild or clear
   `rwwi_nisa_dialog`'s existing state, since it's one shared model instance).
4. Only after step 2 and 3 pass, exercise the new Equipment tab flows (§2) end-to-end.

---

## 7. Open items to confirm during implementation (not blocking this plan)

1. Exact `external_name` string for UUB structures (assumed `"UUB"`, unconfirmed in source — check
   interactively via `a_uub_rwo.external_name` at the Magik prompt).
2. Whether a Pole/UUB can legitimately host more than one FAT splice at once (plan takes
   `an_element()` + warns; revisit if this turns out to be common).
3. Confirm the FAT-name dot-split assumption in §2c against a real FAT splice `.name` value — the
   sample API response's `fdt_code`/`fat_code` shapes don't obviously match the `"WNMLT4-5.040"` →
   `"040"` pattern from `rwwi_stella_integration_dialog.magik:2567`.
4. Confirm `sheath_splice`/cable collection names at runtime (`:sheath_splice`, `:sheath_with_loc`) —
   these exemplars ship compiled (no Magik source/datamodel XML in this repo), so slot/collection
   names in this plan are reconstructed from call-site usage in `pni_custom`, not from a canonical
   definition.

---

*Plan created: 2026-09-01. Awaiting approval before implementation.*
