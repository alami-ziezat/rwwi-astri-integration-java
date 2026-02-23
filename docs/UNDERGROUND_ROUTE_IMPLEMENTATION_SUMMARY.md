# Underground Route Migration Implementation Summary

## Overview

This document summarizes the implementation of underground infrastructure support for the ASTRI KML migration system. The implementation allows the system to create underground routes and UUBs (underground utility boxes/handholes) based on topology detection from KML files.

## Date Implemented

**2026-02-17**

## Files Created

### 1. `astri_underground_route_migrator.magik`
**Location:** `pni_custom/rwwi_astri_integration_java/magik/rwwi_astri_integration/source/`

**Purpose:** Adds `create_underground_route(pm)` method to `astri_design_migrator`

**Key Features:**
- Creates `underground_route` objects from cable placemarks
- Validates cable/route keywords (KABEL, CABLE, FO, ROUTE, DUCT)
- Parses route geometry and validates minimum length (0.4m)
- Detects fiber count from placemark name
- Populates network hierarchy fields (cluster_code, subfeeder_code, feeder_code, olt_code)
- Sets default underground_route_type to "duct"
- Updates statistics counter for underground_routes

**Fields Populated:**
- Core: name, construction_status, route geometry, fiber_count
- Network: pop, olt, region, cluster, fttx_network_type, line_type, ring_name, segment
- Hierarchy: cluster_code, subfeeder_code, feeder_code, olt_code
- Metadata: folders, uuid
- Type: underground_route_type = "duct"

### 2. `astri_uub_migrator.magik`
**Location:** `pni_custom/rwwi_astri_integration_java/magik/rwwi_astri_integration/source/`

**Purpose:** Adds `create_uub(pm)` method to `astri_design_migrator`

**Key Features:**
- Creates `uub` (underground utility box/handhole) objects from structure placemarks
- Parses point geometry from placemark coordinates
- Performs cable snapping to underground routes (500cm/5m radius)
- Checks for duplicate UUBs within 2m radius
- Sets default handhole spec (80X80X130 = 80cm x 80cm x 130cm)
- Sets default physical characteristics (depth: 1.3m, width: 0.8m)
- Updates statistics counter for uubs

**Fields Populated:**
- Core: type = "handhole", label, spec_id, construction_status, location
- Network: pop, olt, region, cluster, fttx_network_type, line_type, ring_name, segment
- Hierarchy: cluster_code, subfeeder_code, feeder_code, olt_code
- Physical: depth = 1.3, width = 0.8
- Metadata: folders, uuid

## Files Modified

### 1. `astri_design_migrator.magik`

#### Exemplar Definition (Lines 26-71)
**Added Slots:**
```magik
{:ug_route_col,    _unset, :writable},   # underground_route collection
{:uub_col,         _unset, :writable},   # uub collection
{:topology,        _unset, :writable},   # Network topology (:aerial, :underground, :both)
```

#### Collection Initialization (Lines 140-155)
**Added:**
```magik
.ug_route_col << .database.collections[:underground_route]
.uub_col << .database.collections[:uub]
```

#### Pass 2: Route Creation (Lines 456-484)
**Modified from:**
```magik
write("  Pass 2: Creating cables and aerial routes...")
_if is_route
_then
    _self.create_aerial_route(pm)
_endif
```

**Modified to:**
```magik
write("  Pass 2: Creating cables and routes (topology: ", .topology.default(:aerial), ")...")
_if is_route
_then
    # TOPOLOGY-BASED ROUTING
    _local topology << .topology.default(:aerial)

    # Create aerial route if topology supports it
    _if topology = :aerial _orif topology = :both
    _then
        _self.create_aerial_route(pm)
    _endif

    # Create underground route if topology supports it
    _if topology = :underground _orif topology = :both
    _then
        _self.create_underground_route(pm)
    _endif
_endif
```

#### Pass 3: Structure Creation (Lines 485-507)
**Modified from:**
```magik
write("  Pass 3: Creating poles...")
_if is_pole
_then
    _self.create_pole(pm)
_endif
```

