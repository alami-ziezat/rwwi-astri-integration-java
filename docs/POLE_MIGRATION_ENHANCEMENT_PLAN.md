# Pole Migration Enhancement Plan

## Overview
This document describes the enhancement of the `create_pole()` method in `astri_design_migrator.magik` from **simplified mode** to **advanced mode** based on the reference implementation `cluster_pole_migration_astri()` in `cluster_astri (1).magik`.

## Current Implementation (Simplified Mode)

**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:499-537`

**Current Behavior:**
- Parses point geometry from KML coordinate
- Creates pole with hardcoded type "Pole 7-4"
- Sets basic attributes (name, usage, material, construction status)
- No duplicate detection
- No cable snapping
- No dynamic type detection from folder names

## Reference Implementation (Advanced Mode)

**Location:** `C:\Users\user\Downloads\cluster_astri (1).magik:600-842`

**Key Features:**

### 1. Folder Hierarchy Parsing
```magik
fol << a_shp.folders.split_by("|")
fsize << fol.size
fparent << fsize - 1
```
- Folders are separated by "|" delimiter
- Need at least 2 folder levels to process
- Last folder level `fol[fsize]` contains pole type information

### 2. Pole Type Pattern Matching
The reference implementation matches against multiple pole naming patterns:
- **7m poles:** "NEW POLE 7M3", "NEW POLE 7-3", "NEW POLE 7M 3", "NEW POLE 7m3", "NEW POLE 7M4", "NEW POLE 7-4", "NEW POLE 7M 4", "NEW POLE 7-5"
- **9m poles:** "NEW POLE 9m4", "NEW POLE 9M 4", "NEW POLE 9-4", "NEW POLE 9-5"
- **Existing poles:** "EXT", "EXT POLE", "EXISTING POLE PARTNER 7-4", "EXISTING POLE EMR 7-4", "EXISTING POLE EMR 9-4", "EXISTING POLE PARTNER 9-4", "EXISTING POLE EMR 7-5", "EXISTING POLE EMR 9-5"

### 3. Helper Method: match_pole_type()
**Purpose:** Extract pole type from folder string using wildcard pattern matching

**Logic:**
- **Step 1:** Convert folder_string to lowercase
- **Step 2:** Match against wildcard patterns with "*" as any character
- Pattern matching:
  - "*7*3*" matches "7m3", "7-3", "7m 3", "7 3", etc. → "Pole 7-3"
  - "*7*4*" matches "7m4", "7-4", "7m 4", "7 4", etc. → "Pole 7-4"
  - "*7*5*" matches "7-5", "7 5", etc. → "Pole 7-5"
  - "*9*4*" matches "9m4", "9-4", "9m 4", "9 4", etc. → "Pole 9-4"
  - "*9*5*" matches "9-5", "9 5", etc. → "Pole 9-5"
- Returns standardized pole type or "Pole 7-4" as default

**Example Implementation:**
```magik
_private _method astri_design_migrator.match_pole_type(folder_string)
    ## Extract pole type from folder string using wildcard matching
    ## Returns: "Pole 7-3", "Pole 7-4", "Pole 7-5", "Pole 9-4", "Pole 9-5"

    # Convert to lowercase for case-insensitive matching
    _local fs << folder_string.lowercase

    # Match patterns with wildcards (* = any character)
    _if fs.matches?("*7*3*")
    _then
        >> "Pole 7-3"
    _elif fs.matches?("*7*4*")
    _then
        >> "Pole 7-4"
    _elif fs.matches?("*7*5*")
    _then
        >> "Pole 7-5"
    _elif fs.matches?("*9*4*")
    _then
        >> "Pole 9-4"
    _elif fs.matches?("*9*5*")
    _then
        >> "Pole 9-5"
    _else
        >> "Pole 7-4"  # Default
    _endif
