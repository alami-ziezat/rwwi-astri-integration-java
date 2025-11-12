# Figure Eight Migration Enhancement Plan

## Date: 2025-11-06
## Status: 📋 PLANNED

## Overview
This document outlines the enhancement plan to add **figure_eight (slack/coil)** migration functionality to the ASTRI design migrator. Figure eights are point-based placemarks representing cable slack storage or cable coils mounted on poles.

## Reference Implementation
Based on `cluster_figure_eight_migration_astri()` from:
**File:** `C:\Users\user\Downloads\cluster_astri (1).magik:1357-1545`

## Background

### What is a Figure Eight?
A figure eight (also called "slack" or "coil") is a cable storage device used to manage excess fiber optic cable. It's typically mounted on poles and connected to aerial routes.

### Current State
The astri_design_migrator currently does NOT handle figure eight objects. This enhancement will add complete figure_eight migration support.

## Folder Pattern Matching

### Identification Patterns
Figure eight placemarks are identified by folder names containing (case-insensitive):
- `*slack*`
- `*slack hanger*`
- `*coil*`

### Type Validation
- **Must be POINT type** (not line or area)
- **Must have valid location data**

## Migration Logic Flow

### Step 0: Validation
```magik
# Check if placemark is a figure_eight
_if _not _self.is_figure_eight?(pm)
_then
    write("  ⚠ Skipping - not a figure_eight: ", folders)
    _return
_endif
```

### Step 1: Parse Location
```magik
_local location << _self.parse_point_geometry(pm[:coord])
_local coord << location.coord

_local pp << pseudo_point.new(coord)
pp.world << .database.world
```

### Step 2: Find or Create Pole

#### Option A: Existing Pole Found (within 700m)
```magik
_local (h, pl_sl) << _self.scan_pole_st(pp, 700)

_if h _is _true
_then
    # Use existing pole
    write("    → Found existing pole within 700m")
_endif
```

#### Option B: No Pole Found
```magik
_else
    # Create placeholder pole
    write("    → Creating placeholder pole for slack")

    # Try to snap to aerial route
    _local (tf, r_pipe, coord) << _self.scan_ar_on_design(pp)

    _if tf _is _true
    _then
        sh_loc << # snapped location on aerial route
    _else
        sh_loc << location  # original location
    _endif

    # Create pole with type "T7"
    pl_sl << # created placeholder pole
_endif
```

### Step 3: Create Figure Eight
```magik
_local prop_values << property_list.new_with(
    :construction_status, "Proposed",
    :type, "Circle",
    :length, 20,
    :name, pm[:name],
    :folders, folders,
    :fttx_network_type, "Cluster",
    :segment, .segment_id,
    :pop, .pop_name,
    :olt, .pop_name,
    :project, .prj_id,
    :uuid, .uuid
)

_local rec_trans << record_transaction.new_insert(.fe_col, prop_values)
_local result << rec_trans.run()
```

### Step 4: Associate to Pole Structure
```magik
result.associate_to_structure(pl_sl)
write("    → Associated to pole: ", pl_sl.telco_pole_tag)
```

### Step 5: Connect to Cable
```magik
_local l_cables << pl_sl.cable_records.an_element()

_if l_cables _isnt _unset
_then
    l_cables.mcn!connect(result)
    write("    → Connected to cable")
_endif
```

## Required New Methods

### 1. is_figure_eight?(pm)
**Purpose:** Validate if placemark is a figure_eight based on folder pattern

**Logic:**
```magik
_local folders << pm[:parent].default("")
_local folders_lc << folders.lowercase

# Check POINT type
_if pm[:type] <> "point"
_then
    _return _false
_endif

# Check folder patterns
>> folders_lc.matches?("*slack*") _orif
   folders_lc.matches?("*slack hanger*") _orif
   folders_lc.matches?("*coil*")
```

### 2. scan_ar_on_design(location)
**Purpose:** Find nearest aerial route for snapping placeholder pole location

**Logic:**
```magik
_local buff << location.buffer(500)
_local pred << predicate.interacts(:route, {buff})
_local routes << .ar_col.select(pred)

_if routes.size > 0
_then
    _local ar << routes.an_element()
    _local pc << pseudo_chain.new(ar.route)
    _local snap_loc << pc.segpoint_location_near(location)
    _return _true, ar, snap_loc
_else
    _return _false, _unset, _unset
_endif
```

