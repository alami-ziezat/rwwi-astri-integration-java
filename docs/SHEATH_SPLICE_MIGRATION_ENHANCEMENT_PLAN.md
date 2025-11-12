# Sheath Splice Migration Enhancement Plan

## Overview
This document describes the implementation of sheath splice (closure) migration from KML point placemarks to real Smallworld sheath_splice objects based on the reference implementation `cluster_sheath_splice_migration_astri()` in `cluster_astri (1).magik`.

The implementation includes:
- **Multi-type closure detection** (Join Closure, FDT, FAT)
- **Core count detection** from placemark name
- **Pole association** (search nearby or create placeholder)
- **Sheath splice creation** with complete attributes
- **Optical splitter creation** for FDT and FAT types
- **STF item code mapping** based on closure type and core count
- **Wildcard pattern matching** for flexible folder validation

## Current Implementation (Simplified Mode)

**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:1217-1248`

**Current Behavior:**
- Checks if placemark is splice based on parent folder (area-based)
- Currently only handles area-based splices
- No point-based sheath splice implementation
- No closure type detection
- No optical splitter creation
- No pole association

**Current is_splice?() Method:**
```magik
_private _method astri_design_migrator.is_splice?(pm)
    ## Check if placemark is a splice/sheath (area-based)

    _local parent << pm[:parent].default("").lowercase

    >> pm[:type] = "area" _andif
       (parent.matches?("splice") _orif
        parent.matches?("closure") _orif
        parent.matches?("joint"))
_endmethod
```

**Issues:**
1. This only handles **area-based** splices, but the reference implementation processes **point-based** sheath splices
2. Not using wildcard patterns `*pattern*`
3. Not converting to lowercase first

## Reference Implementation (Advanced Mode)

**Location:** `C:\Users\user\Downloads\cluster_astri (1).magik:846-1353`

**Key Features:**

### 1. Folder Pattern Validation

**Purpose:** Detect closure type from folder hierarchy

**Folder Hierarchy Parsing:**
```magik
fol << a_shp.folders.split_by("|")
fsize << fol.size

_if fsize = 1
_then
    fparent << fsize
_else
    fparent << fsize - 1
_endif
```

**Closure Type Detection (Lines 912-1348):**
```magik
_if fol[fparent] = "Closure" _orif fol[fsize] = "Closure" _orif
    fol[fparent] = "CLOSURE" _orif fol[fsize] = "CLOSURE" _orif
    fol[fparent] = "JOINT CLOSURE" _orif fol[fsize] = "JOINT CLOSURE"
_then
    # Join Closure processing

_elif fol[fparent] = "FDT" _orif fol[fsize] = "FDT"
_then
    # FDT processing

_elif fol[fparent] = "FAT" _orif fol[fsize] = "FAT"
_then
    # FAT processing
_endif
```

**Enhanced Logic (Wildcard Matching):**
- **Step 1:** Check if placemark type is "point" (not "area")
- **Step 2:** Convert folder string to lowercase FIRST
- **Step 3:** Check patterns using wildcards
- **Simplified Patterns:**
  - `"*closure*"` OR `"*joint*"` → Join Closure
  - `"*fat*"` → FAT (Fiber Access Terminal)
  - `"*fdt*"` → FDT (Fiber Distribution Terminal)
- **Processing Order:** CLOSURE/JOINT → FAT → FDT

### 2. Core Count Detection

**Purpose:** Extract core count from placemark name for spec_id and STF mapping

**IMPORTANT:** Join Closure and FDT use the **same detection method** (`match_core()`) but map to **completely different** spec_id and STF codes!

**Join Closure Core Detection (Lines 926-953):**
```magik
m_clr << _self.match_closure_core(a_shp.name.write_string)

_if m_clr = "24"
_then
    sp << "Join Closure Dome 24 Core"
    st_cd << "200000180"
_elif m_clr = "36"
_then
    sp << "Join Closure Dome 36 Core"
    st_cd << "200001049"
_elif m_clr = "48"
_then
    sp << "Join Closure Dome 48 Core"
    st_cd << "200000164"
_elif m_clr = "72"
_then
    sp << "Join Closure Dome 72 Core"
    st_cd << "200000159"
_elif m_clr = "96"
_then
    sp << "Join Closure Dome 96 Core"
    st_cd << "200000176"
