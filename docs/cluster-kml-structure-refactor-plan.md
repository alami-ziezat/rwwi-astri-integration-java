# Cluster KML Structure Refactor - Implementation Plan

**Date:** 2025-02-07
**Author:** Claude Code
**Status:** ✅ IMPLEMENTED

## Overview
Refactor the cluster KML export structure to change how FDT and LINE folders are organized. Instead of creating separate FDT folders with nested LINEs, create a single FDT folder at the top level with all FDT objects, and make LINE folders siblings of the FDT folder.

## Current Structure

### When FDT = 1:
```
CLUSTER CODE
├── BOUNDARY CLUSTER
└── FDT
    ├── LINE A
    │   ├── BOUNDARY FAT
    │   ├── FAT
    │   ├── HP COVER
    │   ├── HP UNCOVER
    │   ├── EXISTING POLE EMR 7-2.5
    │   └── ... (other pole types, cables, etc.)
    └── LINE B
        ├── BOUNDARY FAT
        └── ... (same subfolders as LINE A)
```

### When FDT > 1:
```
CLUSTER CODE
├── BOUNDARY CLUSTER
├── FDT_FDT001
│   ├── LINE A
│   │   ├── BOUNDARY FAT
│   │   └── ... (subfolders)
│   └── LINE B
│       └── ... (subfolders)
└── FDT_FDT002
    ├── LINE A
    │   └── ... (subfolders)
    └── LINE B
        └── ... (subfolders)
```

## New Structure (Target)

### When FDT = 1:
```
CLUSTER CODE
├── BOUNDARY CLUSTER
├── FDT
│   └── FDT object (single FDT placemark)
├── LINE A - FDT
│   ├── BOUNDARY FAT
│   ├── FAT
│   ├── HP COVER
│   ├── HP UNCOVER
│   ├── EXISTING POLE EMR 7-2.5
│   └── ... (other pole types, cables, etc.)
└── LINE B - FDT
    ├── BOUNDARY FAT
    └── ... (same subfolders as LINE A)
```

### When FDT > 1:
```
CLUSTER CODE
├── BOUNDARY CLUSTER
├── FDT
│   ├── FDT 1 object (placemark)
│   ├── FDT 2 object (placemark)
│   └── ... (all FDT placemarks)
├── LINE A - FDT 1
│   ├── BOUNDARY FAT
│   └── ... (subfolders)
├── LINE B - FDT 1
│   └── ... (subfolders)
├── LINE A - FDT 2
│   └── ... (subfolders)
└── LINE B - FDT 2
    └── ... (subfolders)
```

## Key Changes

### 1. Folder Builder Changes (`rwi_aerial_kmz_folder_builder.magik`)

**Method:** `build_cluster_folders(p_fdts)`

#### For FDT = 1:
- Create ONE top-level "FDT" folder
  - This folder will contain the FDT object (no subfolders)
- Create "LINE A - FDT" folder (sibling to FDT folder)
  - Contains all LINE A subfolders
- Create "LINE B - FDT" folder (sibling to FDT folder)
  - Contains all LINE B subfolders

#### For FDT > 1:
- Create ONE top-level "FDT" folder
  - This folder will contain ALL FDT objects as placemarks (no subfolders)
- For each FDT:
  - Create "LINE A - FDT [NAME]" folder (sibling to FDT folder)
    - Contains all LINE A subfolders
  - Create "LINE B - FDT [NAME]" folder (sibling to FDT folder)
    - Contains all LINE B subfolders

### 2. Export Changes (`rwi_export_to_aerial_kmz.magik`)

**Method:** `write_cluster_section(p_stream, p_area, p_fdts)`
- No major changes needed - already iterates through folder_defs

**Method:** `write_folder_contents(p_stream, p_folder_def, p_area, p_indent, p_parent_line_id, p_parent_fdt_folder)`
- FDT folder handling:
  - When folder name = "FDT", write ALL FDT objects (not filtered)
  - Pass _unset for p_fdt_folder (since FDT folder has all FDTs)

- LINE folder handling:
  - LINE folders now have FDT reference in their name (e.g., "LINE A - FDT 1")
  - Extract FDT name from folder name for filtering
  - Pass FDT name to subfolder methods for filtering

### 3. FDT Filtering Updates

**Current behavior:**
- p_fdt_folder is passed down from parent FDT_XXX folder
- Used to filter FAT, cables, poles, etc. to match specific FDT

