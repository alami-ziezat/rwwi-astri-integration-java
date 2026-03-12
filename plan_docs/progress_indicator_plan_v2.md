# Plan v2: Progress Bar for KML Export (sw_progress_dialog approach)

## Reference: rwi_construction_view_export.magik

The working pattern used in the codebase is `sw_progress_dialog`, NOT
`progress_indicator_dialog`. Key differences:

```magik
# 1. Get application owner first
.owner << smallworld_product.pni_application()

# 2. Create dialog (needs owner, named args, modality)
.dialog << sw_progress_dialog.new_dialog(
    .owner,
    :title,                  "KML Export",
    :modality,               :primary_modal,
    :show_progress_percent?, _true
)

# 3. Activate + set task description + start
.dialog.activate()
.dialog.set_label(:task, "Please wait while exporting KML.\nThis may take some time.")
.dialog.start_progress()

# 4. During export — update info label + set percentage (0-100)
.dialog.set_label(:info, "Detecting network levels...")
.dialog.set_progress(10)

# 5. At the end
.dialog.complete_progress()
.dialog.close()
```

---

## Key API Comparison

| `progress_indicator_dialog` (old, rejected) | `sw_progress_dialog` (new, working) |
|--------------------------------------------|--------------------------------------|
| `progress_indicator_dialog.new("Title")` | `sw_progress_dialog.new_dialog(owner, :title, "...")` |
| No owner needed | Needs `owner` (application handle) |
| `.max_count << N` (step count) | `:show_progress_percent?, _true` |
| `.progress_changed(step_number)` | `.set_progress(percentage_0_to_100)` |
| `.info_string << "text"` | `.set_label(:info, "text")` |
| No task label | `.set_label(:task, "description")` |
| No start call | `.start_progress()` |
| No complete call | `.complete_progress()` |

---

## Architecture

### New slots on `rwi_export_to_aerial_kmz`

```magik
{:progress_dialog, _unset, :writable},  # sw_progress_dialog instance
{:progress_owner,  _unset, :writable},  # Application owner for dialog
```

### Percentage mapping (stage → %)

| % | Stage |
|---|-------|
| 5  | Dialog created, starting export |
| 10 | Detecting network levels |
| 15 | Network levels detected, creating KML file |
| 20 | KML header and styles written |
| 30 | Feeder section complete (or skipped) |
| 40 | Subfeeder section complete (or skipped) |
| 50 | Cluster: counting / filtering empty LINE folders |
| 60 | Cluster: LINE A written |
| 70 | Cluster: LINE B written |
| 80 | Cluster: LINE C/D written |
| 90 | KML footer written |
| 95 | Generating statistics |
| 100 | complete_progress() → close |

> Sections that are skipped simply advance straight to the next milestone.
> The bar never goes backward.

### Helper method: `update_progress(p_percent, p_message)`

```magik
_private _method rwi_export_to_aerial_kmz.update_progress(p_percent, p_message)
    write("[", p_percent, "%] ", p_message)
    _if .progress_dialog _isnt _unset
    _then
        _try
            .progress_dialog.set_label(:info, p_message)
            .progress_dialog.set_progress(p_percent)
        _when error
            # Dialog errors must never abort the export
        _endtry
    _endif
_endmethod
```

---

## Files to Modify

### Only one file: `rwi_export_to_aerial_kmz.magik`

#### a) `def_slotted_exemplar` — add two slots

```magik
{:progress_dialog, _unset, :writable},  # sw_progress_dialog instance
{:progress_owner,  _unset, :writable},  # Application owner handle
```

#### b) Add `update_progress(p_percent, p_message)` private method

Insert after `init()` method.

#### c) `export_mixed_network` — create/activate/close dialog

