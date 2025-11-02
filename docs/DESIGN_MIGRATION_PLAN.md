# Design Migration Plan - KML to Real Smallworld Objects

## Overview

Plan to implement `migrate_to_design()` functionality that creates real Smallworld network objects directly from KML parse results within a new Smallworld project and design, bypassing the temporary table step.

## Planning Date
2025-10-31

## Architecture Approach

**Single Unified Class:** `astri_design_migrator` handles both:
1. Project and design creation
2. Object migration from KML to design alternative

This approach eliminates the need for a separate creator class and provides a clean API for the work order dialog.

## Quick API Reference

### Creating and Using the Migrator

```magik
# Create migrator instance
_local migrator << astri_design_migrator.new(
    database,     # GIS database
    prj_id,       # Project ID from work order uuid field
    segment_id,   # Segment ID from work order kmz_uuid field
    pop_name      # POP name from work order
)

# Create project and design (activates design alternative)
# Uses boundary area placemark (parent = null/empty) for project extent
_local (project, scheme) << migrator.create_project_and_design(
    placemarks,        # All placemarks (boundary extracted automatically)
    wo_number,         # WO number -> project name
    cluster_code,      # Cluster code -> project title
    cluster_name       # Cluster name -> design name
)

# Migrate placemarks to design (boundary area automatically excluded)
_local stats << migrator.migrate_placemarks(placemarks)

# Results
write("Project: ", project.name, " (", project.title, ")")
write("Design: ", scheme.name)
write("Poles created: ", stats[:poles])
write("Aerial routes: ", stats[:aerial_routes])
```

### Class Structure Summary

```magik
astri_design_migrator
├── Project/Design Management
│   ├── create_project_and_design()      # Creates and activates design
│   ├── extract_boundary_area()          # Finds area with parent=null/empty
│   ├── is_boundary_area?()              # Checks if placemark is boundary
│   └── migrate_placemarks()             # Migrates objects (excludes boundary)
├── Geometry Parsing
│   ├── parse_point_geometry()
│   ├── parse_line_geometry()
│   └── parse_area_geometry()
├── Object Type Detection
│   ├── is_pole?()
│   ├── is_cable?()
│   ├── is_demand_point?()
│   ├── is_splice?()
│   └── is_micro_cell?()
├── Object Creation
│   ├── create_pole()
│   ├── create_aerial_route()
│   ├── create_demand_point()
│   ├── create_sheath()                    # Area with parent (splice/closure)
│   ├── create_micro_cell()                # Area with parent (zone/cell)
│   └── create_area_based_object()         # Generic area with parent
└── Helper Methods
    ├── Pattern matching (pole type, cable core, STF codes)
    └── Snapping and proximity detection
```

## Project and Design Naming Requirements

### From Work Order
The following fields are extracted from the work order and used for project/design creation:

| Work Order Field | Usage | Description |
|-----------------|-------|-------------|
| `wo_number` | Project Name | Work order number identifies the project |
| `cluster_code` | Project Title | Cluster code describes the project |
| `cluster_name` | Design Name | Cluster name identifies the design/scheme |

### From Work Order (Object Tagging)
The following fields are used for tagging all created objects:

| Work Order Field | Usage | Description |
|-----------------|-------|-------------|
| `uuid` | `prj_id` | Project ID field on all created objects |
| `kmz_uuid` | `segment_id` | Segment ID field on all created objects |
| `kmz_uuid` | `uuid` field | UUID field on all created objects |

**Important:**
- `prj_id` comes from work order's `uuid` field
- `segment_id` comes from work order's `kmz_uuid` field
- All objects' `uuid` field also uses work order's `kmz_uuid`

### Example
```magik
# Work order contains:
wo[:wo_number] = "WO-2024-001"
wo[:cluster_code] = "CLUSTER_JAKARTA_SOUTH"
wo[:cluster_name] = "Design_Phase1"
wo[:uuid] = "7a3f2c1b-8e9d-4f5a-b6c7-123456789abc"        # Project UUID
wo[:kmz_uuid] = "550e8400-e29b-41d4-a716-446655440000"    # KMZ UUID

# Results in:
Project Name: "WO-2024-001"
Project Title: "CLUSTER_JAKARTA_SOUTH"
Design Name: "Design_Phase1"

# All created objects will have:
obj.prj_id = "7a3f2c1b-8e9d-4f5a-b6c7-123456789abc"       # From wo[:uuid]
obj.segment_id = "550e8400-e29b-41d4-a716-446655440000"   # From wo[:kmz_uuid]
obj.uuid = "550e8400-e29b-41d4-a716-446655440000"         # From wo[:kmz_uuid]
```

## Boundary Area Requirements

### Definition
The **boundary area** is a special placemark that defines the geographic extent of the project and design:

- **Type:** Must be `"area"` (polygon/area placemark)
- **Parent:** Must be `null`, `_unset`, or empty string (`""`) - NO folder hierarchy
- **Purpose:** Used to set project and design boundary, NOT migrated as an object

### Critical Distinction: Boundary vs Regular Areas

#### Boundary Area (NOT Migrated)
```magik
# Boundary area - EXCLUDED from migration:
Placemark: name="Project Boundary", type="area", parent=_unset, coord="..."
Placemark: name="Project Boundary", type="area", parent="", coord="..."
Placemark: name="Project Boundary", type="area", parent=null, coord="..."

# Result:
✓ Used for project/design extent
✗ NOT migrated to database
✗ NOT created as object
```

#### Regular Areas (Migrated Normally)
```magik
# Regular areas - INCLUDED in migration:
Placemark: name="Zone A", type="area", parent="Infrastructure|Zones", coord="..."
Placemark: name="Coverage", type="area", parent="Network|Coverage Areas", coord="..."
Placemark: name="Splice", type="area", parent="Infrastructure|Splices", coord="..."

# Result:
✗ NOT used for project extent
✓ Migrated to database according to cluster_astri (1).magik logic
✓ Created as objects (sheath_splice, micro_cell, etc.)
```

### Behavior

#### With Boundary Area Present
```magik
# KML contains:
Placemark: name="Project Boundary", type="area", parent="", coord="..."
Placemark: name="Zone A", type="area", parent="Infrastructure|Zones", coord="..."
Placemark: name="Splice 1", type="area", parent="Infrastructure|Splices", coord="..."

# Result:
1. "Project Boundary" (parent="") → Used for project extent, NOT migrated
2. "Zone A" (parent="Infrastructure|Zones") → Migrated as micro_cell object
3. "Splice 1" (parent="Infrastructure|Splices") → Migrated as sheath_splice object
```

#### Without Boundary Area
```magik
# No placemark with type="area" and parent=null/empty

# Result:
1. Warning message: "No boundary area found"
2. Fallback to database world bounds for project extent
3. Migration continues successfully
4. ALL areas WITH parent are migrated normally (zones, splices, etc.)
```

### Identification Logic
```magik
_private _method astri_design_migrator.is_boundary_area?(pm)
    ## Only areas with NO parent are boundaries
    ## All areas WITH parent are migrated normally

    _local parent << pm[:parent]
    _local type << pm[:type]

    # Boundary: type=area AND parent is null/unset/empty
    >> type = "area" _andif (parent _is _unset _orif parent = "")
_endmethod
```