**New behavior:**
- p_fdt_folder is extracted from LINE folder name
- "LINE A - FDT 1" → extract "FDT 1" as the FDT reference
- Use this for filtering in all subfolder content methods

## Implementation Steps

### Step 1: Update `build_cluster_folders` method
1. When fdt_count = 1:
   - Create FDT folder (no subfolders, just marker)
   - Create LINE A - FDT folder with all subfolders
   - Create LINE B - FDT folder with all subfolders

2. When fdt_count > 1:
   - Create FDT folder (no subfolders, just marker)
   - For each FDT:
     - Create LINE A - FDT [NAME] folder with all subfolders
     - Create LINE B - FDT [NAME] folder with all subfolders

### Step 2: Update `write_folder_contents` method
1. Handle "FDT" folder:
   - Check if folder_name = "FDT"
   - If fdt_count = 1: Write single FDT object
   - If fdt_count > 1: Write all FDT objects

2. Handle LINE folders:
   - Extract FDT reference from folder name
   - Parse "LINE A - FDT 1" to get FDT name "FDT 1"
   - Pass FDT name to filtering methods

### Step 3: Test Cases
1. Single FDT cluster:
   - Verify FDT folder has 1 FDT object
   - Verify LINE A - FDT and LINE B - FDT are siblings
   - Verify objects are filtered correctly by LINE and FDT

2. Multiple FDT cluster:
   - Verify FDT folder has all FDT objects
   - Verify each FDT has 2 LINE folders as siblings
   - Verify objects are distributed correctly by FDT and LINE

## Files to Modify

1. `pni_custom/rwwi_astri_integration_java/magik/rwi_export_to_kml/source/rwi_aerial_kmz_folder_builder.magik`
   - Method: `build_cluster_folders(p_fdts)` - Complete rewrite of structure

2. `pni_custom/rwwi_astri_integration_java/magik/rwi_export_to_kml/source/rwi_export_to_aerial_kmz.magik`
   - Method: `write_folder_contents(...)` - Add FDT folder handling and LINE name parsing
   - Method: `write_cluster_section(...)` - May need adjustment for new structure

## Compatibility Notes

- This change affects ONLY cluster exports
- Feeder and subfeeder exports remain unchanged
- Existing KML template is compatible (no template changes needed)
- All filtering logic (LINE, FDT, ring_name) remains the same

## Expected Benefits

1. **Clearer Structure:** Single FDT folder shows all FDTs at a glance
2. **Consistent Hierarchy:** LINEs are always at the same level
3. **Better Organization:** Easier to navigate in Google Earth
4. **Scalability:** Works well for both single and multiple FDT scenarios

## Rollback Plan

If issues arise, revert changes to:
- `rwi_aerial_kmz_folder_builder.magik` (build_cluster_folders method)
- `rwi_export_to_aerial_kmz.magik` (write_folder_contents method)

## Success Criteria

- [ ] Single FDT cluster exports with correct structure
- [ ] Multiple FDT cluster exports with correct structure
- [ ] All objects filtered correctly by LINE and FDT
- [ ] Statistics report shows correct counts
- [ ] KML opens correctly in Google Earth
- [ ] No regression in feeder/subfeeder exports

---

## Implementation Summary

**Implementation Date:** 2025-02-07
**Actual Time:** < 1 hour
**Risk Level:** Medium (affects core export logic)

### Changes Made

#### 1. `rwi_aerial_kmz_folder_builder.magik` - `build_cluster_folders(p_fdts)`

**Single FDT (fdt_count = 1):**
- Create FDT folder with `:is_fdt_container` flag and `:fdt_objects` containing all FDTs
- Create LINE A folder (no FDT suffix) with `:fdt_name` property for filtering
- Create LINE B folder (no FDT suffix) with `:fdt_name` property for filtering
- LINE folders are siblings of FDT folder (not children)

**Multiple FDTs (fdt_count > 1):**
- Create single FDT folder with `:is_fdt_container` flag and `:fdt_objects` containing all FDTs
- For each FDT:
  - Create LINE A - [FDT_NAME] folder with `:fdt_name` property
  - Create LINE B - [FDT_NAME] folder with `:fdt_name` property
- All LINE folders are siblings of FDT folder

#### 2. `rwi_export_to_aerial_kmz.magik` - `write_folder_contents(...)`

