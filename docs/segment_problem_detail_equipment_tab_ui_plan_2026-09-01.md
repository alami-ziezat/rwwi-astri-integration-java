# Segment Problem Detail — Equipment Tab UI Plan

**Date:** 2026-09-01 (last updated 2026-09-03 — see §8 for what changed after implementation started)
**Author:** Claude Code
**Status:** IMPLEMENTED — reflects the built UI, kept as the as-built reference for this feature
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
  to a selected ticket's FAT, highlight it, and blink all resolved FAT locations.

The Equipment tab uses **two stacked toolbars** (§2 diagram below) rather than one long row:
- **Toolbar 1**: Object Type selector, Get Selected Object, Area, OLT Code, FAT Code, Run, Reset,
  result count.
- **Toolbar 2**: Hostname (read-only), Navigate, **Highlight** (new toggle, positioned right after
  Navigate — mirrors the Cluster tab's highlight button), Start/Stop blink.

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

Dispatch on the dropdown value, then on **what got selected** — the map graphic that's actually
clickable turned out, in practice, to be the equipment/cable itself as often as its parent
structure/route, so both modes accept either shape rather than requiring one specific external_name:

| Mode | Accepts | Detection |
|------|---------|-----------|
| FAT | a FAT `sheath_splice` selected directly, **or** a Pole/UUB structure hosting one | `rwo.responds_to?(:sheath_splice_object_type)` → use directly (verify type = "FAT"); else `rwo.external_name = "Pole" _orif rwo.external_name = "UUB"` → drill into `get_equipment()` (`"Pole"` confirmed in `astri_splice_migrator.magik:310`; `"UUB"` still **unconfirmed in source** — see §7) |
| Cable | a cable (`sheath_with_loc`) selected directly, **or** an Aerial/Underground Route hosting one | `rwo.responds_to?(:cables)` → route, derive via `.cables()` (same call as `rwwi_mancore_plan_dialog.get_rwo()` line 496); else `rwo.responds_to?(:fiber_count)` → cable selected directly |

**FAT mode** — either the splice itself, or drill down from a Pole/UUB:

```magik
_if rwo.responds_to?(:sheath_splice_object_type)
_then
    splice << rwo   # (after checking sheath_splice_object_type.uppercase = "FAT")
_elif rwo.external_name = "Pole" _orif rwo.external_name = "UUB"
_then
    equipment  << rwo.get_equipment()
    splices    << equipment.select(predicate.navigate({:source_collection}, predicate.eq(:name, :sheath_splice)))
    fat_splices << splices.select(predicate.eq(:sheath_splice_object_type, "FAT"))
    splice << fat_splices.an_element()   # logs a warning if more than one is found
_endif
```
(structure-drilldown pattern copied from `rwi_export_to_aerial_kmz.magik:1446-1489`).

**Cable mode** — either the cable itself, or derive it from the selected route:

```magik
_if rwo.responds_to?(:cables)
_then
    cable << rwo.cables().an_element()
_elif rwo.responds_to?(:fiber_count)
_then
    cable << rwo
_endif
```

Either branch ends by populating the Area and OLT Code fields from the resolved object, then calling
`_self.eq_lookup_hostname()` immediately (see §2b/2c) so Hostname is already resolved by the time the
user looks at the dialog — Get Selected Object never leaves Hostname stale.

### 2b/2c. Area / OLT Code / Hostname / FAT Code fields

Four `sw_text_item` fields, populated from the resolved object (FAT splice or cable — both expose
the same field names per `astri_splice_migrator.magik:261-286` and `rwwi_mancore_plan_query.magik`).
**Area / OLT Code / FAT Code live on Toolbar 1** (with Get Selected Object, Run, Reset); **Hostname
lives on Toolbar 2** (with Navigate, Highlight, Start/Stop) — see §4 for the full toolbar layout:

| Field | Toolbar | Editable? | Source | Notes |
|-------|---------|-----------|--------|-------|
| **Area** | 1 | Yes | `.region` on the FAT splice or the cable | direct copy, editable afterwards in case of mismatch |
| **OLT Code** | 1 | Yes | `.olt_code` on the FAT splice or the cable | **new field** — also fully manual-entry capable, so the user can run a query for an OLT code that has no map object selected at all |
| **FAT Code** | 1 | Depends on mode | see below | FAT mode: editable text. Cable mode: dropdown (unchanged from before) |
| **Hostname** | 2 | **No** (`:editable?, _false`) | `dim_olt_master_smallworld.olt_hostname` where `olt_code = <OLT Code field value>` | read-only — always derived, never typed; see lookup trigger below |

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
one row). Selection survives column sorting via a row-cache, same trick as the Cluster tab's
`cluster_cache`: two extra dialog slots beyond the five in §3, `eq_row_cache` (row number → result pl)
and `eq_selected_row` (the single selected pl, or `_unset`) — the table's "#" column carries the
original row number through a sort, and `eq_result_selected()` reads it back to look up
`eq_row_cache`.

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