### Migration Behavior
```magik
# In migrate_placemarks():

_for pm _over placemarks.fast_elements()
_loop
    # Skip ONLY boundary area (parent=null/empty)
    _if _self.is_boundary_area?(pm)
    _then
        _continue  # Skip this one
    _endif

    # All other areas WITH parent folders ARE migrated:
    _if pm[:type] = "area" _andif pm[:parent] _isnt _unset _andif pm[:parent] <> ""
    _then
        # Migrate according to cluster_astri (1).magik logic
        # Examples: sheath_splice, micro_cell, coverage zones, etc.
        _self.create_area_object(pm)  # Based on parent folder pattern
    _endif
_endloop
```

### Summary
- **ONLY** areas with `parent=null/empty/unset` are boundary areas (excluded)
- **ALL** areas with parent folder paths are regular areas (migrated)
- Regular areas are migrated according to `cluster_astri (1).magik` reference logic

## Source Analysis

**Reference Script:** `C:\Users\user\Downloads\cluster_astri (1).magik`

This script currently:
1. Reads from temporary collections (`rw_point`, `rw_line`, `rw_area`)
2. Filters by UUID
3. Processes based on folder hierarchy
4. Creates real SW objects (poles, aerial routes, sheaths, demand points, etc.)

## Goal

Adapt the migration logic to work directly with `astri_kml_parser` output (property_list rope) instead of temporary database records.

## Input Source Comparison

### Current Approach (Temporary Table)
```magik
# Read from temporary collection
pred_astri << predicate.eq(:uuid, uuid)
shp_col << s_col.select(pred_astri)

_for a_shp _over shp_col.fast_elements()
_loop
	# Access fields directly from record
	location << a_shp.location
	name << a_shp.name
	folders << a_shp.folders
	route << a_shp.route
	area << a_shp.area
_endloop
```

### New Approach (KML Parse Results)
```magik
# Placemarks already filtered by UUID during parsing
_for pm _over placemarks.fast_elements()
_loop
	# Extract from property_list
	type << pm[:type]          # "point", "line", "area"
	name << pm[:name]
	parent << pm[:parent]      # Folder hierarchy
	coord << pm[:coord]        # Coordinate string
	extended << pm[:extended]  # Extended data attributes

	# Need to parse coordinates and create geometry
	_if type = "point"
	_then
		location << _self.parse_point_geometry(pm[:coord])
	_elif type = "line"
	_then
		route << _self.parse_line_geometry(pm[:coord])
	_elif type = "area"
	_then
		area << _self.parse_area_geometry(pm[:coord])
	_endif
_endloop
```

## Key Differences

### 1. Geometry Creation

**Temporary Table:** Geometry already exists as SW objects
```magik
location << a_shp.location    # Already a pseudo_point
route << a_shp.route          # Already a sector_rope
area << a_shp.area            # Already a pseudo_area
```

**KML Parse:** Need to create geometry from coordinate strings
```magik
# Point: "lon,lat,elevation"
coord_parts << pm[:coord].split_by(",")
lon << coord_parts[1].as_number()
lat << coord_parts[2].as_number()
wgs84_coord << coordinate.new(lon, lat)
local_coord << transform.convert(wgs84_coord)
location << pseudo_point.new(local_coord)

# Line: "lon1,lat1,0 lon2,lat2,0 ..."
coord_pairs << pm[:coord].split_by(",0")
sector << sector.new()
_for coord_pair _over coord_pairs.fast_elements()
_loop
	# Parse and transform each coordinate
	# Add to sector
_endloop
route << sector.as_sector_rope()

# Area: Same as line but create pseudo_area
```

### 2. Folder Hierarchy

**Temporary Table:** Single string field
```magik
folders << a_shp.folders  # "Infrastructure|POLE|NEW POLE 7M4"
fol << folders.split_by("|")
```

**KML Parse:** Already in `:parent` field
```magik
parent << pm[:parent]     # Already contains folder path
fol << parent.split_by("|")
```

### 3. Extended Data

**Temporary Table:** Not typically stored

**KML Parse:** Available in `:extended` property_list
```magik
extended << pm[:extended]
pole_height << extended[:pole_height]
cable_core << extended[:cable_core]
status << extended[:status]
```

## Migration Class Architecture

### Create New Class: `astri_design_migrator`

**Purpose:** Convert parsed KML placemarks to real Smallworld design objects

**Structure:**
```magik
def_slotted_exemplar(:astri_design_migrator,
{
	{:database,        _unset, :writable},   # GIS database
	{:ace_view,        _unset, :writable},   # For coordinate system
	{:transform,       _unset, :writable},   # WGS84 → local CS transform
	{:uuid,            _unset, :writable},   # KMZ UUID

	# Design management
	{:project,         _unset, :writable},   # swg_dsn_project
	{:scheme,          _unset, :writable},   # swg_dsn_scheme (design)
	{:design_manager,  _unset, :writable},   # swg_dsn_design_manager

	# Collection references
	{:ar_col,          _unset, :writable},   # aerial_route
	{:pole_col,        _unset, :writable},   # pole
	{:sheath_col,      _unset, :writable},   # sheath_with_loc
	{:sw_col,          _unset, :writable},   # sling_wire
	{:dp_col,          _unset, :writable},   # ftth!demand_point
	{:os_col,          _unset, :writable},   # optical_splitter
	{:cell_col,        _unset, :writable},   # ftth!zone

	# Migration context
	{:pop_name,        _unset, :writable},   # POP name
	{:prj_id,          _unset, :writable},   # Project ID (from wo[:uuid])
	{:segment_id,      _unset, :writable},   # Segment ID (from wo[:kmz_uuid])

	# Statistics
	{:stats,           _unset, :writable}    # Migration results
})
```

### Methods Required

#### 1. Constructor and Initialization
```magik
_method astri_design_migrator.new(database, prj_id, segment_id, pop_name)
	## Create new design migrator
	##
	## Parameters:
	##   database - GIS database (e.g., gis_program_manager.databases[:gis])
	##   prj_id - Project ID from work order uuid field
	##   segment_id - Segment ID from work order kmz_uuid field
	##   pop_name - POP name from work order
	##
	## Returns: New astri_design_migrator instance

	>> _clone.init(database, prj_id, segment_id, pop_name)
_endmethod

_private _method astri_design_migrator.init(database, prj_id, segment_id, pop_name)
	## Initialize migrator

	.database << database
	.uuid << segment_id     # uuid field = kmz_uuid
	.pop_name << pop_name
	.prj_id << prj_id       # From wo[:uuid]
	.segment_id << segment_id  # From wo[:kmz_uuid]
	.ace_view << gis_program_manager.ace_view

	# Get design manager
	.design_manager << gis_program_manager.cached_dataset(:design).actual_dataset

	# Setup coordinate system transformation
	_local cs_local << .database.world.coordinate_system
	_local cs_wgs84 << .ace_view.collections[:sw_gis!coordinate_system].at(:world_longlat_wgs84_degree)
	.transform << transform.new_converting_cs_to_cs(cs_wgs84, cs_local)

	# Setup collection references
	.ar_col << .database.collections[:aerial_route]
	.pole_col << .database.collections[:pole]
	.sheath_col << .database.collections[:sheath_with_loc]
	.sw_col << .database.collections[:sling_wire]
	.dp_col << .database.collections[:ftth!demand_point]
	.os_col << .database.collections[:optical_splitter]
	.cell_col << .database.collections[:ftth!zone]

	# Initialize statistics
	.stats << property_list.new_with(
		:aerial_routes, 0,
		:poles, 0,
		:sheaths, 0,
		:sling_wires, 0,
		:demand_points, 0,
		:micro_cells, 0,
		:areas_created, 0,
		:errors, 0,
		:skipped, 0
	)

	>> _self
_endmethod
```

