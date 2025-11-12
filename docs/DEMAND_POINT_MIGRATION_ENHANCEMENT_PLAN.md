# Demand Point Migration Enhancement Plan

## Overview
This document describes the enhancement of the `create_demand_point()` method in `astri_design_migrator.magik` from **simplified mode** to **advanced mode** based on the reference implementation `cluster_demand_point_migration_astri()` in `cluster_astri (1).magik`.

The enhanced implementation includes:
- **Multi-level folder pattern matching** (folder, parent, grandparent levels)
- **Demand point creation** with complete attributes
- **Annotation geometry** creation for labels
- **Micro cell intersection** to detect splitter_id
- **Optical splitter lookup** for splice coordinates
- **Boundary intersection** for administrative data (province, city, district)
- **Customer premise creation** with full attributes and lat/long
- **Building creation** for special cases (GEDUNG type)
- **Coordinate transformation** to WGS84 lat/long

## Current Implementation (Simplified Mode)

**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:585-621`

**Current Behavior:**
- Parses point geometry from KML coordinates
- Creates demand point with basic attributes
- Sets hardcoded values (status="Active", type="Type 1")
- No customer premise creation
- No administrative data lookup
- No splitter detection
- No annotation
- No building creation

## Reference Implementation (Advanced Mode)

**Location:** `C:\Users\user\Downloads\cluster_astri (1).magik:1698-2096`

**Key Features:**

### 1. Folder Pattern Validation (Multi-Level)

**Purpose:** Check folder at multiple levels for demand point patterns

**Folder Hierarchy Parsing:**
```magik
fol << a_shp.folders.split_by("|")

_if fol.size = 1
_then
    fsize << fol.size
    fparent << fsize
    fgp << fsize
_elif fol.size = 2
_then
    fsize << fol.size
    fparent << fsize - 1
    fgp << fparent
_else
    fsize << fol.size
    fparent << fsize - 1
    fgp << fparent - 1
_endif
```

**Reference Pattern Matching (Lines 1927-1937):**
```magik
_if fol[fsize] = "COSTUMER" _orif fol[fparent] = "COSTUMER" _orif fol[fgp] = "COSTUMER" _orif
    fol[fsize] = "CUSTOMER" _orif fol[fparent] = "CUSTOMER" _orif fol[fgp] = "CUSTOMER" _orif
    fol[fsize] = "HOMEPASS" _orif fol[fparent] = "HOMEPASS" _orif fol[fgp] = "HOMEPASS" _orif
    fol[fsize] = "Homepass ID" _orif fol[fparent] = "Homepass ID" _orif fol[fgp] = "Homepass ID" _orif
    fol[fsize] = "HP UNCOVER" _orif fol[fparent] = "HP UNCOVER" _orif fol[fgp] = "HP UNCOVER" _orif
    fol[fsize] = "HP COVER" _orif fol[fparent] = "HP COVER" _orif fol[fgp] = "HP COVER" _orif
    fol[fsize] = "REDUCE" _orif fol[fparent] = "REDUCE" _orif fol[fgp] = "REDUCE" _orif
    fol[fsize] = "Reduce" _orif fol[fparent] = "Reduce" _orif fol[fgp] = "Reduce" _orif
    fol[fsize] = "reduce" _orif fol[fparent] = "reduce" _orif fol[fgp] = "Reduce"
_then
```

**Enhanced Logic (Simplified Wildcard Matching):**
- **Step 1:** Convert entire folder string to lowercase FIRST
- **Step 2:** Parse folder hierarchy (fsize, fparent, fgp)
- **Step 3:** Check each level (fsize, fparent, fgp) against simplified patterns
- **Simplified Patterns:** `"*homepass*"` OR `"*hp*"` OR `"*reduce*"` OR `"*customer*"`
- Much simpler than checking multiple variants (hp cover, hp uncover, costumer, etc.)
- Handles all variations automatically (HP COVER, HP UNCOVER, HOMEPASS ID, CUSTOMER, COSTUMER)

### 2. Folder Truncation (Lines 1941-1948)

**Purpose:** Ensure folder string doesn't exceed 100 characters

```magik
_if a_shp.folders.size > 100
_then
    ff << a_shp.folders.slice(1, 100)