_endmethod
```

### 4. Helper Method: match_pole_status()
**Purpose:** Determine if pole is NEW, EXISTING, EMR, or PARTNER using nested logic

**Logic:**
- **Step 1:** Convert folder_string to lowercase
- **Step 2:** Check if "ex*" (EXISTING) using nested conditions:
  - If matches "ex*" → Base status = "Existing"
  - Then check if "*partner*" → Return "Existing Partner"
  - Then check if "*emr*" → Return "Existing EMR"
  - Else → Return "Existing"
- **Step 3:** Check if "new*" using nested conditions:
  - If matches "new*" → Base status = "New"
  - Then check if "*partner*" → Return "New Partner"
  - Then check if "*emr*" → Return "New EMR"
  - Else → Return "New"
- **Step 4:** Default → Return "New"

**Example Implementation:**
```magik
_private _method astri_design_migrator.match_pole_status(folder_string)
    ## Determine pole status using nested wildcard matching
    ## Returns: "New", "Existing", "New Partner", "Existing Partner", "New EMR", "Existing EMR"

    # Convert to lowercase for case-insensitive matching
    _local fs << folder_string.lowercase

    # Check for EXISTING variations
    _if fs.matches?("ex*")
    _then
        # Check sub-types within EXISTING
        _if fs.matches?("*partner*")
        _then
            >> "Existing Partner"
        _elif fs.matches?("*emr*")
        _then
            >> "Existing EMR"
        _else
            >> "Existing"
        _endif

    # Check for NEW variations
    _elif fs.matches?("new*")
    _then
        # Check sub-types within NEW
        _if fs.matches?("*partner*")
        _then
            >> "New Partner"
        _elif fs.matches?("*emr*")
        _then
            >> "New EMR"
        _else
            >> "New"
        _endif

    _else
        # Default to New
        >> "New"
    _endif
_endmethod
```

### 5. Helper Method: match_line()
**Purpose:** Extract line designation (LINE A, LINE B, etc.) from folder string

**Logic:**
- **Step 1:** Convert folder_string to lowercase
- **Step 2:** Search for line patterns with space between "line" and letter
- Patterns: "*line a*", "*line b*", "*line c*", "*line d*", "*line e*", "*line f*"
- Note: Space is required between "line" and the letter
- Returns matching line identifier or _unset

**Example Implementation:**
```magik
_private _method astri_design_migrator.match_line(folder_string)
    ## Extract line designation from folder string
    ## Returns: "LINE A", "LINE B", etc. or _unset

    # Convert to lowercase for case-insensitive matching
    _local fs << folder_string.lowercase

    # Match line patterns (with space between "line" and letter)
    _if fs.matches?("*line a*")
    _then
        >> "LINE A"
    _elif fs.matches?("*line b*")
    _then
        >> "LINE B"
    _elif fs.matches?("*line c*")
    _then
        >> "LINE C"
    _elif fs.matches?("*line d*")
    _then
        >> "LINE D"
    _elif fs.matches?("*line e*")
    _then
        >> "LINE E"
    _elif fs.matches?("*line f*")
    _then
        >> "LINE F"
    _else
        >> _unset
    _endif
_endmethod
```

### 6. Helper Method: match_segment()
**Purpose:** Extract segment designation (SEGMENT 1, SEGMENT 2, etc.) from folder string

**Logic:**
- **Step 1:** Convert folder_string to lowercase
- **Step 2:** Search for segment patterns using wildcards
- Patterns: "*seg*1*", "*segment*1*", "*seg*2*", "*segment*2*"
- Returns matching segment identifier or _unset

**Example Implementation:**
```magik
_private _method astri_design_migrator.match_segment(folder_string)
    ## Extract segment designation from folder string
    ## Returns: "SEGMENT 1", "SEGMENT 2", etc. or _unset

    # Convert to lowercase for case-insensitive matching
    _local fs << folder_string.lowercase

    # Match segment patterns
    _if fs.matches?("*seg*1*") _orif fs.matches?("*segmen*1*")
    _then
        >> "SEGMENT 1"
    _elif fs.matches?("*seg*2*") _orif fs.matches?("*segmen*2*")
    _then
        >> "SEGMENT 2"
    _else
        >> _unset
    _endif