#### 2. Boundary Area Detection
```magik
_private _method astri_design_migrator.is_boundary_area?(pm)
	## Check if placemark is the project boundary area
	## Boundary area has parent = null or empty AND type = "area"
	##
	## Parameters:
	##   pm (property_list) - Placemark data
	##
	## Returns:
	##   boolean - True if this is the boundary area

	_local parent << pm[:parent]
	_local type << pm[:type]

	>> type = "area" _andif (parent _is _unset _orif parent = "")
_endmethod
$

_private _method astri_design_migrator.extract_boundary_area(placemarks)
	## Extract boundary area placemark from placemarks
	## The boundary area is an area with parent = null or empty
	##
	## Parameters:
	##   placemarks (rope) - All placemarks
	##
	## Returns:
	##   property_list - Boundary area placemark, or _unset if not found

	_for pm _over placemarks.fast_elements()
	_loop
		_if _self.is_boundary_area?(pm)
		_then
			write("  Found boundary area: ", pm[:name])
			>> pm
		_endif
	_endloop

	write("  WARNING: No boundary area found (area with parent=null/empty)")
	>> _unset
_endmethod
```

#### 3. Project and Design Creation
```magik
_method astri_design_migrator.create_project_and_design(placemarks, wo_number, cluster_code, cluster_name)
	## Create new project and design for migration
	##
	## Parameters:
	##   placemarks (rope) - All placemarks (to extract boundary from)
	##   wo_number (string) - Work order number -> project name
	##   cluster_code (string) - Cluster code -> project title
	##   cluster_name (string) - Cluster name -> design name
	##
	## Returns:
	##   (project, scheme) - Created project and scheme objects

	write("Creating project and design...")

	# Extract boundary area from placemarks
	_local boundary_pm << _self.extract_boundary_area(placemarks)
	_local area_sr << _unset

	_if boundary_pm _isnt _unset
	_then
		# Parse boundary area geometry
		_try
			area_sr << _self.parse_line_geometry(boundary_pm[:coord])

			# Ensure it's closed
			_if area_sr.closed? _is _false
			_then
				write("  WARNING: Boundary area not closed, using world bounds")
				area_sr << _unset
			_endif
		_when error
			write("  ERROR parsing boundary area: ", condition.report_contents_string)
			area_sr << _unset
		_endtry
	_endif

	# Fallback to world bounds if no valid boundary area
	_if area_sr _is _unset
	_then
		write("  Using database world bounds for project area")
		_local bounds << .database.world.bounds
		area_sr << sector_rope.new_with(
			coordinate.new(bounds.xmin, bounds.ymin),
			coordinate.new(bounds.xmax, bounds.ymin),
			coordinate.new(bounds.xmax, bounds.ymax),
			coordinate.new(bounds.xmin, bounds.ymax),
			coordinate.new(bounds.xmin, bounds.ymin)
		)
	_endif

	# Get all GIS dataset names
	_local dsnames << set.new()
	_for ds _over gis_program_manager.databases.fast_elements()
	_loop
		_if ds.name _isnt _unset
		_then
			dsnames.add(ds.name)
		_endif
	_endloop

	# Create project
	# project_name = WO number
	# project_title = cluster code
	_local prj_attrs << property_list.new_with(
		:title, cluster_code
	)
	_local prj << swg_dsn_admin_engine.create_project(
		wo_number,       # Project name from WO number
		dsnames,
		area_sr,
		prj_attrs
	)
	write("  Project created: ", wo_number, " (Title: ", cluster_code, ", ID: ", prj.id, ")")

	# Create scheme (design)
	# design_name = cluster name
	_local sch_attrs << property_list.new()
	_local sch << swg_dsn_admin_engine.create_scheme(
		prj,                  # Parent project
		_unset,               # No parent scheme
		cluster_name,         # Scheme name from cluster name
		dsnames,              # Dataset names
		prj.design_partition, # Design partition
		sch_attrs             # Additional attributes
	)
	write("  Design created: ", cluster_name, " (ID: ", sch.id, ")")

	# Activate the design
	.design_manager.activate_design(sch)
	write("  Design activated")

	# Store references
	.project << prj
	.scheme << sch

	>> (prj, sch)
_endmethod
```

#### 4. Main Migration Method
```magik
_method astri_design_migrator.migrate_placemarks(placemarks)
	## Migrate placemarks to design objects
	## NOTE: Design must be activated before calling this method
	## NOTE: Boundary area (parent=null/empty) is automatically excluded
	##
	## Parameters:
	##   placemarks (rope) - Rope of property_lists from astri_kml_parser
	##
	## Returns:
	##   property_list - Migration statistics

	write("Starting design migration of ", placemarks.size, " placemarks...")

	_if .scheme _is _unset
	_then
		condition.raise(:error, :string, "Design not activated. Call create_project_and_design() first.")
	_endif

	# All object creation automatically goes into the activated design alternative
	# No need for database mode switching

	# First pass: Create poles (needed for cable snapping)
	write("  Pass 1: Creating poles...")
	_for pm _over placemarks.fast_elements()
	_loop
		# Skip boundary area
		_if _self.is_boundary_area?(pm)
		_then
			_continue
		_endif

		_if _self.is_pole?(pm)
		_then
			_self.create_pole(pm)
		_endif
	_endloop

	# Second pass: Create cables/aerial routes
	write("  Pass 2: Creating cables and aerial routes...")
	_for pm _over placemarks.fast_elements()
	_loop
		# Skip boundary area
		_if _self.is_boundary_area?(pm)
		_then
			_continue
		_endif

		_if _self.is_cable?(pm)
		_then
			_self.create_aerial_route(pm)
		_endif
	_endloop

	# Third pass: Create other objects (demand points, splices, zones, etc.)
	write("  Pass 3: Creating demand points, splices, and area-based objects...")
	_for pm _over placemarks.fast_elements()
	_loop
		# Skip boundary area (ONLY areas with parent=null/empty)
		_if _self.is_boundary_area?(pm)
		_then
			_continue
		_endif

		# Demand points
		_if _self.is_demand_point?(pm)
		_then
			_self.create_demand_point(pm)

		# Sheath splices (area-based)
		_elif _self.is_splice?(pm)
		_then
			_self.create_sheath(pm)

		# Micro cells / zones (area-based with parent folders)
		_elif _self.is_micro_cell?(pm)
		_then
			_self.create_micro_cell(pm)

		# Other area-based objects with parent folders
		# (These are migrated according to cluster_astri (1).magik logic)
		_elif pm[:type] = "area" _andif pm[:parent] _isnt _unset _andif pm[:parent] <> ""
		_then
			_self.create_area_based_object(pm)
		_endif
	_endloop

	_self.print_statistics()
	>> .stats
_endmethod
```

