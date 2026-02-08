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