Lives on **Toolbar 2**. Enabled only when exactly one row is selected (mirrors `goto_selected()` /
`single_selected?` in `manage_actions()`). Resolve the row's FAT splice by name and go to it — **no
WGS84↔local coordinate
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

### 2i. Highlight (toggle) — new, added 2026-09-03

Lives on **Toolbar 2**, positioned right after Navigate. An `sw_image_toggle_item` (`eq_highlight_btn`)
mirroring the Cluster tab's `highlight_btn` exactly: toggling it on/off shows/hides a persistent
tooltip-style highlight for the **currently selected result row's FAT** on the map (independent of
Navigate, which moves the view; Highlight just marks the spot and can stay on while the user pans
around). Since the Equipment table is single-selection, this always highlights at most one FAT — no
multi-row highlighting like the Cluster tab's `:show_map`.

- `eq_show_map(toggled?)` — same shape as the Cluster tab's `show_map()`: warns and resets the toggle
  if nothing is selected, otherwise fires a new `:eq_show_map` databus message (kept separate from
  `:show_map` so the two tabs' highlight sessions don't collide, same reasoning as `:eq_blink_mode`).
- `eq_highlight_record()` — same shape as `records()`, but returns a rope of **0 or 1**
  `{:pseudo_point, :result_pl}` for `eq_selected_row`, resolved via the same `eq_resolve_splice`
  helper used by Navigate/Blink.
- `eq_highlight_tooltip_for(pl)` — builds the tooltip text (ticket number, status, area, hostname,
  FDT/FAT code, cluster, created date) from a result row.
- `eq_result_selected()` refreshes the highlight immediately when the toggle is already on and the
  selection changes (mirrors `cluster_selected()`'s equivalent refresh call).
- `eq_reset()` now also turns the toggle off (`eq_show_map(_false)` + resets the button value), same
  as it already did for blink.

**Plugin side — sharing the render pipeline, not duplicating it**: the Cluster tab's tooltip-drawing
machinery (`int!draw_cluster` — draws a pointer-tooltip bubble at a pixel location — and
`tooltip_border_points`) takes its text as a plain parameter already, so it needed no changes; a new
`draw_all_equipment_highlights(gc)` calls it directly with a new `:equipment` style (blue fill, bold
white text, added to the existing `:fill_style`/`:text_style` shared-constant maps) instead of
duplicating the drawing code. The real wrinkle was the **post-renderer registration**: Cluster's
`show_mode()` and the new `eq_show_mode()` both need the same `:transient_drawer` post-renderer
registered with the map manager, and toggling one off must not silently break the other if it's still
active. Two small private helpers, `int!ensure_post_renderer()` / `int!maybe_remove_post_renderer()`
(the latter checks *both* `.render_status` and `.eq_render_status` before actually unregistering),
now do that registration/deregistration instead of `show_mode()` calling `add_post_renderer` /
`remove_post_renderer` directly — this is the one place `show_mode()`'s body actually changed (see §6
for why that's still safe). `map_damage_notify` now checks both `.render_status` and
`.eq_render_status` each redraw and draws whichever (or both) are active.

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

Slots added to `rwwi_nisa_dialog`'s `def_slotted_exemplar` (as built — two more than originally
planned, `eq_row_cache` and `eq_selected_row`, added for sort-safe single-row selection per §2f):
```magik
{:eq_mode,            _unset, :writable},   # :fat or :cable
{:eq_selected_object, _unset, :writable},   # resolved sheath_splice (FAT mode) or cable (Cable mode)
{:eq_result_rows,     _unset, :writable},   # rope of pl from nisa_parse_segment_problem_detail_response[:data]
{:eq_row_cache,       _unset, :writable},   # row number -> result pl (survives table sort, like cluster_cache)
{:eq_selected_row,    _unset, :writable},   # currently selected result pl (single-selection table)
{:eq_splice_cache,    _unset, :writable},   # splice_name -> sheath_splice rwo (resolved lazily for goto/blink/highlight)
{:eq_blink_active,    _false}
```

