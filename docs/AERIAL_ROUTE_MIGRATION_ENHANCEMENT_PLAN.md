# Aerial Route Migration Enhancement Plan

## Overview
This document describes the enhancement of the `create_aerial_route()` method in `astri_design_migrator.magik` from **simplified mode** to **advanced mode** based on the reference implementation `cluster_aerial_route_migration_astri()` in `cluster_astri (1).magik`.

The enhanced implementation includes:
- **Fiber core detection** from cable names
- **Pole detection** at start/end points (500m radius)
- **Placeholder pole creation** when poles not found
- **Additional aerial route segments** to connect to existing poles
- **Sheath creation** with continuous structures
- **Sling wire handling** as separate object type

## Current Implementation (Simplified Mode)

**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:855-882`

**Current Behavior:**
- Parses line geometry from KML coordinates
- Validates minimum length (0.4m)
- Creates aerial route with hardcoded fiber count (24)
- Sets basic attributes (name, pop, segment)
- No pole detection
- No sheath creation
- No cable core detection from name

## Reference Implementation (Advanced Mode)

**Location:** `C:\Users\user\Downloads\cluster_astri (1).magik:2-596`

**Key Features:**

### 1. Folder Pattern Validation (Lines 77-82)

**Reference Logic:**
```magik
_if fol[fsize] = "KABEL" _orif
    fol[fsize] = "CABLE" _orif
    fol[fsize] = "CABEL" _orif
    fol[fsize] = "DISTRIBUTION CABLE" _orif
    fol[fsize] = "SLING WIRE"
_then
```

**Enhanced Logic (Wildcard Matching):**
- **Step 1:** Convert folder string to lowercase
- **Step 2:** Match against wildcard patterns with "*" as any character
- Patterns: `"*cable*"`, `"*kabel*"`, `"*cabel*"`, `"*distribution cable*"`, `"*sling wire*"`
- More flexible than exact matching
- Handles variations and extra text

### 2. Cable Core Detection (Lines 95-182)

**Purpose:** Extract fiber count from cable name using wildcard pattern matching

**Enhanced Logic:**
- **Step 1:** Convert cable name to lowercase
- **Step 2:** Match against wildcard patterns with prefix/suffix
- Pattern format: `"*fo*<number>*"` or `"*<number>c*"` or `"*<number>core*"`

**Patterns:**
- `"*12*"` or `"*12c*"` or `"*12core*"` → 12 cores
- `"*24*"` or `"*24c*"` or `"*24core*"` → 24 cores
- `"*36*"` or `"*36c*"` or `"*36core*"` → 36 cores
- `"*48*"` or `"*48c*"` or `"*48core*"` → 48 cores
- `"*72*"` or `"*72c*"` or `"*72core*"` → 72 cores
- `"*96*"` or `"*96c*"` or `"*96core*"` → 96 cores
- `"*144*"` or `"*144c*"` or `"*144core*"` → 144 cores
- `"*288*"` or `"*288c*"` or `"*288core*"` → 288 cores
- `"*576*"` → 576 cores (rare)

**Example Matches:**
- "FO 24C" → `"*24*"` → 24 cores
- "Cable-144-Core" → `"*144*"` → 144 cores
- "ADSS48" → `"*48*"` → 48 cores

**Note:** Check larger numbers first to avoid false matches (e.g., 144 before 14)

**Mapping to Spec and STF Code:**
```magik
_if m_cbl = "12" _then
    s_sp << "SM G652D-ADSS 12C"
    s_cd << ""
    fb_c << "12"
_elif m_cbl = "24" _then
    s_sp << "SM G652D-ADSS 24C"
    s_cd << "200000100"
    fb_c << "24"
_elif m_cbl = "36" _then
    s_sp << "SM G652D-ADSS 36C"
    s_cd << "200000975"
    fb_c << "36"
_elif m_cbl = "48" _then
    s_sp << "SM G652D-ADSS 48C"
    s_cd << "200001038"
    fb_c << "48"
# ... etc
```

### 3. Sling Wire Special Handling (Lines 206-232)

**Purpose:** Create sling_wire object instead of aerial route when folder is "SLING WIRE"

```magik
_if fol[fsize] = "SLING WIRE"
_then
    _if t_len > 0.6 _then
        l_prop_values << property_list.new_with(
            :status, "Proposed",
            :name, a_shp.name,
            :pop, .pop_name,
            :olt, .pop_name,
            :segment, .segment_id,
            :fttx_network_type, "Cluster",
            :line_type, m_line,
            :route, a_shp.route
        )
        l_rec_trans << record_transaction.new_insert(sw_col, l_prop_values)
        l_result_sl << l_rec_trans.run()
        _continue  # Skip rest of aerial route processing
    _endif