#### 5. Geometry Parsing Helpers
```magik
_private _method astri_design_migrator.parse_point_geometry(coord_string)
	## Convert "lon,lat,elevation" to local coordinate

	_local coord_parts << coord_string.split_by(",")
	_local lon << coord_parts[1].as_number()
	_local lat << coord_parts[2].as_number()

	_local wgs84_coord << coordinate.new(lon, lat)
	_local local_coord << .transform.convert(wgs84_coord)

	_local point << pseudo_point.new(local_coord)
	point.world << .database.world

	>> point
_endmethod

_private _method astri_design_migrator.parse_line_geometry(coord_string)
	## Convert "lon1,lat1,0 lon2,lat2,0 ..." to sector_rope

	_local coord_pairs << coord_string.split_by(",0")
	_local sect << sector.new()

	_for coord_pair _over coord_pairs.fast_elements()
	_loop
		_local parts << coord_pair.split_by(",")
		_if parts.size >= 2
		_then
			_local lon << parts[1].as_number()
			_local lat << parts[2].as_number()

			_local wgs84_coord << coordinate.new(lon, lat)
			_local local_coord << .transform.convert(wgs84_coord)
			sect.add_last(local_coord)
		_endif
	_endloop

	_local sector_rope << sect.as_sector_rope()
	>> sector_rope
_endmethod

_private _method astri_design_migrator.parse_area_geometry(coord_string)
	## Convert area coordinates to pseudo_area

	_local sector_rope << _self.parse_line_geometry(coord_string)

	_if sector_rope.closed? _is _false
	_then
		condition.raise(:area_not_closed)
	_endif

	_local p_area << pseudo_area.new(sector_rope)
	p_area.world << .database.world

	>> p_area
_endmethod
```

#### 6. Object Type Detection
```magik
_private _method astri_design_migrator.is_pole?(pm)
	## Check if placemark is a pole based on parent folder

	_local parent << pm[:parent].default("").lowercase

	>> parent.matches?("pole") _orif
	   parent.matches?("new pole") _orif
	   parent.matches?("existing pole")
_endmethod

_private _method astri_design_migrator.is_cable?(pm)
	## Check if placemark is a cable

	_local parent << pm[:parent].default("").lowercase

	>> parent.matches?("cable") _orif
	   parent.matches?("kabel") _orif
	   parent.matches?("distribution cable") _orif
	   parent.matches?("sling wire")
_endmethod

_private _method astri_design_migrator.is_demand_point?(pm)
	## Check if placemark is a demand point (home pass)

	_local parent << pm[:parent].default("").lowercase

	>> parent.matches?("homepass") _orif
	   parent.matches?("home pass") _orif
	   parent.matches?("hp")
_endmethod

_private _method astri_design_migrator.is_splice?(pm)
	## Check if placemark is a splice/sheath (area-based)

	_local parent << pm[:parent].default("").lowercase

	>> pm[:type] = "area" _andif
	   (parent.matches?("splice") _orif
	    parent.matches?("closure") _orif
	    parent.matches?("joint"))
_endmethod

_private _method astri_design_migrator.is_micro_cell?(pm)
	## Check if placemark is a micro cell / zone (area-based)

	_local parent << pm[:parent].default("").lowercase

	>> pm[:type] = "area" _andif
	   (parent.matches?("micro cell") _orif
	    parent.matches?("zone") _orif
	    parent.matches?("cell") _orif
	    parent.matches?("coverage"))
_endmethod
```

**Note:** Additional object type detection methods should be added based on the specific folder patterns in `cluster_astri (1).magik`. All area-based objects WITH parent folders should be detected and migrated according to that reference logic.

#### 7. Pole Creation
```magik
_private _method astri_design_migrator.create_pole(pm)
	## Create pole object from placemark

	_try
		# Parse folder hierarchy
		_local parent << pm[:parent].default("")
		_local fol << parent.split_by("|")
		_local fsize << fol.size

		# Skip if insufficient folder depth
		_if fsize < 2 _then _return _endif

		# Parse geometry
		_local location << _self.parse_point_geometry(pm[:coord])

		# Determine pole type from folder name
		_local pole_type << _self.match_pole_type(parent)
		_local pole_status << _self.match_pole_status(parent)
		_local line_type << _self.match_line_type(parent)

		# Determine STF item code based on pole type
		_local stf_code << _self.get_pole_stf_code(pole_type)

		# Snap to nearest aerial route if within 500m
		_local snapped_location << _self.snap_to_aerial_route(location, 500)
		_if snapped_location _isnt _unset
		_then
			location << snapped_location
		_endif

		# Check for existing pole within 200m
		_local existing << _self.scan_pole(location, 200)
		_if existing _isnt _unset
		_then
			# Update existing pole
			existing.telco_pole_tag << pm[:name]
			existing.type << pole_type
			existing.pole_emr_status << pole_status
			existing.folders << parent
			existing.uuid << .uuid
			.stats[:poles] +<< 1
			>> existing
		_endif

		# Create new pole
		_local prop_values << property_list.new_with(
			:location, location,
			:telco_pole_tag, pm[:name],
			:usage, "Telco",
			:material_type, "Steel",
			:extension_arm, _false,
			:power_riser, _false,
			:telco_riser, _false,
			:bond, _false,
			:ground_status, _false,
			:type, pole_type,
			:pole_emr_status, pole_status,
			:folders, parent,
			:fttx_network_type, "Cluster",
			:segment, .segment_id,
			:stf_item_code, stf_code,
			:line_type, line_type,
			:pop, .pop_name,
			:olt, .pop_name,
			:project, .prj_id,
			:uuid, .uuid,
			:construction_status, "Proposed"
		)

		_local rec_trans << record_transaction.new_insert(.pole_col, prop_values)
		_local result << rec_trans.run()

		.stats[:poles] +<< 1
		write("  ✓ Pole created: ", pm[:name])
		>> result

	_when error
		write("  ERROR creating pole ", pm[:name], ": ", condition.report_contents_string)
		.stats[:errors] +<< 1
	_endtry
_endmethod
```

