# Underground Route Migration Plan

## Overview

This document outlines the plan for implementing underground infrastructure migration based on topology detection. When the KML parser detects `:underground` topology, the migration system will create underground infrastructure objects instead of aerial objects.

## Topology-Based Migration Strategy

### Current Implementation (Aerial)
- **Topology**: `:aerial` or `:both`
- **Route Object**: `aerial_route`
- **Structure Object**: `pole`
- **Migrators**:
  - `migrate_aerial_routes()` - Creates aerial_route chains
  - `migrate_poles()` - Creates pole points

### New Implementation (Underground)
- **Topology**: `:underground`
- **Route Object**: `underground_route`
- **Structure Object**: `uub` (Underground Utility Box / Handhole)
- **Migrators** (to be created):
  - `migrate_underground_routes()` - Creates underground_route chains
  - `migrate_uubs()` - Creates uub (handhole) points

## Migration Flow Modification

**Key Concept:** All placemark migration happens in `astri_design_migrator.migrate_placemarks()`, which uses a **multi-pass iterative approach** to create Smallworld objects from KML placemarks in a specific order.

### Current Flow (Aerial Only - Multi-Pass)
```
astri_design_migrator.migrate_placemarks(placemarks):

  Pass 1: Create OLTs
    - Iterate all placemarks
    - Create OLT objects (needed for cable snapping)

  Pass 2: Create cables/aerial routes
    - Iterate all placemarks
    - Create aerial_route objects from cable placemarks

  Pass 3: Create poles
    - Iterate all placemarks
    - Create pole objects from pole/structure placemarks

  Pass 4: Create demand points, splices, zones
    - Iterate all placemarks
    - Create sheath_splice, figure_eight, demand_point objects

  Pass 4.5: Update FDT :tp field (cluster only)

  Pass 5: Create micro/macro cells
    - Iterate all placemarks
    - Create micro_cell objects from area placemarks

  Pass 6: Create risers
    - Iterate all placemarks
    - Create riser objects from riser placemarks
```

### New Flow (Underground Support - Multi-Pass with Topology Detection)
```
astri_design_migrator.migrate_placemarks(placemarks):

  Pass 1: Create OLTs (unchanged)
    - Iterate all placemarks
    - Create OLT objects

  Pass 2: Create cables and routes (MODIFIED - topology-based)
    - Iterate all placemarks
    - IF placemark is route:
        IF topology = :aerial OR :both
          → create_aerial_route(pm)
        ENDIF
        IF topology = :underground OR :both
          → create_underground_route(pm)    # NEW
        ENDIF

  Pass 3: Create structures (MODIFIED - topology-based)
    - Iterate all placemarks
    - IF placemark is structure:
        IF topology = :aerial OR :both
          → create_pole(pm)
        ENDIF
        IF topology = :underground OR :both
          → create_uub(pm)                   # NEW
        ENDIF

  Pass 4: Create demand points, splices, zones (unchanged)
    - Iterate all placemarks
    - Create sheath_splice, figure_eight, demand_point objects
    - Equipment placement uses topology to find nearest structure

  Pass 4.5: Update FDT :tp field (unchanged)

  Pass 5: Create micro/macro cells (unchanged)

  Pass 6: Create risers (unchanged)
```

**Key Insight:** Routes are created BEFORE structures in the existing implementation. This order must be maintained for underground migration.

## Object Field Mappings

### Underground Route Fields

#### Core Fields (Essential)
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `name` | String | Generated | Format: "UG_ROUTE_{line_type}_{sequence}" |
| `construction_status` | String | Fixed | "proposed" |
| `route` | Geometry | KML LineString | Cable coordinates |
| `line_type` | String | Placemark | "LINE A", "LINE B", etc. |
| `ring_name` | String | Placemark | FDT/Closure ring name |
| `uuid` | String | Generated | UUID for tracking |