### 3. create_figure_eight(pm)
**Purpose:** Create figure_eight object with pole association and cable connection

**Complete implementation** (see Implementation Plan section below)

## Required Slot Addition

### Collection Reference
```magik
# In def_slotted_exemplar (around line 38)
{:fe_col,          _unset, :writable},   # figure_eight
```

### Initialization
```magik
# In init() method (around line 96)
.fe_col << .database.collections[:figure_eight]
```

### Statistics
```magik
# In init() method statistics (around line 111)
:figure_eights, 0,

# In print_statistics() method (around line 1710)
write("Figure Eights:      ", .stats[:figure_eights])

# Update total calculation (around line 1714)
_local total_created << ... + .stats[:figure_eights] + ...
```

## Migration Flow Integration

### Placement in migrate_placemarks()
Figure eight migration should occur **AFTER sheath splice migration** (Pass 3):

```magik
# Pass 3: Creating demand points, splices, and area-based objects...
_for pm _over placemarks.fast_elements()
_loop
    # ... existing code for sheath splice ...

    # Figure eights (point-based: slack, coil)
    _elif _self.is_figure_eight?(pm)
    _then
        _self.create_figure_eight(pm)
        placemarks.remove(pm)

    # ... rest of existing code ...
_endloop
```

## Implementation Plan

### Phase 1: Basic Structure (Lines to Add/Modify)

#### 1.1 Slot Addition (~1 line)
**Location:** Line 38 (after `:sc_col`)
```magik
{:fe_col,          _unset, :writable},   # figure_eight
```

#### 1.2 Collection Initialization (~1 line)
**Location:** Line 96 (after `.sc_col`)
```magik
.fe_col << .database.collections[:figure_eight]
```

#### 1.3 Statistics Initialization (~1 line)
**Location:** Line 111
```magik
:figure_eights, 0,
```

### Phase 2: Validation Method (~30 lines)

#### 2.1 is_figure_eight?(pm)
**Location:** After `is_splice?()` (around line 540)
**Estimated:** ~28 lines

### Phase 3: Helper Method (~40 lines)

#### 3.1 scan_ar_on_design(location)
**Location:** After `scan_pole_st()` (around line 712)
**Estimated:** ~38 lines

### Phase 4: Main Creation Method (~180 lines)

#### 4.1 create_figure_eight(pm)
**Location:** After `create_sheath_splice()` (around line 1590)
**Estimated:** ~175 lines

### Phase 5: Integration (~3 lines)

#### 5.1 migrate_placemarks() Update
**Location:** Line 360 (in Pass 3, after sheath splice)
**Estimated:** ~3 lines

#### 5.2 print_statistics() Update
**Location:** Lines 1710, 1714
**Estimated:** ~2 lines

## Total Implementation Estimate
- **New slots:** 1
- **New methods:** 3 (is_figure_eight, scan_ar_on_design, create_figure_eight)
- **Updated methods:** 3 (init, migrate_placemarks, print_statistics)
- **Total new lines:** ~240 lines
- **Total modified lines:** ~6 lines

## Testing Requirements

### Test Scenarios

#### 1. Folder Pattern Validation
- [ ] Folder "Project|SLACK|Item" + point type → processed
- [ ] Folder "Line A|Slack Hanger" + point type → processed
- [ ] Folder "Segment|Coil" + point type → processed
- [ ] Folder "Project|SLACK" + area type → skipped (not point)
- [ ] Folder without slack keywords → skipped

#### 2. Pole Association
- [ ] Figure eight near pole (< 700m) → uses existing pole
- [ ] Figure eight near pole → associated to structure
- [ ] Figure eight far from pole (> 700m) → creates placeholder

#### 3. Placeholder Pole Creation
- [ ] Placeholder pole near aerial route → snapped to route
- [ ] Placeholder pole far from aerial route → uses original location
- [ ] Placeholder pole type = "T7"
- [ ] Placeholder pole tag = "Existing Pole Slack"

#### 4. Figure Eight Creation
- [ ] Basic figure_eight created with all required fields
- [ ] type = "Circle", length = 20
- [ ] construction_status = "Proposed"
- [ ] Associated to pole structure
- [ ] Connected to cable if available