#### 8. Aerial Route Creation
```magik
_private _method astri_design_migrator.create_aerial_route(pm)
	## Create aerial route (cable) from placemark

	_try
		# Parse folder hierarchy
		_local parent << pm[:parent].default("")
		_local fol << parent.split_by("|")
		_local fsize << fol.size

		# Parse geometry
		_local route << _self.parse_line_geometry(pm[:coord])

		# Check minimum length
		_local length << route.line_length
		_if length < 0.4 _then _return _endif

		# Determine cable type
		_local is_sling_wire << fol[fsize].lowercase = "sling wire"

		_if is_sling_wire
		_then
			# Create sling wire instead
			>> _self.create_sling_wire(pm, route)
		_endif

		# Determine cable core count from name
		_local cable_core << _self.match_cable_core(pm[:name])
		_local (spec, stf_code, fiber_count) << _self.get_cable_specs(cable_core)

		# Determine line type
		_local line_type << _self.match_line_type(parent)

		# Snap endpoints to poles
		_local (start_pole, end_pole) << _self.find_endpoint_poles(route, 500)
		_if start_pole _isnt _unset _orif end_pole _isnt _unset
		_then
			route << _self.adjust_route_to_poles(route, start_pole, end_pole)
		_endif

		# Create aerial route
		_local prop_values << property_list.new_with(
			:construction_status, "Proposed",
			:name, pm[:name],
			:asset_ownership, "Owned",
			:pop, .pop_name,
			:olt, .pop_name,
			:fiber_count, fiber_count,
			:fttx_network_type, "Cluster",
			:segment, .segment_id,
			:line_type, line_type,
			:folders, parent,
			:route, route,
			:uuid, .uuid
		)

		_local rec_trans << record_transaction.new_insert(.ar_col, prop_values)
		_local result << rec_trans.run()

		.stats[:aerial_routes] +<< 1
		write("  ✓ Aerial route created: ", pm[:name])
		>> result

	_when error
		write("  ERROR creating aerial route ", pm[:name], ": ", condition.report_contents_string)
		.stats[:errors] +<< 1
	_endtry
_endmethod
```

#### 9. Demand Point Creation
```magik
_private _method astri_design_migrator.create_demand_point(pm)
	## Create demand point (home pass) from placemark

	_try
		# Parse geometry
		_local location << _self.parse_point_geometry(pm[:coord])

		# Create demand point
		_local prop_values << property_list.new_with(
			:identification, pm[:name],
			:name, pm[:name],
			:status, "Active",
			:mdu?, _false,
			:type, "Type 1",
			:segment, .segment_id,
			:fttx_network_type, "Cluster",
			:folders, pm[:parent],
			:pop, .pop_name,
			:olt, .pop_name,
			:notes, .prj_id,
			:location, location,
			:uuid, .uuid
		)

		_local rec_trans << record_transaction.new_insert(.dp_col, prop_values)
		_local result << rec_trans.run()

		# Find associated micro cell
		_local cell << _self.find_micro_cell(location)
		_if cell _isnt _unset
		_then
			# Associate with splitter if available
			_local splitter_id << cell.splitter_id
			# Update customer premise record...
		_endif

		.stats[:demand_points] +<< 1
		write("  ✓ Demand point created: ", pm[:name])
		>> result

	_when error
		write("  ERROR creating demand point ", pm[:name], ": ", condition.report_contents_string)
		.stats[:errors] +<< 1
	_endtry
_endmethod
```

#### 10. Micro Cell Creation (Area-based with Parent)
```magik
_private _method astri_design_migrator.create_micro_cell(pm)
	## Create micro cell / zone from area placemark WITH parent folder
	## Based on cluster_astri (1).magik logic

	_try
		# Parse folder hierarchy
		_local parent << pm[:parent].default("")
		_local fol << parent.split_by("|")

		# Only process areas WITH parent folders
		_if parent = "" _orif parent _is _unset
		_then
			_return  # Skip boundary areas
		_endif

		# Parse area geometry
		_local area << _self.parse_area_geometry(pm[:coord])

		# Create micro cell / zone
		_local prop_values << property_list.new_with(
			:name, pm[:name],
			:area, area,
			:folders, parent,
			:fttx_network_type, "Cluster",
			:segment, .segment_id,
			:pop, .pop_name,
			:olt, .pop_name,
			:uuid, .uuid
		)

		_local rec_trans << record_transaction.new_insert(.cell_col, prop_values)
		_local result << rec_trans.run()

		.stats[:micro_cells] +<< 1
		write("  ✓ Micro cell created: ", pm[:name])
		>> result

	_when error
		write("  ERROR creating micro cell ", pm[:name], ": ", condition.report_contents_string)
		.stats[:errors] +<< 1
	_endtry
_endmethod
```

#### 11. Generic Area-based Object Creation
```magik
_private _method astri_design_migrator.create_area_based_object(pm)
	## Create area-based object from placemark WITH parent folder
	## Uses cluster_astri (1).magik logic to determine object type from folder path
	##
	## This method handles ANY area placemark that has a parent folder
	## and wasn't already processed by specific creation methods

	_try
		_local parent << pm[:parent].default("")

		# Safety check: Only process areas WITH parent folders
		_if parent = "" _orif parent _is _unset
		_then
			_return  # Skip boundary areas
		_endif

		# Parse folder hierarchy to determine object type
		_local fol << parent.split_by("|")
		_local fsize << fol.size

		_if fsize < 1
		_then
			write("  WARNING: Area with empty folder path skipped: ", pm[:name])
			.stats[:skipped] +<< 1
			_return
		_endif

		# Parse area geometry
		_local area << _self.parse_area_geometry(pm[:coord])

		# Determine object type based on folder pattern
		# (Add more patterns based on cluster_astri (1).magik analysis)
		_local last_folder << fol[fsize].lowercase

		write("  → Area object: ", pm[:name], " (folder: ", parent, ")")

		# Example: Add specific creation logic based on folder patterns
		# This should be expanded based on cluster_astri (1).magik reference

		.stats[:areas_created] +<< 1

	_when error
		write("  ERROR creating area object ", pm[:name], ": ", condition.report_contents_string)
		.stats[:errors] +<< 1
	_endtry
_endmethod
```

**Note:** The `create_area_based_object()` method should be expanded based on the specific area object types found in `cluster_astri (1).magik`. This ensures ALL areas with parent folders are migrated according to the reference logic.

#### 12. Helper Methods (Pattern Matching)
```magik
_private _method astri_design_migrator.match_pole_type(folder_path)
	## Determine pole type from folder path

	_local fp << folder_path.lowercase

	_if fp.matches?("7-3") _orif fp.matches?("7m3")
	_then
		>> "Pole 7-3"
	_elif fp.matches?("7-4") _orif fp.matches?("7m 4") _orif fp.matches?("7m4")
	_then
		>> "Pole 7-4"
	_elif fp.matches?("7-5") _orif fp.matches?("7m5")
	_then
		>> "Pole 7-5"
	_elif fp.matches?("9-4") _orif fp.matches?("9m 4") _orif fp.matches?("9m4")
	_then
		>> "Pole 9-4"
	_elif fp.matches?("9-5") _orif fp.matches?("9m5")
	_then
		>> "Pole 9-5"
	_else
		>> "Pole 7-4"  # Default
	_endif
_endmethod

_private _method astri_design_migrator.match_pole_status(folder_path)
	## Determine pole status (New/Existing)

	_local fp << folder_path.lowercase

	_if fp.matches?("new pole")
	_then
		>> "New"
	_elif fp.matches?("existing") _orif fp.matches?("ext")
	_then
		>> "Existing"
	_else
		>> "New"  # Default
	_endif
_endmethod

_private _method astri_design_migrator.match_cable_core(cable_name)
	## Extract cable core count from name using regex

	_local core_patterns << {"12", "24", "36", "48", "72", "96", "144", "288", "576"}

	_for pattern _over core_patterns.fast_elements()
	_loop
		_if cable_name.matches?(pattern)
		_then
			>> pattern
		_endif
	_endloop

	>> "24"  # Default
_endmethod

_private _method astri_design_migrator.get_cable_specs(core_count)
	## Get cable specifications based on core count

	_local spec_map << hash_table.new_with(
		"12", {"SM G652D-ADSS 12C", "", "12"},
		"24", {"SM G652D-ADSS 24C", "200000100", "24"},
		"36", {"SM G652D-ADSS 36C", "200000975", "36"},
		"48", {"SM G652D-ADSS 48C", "200001038", "48"},
		"72", {"SM G652D-ADSS 72C", "FO_INV_FTTX_0796", "72"},
		"96", {"SM G652D-ADSS 96C", "200001630", "96"},
		"144", {"SM G652D-ADSS 144C", "200001030", "144"},
		"288", {"SM G652D-ADSS 288C", "200001015", "288"}
	)

	_local specs << spec_map[core_count]
	_if specs _is _unset
	_then
		specs << {"SM G652D-ADSS 24C", "200000100", "24"}
	_endif

	>> (_scatter specs)
_endmethod

_private _method astri_design_migrator.get_pole_stf_code(pole_type)
	## Get STF item code for pole type

	_local code_map << hash_table.new_with(
		"Pole 7-3", "200001055",
		"Pole 7-4", "200001183",
		"Pole 7-5", "200000187",
		"Pole 9-4", "200001181",
		"Pole 9-5", "200000169"
	)

	>> code_map[pole_type].default("")
_endmethod
```