_elif m_clr = "144"
_then
    sp << "Join Closure Dome 144 Core"
    st_cd << "200000158"
_elif m_clr = "288"
_then
    sp << "Join Closure Dome 288 Core"
    st_cd << "200000156"
_else
    sp << "Join Closure Dome 48 Core"  # Default
    st_cd << "200000164"
_endif
```

**FDT Core Detection (Lines 1061-1092):**
```magik
m_fdt << _self.match_fdt_core(a_shp.name.write_string)

_if m_fdt = "48"
_then
    sp << "48 Core pole mounted FDT"
    st_cd << "200001039"
    c_spl << 6   # Number of splitters to create
_elif m_fdt = "72"
_then
    sp << "72 Core pole mounted FDT"
    st_cd << "200001040"
    c_spl << 9
_elif m_fdt = "96"
_then
    sp << "96 Core pole mounted FDT"
    st_cd << "200001041"
    c_spl << 12
_elif m_fdt = "144"
_then
    sp << "144 Core Ground mounted FDT"
    st_cd << "200001042"
    c_spl << 18
_elif m_fdt = "288"
_then
    sp << "288 Core Ground mounted FDT"
    st_cd << "200001043"
    c_spl << 36
_elif m_fdt = "576"
_then
    sp << "576 Core Ground mounted FDT"
    st_cd << "200001044"
    c_spl << 72
_else
    sp << "48 Core pole mounted FDT"  # Default
    st_cd << "200001039"
    c_spl << 6
_endif
```

**Enhanced Core Detection Logic:**
- Uses wildcard pattern matching: `"*576*"`, `"*288*"`, `"*144*"`, etc.
- Check larger numbers FIRST to prevent false matches
- Handles variations: "48", "48c", "48C"
- Order: 576 → 288 → 144 → 96 → 72 → 48 → 36 → 24

### 3. Pole Association

**Purpose:** Find nearby pole or create placeholder

**Pole Search (Lines 961-997):**
```magik
l_location_start << a_shp.location.coord
pp << pseudo_point.new(l_location_start)
pp.world << v_gis.world

(k1, pt) << _self.scan_pole_st(pp, 800)  # Search within 800m

_if k1 _is _true
_then
    # Pole found - use its location
    sh_loc << pt.location.coord
    l_result_pl << pt
_else
    # No pole found - create placeholder pole
    l_prop_values_pl << property_list.new_with(
        :location, l_location_start,
        :telco_pole_tag, "Pole" + a_shp.name,
        :usage, "Telco",
        :material_type, "Steel",
        :extension_arm, _false,
        :power_riser, _false,
        :telco_riser, _false,
        :bond, _false,
        :ground_status, _false,
        :type, "T7",
        :folders, a_shp.folders,
        :fttx_network_type, "Cluster",
        :line_type, m_line,
        :pop, .pop_name,
        :olt, .pop_name,
        :segment, .segment_id,
        :project, .prj_id,
        :construction_status, "In Service"
    )

    l_rec_trans_pl << record_transaction.new_insert(pole_col, l_prop_values_pl)
    l_result_pl << l_rec_trans_pl.run()
    sh_loc << l_result_pl.location.coord
_endif
```

### 4. Sheath Splice Creation

**Purpose:** Create sheath_splice object

**Join Closure Creation (Lines 1016-1039):**
```magik
l_prop_values << property_list.new_with(
    :location, sh_loc,
    :asset_owner, "Owned",
    :splice_type, "breaking",
    :splice_method, "Fusion",
    :spec_id, sp,                              # e.g., "Join Closure Dome 48 Core"
    :folders, a_shp.folders,
    :sheath_splice_object_type, ot,            # "Join Closure"
    :fttx_network_type, "Cluster",
    :line_type, m_line,
    :name, a_shp.name,
    :segment, .segment_id,
    :pop, .pop_name,
    :olt, .pop_name,
    :project, .prj_id,
    :stf_item_code, st_cd,                     # e.g., "200000164"
    :construction_status, "In Service"
)

l_rec_trans << record_transaction.new_insert(sc_col, l_prop_values)
l_result << l_rec_trans.run()
```

### 5. Pole Association to Sheath Splice

**Purpose:** Associate sheath splice to pole structure

**Association (Lines 1043-1047):**
```magik
_if l_result_pl _isnt _unset
_then
    _if l_result_pl.external_name = "Pole"
    _then
        l_result.associate_to_structure(l_result_pl)
    _endif