---

## 4. Tab container restructuring (`activate_in`)

Per `tab_container_example.magik` (`core/sw_core/modules/sw_swift/magik_gui_components_examples`).
**As-built correction**: a tab pane returned by `new_tab()` holds exactly **one** direct child (like
a `card_stack` card) — the first build attempt tried adding a toolbar *and* a table straight into the
tab pane and hit `Error: sw_container_19 does not have space to add sw_table_50`. Each tab therefore
gets its own wrapper `sw_container` first, and — since Equipment now has two stacked toolbars plus the
table (§1/§2i) — that wrapper needs 3 rows, not 2:

```magik
_method rwwi_nisa_dialog.activate_in(frame)
    frame.title << "NISA Integration"

    .items[:top] << top_c << sw_canvas_container.new(frame, 4, 1, ...)
    .items[:outer] << outer << sw_container.new(top_c, 4, 1, :row_resize_values, {0, 1, 0, 1})

    .items[:tabs] << tab_con << sw_tab_container.new(outer, :min_height, 500)

    # Tab 1 - Cluster (existing content, unchanged - only its parent changes).
    # 2-row wrapper: toolbar, table.
    cluster_tab   << tab_con.new_tab("Cluster")
    cluster_inner << sw_container.new(cluster_tab, 2, 1, :row_resize_values, {0, 1})
    _self.build_toolbar(cluster_inner)      # existing method, unchanged
    _self.build_table(cluster_inner)        # existing method, unchanged

    # Tab 2 - Equipment (new). 3-row wrapper: toolbar 1, toolbar 2, table.
    equipment_tab   << tab_con.new_tab("Equipment")
    equipment_inner << sw_container.new(equipment_tab, 3, 1, :row_resize_values, {0, 0, 1})
    _self.build_equipment_toolbar(equipment_inner)    # new - Object/Get/Area/OLT Code/FAT Code/Run/Reset
    _self.build_equipment_toolbar2(equipment_inner)   # new - Hostname/Navigate/Highlight/Start/Stop
    _self.build_equipment_table(equipment_inner)      # new

    _self.build_log(outer)   # shared log window below both tabs, unchanged
    ...
_endmethod
```
Existing `build_toolbar`/`build_table`/`build_log` bodies are otherwise untouched — only their
parent container changes from `outer` to `cluster_inner`. The shared log window stays outside the tab
container so both tabs log to the same place, consistent with how the Cluster tab already works.

---

## 5. Files touched