#### 13. Snapping and Proximity Helpers
```magik
_private _method astri_design_migrator.snap_to_aerial_route(point, tolerance)
	## Snap point to nearest aerial route within tolerance

	_local buff << point.buffer(tolerance)
	buff.world << .database.world

	_local pred << predicate.interacts(:route, {buff})
	_local routes << .ar_col.select(pred)

	_if routes.size > 0
	_then
		_local nearest_route << routes.an_element()
		_local chain << pseudo_chain.new(nearest_route.route)
		chain.world << .database.world

		_local snap_point << chain.segpoint_near(point)
		_local snapped << pseudo_point.new(snap_point)
		snapped.world << .database.world

		>> snapped
	_else
		>> _unset
	_endif
_endmethod

_private _method astri_design_migrator.scan_pole(point, tolerance)
	## Find existing pole within tolerance distance

	_local buff << point.buffer(tolerance)
	buff.world << .database.world

	_local pred << predicate.interacts(:location, {buff})
	_local poles << .pole_col.select(pred)

	_if poles.size > 0
	_then
		>> poles.an_element()
	_else
		>> _unset
	_endif
_endmethod

_private _method astri_design_migrator.find_endpoint_poles(route, tolerance)
	## Find poles at route endpoints

	_local start_coord << route.first_coord
	_local end_coord << route.last_coord

	_local start_point << pseudo_point.new(start_coord)
	start_point.world << .database.world

	_local end_point << pseudo_point.new(end_coord)
	end_point.world << .database.world

	_local start_pole << _self.scan_pole(start_point, tolerance)
	_local end_pole << _self.scan_pole(end_point, tolerance)

	>> (start_pole, end_pole)
_endmethod
```

## Integration with Work Order Dialog

### Update `migrate_to_design()` Method

```magik
_method rwwi_astri_workorder_dialog.migrate_to_design()
	## Migrate KML data to design Smallworld objects
	## Creates new project and design, then migrates all objects

	_if .selected_wo _is _unset
	_then
		_self.user_info("Please select a work order first")
		_return
	_endif

	_local wo << .selected_wo
	_local kmz_uuid << wo[:kmz_uuid]

	_if kmz_uuid _is _unset _orif kmz_uuid = ""
	_then
		_self.user_error("No KMZ file associated with this work order")
		_return
	_endif

	_try
		# Download KML file (Scenario 2)
		write("Downloading KML file for UUID: ", kmz_uuid)
		_local result << rwwi_java_interface.download_kml_from_kmz(kmz_uuid)

		# Check for errors
		_if result _is _unset
		_then
			_self.user_error("Failed to download KML file")
			_return
		_endif

		# Parse XML response
		_local xml_doc << simple_xml.read_element_string(result)
		_local success_elem << xml_doc.element_matching_name(:success)
		_local kml_file_path_elem << xml_doc.element_matching_name(:kml_file_path)

		_if success_elem _isnt _unset _andif
		    success_elem.xml_result = "true" _andif
		    kml_file_path_elem _isnt _unset
		_then
			_local kml_file_path << kml_file_path_elem.xml_result
			write("KML file downloaded to:", kml_file_path)

			# Parse KML file
			write("Parsing KML file...")
			_local parser << astri_kml_parser.new(kml_file_path)
			_local placemarks << parser.parse()
			write("KML parsing complete. Found", placemarks.size, "placemarks")

			# Get migration context from work order
			_local pop_name << wo[:pop_name].default("Unknown")
			_local wo_number << wo[:wo_number].default("WO_" + kmz_uuid.subseq(1, 8))
			_local cluster_code << wo[:cluster_code].default("CLUSTER_" + kmz_uuid.subseq(1, 8))
			_local cluster_name << wo[:cluster_name].default("Cluster_" + kmz_uuid.subseq(1, 8))

			# Get project ID and segment ID from work order
			_local prj_id << wo[:uuid]         # Work order UUID field
			_local segment_id << wo[:kmz_uuid]  # Work order KMZ UUID field

			# Create design migrator
			write("Creating design migrator...")
			_local database << gis_program_manager.databases[:gis]

			_if database _is _unset
			_then
				_self.user_error("GIS database not available")
				_return
			_endif

			_local migrator << astri_design_migrator.new(database, prj_id, segment_id, pop_name)

			# Create project and design
			# - Project name = WO number
			# - Project title = cluster code
			# - Design name = cluster name
			# - Boundary extracted from area placemark with parent=null/empty
			write("Creating project and design...")
			_local (project, scheme) << migrator.create_project_and_design(
				placemarks,     # Boundary extracted from placemarks
				wo_number,      # Project name
				cluster_code,   # Project title
				cluster_name    # Design name
			)

			# Migrate placemarks to design (boundary area automatically excluded)
			write("Migrating objects to design...")
			_local stats << migrator.migrate_placemarks(placemarks)

			# Display results
			_local total_objs << stats[:aerial_routes] + stats[:poles] + stats[:sheaths] +
			                      stats[:sling_wires] + stats[:demand_points] +
			                      stats[:micro_cells] + stats[:areas_created]

			_local msg << write_string(
				"=== Design Migration Complete! ===", %newline,
				"File: ", kml_file_path, %newline,
				"Project: ", wo_number, " (", cluster_code, ") - ID: ", project.id, %newline,
				"Design: ", cluster_name, " - ID: ", scheme.id, %newline,
				"=" * 50, %newline,
				"Aerial Routes:   ", stats[:aerial_routes], %newline,
				"Poles:           ", stats[:poles], %newline,
				"Sheaths:         ", stats[:sheaths], %newline,
				"Sling Wires:     ", stats[:sling_wires], %newline,
				"Demand Points:   ", stats[:demand_points], %newline,
				"Micro Cells:     ", stats[:micro_cells], %newline,
				"Other Areas:     ", stats[:areas_created], %newline,
				"Errors:          ", stats[:errors], %newline,
				"Skipped:         ", stats[:skipped], %newline,
				"=" * 50, %newline,
				"Total objects:   ", total_objs, %newline,
				"=" * 50, %newline,
				%newline,
				"All objects have been created in the design alternative.", %newline,
				"Object tagging:", %newline,
				"  - prj_id:     ", prj_id, %newline,
				"  - segment_id: ", segment_id, %newline,
				"  - uuid:       ", segment_id, %newline,
				"Boundary area (parent=null) was used for project extent only.", %newline,
				"The design is now active. You can review and modify objects before posting.")

			_self.user_info(msg)

		_else
			_local error_elem << xml_doc.element_matching_name(:error)
			_local error_msg << _if error_elem _isnt _unset
					    _then >> error_elem.xml_result
					    _else >> "Unknown error"
					    _endif
			_self.user_error(write_string("Failed to download KML file:", %newline, error_msg))
		_endif

	_when error
		_self.user_error(write_string("Error migrating to design:", %newline,
			condition.report_contents_string))
	_endtry
_endmethod
```