_endif
```

### 6. Optical Splitter Creation

**Purpose:** Create optical splitters for FDT and FAT

**FDT Splitter Creation (Lines 1189-1206):**
```magik
_for i _over range(1, 6)  # Create 6 splitters for FDT
_loop
    vals << property_list.new_with(
        :construction_status, "In Service",
        :asset_owner, "Owned",
        :name, a_shp.name,
        :function, "Splitter",
        :spec_id, "STF Splitter 1:8",
        :stf_splitter_type, "FDT",
        :stf_item_code, "200001047",
        :sheath_splice, l_result
    )

    l_rec_trans_s << record_transaction.new_insert(os_col, vals)
    l_result_s << l_rec_trans_s.run()
_endloop
```

**FAT Splitter Creation (Lines 1319-1338):**
```magik
_for i _over range(1, 2)  # Create 2 splitters for FAT
_loop
    vals << property_list.new_with(
        :construction_status, "In Service",
        :asset_owner, "Owned",
        :name, a_shp.name + "-" + i.write_string,
        :function, "Splitter",
        :spec_id, "Splitter 1:8",
        :stf_splitter_type, "FAT",
        :stf_item_code, "200001047",
        :sheath_splice, l_result
    )

    l_rec_trans_s << record_transaction.new_insert(os_col, vals)
    l_result_s << l_rec_trans_s.run()
_endloop
```

**Note:** Number of splitters varies by core count for FDT (calculated as c_spl variable).

## Enhanced Implementation Plan

### Phase 1: Add Helper Methods

**Update existing method and add new helper methods to `astri_design_migrator`:**

1. **is_splice?(pm)** - Update to detect point-based sheath splice (not area-based)
2. **match_closure_type(folders)** - Detect closure type (Join Closure, FAT, FDT)
3. **match_core(name)** - Extract core count from name (used for both Closure and FDT)

**Implementation Details:**

#### is_splice?(pm) - Updated
```magik
_private _method astri_design_migrator.is_splice?(pm)
    ## Check if placemark is a point-based sheath splice
    ## Checks for: CLOSURE, JOINT CLOSURE, FAT, FDT patterns
    ## IMPORTANT: This is for POINT type (not area)

    _local folders << pm[:parent].default("")

    _if folders = "" _orif folders _is _unset
    _then
        _return _false
    _endif

    # STEP 1: Convert to lowercase FIRST
    _local folders_lc << folders.lowercase

    # STEP 2: Check if it's a POINT type (not area)
    _if pm[:type] <> "point"
    _then
        _return _false
    _endif

    # STEP 3: Check simplified wildcard patterns
    >> folders_lc.matches?("*closure*") _orif
       folders_lc.matches?("*joint*") _orif
       folders_lc.matches?("*fdt*") _orif
       folders_lc.matches?("*fat*")
_endmethod
```

**Key Changes:**
1. ✅ Changed from `area` to `point` type check
2. ✅ Uses wildcard patterns `*pattern*`
3. ✅ Converts to lowercase FIRST before validation
4. ✅ Returns false if not point type

#### match_closure_type(folders)
```magik
_private _method astri_design_migrator.match_closure_type(folders)
    ## Determine closure type from folder string
    ## Order: CLOSURE/JOINT → FAT → FDT
    ## Returns: "Join Closure", "FAT", or "FDT"

    # STEP 1: Convert to lowercase FIRST
    _local folders_lc << folders.lowercase

    # STEP 2: Check patterns in order
    # Priority 1: CLOSURE or JOINT CLOSURE
    _if folders_lc.matches?("*closure*") _orif folders_lc.matches?("*joint*")
    _then
        >> "Join Closure"

    # Priority 2: FAT
    _elif folders_lc.matches?("*fat*")
    _then
        >> "FAT"

    # Priority 3: FDT
    _elif folders_lc.matches?("*fdt*")
    _then
        >> "FDT"

    _else
        >> "Join Closure"  # Default
    _endif