| File | Change |
|------|--------|
| `rwwi_nisa_dialog.magik` | Add 7 new slots to `def_slotted_exemplar` (§3); rewrite `activate_in` to build a `sw_tab_container` with Cluster + Equipment tabs, each wrapped in its own row container (§4) |
| `rwwi_nisa_equipment_dialog.magik` (new) | All Equipment-tab UI build + logic methods (§2), split across two toolbar builders (`build_equipment_toolbar` / `build_equipment_toolbar2`), including the OLT Code field's `:eq_olt_code_changed()` change_selector, the eager `eq_lookup_hostname()`, and the Highlight toggle (`eq_show_map`, `eq_highlight_record`, `eq_highlight_tooltip_for` — §2i) |
| `rwwi_nisa_plugin.magik` | Add `:eq_blink_mode` and `:eq_show_map` branches to `note_change`; add `start_equipment_blink_animations()` / `stop_equipment_blink_animations()` (blink), `eq_show_mode()` / `eq_show_records()` / `eq_build_rwo_cache()` / `draw_all_equipment_highlights()` (highlight, §2i); add an `:equipment` style to the shared `:fill_style`/`:text_style` constants; add `int!ensure_post_renderer()` / `int!maybe_remove_post_renderer()` and route `show_mode()`'s post-renderer add/remove through them so Cluster and Equipment highlighting can share one `:transient_drawer` registration safely (no change needed for Navigate — reuses `:goto_request`) |
| `test_nisa_procs.magik` | Fix `nisa_parse_segment_problem_detail_response`'s `:tlop_area_name`/`:tlop_cluster_name` → `:area`/`:cluster_name` field lookups (§2e) |
| `source/load_list.txt` | Add `rwwi_nisa_equipment_dialog` after `rwwi_nisa_dialog` |

No `module.def` change — same module, same Java jar, no new dependencies.

---

## 6. Regression safety — the Cluster tab must keep working exactly as-is

This is a refactor of `activate_in` plus additive changes everywhere else, not a rewrite — the risk
surface is entirely in §4 (re-parenting the existing toolbar/table/log under a tab). To keep that risk
near zero:

- **Almost no existing method body changes.** `build_toolbar`, `build_table`, `build_log`,
  `search_clusters`, `cluster_list_data`, `cluster_selected`, `check_outage_selected/all`,
  `go`/`stop`, `manage_actions`, `records`/`all_outage_records`, the logging helpers, the
  area-search flow — none of these are touched. Only the *parent container argument* passed to
  `build_toolbar`/`build_table` changes (from `outer` to the new `cluster_inner` wrapper container
  inside `tab_con.new_tab("Cluster")`, §4); `build_log` keeps its existing parent (`outer`, outside
  the tab container) so the log stays shared and visible regardless of which tab is active. **One
  exception**: `show_mode()`'s two `add_post_renderer`/`remove_post_renderer` calls were replaced with
  calls to `int!ensure_post_renderer()`/`int!maybe_remove_post_renderer()` (§2i) — required so the new
  Equipment Highlight toggle can share the same `:transient_drawer` registration without the two
  toggles fighting over it. The externally-observable behaviour of `show_mode()` on its own is
  unchanged (registers on enable, unregisters on disable) — the only new case is Cluster disabling
  while Equipment highlight is still on, where the renderer now correctly stays registered instead of
  being torn down out from under Equipment.
- **No existing `.items` keys renamed or removed.** `:search_field`, `:table`, `:log`,
  `:highlight_btn`, `:start_btn`, `:stop_btn`, etc. all keep their current keys — every existing
  method that reads `.items[:...]` keeps working unmodified. Only new `:eq_*`-prefixed keys are added
  for the Equipment tab (§3), so there's no collision risk.
- **`rwwi_nisa_plugin.note_change` keeps its existing `_if .../ _elif ...` branches
  (`:show_map`, `:blink_mode`, `:goto_request`) completely intact** — the Equipment tab's
  `:eq_blink_mode` and `:eq_show_map` are each added as one more `_elif`, and Navigate reuses
  `:goto_request` verbatim rather than adding a parallel path that could diverge from it.
