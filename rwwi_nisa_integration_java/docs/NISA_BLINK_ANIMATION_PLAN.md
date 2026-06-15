# NISA Blink Animation & Auto-Refresh Plan

**Feature:** Add FMS-style blinking animation and timed auto-refresh to NISA outage map display  
**Date:** 2026-06-14  
**Author:** yudo.ariyanto@gmail.com

---

## Problem Statement

Currently the NISA dialog shows outage clusters as **static red tooltip bubbles** permanently painted on the map when the "Show on Map" toggle is ON. When multiple outage clusters are geographically close to each other, their tooltips overlap and become hard to read. There is also no automatic refresh — the user must manually click "Check All Outages" to get updated data.

---

## Proposed Solution

Add two new capabilities borrowed from the `rwwi_fms` module pattern:

1. **Blinking animation markers** — Each outage cluster gets a pulsing concentric-circle blink effect (like FMS fault markers) drawn by a background-thread animator. This visually distinguishes each cluster location even when tooltips overlap.

2. **Timed auto-refresh** — An interval selector (1/4, 1/2, 1, 2, 5 min) and Start/Stop buttons let the user run a periodic background poll that calls the NISA API and refreshes the map automatically.

---

## Current Rendering Pipeline (unchanged)

```
show_map(true)
  → rwwi_nisa_plugin.show_mode(:enable)
    → add_post_renderer(_self, 150, :transient_drawer)
    → show_records()
      → build_rwo_cache()         ← filters clusters visible in current map bounds
      → publish_zone_geometries() ← red 50%-wash zone areas via databus
      → refresh_maps()            ← triggers map_damage_notify callback

map_damage_notify(gc, map_view)
  → build_rwo_cache()
  → draw_all_clusters(gc)         ← draws red tooltip bubbles permanently
```

The static tooltip rendering stays **unchanged**. The blink animation runs **alongside** it in a separate background thread.

---

## Module Registration Required

### Why

`animator_demo` and `rwwi_fms` both live under `pni_custom/rwwi_astri_integration_java/magik/fms/` but are not referenced by any module currently loaded. Before they can be used, the Smallworld module manager must know to load them.

### What to register and where

| Module | Provides | Register in |
|---|---|---|
| `animator_demo` | `map_animator`, `map_blink_animation`, `map_animation_base` | `rwwi_nisa_integration/module.def` → `requires` block |
| `rwwi_fms` | `timed_event` (timer class) | `rwwi_nisa_integration/module.def` → `requires` block |

### Change: `rwwi_nisa_integration/module.def`

**File:** `pni_custom/rwwi_astri_integration_java/rwwi_nisa_integration_java/magik/rwwi_nisa_integration/module.def`

**Current:**
```
rwwi_nisa_integration	1

description
    NISA API Integration Module - Mass Problem queries via JWT Bearer auth.
    Provides global procedures for querying active mass problems by cluster.
end

requires
    base
end

requires_java
    rwwi.nisa.integration
end

language en_gb
```

**After:**
```
rwwi_nisa_integration	1

description
    NISA API Integration Module - Mass Problem queries via JWT Bearer auth.
    Provides global procedures for querying active mass problems by cluster.
end

requires
    base
    animator_demo
    rwwi_fms
end

requires_java
    rwwi.nisa.integration
end

language en_gb
```

> **Note:** The Smallworld module manager resolves `requires` entries by scanning all known product directories. Because `animator_demo` and `rwwi_fms` are inside the `pni_custom` product tree (under `rwwi_astri_integration_java/magik/fms/`), they will be found automatically — no alias file changes needed.

---

## New Feature: Blinking Animation

### How it works (mirrors FMS exactly)

When the user toggles **Show on Map ON**:
1. A `map_animator` is created on the current map view and started (`run()`).
2. For each cluster in `.geoms`, a `map_blink_animation` is created at that cluster's world coordinate and added to the animator.
3. The animator runs in a background thread, drawing/undrawn pulsing concentric circles at 0.2s intervals (5 fps).

When the user toggles **Show on Map OFF** (or calls `reset()`):
1. The animator is stopped (`stop()`).
2. Its reference is cleared.
3. The map is refreshed to remove the blink artifacts.

### Slot changes to `rwwi_nisa_plugin`

Add one new slot to `def_slotted_exemplar` in `rwwi_nisa_plugin.magik`:

```magik
{:animator, _unset}   # map_animator instance (FMS-style blink engine)
```

### New/modified methods in `rwwi_nisa_plugin.magik`

#### Modified: `show_mode(enable?)`