_endmethod
```

**Key Changes:**
1. ✅ Check order: CLOSURE/JOINT first, then FAT, then FDT
2. ✅ Converts to lowercase FIRST

#### match_core(name)
```magik
_private _method astri_design_migrator.match_core(name)
    ## Extract core count from name using wildcard matching
    ## Used for BOTH Join Closure and FDT
    ## Returns: "576", "288", "144", "96", "72", "48", "36", "24", or _unset

    _if name _is _unset _orif name = ""
    _then
        _return _unset
    _endif

    # STEP 1: Convert to lowercase FIRST
    _local name_lc << name.lowercase

    # STEP 2: Check larger numbers FIRST to prevent false matches
    # (e.g., "*28*" would match 288, "*14*" would match 144)
    _if name_lc.matches?("*576*")
    _then
        >> "576"
    _elif name_lc.matches?("*288*")
    _then
        >> "288"
    _elif name_lc.matches?("*144*")
    _then
        >> "144"
    _elif name_lc.matches?("*96*")
    _then
        >> "96"
    _elif name_lc.matches?("*72*")
    _then
        >> "72"
    _elif name_lc.matches?("*48*")
    _then
        >> "48"
    _elif name_lc.matches?("*36*")
    _then
        >> "36"
    _elif name_lc.matches?("*24*")
    _then
        >> "24"
    _else
        >> _unset  # No core count detected
    _endif
_endmethod
```

**Key Changes:**
1. ✅ Renamed from `match_closure_core()` to `match_core()` - used for both Closure and FDT
2. ✅ Converts to lowercase FIRST
3. ✅ Checks larger numbers first to prevent false matches

### Phase 2: Update init() Method

**Add New Collection References:**
```magik
_private _method astri_design_migrator.init(database, prj_id, segment_id, pop_name)
    # ... existing code ...

    # Add sheath splice collection
    .sc_col << .database.collections[:sheath_splice]

    # Note: .os_col (optical_splitter) and .pole_col already exist

    # ... rest of init ...
_endmethod
```

### Phase 3: Add create_sheath_splice() Method

**New Logic Flow:**
```
0. Validate sheath splice folder pattern
   - Already validated by is_splice?(pm) - must be POINT type
   - STEP 1: Convert folder string to lowercase FIRST
   - STEP 2: Check patterns: "*closure*", "*joint*", "*fdt*", "*fat*"
   - If not matched → skip (return, not a sheath splice)

1. Determine closure type
   - Call match_closure_type(folders)
   - Order: CLOSURE/JOINT → FAT → FDT
   - Returns: "Join Closure", "FAT", or "FDT"

2. Detect core count from name (for Join Closure and FDT only)
   - Call match_core(name)
   - Check: 576 → 288 → 144 → 96 → 72 → 48 → 36 → 24
   - Returns core count string or _unset
   - For FAT: Skip core detection (uses fixed 16 ports spec)

3. Map core count to spec_id and STF code
   - **IMPORTANT:** Same core count maps to DIFFERENT specs for Closure vs FDT!

   **Join Closure Mapping:**
   - 24 → "Join Closure Dome 24 Core" / 200000180
   - 36 → "Join Closure Dome 36 Core" / 200001049
   - 48 → "Join Closure Dome 48 Core" / 200000164
   - 72 → "Join Closure Dome 72 Core" / 200000159
   - 96 → "Join Closure Dome 96 Core" / 200000176
   - 144 → "Join Closure Dome 144 Core" / 200000158
   - 288 → "Join Closure Dome 288 Core" / 200000156
   - Default → "Join Closure Dome 48 Core" / 200000164

   **FDT Mapping:**
   - 48 → "48 Core pole mounted FDT" / 200001039 / 6 splitters
   - 72 → "72 Core pole mounted FDT" / 200001040 / 9 splitters
   - 96 → "96 Core pole mounted FDT" / 200001041 / 12 splitters
   - 144 → "144 Core Ground mounted FDT" / 200001042 / 18 splitters
   - 288 → "288 Core Ground mounted FDT" / 200001043 / 36 splitters
   - 576 → "576 Core Ground mounted FDT" / 200001044 / 72 splitters
   - Default → "48 Core pole mounted FDT" / 200001039 / 6 splitters

   **FAT Mapping:**
   - Fixed → "Pole mounted outdoor(16 ports)" / 200001047 / 2 splitters

4. Find or create pole
   - Search for pole within 800m using scan_pole_st()
   - If found: use pole location and reference
   - If not found: create placeholder pole
   - Get sh_loc (splice location) from pole

5. Get line type
   - Call match_line(folders) - already exists