#### 5. Cable Connection
- [ ] If pole has cable → figure_eight connected to cable
- [ ] If pole has no cable → no connection (no error)

## Performance Considerations

### Spatial Queries
- **Pole search:** 1 query per figure_eight (700m buffer)
- **Aerial route search:** 1 query per placeholder pole (500m buffer)

### Optimization Notes
- Queries use spatial predicates (optimized)
- Buffer sizes reasonable (500m, 700m)
- Early returns prevent unnecessary processing

## Migration Statistics

The enhanced implementation will increment statistics:
```magik
.stats[:figure_eights] +<< 1      # On figure_eight create
.stats[:poles] +<< 1               # When placeholder pole created
.stats[:errors] +<< 1              # On error
```

## Example Output

```
Starting design migration of 250 placemarks...
  Pass 3: Creating demand points, splices, and area-based objects...
point
  Processing figure_eight: SLACK-001
    → Found existing pole within 700m
    ✓ Figure eight created: SLACK-001 (Type: Circle, Length: 20)
    → Associated to pole: P001
    → Connected to cable
point
  Processing figure_eight: Coil-A
    → Creating placeholder pole for slack
    → Pole snapped to aerial route
    ✓ Placeholder pole created: Existing Pole Slack
    ✓ Figure eight created: Coil-A (Type: Circle, Length: 20)
    → Associated to pole: Existing Pole Slack
  ...

============================================================
Design Migration Statistics
============================================================
Aerial Routes:      50
Poles:              120
Sheaths:            0
Sheath Splices:     25
Optical Splitters:  180
Sling Wires:        0
Demand Points:      200
Customer Premises:  200
Buildings:          10
Micro Cells:        8
Figure Eights:      15
Other Areas:        0
Errors:             0
Skipped:            0

Total objects:      808
============================================================
```

## Key Design Decisions

### 1. Point Type Only
- Figure eights are exclusively point-based objects
- Area or line types are rejected during validation

### 2. Pole Search Radius
- **700m** search radius (from reference implementation)
- Larger than sheath splice (800m) to ensure pole association

### 3. Placeholder Pole Strategy
- Creates placeholder with tag "Existing Pole Slack"
- Type "T7" (standard placeholder type)
- Attempts to snap to nearest aerial route within 500m

### 4. Cable Connection
- Only connects if pole has cable records
- Uses `mcn!connect()` method
- Non-blocking (continues if cable not found)

### 5. Figure Eight Attributes
- **type:** "Circle" (fixed value from reference)
- **length:** 20 (fixed value from reference)
- **construction_status:** "Proposed" (consistent with other objects)

## Related Documentation

- **Pole Migration:** `POLE_MIGRATION_IMPLEMENTATION_SUMMARY.md`
- **Sheath Splice Migration:** `SHEATH_SPLICE_MIGRATION_IMPLEMENTATION_SUMMARY.md`
- **Reference Implementation:** `C:\Users\user\Downloads\cluster_astri (1).magik:1357-1545`
- **Design Migration Plan:** `DESIGN_MIGRATION_PLAN.md`

## Next Steps

1. ✅ Create enhancement plan document (this file)
2. ⏳ Create implementation summary document template
3. ⏳ Implement is_figure_eight?() method
4. ⏳ Implement scan_ar_on_design() method
5. ⏳ Implement create_figure_eight() method
6. ⏳ Update migrate_placemarks() integration
7. ⏳ Update statistics initialization and reporting
8. ⏳ Test with sample work order containing figure_eight KML data
9. ⏳ Verify folder pattern detection
10. ⏳ Verify pole association (existing and placeholder)
11. ⏳ Verify aerial route snapping
12. ⏳ Verify cable connection
13. ⏳ Deploy to production

## Notes

- **Point type only** - is_figure_eight?() checks for POINT type (not area/line)
- **Lowercase first** - All pattern matching converts to lowercase FIRST
- **Wildcard patterns** - Uses `*pattern*` for flexible matching
- **Pole search radius** - 700m (larger than sheath splice 800m)
- **Aerial route snap** - 500m search radius for placeholder poles
- **Cable connection** - Non-blocking, uses mcn!connect() method
- **Error handling** - All operations wrapped in _try/_when blocks
- **Migration order** - AFTER sheath splice migration (Pass 3)

---
**Enhancement plan created on 2025-11-06**