```
Current:  show_mode adds post_renderer and calls show_records()
Addition: when enable? = true  → also call _self.start_blink_animations()
          when enable? = false → also call _self.stop_blink_animations()
```

#### New: `start_blink_animations()`

```
Purpose: Create map_animator, create one map_blink_animation per outage 
         cluster in .geoms, add each to animator, then run().
Called:  from show_mode(_true), after show_records() populates .geoms.

Logic:
  _self.stop_blink_animations()      # clean up any old animator
  _if .geoms _is _unset _orif .geoms.empty? _then _return _endif
  _if .current_doc _is _unset _then _return _endif
  .animator << map_animator.new(.current_doc)
  .animator.run()
  _for rec _over .geoms.fast_elements()
  _loop
      coord << rec[:pseudo_point].coordinate
      ani   << map_blink_animation.new(.current_doc, coord)
      .animator.add(ani)
  _endloop
```

#### New: `stop_blink_animations()`

```
Purpose: Gracefully stop and discard the animator.
Called:  from show_mode(_false), reset, and start_blink_animations() as cleanup.

Logic:
  _if .animator _isnt _unset
  _then
      .animator.stop()
      .animator << _unset
  _endif
```

#### Modified: `show_records()`

```
Addition: After refresh_maps(), call _self.start_blink_animations()
          so that when outage data changes (e.g. after auto-refresh) 
          the animations are rebuilt to match the new .geoms set.
```

---

## New Feature: Timed Auto-Refresh

### How it works (mirrors FMS exactly)

- A `timed_event` instance (from `rwwi_fms` module) runs in a background thread.
- On each tick it calls a refresh procedure that re-queries NISA and updates the map.
- The user controls the interval (1/4 min = 15s, 1/2 min = 30s, 1 min = 60s, 2 min = 120s, 5 min = 300s).
- Start/Stop buttons in the toolbar control the timer lifecycle.

### Slot changes to `rwwi_nisa_dialog`

Add two new slots to `def_slotted_exemplar` in `rwwi_nisa_dialog.magik`:

```magik
{:timer,    _unset},   # timed_event instance for auto-refresh
{:interval, _unset}    # refresh interval in seconds
```

### UI changes in `rwwi_nisa_dialog.activate_in()` / `build_toolbar()`

Add to the toolbar (after the existing highlight_btn):

```
| Separator |
[ Interval dropdown: "1/4 min" / "1/2 min" / "1 min" / "2 min" / "5 min" ]
[ ▶ Start ]   [ ■ Stop ]
```

Widget keys:
- `.items[:interval_selector]` — `sw_text_item` with text_items list, change_selector `:|interval_changed()|`
- `.items[:start_btn]` — `sw_button_item` selector `:go|()|`, tooltip "Start auto-refresh"
- `.items[:stop_btn]` — `sw_button_item` selector `:stop|()|`, tooltip "Stop auto-refresh", initially disabled

### New/modified methods in `rwwi_nisa_dialog.magik`

#### New: `interval_changed(selected_value)`

```
Maps the dropdown string to seconds and stores in .interval:
  "1/4 min" → 15
  "1/2 min" → 30
  "1 min"   → 60
  "2 min"   → 120
  "5 min"   → 300
Default if not matched: 60
```

#### New: `go()`

```
Purpose: Start the timed auto-refresh loop.

Logic:
  _self.stop()                  # clean up any existing timer first
  interval << .interval.default(60)
  .timer << timed_event.new(interval, _proc() _self.check_for_update() _endproc, {})
  .timer.go(1)
  .items[:start_btn].enabled? << _false
  .items[:stop_btn].enabled?  << _true
  _self.log_info("Auto-refresh started, interval: " + interval.write_string + "s")
```

#### New: `stop()`

```
Purpose: Stop the timed auto-refresh loop.

Logic:
  _if .timer _isnt _unset
  _then
      .timer.stop()
      .timer << _unset
  _endif
  .items[:start_btn].enabled? << _true
  .items[:stop_btn].enabled?  << _false
  _self.log_info("Auto-refresh stopped.")
```

#### New: `check_for_update()`

```
Purpose: Timer callback — re-query NISA and refresh the map display.

Logic:
  _self.log_info("[Auto] Refreshing NISA outage data...")
  _if .result_rows.empty? _then _return _endif
  _if .search_mode _is :area
  _then
      # Re-run full area search to pick up new MPs
      _local term << .items[:search_field].value.default("").trim_spaces()
      _if term.size >= 3 _then _self.search_by_area(term) _endif
  _else
      # Re-check all clusters for updated outage status
      _self.check_outage_all()
  _endif
  # Notify plugin to rebuild map markers (triggers start_blink_animations via show_records)
  _self.changed(:show_map, _true)
```