6. Create sheath splice
   - Create sheath_splice with:
     * location: sh_loc (from pole)
     * asset_owner: "Owned"
     * splice_type: "breaking"
     * splice_method: "Fusion"
     * spec_id: from core count mapping
     * sheath_splice_object_type: closure type
     * folders, name, segment, pop, olt, project
     * stf_item_code: from core count mapping
     * line_type: from match_line()
     * construction_status: "Proposed"

7. Associate to pole structure
   - Check if pole.external_name = "Pole"
   - Call sheath_splice.associate_to_structure(pole)

8. Create optical splitters (for FDT and FAT only)
   - FDT: Create c_spl splitters (calculated from core count)
     * 48 core → 6 splitters
     * 72 core → 9 splitters
     * 96 core → 12 splitters
     * 144 core → 18 splitters
     * 288 core → 36 splitters
     * 576 core → 72 splitters
   - FAT: Create 2 splitters
   - Each splitter:
     * name: splice_name (or splice_name-i for FAT)
     * function: "Splitter"
     * spec_id: "STF Splitter 1:8" (FDT) or "Splitter 1:8" (FAT)
     * stf_splitter_type: "FDT" or "FAT"
     * stf_item_code: "200001047"
     * sheath_splice: reference to created splice

9. Update statistics
   - Increment .stats[:sheath_splices]
   - Increment .stats[:optical_splitters] by splitter count
```

### Phase 4: Update migrate_placemarks() Method

**Add Sheath Splice Pass:**
```magik
# In Pass 3 (after demand points):
_if _self.is_splice?(pm)
_then
    _self.create_sheath_splice(pm)
    placemarks.remove(pm)