_else
    ff << a_shp.folders
_endif
```

### 3. Demand Point Creation (Lines 1950-1967)

**Purpose:** Create ftth!demand_point with complete attributes

```magik
l_prop_values << property_list.new_with(
    :identification, a_shp.name,
    :name, a_shp.name,
    :status, "Active",
    :mdu?, _false,
    :type, "Type 1",
    :segment, .segment_id,
    :fttx_network_type, "Cluster",
    :folders, ff,                    # Truncated to 100 chars
    :notes, l_notes,                 # From get_notes() helper
    :pop, .pop_name,
    :olt, .pop_name,
    :location, a_shp.location
)

l_rec_trans << record_transaction.new_insert(dp_col, l_prop_values)
l_result << l_rec_trans.run()
```

### 4. Annotation Creation (Lines 1970-1972)

**Purpose:** Create annotation geometry for demand point label

```magik
l_result.unset_geometry(:annotation)
l_result.unset_geometry(:annotation_2)
anno << l_result.make_geometry(:annotation,
    coordinate.new(l_result.location.coord.x, l_result.location.coord.y),
    l_result.identification)
```

### 5. Micro Cell Detection for Splitter ID (Lines 1983-2009)

**Purpose:** Find intersecting micro cell to get splitter_id

**Process:**

#### Step A: Create spatial predicate with demand point location
```magik
a_point << pseudo_point.new(l_result.location.coord)
a_point.world << v_gis.world

a_predicate << predicate.interacts(:location, {a_point})
s_cell << cell_col.select(a_predicate)
```

#### Step B: Get splitter_id from micro cell
```magik
_if s_cell.size > 0
_then
    a_cell << s_cell.an_element()
    sp_id << a_cell.splitter_id
_endif
```

#### Step C: Lookup optical splitter and get splice coordinates
```magik
_if sp_id <> "" _andif sp_id _isnt _unset
_then
    pred_sp << predicate.eq(:name, sp_id)
    s_sp << os_col.select(pred_sp)

    _if s_sp.size > 0
    _then
        a_sp << s_sp.an_element()
        l_sp << a_sp.get_splice()
        (latp, longp) << _self.get_latlong(l_sp.location.coord)
        c_ll << latp + "  " + longp    # Splice coordinates as string
    _endif
_endif
```

### 6. Boundary Intersection for Administrative Data (Lines 2011-2029)

**Purpose:** Find intersecting pol_boundary to get province, city, district info

```magik
p_predicate << predicate.interacts(:boundary, {a_point})
s_pol << pol_col.select(p_predicate)

_if s_pol.size > 0
_then
    _for r _over s_pol.fast_elements()
    _loop
        _if r.type = "Town"
        _then
            propinsi << r.provinsi
            kabupaten << r.kabupaten
            kecamatan << r.kecamatan
            desa << r.desa
        _endif
    _endloop