_endif
```

### 4. Main Aerial Route Creation (Lines 249-267)

**Purpose:** Create the primary aerial route from KML geometry

```magik
l_prop_values << property_list.new_with(
    :construction_status, "Proposed",
    :name, a_shp.name,
    :asset_ownership, "Owned",
    :pop, .pop_name,
    :olt, .pop_name,
    :fiber_count, fb_c,           # Detected from name
    :fttx_network_type, "Cluster",
    :segment, .segment_id,
    :line_type, m_line,           # Detected from folders/name
    :folders, a_shp.folders,
    :route, a_shp.route
)

l_rec_trans << record_transaction.new_insert(ar_col, l_prop_values)
l_result << l_rec_trans.run()
```

### 5. Start Point Handling (Lines 270-381)

**Purpose:** Handle first coordinate - find existing pole or create placeholder

**Process:**

#### Step A: Extract first coordinate
```magik
l_location_start << a_shp.route.first_coord
pp << pseudo_point.new(l_location_start)
pp.world << v_gis.world
```

#### Step B: Scan for existing pole (500m radius)
```magik
(k1, a_hub) << _self.scan_pole_st(l_location_start, 500)
```

#### Step C: If pole found
```magik
_if k1 _is _true
_then
    # Get pole coordinate
    n_coord << a_hub.location.coord

    # Calculate distance from pole to first coordinate
    ff << a_shp.route.as_sector_rope()
    bb << sector.new_with(n_coord, ff.first_coord)
    bbb << pseudo_chain.new(bb)
    bbb.world << v_gis.world
    t_len << bbb.line_length

    # If distance > 0.6m, create additional aerial route segment
    _if t_len > 0.6 _then
        l_prop_values_ar << property_list.new_with(
            :construction_status, "Proposed",
            :name, a_shp.name,
            :asset_ownership, "Owned",
            :pop, .pop_name,
            :olt, .pop_name,
            :fiber_count, fb_c,
            :fttx_network_type, "Cluster",
            :segment, .segment_id,
            :line_type, m_line,
            :folders, a_shp.folders,
            :route, bbb                    # Segment from pole to first coord
        )
        l_rec_trans_ar << record_transaction.new_insert(ar_col, l_prop_values_ar)
        l_result_ar << l_rec_trans_ar.run()

        fs << ff  # Store modified sector_rope
    _endif

    l_result_pl << a_hub  # Store pole reference
_endif
```

#### Step D: If no pole found
```magik
_else
    # Create placeholder pole at first coordinate
    fs << a_shp.route.as_sector_rope()
    n_coord << a_shp.route.first_coord

    l_prop_values_pl << property_list.new_with(
        :location, n_coord,
        :telco_pole_tag, "Existing Pole AR",      # Placeholder name
        :usage, "Telco",
        :material_type, "Steel",
        :extension_arm, _false,
        :power_riser, _false,
        :telco_riser, _false,
        :bond, _false,
        :ground_status, _false,
        :type, "T7",
        :folders, a_shp.folders,
        :pop, .pop_name,
        :olt, .pop_name,
        :project, .prj_id,
        :line_type, m_line,
        :fttx_network_type, "Cluster",
        :segment, .segment_id,
        :construction_status, "Proposed"
    )
    l_rec_trans_pl << record_transaction.new_insert(pole_col, l_prop_values_pl)
    l_result_pl << l_rec_trans_pl.run()