_endmethod
```

### 7. Cable Snapping Logic
**Purpose:** Snap pole location to nearest aerial route if one exists nearby

**Process:**
1. Create 500m buffer around original pole location
2. Search for aerial routes intersecting the buffer
3. If aerial route found:
   - Get nearest point on route to original location using `segpoint_near()`
   - Use this snapped coordinate as pole location
4. If no aerial route found:
   - Use original pole location

**Implementation:**
```magik
_local c << a_shp.location.coord
_local pa << pseudo_point.new(c)
pa.world << .database.world

_local buff << pa.buffer(500)
buff.world << .database.world

_local pred << predicate.interacts(:route, {buff})
_local g << .ar_col.select(pred)

_local n_coord << _unset
_if g.size > 0
_then
    # Aerial route found - snap to it
    _local gg << g.an_element()
    _local q_sec << pseudo_chain.new(gg.route)
    q_sec.world << .database.world
    _local cp << q_sec.segpoint_near(pa)
    n_coord << cp
_else
    # No aerial route - use original location
    n_coord << a_shp.location
_endif
```

### 8. STF Item Code Mapping
**Purpose:** Set standard item codes based on pole type

**Mapping:**
- Pole 7-3 → "200001055"
- Pole 7-4 → "200001183"
- Pole 7-5 → "200000187"
- Pole 9-4 → "200001181"
- Pole 9-5 → "200000169"

### 9. Duplicate Pole Detection
**Purpose:** Check if pole already exists within 200m radius

**Process:**
1. Call `scan_pole_st(n_coord, 200)` to search for existing poles
2. Returns (boolean, pole_record)
3. If pole found AND pole.telco_pole_tag = "Existing Pole AR":
   - **UPDATE** the existing pole instead of creating new
   - Update: telco_pole_tag, pop, olt, type, pole_emr_status, folders, fttx_network_type, segment, stf_item_code
   - Recreate annotation_3 geometry
4. If no pole found OR pole has different tag:
   - **CREATE** new pole

**scan_pole_st Implementation Needed:**
```magik
_private _method astri_design_migrator.scan_pole_st(location, radius)
    ## Search for existing pole within radius
    ## Parameters:
    ##   location - coordinate or pseudo_point
    ##   radius - search radius in meters
    ## Returns:
    ##   (boolean, pole_record) - (_true, pole) if found, (_false, _unset) if not found

    _local coord << _if location.responds_to?(:coord)
                    _then >> location.coord
                    _else >> location
                    _endif

    _local pa << pseudo_point.new(coord)
    pa.world << .database.world

    _local buff << pa.buffer(radius)
    buff.world << .database.world

    _local pred << predicate.interacts(:location, {buff})
    _local poles << .pole_col.select(pred)

    _if poles.size > 0
    _then
        # Found existing pole
        _local nearest_pole << poles.an_element()
        _return _true, nearest_pole
    _else
        # No pole found
        _return _false, _unset
    _endif
_endmethod
```

### 10. Annotation Handling
When updating existing pole:
```magik
pl_s1.unset_geometry(:annotation_3)
anno << pl_s1.make_geometry(:annotation_3, a_shp.location, pl_s1.telco_pole_tag)
```

## Enhanced Implementation Plan

### Phase 1: Add Helper Methods
Add five new helper methods to `astri_design_migrator`:

1. **match_pole_type(folder_string)** - Extract pole type using wildcard patterns
2. **match_pole_status(folder_string)** - Extract pole status with nested logic
3. **match_line(folder_string)** - Extract line designation (LINE A-F)
4. **match_segment(folder_string)** - Extract segment designation (SEGMENT 1-2)
5. **scan_pole_st(location, radius)** - Search for nearby poles

### Phase 2: Enhance create_pole() Method

**New Logic Flow:**

```
0. Validate pole folder pattern
   - Convert folders to lowercase
   - Check if matches "*|pole|*" pattern
   - If not matched → skip (return, not a pole)
   - If matched → proceed with pole migration