_endif
```

### 7. Coordinate Conversion to Lat/Long (Line 2031)

**Purpose:** Convert demand point location to WGS84 latitude/longitude

```magik
(lat, long) << _self.get_latlong(l_result.location.coord)
```

**Note:** get_latlong() is likely a helper method that:
1. Takes local coordinate
2. Transforms to WGS84
3. Returns (latitude_string, longitude_string)

### 8. Customer Premise Creation (Lines 2033-2078)

**Purpose:** Create ftth!customer_premise with full administrative and splitter info

```magik
l_prop_values_cs << property_list.new_with(
    :identification, a_shp.name,
    :customer_name, a_shp.name,
    :type, "Residential",

    # Administrative fields from boundary
    :province, propinsi,
    :branch, kabupaten,
    :city, kecamatan,
    :district, desa,
    :sub_district, "",
    :postal_code, "",

    # Address fields
    :area_name, "",
    :residence_type, "",
    :residence_name, "",
    :street_name, "",
    :unit, "",

    # Network fields
    :pop_id, .pop_name,
    :splitter_id, sp_id,                              # From micro cell
    :spliter_distribution_coordinate, c_ll,           # Splice lat/long

    # Metadata
    :remark, "",
    :remark_2, "",
    :rfs_year, tahun.write_string,                    # Current year
    :rfs_status, "",
    :submission_date, "",
    :last_update, "",
    :stf_item_code, "",

    # Coordinates
    :latitude, lat,                                    # From conversion
    :longitude, long,                                  # From conversion

    # Reference to demand point
    :ftth!demand_point, l_result
)

l_rec_trans_cs << record_transaction.new_insert(cs_col, l_prop_values_cs)
l_result_cs << l_rec_trans_cs.run()
```

### 9. Building Creation (Lines 1902-1918)

**Purpose:** Create building for special demand points marked as "GEDUNG"

```magik
_if a_shp.descriptio = "GEDUNG"
_then
    # Create 400m bounding box around location
    bb << bounding_box.new_enclosing(a_shp.location.bounds, 400)

    l_prop_values << property_list.new_with(
        :type, "Residential",
        :asset_owner, "Owned",
        :name, a_shp.name,
        :boundary, bb
    )

    l_rec_trans << record_transaction.new_insert(bld_col, l_prop_values)
    l_result << l_rec_trans.run()
_endif
```

## Enhanced Implementation Plan

### Phase 1: Add Helper Methods

Add new helper methods to `astri_design_migrator`:

1. **is_demand_point?(folders)** - Update to use multi-level wildcard matching
2. **get_latlong(coordinate)** - Convert local coordinate to WGS84 lat/long strings
3. **truncate_folders(folders, max_length)** - Truncate folder string to max length

**Implementation Details:**

#### is_demand_point?(folders) - Enhanced
```magik
_private _method astri_design_migrator.is_demand_point?(folders)
    ## Check if placemark is a demand point based on multi-level folder patterns
    ## Checks at three levels: fsize (last), fparent, fgp (grandparent)

    _if folders _is _unset _orif folders = ""
    _then
        _return _false
    _endif

    # STEP 1: Convert entire folder string to lowercase FIRST
    _local folders_lc << folders.lowercase

    # STEP 2: Parse folder hierarchy
    _local fol << folders_lc.split_by("|")
    _local fsize << fol.size

    _if fsize = 0
    _then
        _return _false
    _endif

    # Determine folder levels
    _local fparent << _if fsize >= 2 _then >> fsize - 1 _else >> fsize _endif
    _local fgp << _if fsize >= 3 _then >> fparent - 1 _else >> fparent _endif

    # STEP 3: Check simplified patterns at all three levels
    _for fs _over {fol[fsize], fol[fparent], fol[fgp]}.fast_elements()
    _loop
        _if fs.default("").matches?("*homepass*") _orif
           fs.default("").matches?("*hp*") _orif
           fs.default("").matches?("*reduce*") _orif
           fs.default("").matches?("*customer*")
        _then
            _return _true
        _endif
    _endloop

    _return _false