_endif
```

### 6. End Point Handling (Lines 383-509)

**Purpose:** Handle last coordinate - find existing pole or create placeholder

**Process:**

#### Step A: Extract last coordinate
```magik
l_location_end << a_shp.route.last_coord
ppr << pseudo_point.new(l_location_end)
ppr.world << v_gis.world
```

#### Step B: Scan for existing pole (500m radius)
```magik
(k2, b_hub) << _self.scan_pole_st(ppr, 500)
```

#### Step C: If pole found
```magik
_if k2 _is _true
_then
    # Get the sector_rope from previous processing (fs)
    rr << fs
    bn << rr.sectors[1]

    pss << pseudo_chain.new(bn)
    pss.world << v_gis.world

    # Get pole coordinate
    e_coord << b_hub.location.coord

    # Create segment from last coordinate to pole
    sec << sector.new_with(pss.last_coord, e_coord)
    ps << pseudo_chain.new(sec)
    ps.world << v_gis.world

    t_len << ps.line_length

    # If distance > 0.6m, create additional aerial route segment
    _if t_len > 0.6 _then
        l_prop_values_ar << property_list.new_with(
            :construction_status, "Proposed",
            :name, a_shp.name,
            :asset_ownership, "Owned",
            :fttx_network_type, "Cluster",
            :segment, .segment_id,
            :pop, .pop_name,
            :olt, .pop_name,
            :line_type, m_line,
            :fiber_count, fb_c,
            :route, ps                     # Segment from last coord to pole
        )
        l_rec_trans_ar << record_transaction.new_insert(ar_col, l_prop_values_ar)
        l_result_ar2 << l_rec_trans_ar.run()
    _endif

    l_result_pl2 << b_hub  # Store pole reference
_endif
```

#### Step D: If no pole found
```magik
_else
    # Create placeholder pole at last coordinate
    e_coord << a_shp.route.last_coord

    l_prop_values_pl << property_list.new_with(
        :location, e_coord,
        :telco_pole_tag, "Existing Pole AR",      # Placeholder name
        :usage, "Telco",
        :material_type, "Steel",
        :fttx_network_type, "Cluster",
        :extension_arm, _false,
        :power_riser, _false,
        :telco_riser, _false,
        :bond, _false,
        :ground_status, _false,
        :pop, .pop_name,
        :olt, .pop_name,
        :project, .prj_id,
        :line_type, m_line,
        :segment, .segment_id,
        :type, "T7",
        :folders, a_shp.folders,
        :construction_status, "Proposed"
    )
    l_rec_trans_pl << record_transaction.new_insert(pole_col, l_prop_values_pl)
    l_result_pl2 << l_rec_trans_pl.run()
_endif
```

### 7. Sheath Creation with Structures (Lines 514-588)

**Purpose:** Create sheath_with_loc that connects all structures (poles and aerial routes)

**Process:**

#### Step A: Collect all structures
```magik
l_structures << rope.new()
l_structures.add(l_result_pl)        # Start pole

_if l_result_ar _isnt _unset
_then
    l_structures.add(l_result_ar)    # Start aerial route segment
_endif

l_structures.add(l_result)           # Main aerial route

_if l_result_ar2 _isnt _unset
_then
    l_structures.add(l_result_ar2)   # End aerial route segment
_endif

l_structures.add(l_result_pl2)       # End pole
```

#### Step B: Prepare cluster name
```magik
_if ftri.size > 120
_then
    rtr << ftri.slice(1, 120)   # Truncate to 120 chars
_else
    rtr << ftri
_endif
```

#### Step C: Create sheath
```magik
l_prop_values_swl << property_list.new_with(
    :name, a_shp.name,
    :spec_id, s_sp,                    # e.g., "SM G652D-ADSS 24C"
    :construction_status, "Proposed",
    :sheath_network_type, "Cluster",
    :folders, a_shp.folders,
    :pop, .pop_name,
    :olt, .pop_name,
    :project, .prj_id,
    :line_type, m_line,
    :segment, .segment_id,
    :cluster, rtr,                     # Truncated folder name
    :asset_owner, "Owned"
)

l_rec_trans_swl << record_transaction.new_insert(sheath_col, l_prop_values_swl)
l_result_swl << l_rec_trans_swl.run()
```

#### Step D: Place sheath in structures
```magik
l_result_swl.place_in_structures(l_structures)
```

**Note:** This creates the topology connection between sheath and all poles/aerial routes

### 8. Structure Manager Usage (Lines 22, 516)

**Purpose:** Get continuous structures via coordinates for sheath placement

```magik
# Initialize structure manager
stm << structure_manager.new(v_gis, !current_world!)