#### Network Hierarchy Fields
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `olt` | String | Work Order | OLT name |
| `olt_code` | String | Work Order | OLT code |
| `feeder_code` | String | Work Order | Feeder code |
| `subfeeder_code` | String | Work Order | Subfeeder code |
| `cluster_code` | String | Work Order | Cluster code |
| `fttx_network_type` | String | Work Order | "feeder", "subfeeder", "cluster" |

#### Measurement Fields
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `calculated_length` | Float | Calculated | Auto-computed from geometry |
| `measured_length` | Float | Calculated | Same as calculated_length |
| `fiber_count` | Integer | Derived | From cable specification |

#### Physical Characteristics (Optional/Default)
| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `underground_route_type` | String | "duct" | Type of underground route |
| `diameter` | Float | _unset | Conduit diameter |
| `centre_point_depth` | Float | _unset | Depth below surface |
| `surface_material` | String | _unset | Surface type |
| `surrounding_material` | String | _unset | Backfill material |

#### Administrative Fields
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `folders` | String | Placemark | KML folder hierarchy |
| `region` | String | Work Order | Geographic region |
| `sub_region` | String | Work Order | Sub-region |
| `object_id` | String | Generated | Unique object identifier |

### UUB (Handhole) Fields

#### Core Fields (Essential)
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `type` | String | Fixed | "handhole" |
| `label` | String | Placemark name | Handhole identifier |
| `spec_id` | String | Fixed | "80X80X130" (default spec) |
| `construction_status` | String | Fixed | "proposed" |
| `location` | Geometry | KML Point | Handhole coordinates |
| `line_type` | String | Placemark | "LINE A", "LINE B", etc. |
| `ring_name` | String | Placemark | FDT/Closure ring name |
| `uuid` | String | Generated | UUID for tracking |

#### Network Hierarchy Fields
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `olt` | String | Work Order | OLT name |
| `olt_code` | String | Work Order | OLT code |
| `feeder_code` | String | Work Order | Feeder code |
| `subfeeder_code` | String | Work Order | Subfeeder code |
| `cluster_code` | String | Work Order | Cluster code |
| `fttx_network_type` | String | Work Order | "feeder", "subfeeder", "cluster" |

#### Physical Characteristics
| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `depth` | Float | 1.3 | Handhole depth (meters) |
| `width` | Float | 0.8 | Handhole width (meters) |
| `cubic_content` | Float | Calculated | Volume in cubic meters |

#### Administrative Fields
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `folders` | String | Placemark | KML folder hierarchy |
| `region` | String | Work Order | Geographic region |
| `sub_region` | String | Work Order | Sub-region |
| `object_id` | String | Generated | Unique object identifier |

## Implementation Plan

### Phase 1: Create Underground Route Method

#### File: `astri_design_migrator.magik`

**New Method to Add:**

**`create_underground_route(pm)`** - Called from Pass 2 for each route placemark when topology = :underground or :both

This method mirrors the existing `create_aerial_route(pm)` method but creates `underground_route` objects instead of `aerial_route` objects.

**Field Population Strategy:**
```magik
# Core identification
new_route.name << generate_route_name(line_type, sequence)
new_route.construction_status << "proposed"
new_route.route << merged_geometry

# Network hierarchy
new_route.olt << wo[:olt_name]
new_route.olt_code << wo[:olt_code]
new_route.feeder_code << wo[:feeder_code]
new_route.subfeeder_code << wo[:subfeeder_code]
new_route.cluster_code << wo[:cluster_code]
new_route.fttx_network_type << wo[:infrastructure_type]

# LINE and ring identification
new_route.line_type << placemark[:line_type]
new_route.ring_name << placemark[:ring_name]

# Measurements
new_route.calculated_length << geometry.line_length / 100.0  # Convert cm to m
new_route.measured_length << new_route.calculated_length

# Metadata
new_route.folders << placemark[:parent]
new_route.uuid << generate_uuid()
```