_endmethod
```

**Key Changes:**
1. **Convert to lowercase FIRST** before parsing folders
2. **Simplified patterns:** Only 4 patterns instead of 9+ variants
   - `"*homepass*"` - Covers HOMEPASS, Homepass ID, homepass, etc.
   - `"*hp*"` - Covers HP COVER, HP UNCOVER, HP, hp, etc.
   - `"*reduce*"` - Covers REDUCE, Reduce, reduce
   - `"*customer*"` - Covers CUSTOMER, COSTUMER (typo), customer
3. All folder elements already lowercase from Step 1

#### get_latlong(coordinate)
```magik
_private _method astri_design_migrator.get_latlong(coord)
    ## Convert local coordinate to WGS84 latitude/longitude strings
    ## Returns: (latitude_string, longitude_string)

    # Get WGS84 coordinate system
    _local cs_wgs84 << .ace_view.collections[:sw_gis!coordinate_system].at(:world_longlat_wgs84_degree)
    _local cs_local << .database.world.coordinate_system

    # Create transform from local to WGS84
    _local transform << transform.new_converting_cs_to_cs(cs_local, cs_wgs84)

    # Convert coordinate
    _local wgs84_coord << transform.convert(coord)

    # Format as strings with precision
    _dynamic !print_float_precision! << 12
    _local lat << wgs84_coord.y.write_string
    _local long << wgs84_coord.x.write_string

    _return lat, long
_endmethod
```

#### truncate_folders(folders, max_length)
```magik
_private _method astri_design_migrator.truncate_folders(folders, max_length)
    ## Truncate folder string to maximum length
    ## Returns: Truncated folder string

    _if folders _is _unset
    _then
        _return ""
    _endif

    _if folders.size > max_length
    _then
        _return folders.slice(1, max_length)
    _else
        _return folders
    _endif
_endmethod
```

### Phase 2: Update init() Method

**Add New Collection References:**
```magik
_private _method astri_design_migrator.init(database, prj_id, segment_id, pop_name)
    # ... existing code ...

    # Add new collections for demand point enhancement
    .cs_col << .database.collections[:ftth!customer_premise]
    .cell_col << .database.collections[:ftth!zone]           # Already exists
    .os_col << .database.collections[:optical_splitter]      # Already exists
    .pol_col << .database.collections[:pol_boundary]
    .bld_col << .database.collections[:building]

    # ... rest of init ...
_endmethod
```

### Phase 3: Enhance create_demand_point() Method

**New Logic Flow:**

```
0. Validate demand point folder pattern
   - STEP 1: Convert entire folder string to lowercase FIRST
   - STEP 2: Parse folder hierarchy (fsize, fparent, fgp)
   - STEP 3: Check simplified patterns at all three levels:
     * "*homepass*" OR "*hp*" OR "*reduce*" OR "*customer*"
   - If not matched at any level → skip (return, not a demand point)

1. Truncate folders
   - If folders.size > 100, truncate to 100 characters
   - Prevents field overflow errors

2. Create demand point
   - Parse point geometry
   - Create ftth!demand_point with:
     * identification, name from placemark
     * status = "Active", mdu? = false, type = "Type 1"
     * segment, fttx_network_type, pop, olt
     * Truncated folders
   - Store result as l_result

3. Create annotation geometry
   - Unset existing annotations (:annotation, :annotation_2)
   - Create new :annotation with demand point identification

4. Find intersecting micro cell
   - Create pseudo_point from demand point location
   - Use spatial predicate: predicate.interacts(:location, {point})
   - Select from ftth!zone (cell_col)
   - If found: get splitter_id

5. Lookup optical splitter (if splitter_id exists)
   - Find optical_splitter by name = splitter_id
   - Get splice location: optical_splitter.get_splice()
   - Convert splice coordinate to lat/long
   - Format as "lat  long" string (splice coordinates)

6. Find intersecting boundary
   - Use spatial predicate: predicate.interacts(:boundary, {point})
   - Select from pol_boundary where type = "Town"
   - Extract: provinsi, kabupaten, kecamatan, desa

7. Convert demand point coordinate to lat/long
   - Call get_latlong(demand_point.location.coord)
   - Returns (latitude, longitude) as strings