**New FDT Container Handling:**
- Check for `:is_fdt_container` flag at method start
- If true, call `write_fdt_equipment()` with _unset filter to write all FDTs
- Return early (no other content in FDT container)

**FDT Filtering:**
- Determine `current_fdt_folder` from:
  1. `:fdt_name` property (new structure - LINE folders)
  2. `p_parent_fdt_folder` parameter (legacy/nested structure)
- Use `current_fdt_folder` for all filtering calls instead of `p_parent_fdt_folder`

**Updated FDT Folder Check:**
- Added check to prevent duplicate FDT writing: only write FDTs for nested FDT folders (not container)

#### 3. `rwi_export_to_aerial_kmz.magik` - `write_folder(...)`

**FDT Name Propagation:**
- Priority order for determining `current_fdt_folder`:
  1. `:fdt_name` from folder definition (new structure)
  2. `p_parent_fdt_folder` parameter (inherited)
  3. Legacy FDT_* pattern matching (old structure support)
- Pass `current_fdt_folder` to children and content writer

### Key Features

1. **Single Top-Level FDT Folder:** Regardless of FDT count, only one FDT folder at top level
2. **Sibling LINE Folders:** LINE folders are siblings of FDT folder, not nested inside
3. **Smart Naming:**
   - FDT = 1: "LINE A", "LINE B" (no suffix)
   - FDT > 1: "LINE A - FDT001", "LINE B - FDT001", etc.
4. **Backward Compatible:** Legacy FDT_* pattern still supported for nested structures
5. **Filter Propagation:** FDT name correctly propagates through folder hierarchy

### Testing Checklist

- [ ] Single FDT cluster exports with correct structure
- [ ] Multiple FDT cluster exports with correct structure
- [ ] All objects filtered correctly by LINE and FDT
- [ ] Statistics report shows correct counts
- [ ] KML opens correctly in Google Earth
- [ ] No regression in feeder/subfeeder exports

### Files Modified

1. `pni_custom/rwwi_astri_integration_java/magik/rwi_export_to_kml/source/rwi_aerial_kmz_folder_builder.magik`
   - Method: `build_cluster_folders(p_fdts)` - Complete rewrite

2. `pni_custom/rwwi_astri_integration_java/magik/rwi_export_to_kml/source/rwi_export_to_aerial_kmz.magik`
   - Method: `write_folder_contents(...)` - Added FDT container handling and FDT name extraction
   - Method: `write_folder(...)` - Updated FDT name propagation logic

---

## Bug Fixes: Pole Filtering Issues (2025-02-10)

### Issue 1: Duplicate Pole Export Across LINEs
After the restructuring, poles were being exported twice - appearing in both LINE A and LINE B folders. Poles that belonged to LINE A were incorrectly showing up in LINE B folders and vice versa.

**Root Cause:**
The pole filtering logic in `write_folder_contents()` was only checking if the pole's template folder name matched the current folder name, but it was **not filtering by the pole's `line_type` attribute**. This caused all poles matching the folder criteria (e.g., "EXISTING POLE EMR 7-2.5") to be exported to every LINE folder that had that pole type, regardless of which LINE the pole actually belonged to.

### Issue 2: Duplicate Pole Export Across FDTs
When there were multiple FDTs, poles (and initially other objects) from all FDTs were appearing in each FDT's folders.

**Example:**
- FDT 1, LINE A has 2 poles
- FDT 2, LINE A has 1 pole
- **Bug:** Both FDT folders showed 3 poles (total from all FDTs)
- **Expected:** FDT 1 shows 2 poles, FDT 2 shows 1 pole

**Root Cause:**
1. **Poles:** Had no FDT filtering at all - didn't check `ring_name` attribute
2. **ALL Other Objects:** Had FDT filtering code BUT it only worked with old folder structure format "FDT_FDT001"

The critical bug: All existing FDT filtering code checked `_if p_fdt_folder.index_of_seq("FDT_") _isnt _unset` before doing any filtering. In the new structure where `current_fdt_folder` = "FDT001" (without "FDT_" prefix), this condition was always FALSE, so **NO FDT filtering happened for ANY objects**!

This is why ALL objects from ALL FDTs appeared in ALL folders - the FDT filtering was completely bypassed!

### Fixes Applied

**File:** `rwi_export_to_aerial_kmz.magik`
**Method:** `write_folder_contents()` (around line 934-990)