**Modified to:**
```magik
write("  Pass 3: Creating structures (poles and/or UUBs based on topology)...")
_if is_structure
_then
    # TOPOLOGY-BASED STRUCTURE CREATION
    _local topology << .topology.default(:aerial)

    # Create pole if topology supports it
    _if topology = :aerial _orif topology = :both
    _then
        _self.create_pole(pm)
    _endif

    # Create UUB/handhole if topology supports it
    _if topology = :underground _orif topology = :both
    _then
        _self.create_uub(pm)
    _endif
_endif
```

### 2. `rwwi_astri_workorder_dialog_migration.magik`

#### First Migration Path (New Design, Line 145)
**Added before `create_project_and_design()`:**
```magik
# Set topology on migrator (from KML parser)
migrator.topology << topology
write("Topology set on migrator: ", topology)
```

#### Second Migration Path (Existing Alternative, Line 398)
**Added before `migrate_placemarks()`:**
```magik
# Set topology on migrator (from KML parser)
migrator.topology << topology
write("Topology set on migrator: ", topology)
```

#### Statistics Display (Line 184)
**Added:**
```magik
_self.log_info("  Network Topology:   " + topology.write_string)
_self.log_info("  Aerial Routes:      " + stats[:aerial_routes].write_string)
_self.log_info("  Underground Routes: " + stats[:underground_routes].default(0).write_string)
_self.log_info("  UUBs/Handholes:     " + stats[:uubs].default(0).write_string)
```

### 3. `load_list.txt`
**Added:**
```
astri_uub_migrator
astri_underground_route_migrator
```
(After `astri_aerial_route_migrator`, before `astri_demand_point_migrator`)

## How It Works

### Migration Flow

1. **KML Parsing** (`astri_kml_parser.parse()`)
   - Returns `(placemarks, topology)`
   - Topology values: `:aerial`, `:underground`, or `:both`

2. **Migrator Initialization** (`astri_design_migrator.new()`)
   - Creates migrator instance
   - Initializes collection references including `ug_route_col` and `uub_col`

3. **Topology Assignment**
   - `migrator.topology << topology`
   - Makes topology available across all migration passes

4. **Multi-Pass Migration** (`migrate_placemarks()`)
   - **Pass 1:** Create OLTs (unchanged)
   - **Pass 2:** Create routes (MODIFIED)
     - IF topology = :aerial OR :both → create_aerial_route()
     - IF topology = :underground OR :both → create_underground_route()
   - **Pass 3:** Create structures (MODIFIED)
     - IF topology = :aerial OR :both → create_pole()
     - IF topology = :underground OR :both → create_uub()
   - **Pass 4+:** Create equipment, cells, risers (unchanged)

5. **Statistics Collection**
   - Counters updated: `underground_routes`, `uubs`
   - Displayed in migration results

## Topology Handling

### Three Topology Modes

#### 1. Aerial Only (`:aerial`)
- Creates: aerial_routes, poles
- Skips: underground_routes, uubs

#### 2. Underground Only (`:underground`)
- Creates: underground_routes, uubs
- Skips: aerial_routes, poles

#### 3. Mixed/Both (`:both`)
- Creates: aerial_routes, poles, underground_routes, uubs
- All infrastructure types created

## Field Mappings

### Underground Route Fields

| Field | Value | Source |
|-------|-------|--------|
| construction_status | "Proposed" or "In Service" | Migrator context |
| name | Placemark name | KML |
| pop | POP name | Work order |
| olt | POP name | Work order |
| region | Region | Work order |
| cluster | Infrastructure name | Work order |
| fiber_count | Detected from name | Parsed (default: 24) |
| fttx_network_type | cluster/subfeeder/feeder | Work order |
| segment | Segment ID | Parsed from folders |
| ring_name | Ring name | KML placemark |
| line_type | LINE A/B/C/etc. | Parsed from folders |
| folders | Folder hierarchy | KML |
| route | Geometry | KML coordinates |
| uuid | KMZ UUID | Work order |
| cluster_code | Cluster code | Database lookup |
| subfeeder_code | Subfeeder code | Database lookup |
| feeder_code | Feeder code | Database lookup |
| olt_code | OLT code | Database lookup |
| underground_route_type | "duct" | Default value |

### UUB (Handhole) Fields