# Later: Get continuous structures
l_sector << sector_rope.new_with(sector.new_with(n_coord, e_coord))
shc << pseudo_chain.new(l_sector)
path_swl << stm.get_continuous_structures_via_coords(shc.sectors[1])
```

## Enhanced Implementation Plan

### Phase 1: Add Helper Methods

Add new helper methods to `astri_design_migrator`:

1. **match_cable_core(cable_name)** - Extract fiber count from cable name using wildcard patterns
2. **is_cable?(folders)** - Check if this is a cable (renamed from existing method for consistency)
3. **is_sling_wire?(folders)** - Check if this is a sling wire
4. Already have: **scan_pole_st(location, radius)** - Used for pole detection
5. Already have: **match_line(folders)** - Used for line type detection

**Implementation Details:**

#### match_cable_core(cable_name)
```magik
_private _method astri_design_migrator.match_cable_core(cable_name)
    ## Extract fiber count from cable name using wildcard matching
    ## Returns: "12", "24", "36", "48", "72", "96", "144", "288", "576", or "24" (default)

    # Convert to lowercase for case-insensitive matching
    _local cn << cable_name.lowercase

    # Check larger numbers first to avoid false matches
    # (e.g., check 144 before 14, check 288 before 28)
    _if cn.matches?("*576*")
    _then
        >> "576"
    _elif cn.matches?("*288*")
    _then
        >> "288"
    _elif cn.matches?("*144*")
    _then
        >> "144"
    _elif cn.matches?("*96*")
    _then
        >> "96"
    _elif cn.matches?("*72*")
    _then
        >> "72"
    _elif cn.matches?("*48*")
    _then
        >> "48"
    _elif cn.matches?("*36*")
    _then
        >> "36"
    _elif cn.matches?("*24*")
    _then
        >> "24"
    _elif cn.matches?("*12*")
    _then
        >> "12"
    _else
        >> "24"  # Default to 24 cores
    _endif
_endmethod
```

#### is_cable?(folders)
```magik
_private _method astri_design_migrator.is_cable?(pm)
    ## Check if placemark is a cable based on parent folder
    ## Folder must contain cable patterns (case-insensitive)

    _local parent << pm[:parent].default("").lowercase

    >> parent.matches?("*cable*") _orif
       parent.matches?("*kabel*") _orif
       parent.matches?("*cabel*") _orif
       parent.matches?("*distribution cable*")
_endmethod
```

#### is_sling_wire?(folders)
```magik
_private _method astri_design_migrator.is_sling_wire?(folders)
    ## Check if this is a sling wire
    ## Returns: boolean

    _local fs << folders.lowercase

    >> fs.matches?("*sling wire*") _orif
       fs.matches?("*sling_wire*")
_endmethod
```

### Phase 2: Enhance create_aerial_route() Method

**New Logic Flow:**

```
0. Validate cable folder pattern
   - Convert folders to lowercase
   - Check if folders match cable patterns using wildcard matching
   - Patterns: "*cable*", "*kabel*", "*cabel*", "*distribution cable*"
   - If not matched → skip (return, not a cable)
   - Note: This replaces the existing is_cable?() check

1. Parse folder hierarchy
   - Split folders by "|"
   - Validate folder structure
   - Extract folder levels

2. Check for Sling Wire
   - Convert folders to lowercase
   - If folders match "*sling wire*":
     → Create sling_wire object
     → Return (skip aerial route processing)

3. Detect cable fiber count
   - Convert cable name to lowercase
   - Call match_cable_core(cable_name) → fiber_count
   - Uses wildcard patterns: "*576*", "*288*", "*144*", etc.
   - Check larger numbers first to avoid false matches
   - Map fiber_count → spec_id, stf_code
   - Default: 24 cores

4. Detect line type
   - Call match_line(folders)
   - If _unset, call match_line(cable_name)

5. Validate cable length
   - Check route.line_length >= 0.4m
   - Skip if too short

6. Create main aerial route
   - Use detected fiber_count
   - Use detected line_type
   - Store result as l_result

7. Handle start point
   - Extract first_coord
   - Scan for pole within 500m: scan_pole_st(first_coord, 500)
   - If pole found AND distance > 0.6m:
     → Create additional aerial route segment (pole → first_coord)
     → Store as l_result_ar
   - If no pole found:
     → Create placeholder pole "Existing Pole AR"
     → Store as l_result_pl

8. Handle end point
   - Extract last_coord
   - Scan for pole within 500m: scan_pole_st(last_coord, 500)
   - If pole found AND distance > 0.6m:
     → Create additional aerial route segment (last_coord → pole)
     → Store as l_result_ar2
   - If no pole found:
     → Create placeholder pole "Existing Pole AR"
     → Store as l_result_pl2