_endif
```

**Note:** `is_splice?(pm)` now checks for POINT type (not area), so this processes point-based sheath splices only.

## File Changes Summary

### Files to Modify:
1. `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik`

### New Slots to Add (in exemplar definition):
1. `:sc_col` - sheath_splice collection

### Methods to Update:
1. `is_splice?(pm)` - Update from area-based to point-based (~20 lines)

### New Methods to Add:
1. `match_closure_type(folders)` - ~20 lines (type detection)
2. `match_core(name)` - ~40 lines (core count detection for Closure and FDT)
3. `create_sheath_splice(pm)` - ~200 lines (main migration logic)

### Other Methods to Update:
1. `init()` - Add sc_col reference (~1 line)
2. `migrate_placemarks()` - Update to call is_splice?(pm) for point-based processing (~5 lines)
3. `print_statistics()` - Add sheath_splices and optical_splitters counters (~4 lines)

### Statistics to Add:
1. `:sheath_splices` - Counter for sheath splices created
2. `:optical_splitters` - Counter for optical splitters created

### Estimated Total Changes:
- Add: ~275 lines (3 helper methods + 1 main method)
- Update: ~8 lines (init, migrate_placemarks, statistics)
- **Total: ~283 new/modified lines**

## Spec ID and STF Item Code Mapping

**IMPORTANT:** Even though Join Closure and FDT use the same `match_core()` method to detect core count, they map to **completely different** spec_id and STF codes!

### Join Closure Mapping (Reference Lines 928-953)

| Core Count | Spec ID | STF Item Code | Splitters |
|------------|---------|---------------|-----------|
| 24 | Join Closure Dome 24 Core | 200000180 | 0 |
| 36 | Join Closure Dome 36 Core | 200001049 | 0 |
| 48 | Join Closure Dome 48 Core | 200000164 | 0 |
| 72 | Join Closure Dome 72 Core | 200000159 | 0 |
| 96 | Join Closure Dome 96 Core | 200000176 | 0 |
| 144 | Join Closure Dome 144 Core | 200000158 | 0 |
| 288 | Join Closure Dome 288 Core | 200000156 | 0 |
| **Default** | **Join Closure Dome 48 Core** | **200000164** | **0** |

**Note:** Join Closure does NOT create optical splitters

### FDT (Fiber Distribution Terminal) Mapping (Reference Lines 1063-1092)

| Core Count | Spec ID | STF Item Code | Splitters |
|------------|---------|---------------|-----------|
| 48 | 48 Core pole mounted FDT | 200001039 | 6 |
| 72 | 72 Core pole mounted FDT | 200001040 | 9 |
| 96 | 96 Core pole mounted FDT | 200001041 | 12 |
| 144 | 144 Core Ground mounted FDT | 200001042 | 18 |
| 288 | 288 Core Ground mounted FDT | 200001043 | 36 |
| 576 | 576 Core Ground mounted FDT | 200001044 | 72 |
| **Default** | **48 Core pole mounted FDT** | **200001039** | **6** |

**Note:** FDT creates optical splitters (count varies by core)

### FAT (Fiber Access Terminal) Mapping (Reference Lines 1283-1304)

| Type | Spec ID | STF Item Code | Splitters |
|------|---------|---------------|-----------|
| FAT (Fixed) | Pole mounted outdoor(16 ports) | 200001047 | 2 |

**Note:** FAT uses fixed 16 ports specification (no core detection needed)

### Optical Splitter Spec (Reference Lines 1195, 1326)

| Closure Type | Splitter Spec ID | STF Item Code |
|--------------|------------------|---------------|
| FDT | STF Splitter 1:8 | 200001047 |
| FAT | Splitter 1:8 | 200001047 |

**Note:** Both FDT and FAT use the same splitter STF code but different spec_id

## Testing Checklist

After implementation, test the following scenarios:

### Test 1: Folder Pattern Validation
- [ ] Folder "Project|CLOSURE|Item" → matches `"*closure*"` → processed
- [ ] Folder "Line A|JOINT CLOSURE" → matches `"*joint*"` → processed
- [ ] Folder "Area|FDT" → matches `"*fdt*"` → processed
- [ ] Folder "Segment|FAT" → matches `"*fat*"` → processed
- [ ] Folder without closure keywords → skipped

### Test 2: Closure Type Detection
- [ ] Folder with "CLOSURE" → "Join Closure"
- [ ] Folder with "JOINT CLOSURE" → "Join Closure"
- [ ] Folder with "FDT" → "FDT"
- [ ] Folder with "FAT" → "FAT"

### Test 3: Core Count Detection
- [ ] Name "JC-48-001" → "48"
- [ ] Name "FDT 144" → "144"
- [ ] Name "Closure 288C" → "288"
- [ ] Name "FDT-576-A" → "576"
- [ ] Name "JC-24" → "24"
- [ ] Name without core count → _unset

### Test 4: Spec ID and STF Mapping (CRITICAL - Different for Same Core!)
- [ ] **Join Closure 48 core** → "Join Closure Dome 48 Core", STF="200000164", 0 splitters
- [ ] **FDT 48 core** → "48 Core pole mounted FDT", STF="200001039", 6 splitters
- [ ] Verify SAME core count maps to DIFFERENT specs!
- [ ] Join Closure 144 core → "Join Closure Dome 144 Core", STF="200000158", 0 splitters
- [ ] FDT 144 core → "144 Core Ground mounted FDT", STF="200001042", 18 splitters
- [ ] FDT 576 core → "576 Core Ground mounted FDT", STF="200001044", 72 splitters
- [ ] FAT (no core) → "Pole mounted outdoor(16 ports)", STF="200001047", 2 splitters

### Test 5: Pole Association
- [ ] Sheath splice near pole (< 800m) → uses pole location
- [ ] Sheath splice far from pole (> 800m) → creates placeholder pole
- [ ] Sheath splice associated to pole structure

### Test 6: Sheath Splice Creation
- [ ] Basic sheath splice created with all required fields
- [ ] splice_type = "breaking", splice_method = "Fusion"
- [ ] Correct spec_id and stf_item_code based on type and core
- [ ] construction_status = "Proposed"

### Test 7: Optical Splitter Creation
- [ ] FDT 48 core → 6 splitters created
- [ ] FDT 144 core → 18 splitters created
- [ ] FAT → 2 splitters created
- [ ] Join Closure → 0 splitters (no splitters for Join Closure)
- [ ] Each splitter linked to sheath_splice

## Benefits of Advanced Mode

1. **Complete Topology** - Creates sheath splice + pole + optical splitters
2. **Multiple Closure Types** - Supports Join Closure, FDT, FAT
3. **Intelligent Core Detection** - Extracts core count from name
4. **Automatic STF Mapping** - Maps core count to correct item codes
5. **Pole Integration** - Associates to nearby poles or creates placeholder
6. **Network Completeness** - Creates optical splitters for FDT/FAT
7. **Wildcard Flexibility** - Simple patterns cover all variations
8. **Field Safety** - Default values for missing data

## Implementation Notes

1. **Collection References:** Add to init() method:
   ```magik
   .sc_col << .database.collections[:sheath_splice]
   ```

2. **Construction Status:** Use "Proposed" (not "In Service" as in reference)

3. **Point Type Validation:** IMPORTANT - `is_splice?()` checks for **POINT** type (not area type)

4. **Pole Search Radius:** Use 800m (from reference implementation)

5. **Error Handling:** Wrap all operations in `_try/_when error` blocks

6. **Core Count Detection:**
   - **Join Closure and FDT:** Use same `match_core(name)` method with wildcard patterns
   - **CRITICAL:** Same core count maps to DIFFERENT spec_id and STF codes for Closure vs FDT!
     - Example: Core 48
       - Join Closure → "Join Closure Dome 48 Core" / STF 200000164
       - FDT → "48 Core pole mounted FDT" / STF 200001039
   - **FAT:** No core detection, uses fixed spec: "Pole mounted outdoor(16 ports)" / STF 200001047

7. **Spec ID and STF Code Mapping:**
   - Must implement separate mapping logic for Join Closure vs FDT
   - Join Closure: 7 core sizes (24, 36, 48, 72, 96, 144, 288)
   - FDT: 6 core sizes (48, 72, 96, 144, 288, 576)
   - FAT: Fixed (no mapping needed)

8. **Splitter Count Calculation:** For FDT, calculate based on core count:
   - 48 → 6, 72 → 9, 96 → 12, 144 → 18, 288 → 36, 576 → 72
   - FAT: Always 2 splitters
   - Join Closure: 0 splitters (no splitters created)

9. **Processing Order:** CLOSURE/JOINT first, then FAT, then FDT

10. **Lowercase Conversion:** ALWAYS convert to lowercase FIRST before pattern matching

11. **Progress Logging:** Enhanced logging:
    ```
    ✓ Sheath splice created: JC-48-001 (Type: Join Closure, Core: 48)
    → Associated to pole: P001
    ✓ Created 6 optical splitters
    ```

12. **Statistics:** Track sheath splices and optical splitters separately

## References

- **Source Implementation:** `C:\Users\user\Downloads\cluster_astri (1).magik:846-1353`
- **Current Implementation:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:1217-1248`
- **Design Plan:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\DESIGN_MIGRATION_PLAN.md`

## Next Steps

1. Review and approve this plan
2. Implement Phase 1: Add helper methods (is_sheath_splice_point?, match_closure_type, match_closure_core)
3. Update Phase 2: Add collection reference in init()
4. Implement Phase 3: Add create_sheath_splice() method
5. Update Phase 4: Add sheath splice processing in migrate_placemarks()
6. Test with sample KML data containing sheath splice points
7. Verify closure type and core count detection
8. Verify pole association (search and create)
9. Verify optical splitter creation for FDT and FAT
10. Deploy to production

---
**Document Version:** 1.2
**Created:** 2025-11-04
**Last Updated:** 2025-11-04
**Status:** Ready for Review

## Revision History

**v1.2 (2025-11-04):**
- **CRITICAL:** Clarified that Join Closure and FDT use **same `match_core()` method** but map to **completely different spec_id and STF codes**
- Added detailed spec_id and STF code mapping tables for each type:
  - Join Closure: 7 core sizes (24-288), STF codes 200000xxx series, 0 splitters
  - FDT: 6 core sizes (48-576), STF codes 200001039-200001044, variable splitters
  - FAT: Fixed 16 ports, STF code 200001047, 2 splitters
- Example: Core 48
  - Join Closure → "Join Closure Dome 48 Core" / STF 200000164 / 0 splitters
  - FDT → "48 Core pole mounted FDT" / STF 200001039 / 6 splitters
- Emphasized that implementation must have separate mapping logic for each closure type

**v1.1 (2025-11-04):**
- **CRITICAL:** Updated `is_splice?()` to check for **POINT** type (not area)
- **CRITICAL:** Convert folder string to lowercase FIRST before validation
- Uses wildcard patterns `*pattern*` for flexible matching
- Processing order: CLOSURE/JOINT → FAT → FDT
- Renamed `match_closure_core()` to `match_core()` - used for both Closure and FDT
- FAT uses default 16 core (no detection), fixed spec: "Pole mounted outdoor(16 ports)"
- Consistent with pole, aerial route, and demand point enhancement approaches

**v1.0 (2025-11-04):**
- Initial document creation
- Based on cluster_sheath_splice_migration_astri reference