**Changes:** Added both LINE and FDT filtering checks for poles, matching the pattern used by all other equipment types:

```magik
# Check if constructed folder name matches current template folder
_if pole_template_folder = folder_name
_then
    # Filter by LINE if specified (check pole's line_type)
    _if line_id _isnt _unset
    _then
        # Convert line_id to line_type format (e.g., "A" -> "LINE A")
        expected_line_type << "LINE " + line_id
        pole_line_type << pole.perform(:line_type).default("")

        # Skip pole if line_type doesn't match
        _if pole_line_type.uppercase <> expected_line_type.uppercase
        _then
            _continue
        _endif
    _endif

    # Filter by FDT if specified (check pole's ring_name)
    _if current_fdt_folder _isnt _unset
    _then
        # Get pole's ring_name
        pole_ring_name << pole.perform(:ring_name).default("")

        _if pole_ring_name <> ""
        _then
            # Extract FDT name from current_fdt_folder
            # Format can be "FDT_FDT001" (old) or "FDT001" (new)
            expected_fdt_name << current_fdt_folder
            _if current_fdt_folder.index_of_seq("FDT_") _isnt _unset
            _then
                # Old format: "FDT_FDT001" -> extract "FDT001"
                expected_fdt_name << current_fdt_folder.slice(5, current_fdt_folder.size)
            _endif

            # Skip pole if ring_name doesn't match FDT name
            _if pole_ring_name.uppercase <> expected_fdt_name.uppercase
            _then
                _continue
            _endif
        _endif
    _endif

    .object_writer.write_pole(pole, p_stream, p_indent, folder_name)
    pole_count +<< 1
    .total_pole_count +<< 1
_endif
```

**How it works:**
1. After matching the pole to the folder template name, check LINE filtering:
   - Get the pole's `line_type` attribute
   - Compare it to the expected LINE for this folder
   - Skip if it doesn't match
2. Then check FDT filtering:
   - Get the pole's `ring_name` attribute
   - Extract the FDT name from `current_fdt_folder`
   - Compare pole's ring_name with the FDT name
   - Skip if it doesn't match
3. Only write the pole if it passes both filters

This ensures poles are only exported to their assigned LINE and FDT folders, preventing duplicates across both dimensions.

### Comprehensive FDT Filtering Fix

**All object types updated to handle both folder format:**
1. **Poles** - Added complete FDT filtering (was completely missing)
2. **FAT** - Fixed to extract FDT name from both "FDT_FDT001" and "FDT001" formats
3. **Figure Eight (Slack Hanger)** - Fixed format handling
4. **Sling Wire** - Fixed format handling
5. **Cables** - Fixed format handling
6. **Demand Points (HP COVER/UNCOVER)** - Fixed format handling (2 occurrences)
7. **Cells (BOUNDARY FAT)** - Fixed format handling

**Pattern Applied to All:**
```magik
# Extract FDT name from folder
expected_fdt_name << p_fdt_folder
_if p_fdt_folder.index_of_seq("FDT_") _isnt _unset
_then
    # Old format: "FDT_FDT001" -> extract "FDT001"
    expected_fdt_name << p_fdt_folder.slice(5, p_fdt_folder.size)
_endif

# Then compare with object's ring_name
_if ring_name <> "" _andif ring_name.uppercase <> expected_fdt_name.uppercase
_then
    _continue  # Skip if doesn't match
_endif
```

This ensures FDT filtering works in both old and new folder structures.

### Testing Verification

**LINE Filtering Tests:**
- [ ] Verify poles appear only once in the correct LINE folder
- [ ] Verify LINE A poles don't appear in LINE B folders
- [ ] Verify LINE B poles don't appear in LINE A folders
- [ ] Verify pole counts are accurate per LINE

**FDT Filtering Tests (Multiple FDTs):**
- [ ] Verify poles from FDT 1 appear only in FDT 1 folders
- [ ] Verify poles from FDT 2 appear only in FDT 2 folders
- [ ] Verify pole counts are accurate per FDT (e.g., FDT1 has 2 poles, FDT2 has 1 pole)
- [ ] Verify no duplication across FDT folders

**Combined Tests:**
- [ ] Verify total pole count across all FDTs and LINEs matches actual poles in area
- [ ] Verify each pole appears exactly once in the entire KML export
- [ ] Test with single FDT scenario (FDT filtering still works)
- [ ] Test with multiple FDT scenario (2+ FDTs with different pole counts)