```magik
_method rwi_export_to_aerial_kmz.export_mixed_network(p_area, p_output_file)
    write("Starting aerial KMZ export...")
    write("  Project:", .project_name)

    # Create and activate sw_progress_dialog
    _try
        .progress_owner << smallworld_product.pni_application()
        _if .progress_owner _isnt _unset
        _then
            .progress_dialog << sw_progress_dialog.new_dialog(
                .progress_owner,
                :title,                  "KML Export",
                :modality,               :primary_modal,
                :show_progress_percent?, _true
            )
            .progress_dialog.activate()
            .progress_dialog.set_label(:task,
                "Please wait while exporting KML data." + newline_char +
                "This may take some time.")
            .progress_dialog.start_progress()
        _endif
    _when error
        .progress_dialog << _unset
    _endtry

    _self.update_progress(5, "Starting export...")

    # Step: Detect network levels
    _self.update_progress(10, "Detecting network levels in area...")
    levels << _self.detect_network_levels_in_area(p_area)

    # ... filter logic unchanged ...

    # Step: Create KML file (bulk of progress happens inside)
    _self.update_progress(15, "Creating KML file...")
    kml_file << _self.create_kml_file(p_output_file, p_area, levels)

    write("Export complete:", kml_file)

    # Close dialog
    _if .progress_dialog _isnt _unset
    _then
        _try
            .progress_dialog.complete_progress()
            .progress_dialog.close()
        _when error
            # ignore
        _endtry
        .progress_dialog << _unset
    _endif

    >> property_list.new_with(...)   # statistics unchanged
_endmethod
```

#### d) `create_kml_file` — progress at each section boundary

```magik
# After writing template header:
_self.update_progress(20, "KML header and styles written.")

# Feeder+Subfeeder combined case:
_self.update_progress(30, "Exporting FEEDER/SUBFEEDER network...")
_self.write_subfeeder_section(...)
_self.update_progress(40, "FEEDER/SUBFEEDER complete.")

# Feeder only:
_self.update_progress(30, "Exporting FEEDER network...")
_self.write_feeder_section(...)
_self.update_progress(40, "FEEDER complete.")

# Subfeeder:
_self.update_progress(40, "Exporting SUBFEEDER network...")
_self.write_subfeeder_section(...)
_self.update_progress(50, "SUBFEEDER complete.")

# Cluster:
_self.update_progress(50, "Exporting CLUSTER network...")
_self.write_cluster_section(...)
_self.update_progress(85, "CLUSTER complete.")

# Footer:
_self.update_progress(90, "Writing KML footer...")
_self.write_kml_footer(kml_stream)

# Statistics:
_self.update_progress(95, "Generating export statistics...")
_self.print_export_statistics()
```

#### e) `write_cluster_section` — per-LINE-folder progress (60-80%)

In the SECOND PASS loop (both UG and aerial paths):

```magik
# Map LINE A/B/C/D to percentages 60/65/70/75
_local line_pct_map << property_list.new_with(
    "A", 60, "B", 65, "C", 70, "D", 75)

_for folder_def _over filtered_defs.fast_elements()
_loop
    _if folder_def[:line_id] _isnt _unset
    _then
        _local pct << line_pct_map[folder_def[:line_id]].default(60)
        _self.update_progress(pct,
            write_string("Writing LINE ", folder_def[:line_id], " folder..."))
    _endif
    _self.write_folder(p_stream, folder_def, p_area, 4)
_endloop
```

---

## Error Handling

- All dialog creation in `_try/_when error` block
- All `update_progress` calls guarded by `_if .progress_dialog _isnt _unset`
- All dialog API calls in `_try/_when error` inside `update_progress`
- If owner is `_unset` (no PNI application running), dialog is skipped entirely
- Export logic is 100% unchanged if dialog is unavailable

---

## Summary of Changes

| Location | Change |
|----------|--------|
| `def_slotted_exemplar` | +2 slots: `:progress_dialog`, `:progress_owner` |
| After `init()` | New method `update_progress(p_percent, p_message)` |
| `export_mixed_network` | Create/activate dialog at start, close at end |
| `create_kml_file` | `update_progress` calls at each stage boundary |
| `write_cluster_section` | `update_progress` per LINE folder in second pass (both UG + aerial) |

**No other files need to change.**

---

## Percentage Timeline (visual)

```
 5%  Starting export
10%  Detecting network levels
15%  Creating KML file
20%  Header written
30%  Feeder started
40%  Feeder/Subfeeder done
50%  Cluster counting/filtering
60%  LINE A
65%  LINE B
70%  LINE C
75%  LINE D
85%  Cluster done
90%  Footer
95%  Statistics
100% complete_progress() → close
```