1. Parse folder hierarchy
   - Split folders by "|"
   - Validate folder structure (need at least 2 levels)
   - Extract last folder level for pole type matching

2. Parse pole attributes from folder names
   - Call match_pole_type() → pole_type
   - Call match_pole_status() → pole_emr_status
   - Call match_line() → line_type (or _unset)
   - Call match_segment() → segment_type (or _unset)
   - Use line_type if available, else use segment_type for line_type field
   - Map pole_type → stf_item_code

3. Perform cable snapping
   - Create 500m buffer around original location
   - Search for intersecting aerial routes
   - If found: snap to nearest point on route
   - If not: use original location

4. Check for duplicate poles
   - Call scan_pole_st(location, 200)
   - If found AND tag = "Existing Pole AR":
     → Update existing pole
   - Else:
     → Create new pole

5. Create/Update pole record
   - Set all attributes including:
     * telco_pole_tag, type, pole_emr_status
     * stf_item_code, line_type
     * folders, pop, olt, segment, project
     * construction_status, material_type, usage
```

### Phase 3: Update Pole Property List

**New/Modified Fields:**
```magik
property_list.new_with(
    :location, n_coord,                    # Potentially snapped coordinate
    :telco_pole_tag, pm[:name],
    :usage, "Telco",
    :material_type, "Steel",
    :extension_arm, _false,
    :power_riser, _false,
    :telco_riser, _false,
    :bond, _false,
    :ground_status, _false,
    :type, pole_type,                      # From match_pole_type()
    :pole_emr_status, pole_emr_status,     # From match_pole_status()
    :folders, pm[:parent],
    :fttx_network_type, "Cluster",
    :segment, .segment_id,
    :stf_item_code, stf_item_code,         # Mapped from pole type
    :line_type, line_type,                 # From match_line()
    :pop, .pop_name,
    :olt, .pop_name,
    :project, .prj_id,
    :uuid, .uuid,
    :construction_status, "Proposed"
)
```

## File Changes Summary

### Files to Modify:
1. `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik`

### New Methods to Add:
1. `match_pole_type(folder_string)` - ~25 lines (wildcard matching)
2. `match_pole_status(folder_string)` - ~35 lines (nested conditions)
3. `match_line(folder_string)` - ~25 lines (line patterns)
4. `match_segment(folder_string)` - ~20 lines (segment patterns)
5. `scan_pole_st(location, radius)` - ~25 lines (duplicate detection)

### Methods to Modify:
1. `create_pole(pm)` - Expand from ~38 lines to ~160 lines

### Estimated Total Changes:
- Add: ~130 lines (5 new methods)
- Modify: ~122 lines (enhanced create_pole with folder validation)
- **Total: ~252 new/modified lines**

## Testing Checklist

After implementation, test the following scenarios:

### Test 1: Pole Type Detection
- [ ] "NEW POLE 7-3" → creates Pole 7-3 with stf_item_code "200001055"
- [ ] "NEW POLE 7-4" → creates Pole 7-4 with stf_item_code "200001183"
- [ ] "NEW POLE 7-5" → creates Pole 7-5 with stf_item_code "200000187"
- [ ] "NEW POLE 9-4" → creates Pole 9-4 with stf_item_code "200001181"
- [ ] "NEW POLE 9-5" → creates Pole 9-5 with stf_item_code "200000169"

### Test 2: Pole Status Detection
- [ ] "EXISTING POLE EMR 7-4" → pole_emr_status = "EXISTING EMR"
- [ ] "NEW POLE PARTNER 9-4" → pole_emr_status = "NEW PARTNER"
- [ ] "EXT POLE" → pole_emr_status = "EXISTING"

### Test 3: Line Type Detection
- [ ] Folder contains "LINE A" → line_type = "LINE A"
- [ ] Folder contains "SEGMENT 1" → line_type = "SEGMENT 1"

### Test 4: Cable Snapping
- [ ] Pole within 500m of aerial route → snapped to nearest point on route
- [ ] Pole beyond 500m of aerial route → uses original location

### Test 5: Duplicate Detection
- [ ] New pole within 200m of "Existing Pole AR" → updates existing pole
- [ ] New pole within 200m of other poles → creates new pole
- [ ] New pole in empty area → creates new pole

### Test 6: Folder Validation
- [ ] Folder not containing "|pole|" → skipped (not a pole)
- [ ] Folder containing "|Pole|" → processed (case-insensitive)
- [ ] Folder with single level → skipped (continued)
- [ ] Folder with 2+ levels and "|pole|" → processed correctly
- [ ] Empty folder → skipped

### Test 7: Wildcard Pattern Matching
- [ ] "NEW POLE 7M4" → matches "*7*4*" → "Pole 7-4"
- [ ] "new pole 7 4" → matches "*7*4*" → "Pole 7-4"
- [ ] "EXISTING POLE EMR 9-4" → matches "ex*" + "*emr*" + "*9*4*" → status="Existing EMR", type="Pole 9-4"
- [ ] "Line A" → matches "*line a*" → "LINE A"
- [ ] "segment 1" → matches "*seg*1*" → "SEGMENT 1"
- [ ] "LineA" (no space) → does NOT match "*line a*" → returns _unset

## Benefits of Advanced Mode

1. **Accurate Pole Typing** - Automatically detects pole type from KML folder names
2. **Cable Snapping** - Ensures poles align with aerial routes for proper connectivity
3. **Duplicate Prevention** - Avoids creating duplicate poles in same location
4. **Update Existing** - Can update placeholder poles from aerial route migration
5. **Complete Attributes** - Sets STF codes, line types, EMR status for billing/inventory
6. **Pattern Flexibility** - Handles various naming conventions from ASTRI API

## Implementation Notes

1. **Dynamic Globals**: Already set in `init()` method:
   ```magik
   _dynamic !current_world! << .database.world
   _dynamic !current_coordinate_system! << .database.world.coordinate_system
   ```

2. **Error Handling**: Wrap all logic in `_try/_when error` blocks as currently done

3. **Progress Logging**: Keep existing write() statements for tracking progress

4. **Statistics**: Increment `.stats[:poles]` counter as currently done

5. **Performance**: Cable snapping and duplicate detection add queries but are necessary for data quality

## References

- **Source Implementation:** `C:\Users\user\Downloads\cluster_astri (1).magik:600-842`
- **Current Implementation:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:499-537`
- **Design Plan:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\DESIGN_MIGRATION_PLAN.md`

## Next Steps

1. Review and approve this plan
2. Implement Phase 1: Add helper methods
3. Implement Phase 2: Enhance create_pole()
4. Test with sample KML data containing various pole types
5. Verify pole snapping and duplicate detection
6. Deploy to production

---
**Document Version:** 1.1
**Created:** 2025-11-02
**Last Updated:** 2025-11-02
**Status:** Ready for Implementation

## Revision History

**v1.1 (2025-11-02):**
- Added initial folder validation check: "*|pole|*" pattern
- Changed to wildcard pattern matching ("*7*4*" instead of exact matches)
- Implemented nested logic for pole status detection
- Separated match_line() and match_segment() into distinct methods
- Added lowercase conversion for all folder string matching
- Updated from 4 to 5 helper methods
- Updated testing checklist with wildcard pattern tests
- Fixed match_line() to use "*line a*" (with space) instead of "*line*a*"

**v1.0 (2025-11-02):**
- Initial document creation
- Basic implementation plan with exact string matching