- **The `.animator` slot sharing (§2h)** is the one place Cluster-tab behaviour is *observably*
  affected by the new tab: starting an Equipment blink while a Cluster blink is running will stop the
  Cluster one (and vice versa), because both go through the same `stop_blink_animations()` /
  `.animator` slot. This is called out here again because it's the only behavioural interaction
  between the two tabs' *blink* — Highlight (§2i) is designed to coexist instead (see the
  `int!maybe_remove_post_renderer()` note above), since there's no reason a user shouldn't have a
  Cluster highlight and an Equipment highlight up at the same time.

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
   - `Show on Map` highlight still works, including while switching to the Equipment tab and turning
     its Highlight toggle on too (§2i) — both should coexist without either one going dark.
   - Reset still clears the table, cache, and log as before.
3. Switch to the **Equipment** tab and back to **Cluster** — confirm the Cluster table's data,
   selection, and log content are still intact (i.e. switching tabs doesn't rebuild or clear
   `rwwi_nisa_dialog`'s existing state, since it's one shared model instance).
4. Only after step 2 and 3 pass, exercise the new Equipment tab flows (§2) end-to-end, including:
   toggling Object Type between FAT/Cable a few times (should never crash — see §8), Get Selected
   Object with the splice/cable itself selected AND with the parent Pole/UUB/route selected, Run,
   Navigate, Highlight (toggle on, change selected row, toggle off), Start/Stop blink.

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
5. `rwo.responds_to?(:fiber_count)` (§2a) is a heuristic to tell "cable selected directly" apart from
   "something else" — if a non-cable object also happens to respond to `:fiber_count`, Cable mode
   could mis-accept it. Same caveat, lower risk, for `rwo.responds_to?(:sheath_splice_object_type)` in
   FAT mode. Revisit if either misfires against real data.

---

## 8. As-built deviations from the original 2026-09-01 plan

Logged here rather than silently editing history, since this doc doubles as the as-built reference.
All three were found by actually running the dialog in `gis.exe`, not by re-reading the plan.

1. **Tab pane holds one child, not many (§4).** The original plan's `activate_in` sketch added a
   toolbar and a table straight into each `new_tab()` result. That crashes at realise time
   (`Error: sw_container_19 does not have space to add sw_table_50`) — a tab pane behaves like a
   `card_stack` card: exactly one direct child. Fixed by wrapping each tab's contents in its own
   `sw_container` (2 rows for Cluster, 3 for Equipment once the second toolbar existed).
2. **`simple_vector.new()` takes a required size argument.** Three places cleared `eq_fat_code_field`'s
   `.text_items` with `simple_vector.new()` (zero args) to switch the field back to free-text mode —
   this raised `too_few_arguments` every time the Object Type dropdown was toggled (since
   `eq_mode_changed()` calls `eq_reset()`). Fixed by using the empty literal `{}` instead, matching how
   populated `.text_items` values are already written elsewhere in this file (e.g. `{"FAT", "Cable"}`).
3. **Get Selected Object needed to accept the equipment/cable itself, not just its parent
   structure/route (§2a).** The original plan assumed the user would always select a Pole/UUB (for
   FAT) or a Route (for Cable) and drill down via `get_equipment()`/`.cables()`. In practice the
   splice/cable graphic itself is often what's actually clickable on the map. Both `eq_get_fat_object`
   and `eq_get_cable_object` now check what was actually selected first (`responds_to?` on a
   distinguishing field) and only fall back to the structure/route drilldown if something else was
   selected — see the open item 5 above for the residual risk in that heuristic.

The Highlight button (§2i) and the two-toolbar layout (§1) were requested as follow-up adjustments
after the initial implementation and are documented in place above, not listed separately here since
they were planned (as a doc update) before being built, unlike the three fixes above.

---

*Plan created: 2026-09-01. Updated 2026-09-03 to match the as-built UI (two toolbars, Highlight
button, and the fixes in §8).*