## File Structure

```
C:\Smallworld\pni_custom\rwwi_astri_integration_java\
  magik\
    rwwi_astri_integration\
      source\
        astri_kml_parser.magik          # Existing
        astri_kml_migrator.magik        # Existing (temp objects)
        astri_design_migrator.magik     # NEW - Design objects
        load_list.txt                   # Update to include astri_design_migrator
  docs\
    DESIGN_MIGRATION_PLAN.md           # This document
```

## Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Create `astri_design_migrator` class with design management slots
- [ ] Implement constructor and initialization
- [ ] **Implement `is_boundary_area?()` method** - Check if placemark is boundary (type=area, parent=null/empty)
- [ ] **Implement `extract_boundary_area()` method** - Extract boundary from placemarks
- [ ] **Implement `create_project_and_design()` method** - Use boundary area for project extent
  - [ ] Accept parameters: placemarks, wo_number, cluster_code, cluster_name
  - [ ] Extract boundary area and parse geometry
  - [ ] Fallback to world bounds if no boundary found
  - [ ] Set project name = wo_number
  - [ ] Set project title = cluster_code
  - [ ] Set design name = cluster_name
- [ ] Implement geometry parsing methods (point, line, area)
- [ ] Add to load_list.txt
- [ ] Test coordinate transformation
- [ ] **Test boundary extraction** - Verify boundary area is found and parsed correctly

### Phase 2: Pattern Matching
- [ ] Implement object type detection methods
- [ ] Implement pattern matching helpers (pole type, cable core, etc.)
- [ ] Implement STF code mapping
- [ ] Test with sample data

### Phase 3: Object Creation
- [ ] **Update `migrate_placemarks()` to skip boundary area** - Add `is_boundary_area?()` check in all passes
- [ ] Implement pole creation
- [ ] Implement aerial route creation
- [ ] Implement sling wire creation
- [ ] Implement demand point creation
- [ ] **Implement area-based object creation** (based on cluster_astri (1).magik):
  - [ ] Implement `is_micro_cell?()` detection
  - [ ] Implement `create_sheath()` for splice areas (WITH parent)
  - [ ] Implement `create_micro_cell()` for zone/cell areas (WITH parent)
  - [ ] Implement `create_area_based_object()` for other area types (WITH parent)
- [ ] **Verify boundary area is NOT migrated as object** - Confirm only areas with parent=null/empty are excluded
- [ ] **Verify areas WITH parent ARE migrated** - Confirm zones, splices, etc. are created as objects

### Phase 4: Advanced Features
- [ ] Implement snapping to aerial routes
- [ ] Implement pole proximity detection
- [ ] Implement endpoint pole finding
- [ ] Implement route adjustment to poles

### Phase 5: Integration
- [ ] Update `migrate_to_design()` in work order dialog
- [ ] **Extract work order fields**: wo_number, cluster_code, cluster_name
- [ ] **Pass placemarks to `create_project_and_design()`** with wo_number, cluster_code, cluster_name
- [ ] Update statistics display to show:
  - [ ] Project name (wo_number) and title (cluster_code)
  - [ ] Design name (cluster_name)
  - [ ] Whether boundary area was found and used
- [ ] Add error handling for missing boundary area

### Phase 6: Testing
- [ ] **Test boundary area extraction**
  - [ ] Test with KML containing boundary area (type=area, parent=null)
  - [ ] Verify boundary geometry is parsed correctly
  - [ ] Verify boundary is used for project extent
  - [ ] Test with KML missing boundary area (fallback to world bounds)
- [ ] **Test boundary area exclusion from migration**
  - [ ] Verify boundary area (parent=null/empty) is NOT created as object
  - [ ] Verify areas WITH parent folders ARE migrated as objects
  - [ ] Test splice areas (parent="Infrastructure|Splices") → created as sheath objects
  - [ ] Test zone areas (parent="Infrastructure|Zones") → created as micro_cell objects
  - [ ] Count migrated areas: boundary excluded, regular areas included
  - [ ] Verify statistics correctly show boundary NOT counted
- [ ] **Test project/design naming**
  - [ ] Verify project name = wo_number
  - [ ] Verify project title = cluster_code
  - [ ] Verify design name = cluster_name
- [ ] Test project and design creation
- [ ] Test design activation
- [ ] Verify design isolation (objects not in main database)
- [ ] Test with small KML file (< 50 placemarks)
- [ ] Test pole creation in design
- [ ] Test cable creation in design
- [ ] Test demand point creation in design
- [ ] Test with large KML file (> 500 placemarks)
- [ ] Verify duplicate handling
- [ ] Test design deactivation and reactivation

## Key Differences from Temporary Migration

### Temporary Migration (astri_kml_migrator)
- **Purpose:** Preview/review before final migration
- **Target:** Temporary collections (rw_point, rw_line, rw_area)
- **Database Management:** Mode switching (readonly ↔ writable)
- **Duplicate Handling:** Update if exists (by uuid, name, parent)
- **Complexity:** Simple geometry storage
- **Context:** Minimal (just uuid)
- **Output:** Objects in main database alternative

### Design Migration (astri_design_migrator)
- **Purpose:** Create final production network objects in isolated design
- **Target:** Real collections (pole, aerial_route, demand_point, etc.)
- **Database Management:** Design alternative (automatic isolation)
- **Duplicate Handling:** Check proximity, update existing if nearby
- **Complexity:** Complex business logic (snapping, type detection, etc.)
- **Context:** Full (pop_name, project_id, segment_id)
- **Output:** Objects in design alternative (isolated from main database)

## Benefits

### 1. Isolated Design Environment
- **No database mode switching required** - Design alternative is automatically writable
- **Complete isolation** - Objects created in design don't affect main database
- **Safe experimentation** - Can review, modify, or discard without impacting production
- **Version control** - Design changes tracked separately from main database

### 2. Unified Class Architecture
- **Single class** handles both project/design creation and migration
- **Simple API** - Two method calls: `create_project_and_design()` then `migrate_placemarks()`
- **Clean integration** - Work order dialog has minimal code
- **Easy maintenance** - All logic in one place

### 3. Direct Migration from KML
- Skip intermediate temporary table step
- Faster workflow for final implementation
- Consistent with existing migration tools pattern