### Phase 2: Create UUB (Handhole) Method

#### File: `astri_design_migrator.magik`

**New Method to Add:**

**`create_uub(pm)`** - Called from Pass 3 for each structure placemark when topology = :underground or :both

This method mirrors the existing `create_pole(pm)` method but creates `uub` (underground utility box/handhole) objects instead of `pole` objects.

**Field Population Strategy:**
```magik
# Core identification
new_uub.type << "handhole"
new_uub.label << placemark[:name]
new_uub.spec_id << "80X80X130"
new_uub.construction_status << "proposed"
new_uub.location << coordinate_point

# Network hierarchy
new_uub.olt << wo[:olt_name]
new_uub.olt_code << wo[:olt_code]
new_uub.feeder_code << wo[:feeder_code]
new_uub.subfeeder_code << wo[:subfeeder_code]
new_uub.cluster_code << wo[:cluster_code]
new_uub.fttx_network_type << wo[:infrastructure_type]

# LINE and ring identification
new_uub.line_type << placemark[:line_type]
new_uub.ring_name << placemark[:ring_name]

# Physical characteristics
new_uub.depth << 1.3
new_uub.width << 0.8
new_uub.cubic_content << calculate_volume(depth, width)

# Metadata
new_uub.folders << placemark[:parent]
new_uub.uuid << generate_uuid()
```

### Phase 3: Modify Main Migration Flow

#### File: `astri_design_migrator.magik`

**Update `migrate_placemarks()` method - Pass 2 and Pass 3:**

The existing `migrate_placemarks()` method uses a multi-pass approach. We need to modify **Pass 2** (routes) and **Pass 3** (structures) to handle topology detection.

**PASS 2 MODIFICATION - Route Creation:**

```magik
# Second pass: Create cables/aerial routes
write("  Pass 2: Creating cables and routes...")
_for pm _over placemarks.fast_elements()
_loop
	# Skip boundary area
	_if _self.is_boundary_area?(pm)
	_then
		_continue
	_endif

	_local is_route << _self.is_route?(pm)
	_if is_route
	_then
		# TOPOLOGY-BASED ROUTING (MODIFIED)
		_local topology << .topology  # Get topology from migrator slot

		# Create aerial route if topology supports it
		_if topology = :aerial _orif topology = :both
		_then
			_self.create_aerial_route(pm)
		_endif

		# Create underground route if topology supports it
		_if topology = :underground _orif topology = :both
		_then
			_self.create_underground_route(pm)  # NEW METHOD
		_endif
	_endif
_endloop
```

**PASS 3 MODIFICATION - Structure Creation:**

```magik
# Third pass: Create structures (poles and/or UUBs)
write("  Pass 3: Creating structures...")
_for pm _over placemarks.fast_elements()
_loop
	# Skip boundary area
	_if _self.is_boundary_area?(pm)
	_then
		_continue
	_endif

	_local is_structure << _self.is_pole?(pm)  # Method should be renamed to is_structure?
	_if is_structure
	_then
		# TOPOLOGY-BASED STRUCTURE CREATION (MODIFIED)
		_local topology << .topology  # Get topology from migrator slot

		# Create pole if topology supports it
		_if topology = :aerial _orif topology = :both
		_then
			_self.create_pole(pm)
		_endif

		# Create UUB/handhole if topology supports it
		_if topology = :underground _orif topology = :both
		_then
			_self.create_uub(pm)  # NEW METHOD
		_endif
	_endif
_endloop
```

**TOPOLOGY SLOT:**

Add a topology slot to the migrator to track the detected topology:

```magik
def_slotted_exemplar(:astri_design_migrator,
	{
		{:scheme, _unset},
		{:topology, _unset},      # NEW: Track network topology
		# ... other existing slots
	},
	{})
$
```

**SET TOPOLOGY BEFORE MIGRATION:**

In the calling code (workorder dialog), set topology before calling `migrate_placemarks()`:

```magik
# Set topology in migrator before migration
migrator.topology << parsed_result[:topology]

# Run multi-pass migration
migrator.migrate_placemarks(placemarks)
```

### Phase 4: Update Equipment Migration

#### File: Equipment migrator files

**Modify equipment placement logic:**

```magik
_method astri_xxx_migrator.place_equipment_on_structure(equipment, placemark, topology, p_view)
	## Place equipment on appropriate structure based on topology

	_if topology = :aerial _orif topology = :both
	_then
		# Find nearest pole
		structure << _self.find_nearest_pole(placemark[:coord], p_view)

		_if structure _isnt _unset
		_then
			# Place on pole
			equipment.pole << structure
			_return
		_endif
	_endif

	_if topology = :underground _orif topology = :both
	_then
		# Find nearest UUB
		structure << _self.find_nearest_uub(placemark[:coord], p_view)

		_if structure _isnt _unset
		_then
			# Place in UUB
			equipment.uub << structure
			_return
		_endif
	_endif

	write("WARNING: No structure found for equipment:", placemark[:name])
_endmethod
```

## File Structure

### Files to Modify

**PRIMARY FILE:**
```
pni_custom/rwwi_astri_integration_java/magik/rwwi_astri_integration/source/
└── astri_design_migrator.magik
    ├── Add :topology slot to exemplar definition
    ├── Modify Pass 2 (create routes) - add topology checking
    ├── Modify Pass 3 (create structures) - add topology checking
    ├── Add new method: create_underground_route(pm)
    ├── Add new method: create_uub(pm)
    └── Update Pass 4 equipment placement to handle UUBs
```

**CALLING CODE:**
```
pni_custom/rwwi_astri_integration_java/magik/rwwi_astri_workorder/source/
└── rwwi_astri_workorder_dialog_migration.magik
    └── Set migrator.topology before calling migrate_placemarks()
```

## Testing Plan

### Test Cases

#### TC1: Pure Underground Network
- **Input**: KML with only handholes (topology = :underground)
- **Expected**:
  - No poles created
  - No aerial_routes created
  - UUBs created from handhole placemarks
  - underground_routes created from cable placemarks
  - Equipment placed in UUBs

#### TC2: Pure Aerial Network
- **Input**: KML with only poles (topology = :aerial)
- **Expected**:
  - Poles created
  - aerial_routes created
  - No UUBs created
  - No underground_routes created
  - Equipment placed on poles

#### TC3: Mixed Network
- **Input**: KML with both poles and handholes (topology = :both)
- **Expected**:
  - Both poles and UUBs created
  - Both aerial_routes and underground_routes created
  - Equipment placed on appropriate structures

### Validation Checks

1. **Geometry Validation**
   - Underground routes have valid line geometry
   - UUBs have valid point geometry
   - All coordinates are within expected bounds

2. **Field Population**
   - All mandatory fields are populated
   - Network hierarchy fields match work order
   - LINE and ring_name match placemark data

3. **Relationship Validation**
   - Equipment correctly associated with UUBs
   - Cables correctly routed through UUBs
   - Ring structure maintained

## Implementation Checklist

- [ ] Phase 1: Add underground route creation method
  - [ ] Add `create_underground_route(pm)` method to `astri_design_migrator.magik`
  - [ ] Implement field mapping (name, construction_status, route geometry, network hierarchy)
  - [ ] Add coordinate conversion and geometry handling
  - [ ] Add error handling and logging

- [ ] Phase 2: Add UUB/handhole creation method
  - [ ] Add `create_uub(pm)` method to `astri_design_migrator.magik`
  - [ ] Implement field mapping (type, label, spec_id, location, network hierarchy)
  - [ ] Set default physical characteristics (depth, width)
  - [ ] Add error handling and logging