9. Collect all structures
   - Create rope: [start_pole, start_ar, main_ar, end_ar, end_pole]
   - Skip _unset elements

10. Create sheath with structures
    - Create sheath_with_loc
    - Set spec_id from fiber_count mapping
    - Set cluster name (truncate to 120 chars)
    - Call place_in_structures(l_structures)

11. Update statistics
    - Increment .stats[:aerial_routes] for each AR created
    - Increment .stats[:sheaths] for sheath
    - Track poles created (if placeholders)
```

### Phase 3: Update Statistics Tracking

**New Statistics Fields:**
```magik
.stats << property_list.new_with(
    :aerial_routes, 0,         # Existing
    :poles, 0,                 # Existing
    :sheaths, 0,               # Existing
    :sling_wires, 0,          # Existing
    :ar_segments, 0,           # NEW - Additional AR segments created
    :placeholder_poles, 0,     # NEW - Placeholder poles created
    # ... other stats
)
```

## File Changes Summary

### Files to Modify:
1. `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik`

### New Methods to Add:
1. `match_cable_core(cable_name)` - ~40 lines (wildcard matching, check large numbers first)
2. `is_sling_wire?(folders)` - ~10 lines (wildcard pattern check)

### Methods to Update:
1. `is_cable?(pm)` - Update from exact matches to wildcard patterns (~5 lines)

### Methods to Enhance:
1. `create_aerial_route(pm)` - Expand from ~30 lines to ~250 lines

### New Slots Needed:
1. `:sheath_col` - sheath_with_loc collection reference (add to init)
2. `:sw_col` - sling_wire collection reference (add to init)
3. `:structure_manager` - structure_manager instance (add to init)

### Estimated Total Changes:
- Add: ~50 lines (2 new methods: match_cable_core, is_sling_wire)
- Update: ~5 lines (is_cable? method to use wildcards)
- Modify: ~220 lines (enhanced create_aerial_route)
- Init updates: ~10 lines (new collections)
- **Total: ~285 new/modified lines**

## Fiber Count to Spec/STF Mapping

| Fiber Count | Spec ID             | STF Item Code  |
|-------------|---------------------|----------------|
| 12          | SM G652D-ADSS 12C   | (empty)        |
| 24          | SM G652D-ADSS 24C   | 200000100      |
| 36          | SM G652D-ADSS 36C   | 200000975      |
| 48          | SM G652D-ADSS 48C   | 200001038      |
| 72          | SM G652D-ADSS 72C   | FO_INV_FTTX_0796 |
| 96          | SM G652D-ADSS 96C   | 200001630      |
| 144         | SM G652D-ADSS 144C  | 200001030      |
| 288         | SM G652D-ADSS 288C  | 200001015      |
| 576         | SM G652D-ADSS 576C  | (custom)       |

## Testing Checklist

After implementation, test the following scenarios:

### Test 1: Cable Folder Validation (Wildcard Patterns)
- [ ] Folder with "CABLE" → matches `"*cable*"` → processed
- [ ] Folder with "Distribution Cable Line A" → matches `"*cable*"` → processed
- [ ] Folder with "KABEL" (Indonesian) → matches `"*kabel*"` → processed
- [ ] Folder with "CABEL" (typo) → matches `"*cabel*"` → processed
- [ ] Folder without cable keywords → skipped

### Test 2: Fiber Count Detection (Wildcard Patterns)
- [ ] Cable name "Cable 24C" → matches `"*24*"` → 24 cores, spec "SM G652D-ADSS 24C"
- [ ] Cable name "FO-144" → matches `"*144*"` → 144 cores, spec "SM G652D-ADSS 144C"
- [ ] Cable name "ADSS 48 Core" → matches `"*48*"` → 48 cores
- [ ] Cable name "288C Main" → matches `"*288*"` (checked before *28*) → 288 cores
- [ ] Cable name "144-CORE" → matches `"*144*"` (checked before *14*) → 144 cores
- [ ] Cable name with no number → default 24 cores
- [ ] Cable name "FO 12" → matches `"*12*"` → 12 cores

### Test 3: Sling Wire Handling (Wildcard Patterns)
- [ ] Folder "SLING WIRE" → matches `"*sling wire*"` → creates sling_wire, not aerial_route
- [ ] Folder "sling_wire" → matches `"*sling_wire*"` → creates sling_wire
- [ ] Folder "Line A|Sling Wire|Item" → matches `"*sling wire*"` → creates sling_wire
- [ ] Sling wire length > 0.6m → created
- [ ] Sling wire length < 0.6m → skipped

### Test 4: Start Point Handling
- [ ] Pole exists within 500m → uses existing pole, creates AR segment if > 0.6m
- [ ] No pole within 500m → creates placeholder "Existing Pole AR"
- [ ] Distance < 0.6m → no additional AR segment

### Test 5: End Point Handling
- [ ] Pole exists within 500m → uses existing pole, creates AR segment if > 0.6m
- [ ] No pole within 500m → creates placeholder "Existing Pole AR"
- [ ] Distance < 0.6m → no additional AR segment

### Test 6: Sheath Creation
- [ ] Sheath created with correct spec_id based on fiber count
- [ ] Sheath placed in all structures (poles + aerial routes)
- [ ] Cluster name truncated to 120 characters if needed
- [ ] All structures properly connected

### Test 7: Multiple Aerial Routes
- [ ] Cable with poles at both ends → 3 aerial routes + sheath
- [ ] Cable with no poles → 1 aerial route + 2 placeholder poles + sheath
- [ ] Cable with pole at start only → 2 aerial routes + 1 placeholder pole + sheath

## Benefits of Advanced Mode

1. **Complete Topology** - Creates poles, aerial routes, and sheaths with proper connections
2. **Smart Snapping** - Connects to existing poles when found within 500m
3. **Placeholder Creation** - Creates poles where needed for complete topology
4. **Accurate Attributes** - Detects fiber count, spec, and STF codes from cable names
5. **Sling Wire Support** - Handles sling wires separately from aerial routes
6. **Structure Continuity** - Uses sheath to maintain continuous structure path
7. **Flexible Patterns** - Handles variations in naming (CABLE, KABEL, CABEL)

## Implementation Notes

1. **Structure Manager**: Initialize in init() method:
   ```magik
   .structure_manager << structure_manager.new(.database, .database.world)
   ```

2. **Collection References**: Add to init() method:
   ```magik
   .sheath_col << .database.collections[:sheath_with_loc]
   .sw_col << .database.collections[:sling_wire]
   ```

3. **Error Handling**: Wrap all operations in `_try/_when error` blocks

4. **Progress Logging**: Enhanced logging:
   ```
   ✓ Aerial route created: Cable01 (24 cores)
   → Additional AR segment: pole to start (1.2m)
   → Placeholder pole created at start
   ✓ Sheath created with 5 structures
   ```

5. **Statistics**: Track all components:
   - Main aerial routes
   - Additional AR segments
   - Placeholder poles created
   - Sheaths created
   - Sling wires created

## References

- **Source Implementation:** `C:\Users\user\Downloads\cluster_astri (1).magik:2-596`
- **Current Implementation:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:855-882`
- **Design Plan:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\DESIGN_MIGRATION_PLAN.md`
- **Pole Enhancement:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\POLE_MIGRATION_ENHANCEMENT_PLAN.md`

## Next Steps

1. Review and approve this plan
2. Implement Phase 1: Add helper methods (match_cable_core, is_sling_wire)
3. Update init() to add collection references and structure_manager
4. Implement Phase 2: Enhance create_aerial_route()
5. Update statistics tracking
6. Test with sample KML data containing various cable types
7. Verify pole detection and placeholder creation
8. Verify sheath placement and structure connectivity
9. Deploy to production

---
**Document Version:** 1.1
**Created:** 2025-11-02
**Last Updated:** 2025-11-02
**Status:** Ready for Review

## Revision History

**v1.1 (2025-11-02):**
- Updated folder validation to use wildcard patterns (`"*cable*"`, `"*kabel*"`, etc.)
- Updated cable core detection to use wildcard patterns (`"*24*"`, `"*144*"`, etc.)
- Added note to check larger numbers first (576, 288, 144 before 12)
- Converted all pattern matching to lowercase + wildcards (consistent with pole enhancement)
- Updated is_cable?() method to use wildcard matching
- Updated test cases to reflect wildcard pattern matching
- Reduced code estimate from ~290 to ~285 lines

**v1.0 (2025-11-02):**
- Initial document creation
- Basic implementation plan with exact/regex matching