### 4. Reusable Logic
- Pattern matching methods can be shared
- Geometry parsing is standardized
- Easy to extend for new object types

### 5. Maintains Reference Architecture
- Follows existing `cluster_astri` patterns
- Uses proven snapping/proximity logic
- Compatible with existing data model

### 6. Design Workflow Support
- **Review before posting** - All objects can be inspected in design
- **Incremental posting** - Can post objects in stages
- **Conflict detection** - Design system handles conflicts with main database
- **Rollback capability** - Can discard entire design if needed

## Testing Strategy

### Test 1: Basic Pole Creation
**Input:** KML with 10 poles
**Expected:** 10 poles created with correct types and locations

### Test 2: Cable Creation with Snapping
**Input:** KML with cables and poles
**Expected:**
- Poles created first
- Cables created second
- Cable endpoints snapped to poles

### Test 3: Demand Points with Associations
**Input:** KML with demand points and micro cells
**Expected:**
- Demand points created
- Associated with correct micro cells
- Splitter linkage established

### Test 4: Duplicate Handling
**Input:** Re-run migration with same KML
**Expected:**
- Existing poles updated (not duplicated)
- New objects only created where needed

### Test 5: Large Dataset
**Input:** KML with 1000+ placemarks
**Expected:**
- All objects created successfully
- Performance acceptable (< 5 minutes)
- Design remains active after migration

### Test 6: Design Isolation
**Input:** Create objects in design, then check main database
**Expected:**
- Objects visible in design alternative
- Objects NOT visible in main database
- Can deactivate design and objects disappear from view

### Test 7: Project and Design Creation
**Input:** Work order with wo_number, cluster_code, cluster_name
**Expected:**
- Project name = wo_number
- Project title = cluster_code
- Design name = cluster_name
- Multiple migrations create unique projects and designs

### Test 8: Boundary Area Handling
**Input:** KML with:
- 1 boundary area (type=area, parent=null/empty)
- 2 zones (type=area, parent="Infrastructure|Zones")
- 1 splice (type=area, parent="Infrastructure|Splices")
**Expected:**
- Boundary area: Used for project extent, NOT migrated
- Zone areas: Migrated as micro_cell objects (WITH parent)
- Splice area: Migrated as sheath_splice object (WITH parent)
- Project extent matches boundary geometry
- Statistics show 2 micro_cells + 1 sheath (boundary NOT counted)

### Test 9: Missing Boundary Area
**Input:** KML without boundary area
**Expected:**
- Warning message displayed
- Fallback to world bounds for project extent
- Migration continues successfully
- All areas (except boundary) are migrated normally

## Summary

This plan provides a complete architecture for migrating KML parse results directly to real Smallworld design objects using a **unified class approach**:

### Key Features
1. **Single Class Architecture** - `astri_design_migrator` handles both project/design creation and object migration
2. **Design Alternative Management** - Creates isolated design environment automatically
3. **Direct KML Migration** - Bypasses temporary table step for efficiency
4. **Proven Patterns** - Follows existing `cluster_astri` migration logic
5. **Simple Integration** - Clean two-step API for work order dialog

### Workflow
```magik
# Step 1: Create migrator instance
# Extract prj_id and segment_id from work order
_local prj_id << wo[:uuid]         # Work order UUID field
_local segment_id << wo[:kmz_uuid]  # Work order KMZ UUID field

migrator << astri_design_migrator.new(database, prj_id, segment_id, pop_name)

# Step 2: Create project and design (activates design alternative)
# - Extracts boundary area from placemarks (type=area, parent=null/empty)
# - Uses boundary for project extent
# - Sets project name = wo_number, title = cluster_code
# - Sets design name = cluster_name
(project, scheme) << migrator.create_project_and_design(
    placemarks,     # All placemarks
    wo_number,      # Project name
    cluster_code,   # Project title
    cluster_name    # Design name
)

# Step 3: Migrate objects (all go into activated design)
# - Automatically excludes boundary area from migration
# - Only non-boundary placemarks become objects
# - All objects tagged with prj_id (wo[:uuid]) and segment_id (wo[:kmz_uuid])
stats << migrator.migrate_placemarks(placemarks)
```

### Benefits Over Database Mode Switching
- No manual mode switching required
- Complete isolation from main database
- Safe review and modification workflow
- Built-in versioning and conflict detection
- Can discard or post as needed

## Implementation Improvements Summary

This design migration plan includes the following key improvements:

### 1. Project/Design Naming from Work Order ✓
- **Project Name** = Work order number (`wo[:wo_number]`)
- **Project Title** = Cluster code (`wo[:cluster_code]`)
- **Design Name** = Cluster name (`wo[:cluster_name]`)
- **Project ID** (`prj_id`) = Work order UUID (`wo[:uuid]`)
- **Segment ID** (`segment_id`) = Work order KMZ UUID (`wo[:kmz_uuid]`)
- **Object UUID** (`uuid` field) = Work order KMZ UUID (`wo[:kmz_uuid]`)

### 2. Boundary Area Handling ✓
- **Extraction**: Finds area placemark with `parent=null/empty/unset`
- **Usage**: Sets project and design geographic extent
- **Exclusion**: NOT migrated as database object
- **Fallback**: Uses world bounds if boundary not found

### 3. Area-based Object Migration ✓
- **Boundary areas** (parent=null/empty) → EXCLUDED from migration
- **Regular areas** (parent=folder path) → MIGRATED according to cluster_astri (1).magik
- Examples of migrated areas:
  - Sheath splices (parent contains "splice"/"closure")
  - Micro cells/zones (parent contains "zone"/"cell")
  - Other area objects based on folder patterns
- Automatic boundary exclusion check in all migration passes

### 4. Complete Testing Coverage ✓
- Boundary extraction tests
- Boundary exclusion verification (areas with parent ARE migrated)
- Project/design naming validation
- Missing boundary fallback tests
- Area-based object migration tests

### 5. Clear Documentation ✓
- Boundary area requirements section
- Project naming requirements table
- Updated API reference
- Updated workflow examples
- Comprehensive checklist

## Critical Implementation Note: Area Migration

### IMPORTANT: Only Boundary Areas are Excluded

The implementation must ensure:

```magik
# ✓ CORRECT BEHAVIOR:

# Boundary area (parent=null/empty) → NOT migrated
Placemark: name="Boundary", type="area", parent="", coord="..."
→ Used for project extent ONLY

# Regular areas (parent=folder) → MIGRATED according to cluster_astri (1).magik
Placemark: name="Zone A", type="area", parent="Infrastructure|Zones", coord="..."
→ Migrated as micro_cell object

Placemark: name="Splice 1", type="area", parent="Infrastructure|Splices", coord="..."
→ Migrated as sheath_splice object

Placemark: name="Coverage", type="area", parent="Network|Coverage", coord="..."
→ Migrated according to folder pattern logic
```

### Implementation Verification

**Test cases must verify:**
1. ✓ Areas with `parent=null/empty/unset` are NOT created as objects
2. ✓ Areas with `parent=folder path` ARE created as objects
3. ✓ Area migration follows `cluster_astri (1).magik` reference logic
4. ✓ Statistics do NOT count boundary area
5. ✓ Statistics DO count all regular areas (splices, zones, etc.)

**Ready for implementation when approved!**