- [ ] Phase 3: Modify multi-pass migration flow
  - [ ] Add `:topology` slot to astri_design_migrator exemplar definition
  - [ ] Modify Pass 2: Add topology check before route creation
    - [ ] Call `create_aerial_route()` if topology = :aerial or :both
    - [ ] Call `create_underground_route()` if topology = :underground or :both
  - [ ] Modify Pass 3: Add topology check before structure creation
    - [ ] Call `create_pole()` if topology = :aerial or :both
    - [ ] Call `create_uub()` if topology = :underground or :both
  - [ ] Update logging to show topology-based decisions

- [ ] Phase 4: Update calling code
  - [ ] Modify `rwwi_astri_workorder_dialog_migration.magik`
  - [ ] Set `migrator.topology` from parsed_result before migration
  - [ ] Verify topology value is propagated correctly

- [ ] Phase 5: Update equipment placement (Pass 4)
  - [ ] Update helper methods to find nearest UUB when topology = :underground
  - [ ] Modify equipment creation to associate with UUBs
  - [ ] Add validation for equipment-to-structure relationships

- [ ] Testing
  - [ ] Test TC1: Pure underground (topology = :underground)
  - [ ] Test TC2: Pure aerial (topology = :aerial)
  - [ ] Test TC3: Mixed network (topology = :both)
  - [ ] Validate all fields populated correctly
  - [ ] Validate equipment-to-structure relationships

## Notes

### Design Decisions

1. **Multi-Pass Migration**: The existing `astri_design_migrator.migrate_placemarks()` uses a multi-pass iterative approach where it loops through all placemarks multiple times, creating different object types in each pass. This approach must be maintained for underground support.

2. **Routes Before Structures**: The current implementation creates routes (Pass 2) BEFORE structures (Pass 3). This order is intentional and must be preserved:
   - Pass 2: Create aerial_route AND underground_route objects
   - Pass 3: Create pole AND uub objects

3. **In-Pass Topology Checking**: Instead of separating aerial and underground into different passes, we add topology checks WITHIN existing Pass 2 and Pass 3 to determine which object types to create for each placemark.

4. **Topology Storage**: The topology detected by `astri_kml_parser.parse()` is stored in a slot on the `astri_design_migrator` instance (`.topology`), making it accessible throughout all passes without passing it as a parameter.

5. **Reuse Existing Methods**: The underground route and UUB creation will be new methods that follow the same pattern as existing `create_aerial_route()` and `create_pole()`:
   - `create_underground_route(pm)` - Similar to create_aerial_route()
   - `create_uub(pm)` - Similar to create_pole()

6. **Equipment Placement**: Equipment creation (Pass 4) already uses helper methods to find nearest structures. These methods will be updated to check topology and search for appropriate structure types (poles or UUBs).

7. **Field Defaults**: Many optional fields will use sensible defaults:
   - UUB spec_id: "80X80X130" (standard handhole size)
   - UUB depth: 1.3m, width: 0.8m
   - underground_route type: "duct"

### Future Enhancements

1. **Hybrid Routes**: Support for routes that transition from aerial to underground
2. **Multiple Handhole Types**: Support different handhole specifications based on KML data
3. **Depth Profiles**: Extract depth information from KML extended data
4. **Material Specifications**: Map KML attributes to material fields

---

**Document Version**: 2.0
**Created**: 2026-02-16
**Last Updated**: 2026-02-17
**Author**: Claude Sonnet 4.5
**Change Log**:
- v2.0 (2026-02-17): **Major revision** - Aligned with actual `migrate_placemarks()` multi-pass implementation
  - Changed from batch topology-based approach to in-pass topology checking
  - Routes created in Pass 2, structures in Pass 3 (matches existing order)
  - Methods added to `astri_design_migrator.magik` instead of separate migrator files
  - Added `:topology` slot to migrator for cross-pass access
- v1.1 (2026-02-17): Clarified migration happens in `astri_design_migrator`
- v1.0 (2026-02-16): Initial version