8. Create customer premise
   - Create ftth!customer_premise with:
     * identification, customer_name from placemark
     * type = "Residential"
     * Administrative fields from boundary
     * splitter_id from micro cell
     * spliter_distribution_coordinate from splice
     * latitude, longitude from conversion
     * rfs_year = current year
     * Reference to demand_point

9. Create building (optional)
   - Check if placemark has descriptio = "GEDUNG"
   - If yes:
     → Create 400m bounding box around location
     → Create building with boundary

10. Update statistics
    - Increment .stats[:demand_points]
```

## File Changes Summary

### Files to Modify:
1. `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik`

### New Slots to Add (in init):
1. `:cs_col` - ftth!customer_premise collection
2. `:pol_col` - pol_boundary collection
3. `:bld_col` - building collection
4. Note: `:cell_col` and `:os_col` already exist from aerial route enhancement

### New Methods to Add:
1. `get_latlong(coordinate)` - ~20 lines (coordinate transformation)
2. `truncate_folders(folders, max_length)` - ~15 lines (string truncation)

### Methods to Update:
1. `is_demand_point?(pm)` - Update to multi-level wildcard matching (~40 lines)
2. `create_demand_point(pm)` - Expand from ~40 lines to ~200 lines

### Estimated Total Changes:
- Add: ~35 lines (2 new methods)
- Update: ~40 lines (is_demand_point? method)
- Enhance: ~160 lines (create_demand_point)
- Init updates: ~5 lines (new collections)
- **Total: ~240 new/modified lines**

## Testing Checklist

After implementation, test the following scenarios:

### Test 1: Folder Pattern Validation (Simplified Multi-Level Wildcard)
- [ ] Folder "Project|CUSTOMER|Item" → lowercase → matches `"*customer*"` at fparent → processed
- [ ] Folder "Line A|Segment 1|HOMEPASS" → lowercase → matches `"*homepass*"` at fsize → processed
- [ ] Folder "Area|Zone|HP COVER" → lowercase → matches `"*hp*"` at fsize → processed
- [ ] Folder "Main|Sub|HP UNCOVER|Detail" → lowercase → matches `"*hp*"` at fgp → processed
- [ ] Folder "Project|Line|REDUCE" → lowercase → matches `"*reduce*"` at fsize → processed
- [ ] Folder "COSTUMER" (typo) → lowercase → matches `"*customer*"` → processed
- [ ] Folder "Homepass ID" → lowercase → matches `"*homepass*"` → processed
- [ ] Folder "hp" → lowercase → matches `"*hp*"` → processed
- [ ] Folder without demand point keywords → skipped

### Test 2: Folder Truncation
- [ ] Folder with 50 characters → not truncated
- [ ] Folder with 150 characters → truncated to 100 characters
- [ ] Folder = _unset → empty string

### Test 3: Demand Point Creation
- [ ] Basic demand point created with all required fields
- [ ] status = "Active", mdu? = false, type = "Type 1"
- [ ] Truncated folders stored correctly

### Test 4: Annotation Creation
- [ ] Annotation geometry created at demand point location
- [ ] Annotation displays demand point identification

### Test 5: Micro Cell Detection
- [ ] Demand point inside micro cell → splitter_id detected
- [ ] Demand point outside any micro cell → splitter_id = _unset
- [ ] Micro cell without splitter_id → handles gracefully

### Test 6: Optical Splitter Lookup
- [ ] Valid splitter_id → finds optical_splitter
- [ ] Gets splice location and converts to lat/long
- [ ] Formats as "lat  long" string
- [ ] Invalid splitter_id → returns empty string

### Test 7: Boundary Intersection
- [ ] Demand point inside Town boundary → gets province, city, district
- [ ] Multiple boundaries → uses Town type boundary
- [ ] No boundary → fields remain _unset

### Test 8: Coordinate Conversion
- [ ] Local coordinates converted to WGS84 lat/long
- [ ] Formatted with 12 decimal precision
- [ ] Both latitude and longitude returned as strings

### Test 9: Customer Premise Creation
- [ ] Customer premise created with all fields
- [ ] Administrative data from boundary populated
- [ ] Splitter data populated correctly
- [ ] Lat/long coordinates populated
- [ ] Reference to demand_point set correctly

### Test 10: Building Creation
- [ ] Placemark with descriptio = "GEDUNG" → building created
- [ ] Bounding box 400m around location
- [ ] Placemark without "GEDUNG" → no building

## Benefits of Advanced Mode

1. **Complete Topology** - Creates demand point + customer premise + building
2. **Multi-Level Detection** - Checks folder patterns at 3 levels (flexible structure)
3. **Network Integration** - Links to micro cells and optical splitters
4. **Administrative Data** - Captures province, city, district from boundaries
5. **Coordinate Accuracy** - Converts to WGS84 lat/long for GIS integration
6. **Visual Labels** - Annotation geometry for map display
7. **Data Completeness** - Customer premise includes all required fields
8. **Building Support** - Special handling for building-type demand points
9. **Field Safety** - Truncates folders to prevent overflow

## Implementation Notes

1. **Collection References**: Add to init() method:
   ```magik
   .cs_col << .database.collections[:ftth!customer_premise]
   .pol_col << .database.collections[:pol_boundary]
   .bld_col << .database.collections[:building]
   ```

2. **Coordinate System**: WGS84 already available from .ace_view

3. **Current Year**: Use `date_time_now().year.write_string`

4. **Error Handling**: Wrap all operations in `_try/_when error` blocks

5. **Spatial Predicates**: Use `predicate.interacts()` for point-in-polygon tests

6. **Progress Logging**: Enhanced logging:
   ```
   ✓ Demand point created: DP001
   → Annotation created
   → Found micro cell, splitter: SPLIT-01
   → Found boundary: Kecamatan X, Desa Y
   ✓ Customer premise created with full data
   ✓ Building created (GEDUNG type)
   ```

7. **Statistics**: Track demand points and customer premises created

## References

- **Source Implementation:** `C:\Users\user\Downloads\cluster_astri (1).magik:1698-2096`
- **Current Implementation:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_migrator.magik:585-621`
- **Design Plan:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\DESIGN_MIGRATION_PLAN.md`
- **Pole Enhancement:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\POLE_MIGRATION_ENHANCEMENT_PLAN.md`
- **Aerial Route Enhancement:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\docs\AERIAL_ROUTE_MIGRATION_ENHANCEMENT_PLAN.md`

## Next Steps

1. Review and approve this plan
2. Implement Phase 1: Add helper methods (get_latlong, truncate_folders)
3. Update Phase 2: Add collection references in init()
4. Update Phase 3: Enhance is_demand_point?() with multi-level checking
5. Implement Phase 4: Enhance create_demand_point()
6. Test with sample KML data containing demand points
7. Verify micro cell detection and splitter lookup
8. Verify boundary intersection and administrative data
9. Verify customer premise creation with full attributes
10. Deploy to production

---
**Document Version:** 1.1
**Created:** 2025-11-02
**Last Updated:** 2025-11-02
**Status:** Ready for Review

## Revision History

**v1.1 (2025-11-02):**
- **CRITICAL:** Convert entire folder string to lowercase FIRST before parsing
- **Simplified patterns:** Reduced from 9+ variants to just 4 patterns
  - `"*homepass*"` - Covers all homepass variants
  - `"*hp*"` - Covers HP COVER, HP UNCOVER, hp, etc.
  - `"*reduce*"` - Covers all reduce variants
  - `"*customer*"` - Covers CUSTOMER and COSTUMER (typo)
- Updated is_demand_point?() implementation with 3-step approach
- Updated test cases to reflect simplified patterns
- Consistent with pole and aerial route enhancement approaches

**v1.0 (2025-11-02):**
- Initial document creation
- Complex pattern matching with multiple exact variants