| Field | Value | Source |
|-------|-------|--------|
| type | "handhole" | Fixed |
| label | Placemark name | KML |
| spec_id | "80X80X130" | Default (80cm x 80cm x 130cm) |
| construction_status | "Proposed" or "In Service" | Migrator context |
| location | Point geometry | KML coordinates (snapped if route nearby) |
| pop | POP name | Work order |
| olt | POP name | Work order |
| region | Region | Work order |
| cluster | Infrastructure name | Work order |
| line_type | LINE A/B/C/etc. | Parsed from folders |
| ring_name | Ring name | KML placemark |
| fttx_network_type | cluster/subfeeder/feeder | Work order |
| segment | Segment ID | Parsed from folders |
| folders | Folder hierarchy | KML |
| uuid | KMZ UUID | Work order |
| cluster_code | Cluster code | Database lookup |
| subfeeder_code | Subfeeder code | Database lookup |
| feeder_code | Feeder code | Database lookup |
| olt_code | OLT code | Database lookup |
| depth | 1.3 | Default (meters) |
| width | 0.8 | Default (meters) |

## Testing Scenarios

### TC1: Pure Underground Network
**Input:** KML with only handholes (topology = :underground)
**Expected:**
- ✓ Underground routes created from cable placemarks
- ✓ UUBs created from structure placemarks
- ✓ No aerial routes created
- ✓ No poles created

### TC2: Pure Aerial Network
**Input:** KML with only poles (topology = :aerial)
**Expected:**
- ✓ Aerial routes created from cable placemarks
- ✓ Poles created from structure placemarks
- ✓ No underground routes created
- ✓ No UUBs created

### TC3: Mixed Network
**Input:** KML with both poles and handholes (topology = :both)
**Expected:**
- ✓ Both aerial routes AND underground routes created
- ✓ Both poles AND UUBs created
- ✓ Statistics show counts for all infrastructure types

## Implementation Notes

### Design Decisions

1. **Multi-Pass Approach Maintained**
   - Routes created before structures (existing pattern preserved)
   - Topology checking happens within each pass, not separate passes

2. **In-Pass Topology Checking**
   - Each pass checks `.topology` to determine which objects to create
   - Allows for flexible :both mode where all infrastructure types are created

3. **Code Reuse**
   - Underground migrators follow same pattern as aerial migrators
   - Reuses existing helper methods: `match_cable_core()`, `match_line()`, `match_segment()`, `parse_line_geometry()`, `parse_point_geometry()`

4. **Default Values**
   - UUB spec: "80X80X130" (standard handhole)
   - UUB depth: 1.3m, width: 0.8m
   - Underground route type: "duct"
   - Fiber count: 24 cores (if not detected)
   - Topology: :aerial (if not set)

5. **Cable Snapping**
   - UUBs snap to underground routes within 5m radius
   - Uses same snapping logic as poles to aerial routes

6. **Duplicate Detection**
   - UUBs checked for duplicates within 2m radius
   - Prevents creating multiple UUBs at same location

## Future Enhancements

1. **Variable Handhole Specs**
   - Extract handhole dimensions from KML extended data
   - Support multiple spec types based on placemark attributes

2. **Equipment Placement**
   - Update equipment migrators to place equipment in UUBs
   - Modify `create_sheath_splice()`, `create_figure_eight()`, etc. to check topology

3. **Hybrid Routes**
   - Support routes that transition from aerial to underground
   - Detect transition points and create appropriate connections

4. **Depth Profiles**
   - Extract depth information from KML for underground routes
   - Set depth fields based on KML data instead of defaults

5. **Material Specifications**
   - Map KML attributes to material fields (duct type, surface material)

## Validation Checklist

- [x] Topology slot added to astri_design_migrator
- [x] Collection references added (ug_route_col, uub_col)
- [x] Create_underground_route() method implemented
- [x] Create_uub() method implemented
- [x] Pass 2 modified for topology-based route creation
- [x] Pass 3 modified for topology-based structure creation
- [x] Topology set before migrate_placemarks() calls
- [x] Statistics updated to include underground infrastructure
- [x] Load_list.txt updated with new migrator files
- [ ] Test with underground KML file
- [ ] Test with mixed topology KML file
- [ ] Verify field population in database
- [ ] Verify equipment placement with underground topology

---

**Implementation Status:** ✅ COMPLETE (Code Ready for Testing)
**Next Steps:** Test with actual KML files containing underground infrastructure
**Document Version:** 1.0
**Date:** 2026-02-17
**Author:** Claude Sonnet 4.5