#### Modified: `reset()`

```
Addition: Call _self.stop() before clearing result_rows, so the timer is
          halted and buttons reset whenever the user resets the dialog.
```

#### Modified: `manage_actions()`

```
Addition: .items[:start_btn].enabled? is only _true when:
          has_outage AND .timer _is _unset
          (prevents double-starting)
```

---

## Updated Load Order

`rwwi_nisa_integration/source/load_list.txt` — **no change needed**. The `timed_event` and animation classes are loaded as part of `animator_demo` and `rwwi_fms` modules, which Smallworld loads before `rwwi_nisa_integration` (per the `requires` dependency order).

---

## File Change Summary

| File | Change |
|---|---|
| `rwwi_nisa_integration/module.def` | Add `requires animator_demo` and `requires rwwi_fms` |
| `rwwi_nisa_plugin.magik` | Add `:animator` slot; add `start_blink_animations()`, `stop_blink_animations()`; modify `show_mode()` and `show_records()` |
| `rwwi_nisa_dialog.magik` | Add `:timer`, `:interval` slots; add interval dropdown + Start/Stop buttons to toolbar; add `go()`, `stop()`, `check_for_update()`, `interval_changed()`; modify `reset()` and `manage_actions()` |

---

## UI Mockup: Updated Toolbar

```
[ Search by: Area ▾ ] [ search field        ] [🔍] [✖] [▶] [📍] [◉ Show on Map]
  ── separator ──
[ 1 min ▾ ] [ ▶ Start ] [ ■ Stop ]
```

Legend:
- `[🔍]` Search
- `[✖]` Reset
- `[▶]` Check All Outages (cluster_code mode only)
- `[📍]` Go To Selected
- `[◉ Show on Map]` Toggle map highlights
- `[ 1 min ▾ ]` Interval selector
- `[ ▶ Start ]` Start auto-refresh
- `[ ■ Stop ]` Stop auto-refresh

---

## Interaction Diagram

```
User: Show on Map = ON
  → rwwi_nisa_plugin.show_mode(_true)
      → add_post_renderer (static tooltips still work)
      → show_records()
          → build_rwo_cache() + publish_zone_geometries() + refresh_maps()
          → start_blink_animations()      ← NEW
              → map_animator.new() + run()
              → map_blink_animation.new() × N clusters
              → animator.add(each blink)
              → [background thread: pulsing circles at each cluster coord]

User: Start auto-refresh (1 min)
  → rwwi_nisa_dialog.go()
      → timed_event.new(60, check_for_update, {}).go()
      → [background thread: fires every 60s]
          → check_for_update()
              → check_outage_all() / search_by_area()   ← re-queries NISA
              → changed(:show_map, _true)
                  → plugin.show_mode(_true)
                      → start_blink_animations()         ← rebuilds animator with updated cluster set

User: Show on Map = OFF  (or Reset)
  → rwwi_nisa_plugin.show_mode(_false)
      → stop_blink_animations()          ← NEW: .animator.stop()
      → remove_post_renderer
      → refresh_maps()

User: Stop auto-refresh
  → rwwi_nisa_dialog.stop()
      → .timer.stop()
```

---

## Notes & Constraints

1. **No MySQL dependency in NISA context.** `rwwi_fms` contains MySQL JDBC configuration code (`rwwi_configure_external_db.magik`) but that code only runs when explicitly called — the `timed_event` class itself has no DB dependency. Loading `rwwi_fms` is safe.

2. **`animator_demo` package is `:mapani`.** The blink/animator classes are in the `:mapani` package. They must be referenced with their full package name in `_package user` context: `mapani:map_animator`, `mapani:map_blink_animation`.

3. **Background thread safety.** `map_animator` uses an atomic command queue internally — it is safe to call `add()`, `remove()`, `stop()` from the UI thread while the animation thread is running. No extra locking needed.

4. **Show on Map re-toggle after auto-refresh.** When `check_for_update()` fires `changed(:show_map, _true)`, the plugin calls `show_mode(_true)` which calls `show_records()`. `show_records()` rebuilds `.geoms` from the updated outage data, then calls `start_blink_animations()` which stops the old animator and creates a new one with the correct cluster set. This is intentional — the cluster set may have changed after a NISA API refresh.

5. **`map_view_mods.magik` side-effect.** `animator_demo` patches `map_view.run_controlled_render()` and `document_gui_framework.handle_pan_action()`. These patches pause the animator during map renders and pans. This is harmless to NISA's static tooltip rendering (which runs in `map_damage_notify`, not in the animator thread).
