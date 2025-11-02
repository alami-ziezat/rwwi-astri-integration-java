# KML to Temporary Objects Implementation Plan

## Overview

Implementation plan to create temporary Smallworld objects from parsed KML placemarks in the `rwwi_astri_integration` module. Based on the existing logic in `test_astri_kml_parser`.

## Implementation Date
2025-10-31

## Source Analysis

### Current Test Implementation
**File:** `C:\Users\user\Downloads\test_astri_procs_edit.magik`
**Method:** `test_astri_kml_parser()` (Lines 90-370)

**Key Components Identified:**

1. **Database Setup (Lines 103-125)**
   - Gets GIS database and ace_view
   - Accesses collections: `rw_point`, `rw_line`, `rw_area`, `ftth!demand_point`
   - Sets up coordinate system transformation (WGS84 → local CS)
   - Configures float formatting and dynamic globals

2. **Coordinate Transformation (Lines 123-125)**
   ```magik
   cs1 << v.world.coordinate_system
   cs2 << a_view.collections[:sw_gis!coordinate_system].at(:world_longlat_wgs84_degree)
   t << transform.new_converting_cs_to_cs(cs2, cs1)
   ```

3. **KML Parsing (Lines 162-166)**
   ```magik
   parser << astri_kml_parser.new(kml_file_path)
   placemarks << parser.parse()
   ```

4. **Point Creation (Lines 218-233)**
   - Splits coordinates by comma
   - Converts lon/lat to coordinate
   - Transforms to local coordinate system
   - Inserts into `rw_point` collection

5. **Area Creation (Lines 235-275)**
   - Splits coordinates by ",0" (removes elevation)
   - Creates sector from coordinate chain
   - Converts to sector_rope and pseudo_area
   - Validates closure
   - Inserts into `rw_area` collection

6. **Line Creation (Lines 277-304)**
   - Splits coordinates by ",0"
   - Creates sector from coordinate chain
   - Converts to pseudo_chain
   - Inserts into `rw_line` collection

## Target Module

**Module:** `rwwi_astri_integration`
**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\`

## Implementation Structure

### New Class: `astri_kml_migrator`

**Purpose:** Convert parsed KML placemarks to Smallworld temporary objects

**File:** `source\astri_kml_migrator.magik`

**Slots:**
```magik
def_slotted_exemplar(:astri_kml_migrator,
{
	{:database,        _unset, :writable},   # Target database
	{:ace_view,        _unset, :writable},   # Ace view for CS
	{:transform,       _unset, :writable},   # WGS84 → local CS transform
	{:point_col,       _unset, :writable},   # Point collection
	{:line_col,        _unset, :writable},   # Line collection
	{:area_col,        _unset, :writable},   # Area collection
	{:uuid,            _unset, :writable},   # Current KMZ UUID
	{:stats,           _unset, :writable}    # Migration statistics
})
```

## Method Breakdown

### 1. Initialization Methods

#### `new(database, uuid)`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_method astri_kml_migrator.new(database, uuid)
	## Create new migrator instance
	##
	## Parameters:
	##   database - GIS database (e.g., gis_program_manager.databases[:gis])
	##   uuid - KMZ document UUID
	##
	## Returns: New astri_kml_migrator instance

	>> _clone.init(database, uuid)
_endmethod
```

#### `init(database, uuid)`
```magik
_private _method astri_kml_migrator.init(database, uuid)
	## Initialize migrator with database and coordinate system setup

	.database << database
	.uuid << uuid
	.ace_view << gis_program_manager.ace_view

	# Setup collections
	.point_col << .database.collections[:rw_point]
	.line_col << .database.collections[:rw_line]
	.area_col << .database.collections[:rw_area]

	# Setup coordinate system transformation
	cs_local << .database.world.coordinate_system
	cs_wgs84 << .ace_view.collections[:sw_gis!coordinate_system].at(:world_longlat_wgs84_degree)
	.transform << transform.new_converting_cs_to_cs(cs_wgs84, cs_local)

	# Initialize statistics
	.stats << property_list.new_with(
		:points_created, 0,
		:lines_created, 0,
		:areas_created, 0,
		:errors, 0,
		:skipped, 0
	)

	# Setup dynamic globals
	!current_dsview! << .database
	!current_world! << .database.world
	!current_coordinate_system! << .database.world.coordinate_system

	>> _self
_endmethod
```

### 2. Main Migration Method

#### `migrate_placemarks(placemarks)`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_method astri_kml_migrator.migrate_placemarks(placemarks)
	## Migrate all placemarks to temporary Smallworld objects
	##
	## Parameters:
	##   placemarks (rope) - Rope of property_lists from astri_kml_parser
	##
	## Returns:
	##   property_list - Migration statistics

	write("Starting migration of ", placemarks.size, " placemarks...")

	_for i, pm _over placemarks.fast_keys_and_elements()
	_loop
		_try
			_if pm[:type] = "point"
			_then
				_self.create_point_object(pm)
				.stats[:points_created] +<< 1

			_elif pm[:type] = "area"
			_then
				_self.create_area_object(pm)
				.stats[:areas_created] +<< 1

			_elif pm[:type] = "line"
			_then
				_self.create_line_object(pm)
				.stats[:lines_created] +<< 1

			_else
				write("WARNING: Unknown type '", pm[:type], "' for placemark: ", pm[:name])
				.stats[:skipped] +<< 1
			_endif

		_when error
			write("ERROR creating object for placemark ", i, " (", pm[:name], "): ",
			      condition.report_contents_string)
			.stats[:errors] +<< 1
		_endtry

		# Progress indicator every 100 placemarks
		_if i _mod 100 = 0
		_then
			write("  Progress: ", i, "/", placemarks.size, " placemarks processed")
		_endif
	_endloop

	write("Migration complete!")
	_self.print_statistics()

	>> .stats
_endmethod
```

### 3. Object Creation Methods

#### `create_point_object(pm)`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_migrator.create_point_object(pm)
	## Create point object from placemark
	##
	## Parameters:
	##   pm (property_list) - Placemark data with :coord, :name, :parent

	# Parse coordinates: "lon,lat,elevation"
	coord_parts << pm[:coord].split_by(",")
	lon << coord_parts[1].as_number()
	lat << coord_parts[2].as_number()

	# Transform from WGS84 to local coordinate system
	wgs84_coord << coordinate.new(lon, lat)
	local_coord << .transform.convert(wgs84_coord)

	# Prepare property values
	prop_values << property_list.new_with(
		:location, local_coord,
		:name, pm[:name],
		:folders, pm[:parent],
		:uuid, .uuid
	)

	# Insert record
	rec_trans << record_transaction.new_insert(.point_col, prop_values)
	result << rec_trans.run()

	write("  ✓ Point created: ", pm[:name])
	>> result
_endmethod
```

#### `create_area_object(pm)`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_migrator.create_area_object(pm)
	## Create area object from placemark
	##
	## Parameters:
	##   pm (property_list) - Placemark data with :coord, :name, :parent

	# Parse coordinates: "lon1,lat1,0 lon2,lat2,0 ..."
	# Split by ",0" to remove elevations
	coord_pairs << pm[:coord].split_by(",0")

	# Create sector from coordinates
	sector << sector.new()
	_for coord_pair _over coord_pairs.fast_elements()
	_loop
		parts << coord_pair.split_by(",")
		_if parts.size >= 2
		_then
			lon << parts[1].as_number()
			lat << parts[2].as_number()

			wgs84_coord << coordinate.new(lon, lat)
			local_coord << .transform.convert(wgs84_coord)
			sector.add_last(local_coord)
		_endif
	_endloop

	# Convert to sector_rope
	sector_rope << sector.as_sector_rope()

	# Validate closure
	_if sector_rope.closed? _is _false
	_then
		write("  ⚠ Area not closed, skipping: ", pm[:name])
		condition.raise(:area_not_closed, :name, pm[:name])
	_endif

	# Create pseudo_area
	pseudo_area << pseudo_area.new(sector_rope)
	pseudo_area.world << .database.world

	# Prepare property values
	prop_values << property_list.new_with(
		:area, pseudo_area,
		:name, pm[:name],
		:folders, pm[:parent],
		:uuid, .uuid
	)

	# Insert record
	rec_trans << record_transaction.new_insert(.area_col, prop_values)
	result << rec_trans.run()

	write("  ✓ Area created: ", pm[:name])
	>> result
_endmethod
```

#### `create_line_object(pm)`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_migrator.create_line_object(pm)
	## Create line object from placemark
	##
	## Parameters:
	##   pm (property_list) - Placemark data with :coord, :name, :parent

	# Parse coordinates: "lon1,lat1,0 lon2,lat2,0 ..."
	# Split by ",0" to remove elevations
	coord_pairs << pm[:coord].split_by(",0")

	# Create sector from coordinates
	sector << sector.new()
	_for coord_pair _over coord_pairs.fast_elements()
	_loop
		parts << coord_pair.split_by(",")
		_if parts.size >= 2
		_then
			lon << parts[1].as_number()
			lat << parts[2].as_number()

			wgs84_coord << coordinate.new(lon, lat)
			local_coord << .transform.convert(wgs84_coord)
			sector.add_last(local_coord)
		_endif
	_endloop

	# Convert to sector_rope
	sector_rope << sector.as_sector_rope()

	# Create pseudo_chain
	pseudo_chain << pseudo_chain.new(sector_rope)
	pseudo_chain.world << .database.world

	# Prepare property values
	prop_values << property_list.new_with(
		:route, pseudo_chain,
		:name, pm[:name],
		:folders, pm[:parent],
		:uuid, .uuid
	)

	# Insert record
	rec_trans << record_transaction.new_insert(.line_col, prop_values)
	result << rec_trans.run()

	write("  ✓ Line created: ", pm[:name])
	>> result
_endmethod
```

### 4. Utility Methods

#### `print_statistics()`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_method astri_kml_migrator.print_statistics()
	## Print migration statistics

	write("")
	write("=" * 60)
	write("Migration Statistics")
	write("=" * 60)
	write("Points created:  ", .stats[:points_created])
	write("Lines created:   ", .stats[:lines_created])
	write("Areas created:   ", .stats[:areas_created])
	write("Errors:          ", .stats[:errors])
	write("Skipped:         ", .stats[:skipped])
	write("")
	total << .stats[:points_created] + .stats[:lines_created] + .stats[:areas_created]
	write("Total objects:   ", total)
	write("=" * 60)
_endmethod
```

#### `parse_coordinate(coord_string)`
```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_migrator.parse_coordinate(coord_string)
	## Parse coordinate string and convert to local coordinate system
	##
	## Parameters:
	##   coord_string (string) - "lon,lat" or "lon,lat,elevation"
	##
	## Returns:
	##   coordinate - Transformed local coordinate

	parts << coord_string.split_by(",")
	lon << parts[1].as_number()
	lat << parts[2].as_number()

	wgs84_coord << coordinate.new(lon, lat)
	local_coord << .transform.convert(wgs84_coord)

	>> local_coord
_endmethod
```

## Integration with Work Order Dialog

### Update `migrate_to_design()` Method

**File:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\source\rwwi_astri_workorder_dialog.magik`

**Replace TODO section with:**
```magik
# Parse KML file
parser << astri_kml_parser.new(kml_file_path)
placemarks << parser.parse()

# Get target database
database << gis_program_manager.databases[:gis]

# Create migrator and migrate placemarks
migrator << astri_kml_migrator.new(database, kmz_uuid)
stats << migrator.migrate_placemarks(placemarks)

# Display results
_self.user_info(write_string(
	"Migration to Design Objects Complete!", %newline,
	"=" * 40, %newline,
	"Points created: ", stats[:points_created], %newline,
	"Lines created: ", stats[:lines_created], %newline,
	"Areas created: ", stats[:areas_created], %newline,
	"Errors: ", stats[:errors], %newline,
	"Skipped: ", stats[:skipped], %newline,
	"=" * 40))
```

### Update `migrate_as_temporary()` Method

**Same logic as above**, but potentially use different database or add flag:
```magik
migrator << astri_kml_migrator.new(database, kmz_uuid)
migrator.temporary_mode? << _true  # Optional flag for future use
stats << migrator.migrate_placemarks(placemarks)
```

## File Structure

### New Files to Create

1. **`source\astri_kml_migrator.magik`** (main class)
2. **`source\load_list.txt`** (update to include new file)
3. **`docs\KML_TO_TEMP_OBJECTS_IMPLEMENTATION.md`** (detailed implementation doc)

### Load Order

Update `rwwi_astri_integration\source\load_list.txt`:
```
astri_kml_parser.magik
astri_kml_migrator.magik
test_astri_procs.magik
```

## Error Handling

### Common Errors to Handle

1. **Invalid Coordinates**
   - Empty coordinate string
   - Malformed coordinate format
   - Non-numeric values

2. **Area Not Closed**
   - Skip area creation
   - Log warning with placemark name
   - Increment skipped counter

3. **Transform Errors**
   - Coordinate out of bounds
   - Invalid coordinate system

4. **Database Errors**
   - Collection not found
   - Insert transaction failure
   - Geometry validation failure

### Error Strategy

```magik
_try
	_self.create_point_object(pm)
	.stats[:points_created] +<< 1
_when error
	write("ERROR: ", condition.report_contents_string)
	write("  Placemark: ", pm[:name])
	write("  Type: ", pm[:type])
	write("  Coords: ", pm[:coord].subseq(1, 50.min(pm[:coord].size)))
	.stats[:errors] +<< 1
_endtry
```

## Testing Strategy

### Unit Tests

1. **Test Point Creation**
   - Single point with valid coordinates
   - Point with missing coordinates
   - Point with invalid coordinates

2. **Test Line Creation**
   - Simple 2-point line
   - Multi-segment line
   - Line with duplicate points

3. **Test Area Creation**
   - Simple triangle
   - Complex polygon
   - Non-closed polygon (should fail gracefully)

4. **Test Coordinate Transform**
   - Known WGS84 → Local conversions
   - Boundary coordinates
   - Origin (0,0) handling

### Integration Tests

1. **Small KML File** (< 20 placemarks)
   - Mix of points, lines, areas
   - Verify all created correctly

2. **Large KML File** (> 100 placemarks)
   - Performance test
   - Memory usage
   - Progress indicators

3. **Error Scenarios**
   - Malformed KML
   - Missing collections
   - Invalid coordinate system

### Test Procedure

```magik
# 1. Load module
load_module("rwwi_astri_integration")

# 2. Get database
database << gis_program_manager.databases[:gis]

# 3. Parse KML
parser << astri_kml_parser.new("C:\test\sample.kml")
placemarks << parser.parse()

# 4. Migrate
migrator << astri_kml_migrator.new(database, "test-uuid-001")
stats << migrator.migrate_placemarks(placemarks)

# 5. Verify
write("Points: ", database.collections[:rw_point].size)
write("Lines: ", database.collections[:rw_line].size)
write("Areas: ", database.collections[:rw_area].size)
```

## Performance Considerations

### Optimization Strategies

1. **Batch Transactions** (future enhancement)
   - Group multiple inserts into single transaction
   - Commit every N records

2. **Coordinate Caching**
   - Cache transform object
   - Reuse sector objects where possible

3. **Progress Indicators**
   - Update every 100 placemarks
   - Estimated time remaining

4. **Memory Management**
   - Clear temporary objects after use
   - Avoid holding large ropes in memory

### Expected Performance

- **Small files** (< 100 placemarks): < 10 seconds
- **Medium files** (100-500 placemarks): < 30 seconds
- **Large files** (500+ placemarks): 1-2 minutes

## Rollout Plan

### Phase 1: Core Implementation (Week 1)
- Create `astri_kml_migrator` class
- Implement point creation
- Implement line creation
- Implement area creation
- Basic error handling

### Phase 2: Integration (Week 2)
- Integrate with work order dialog
- Replace TODO sections
- Add progress indicators
- Update documentation

### Phase 3: Testing & Refinement (Week 3)
- Unit tests
- Integration tests
- Performance optimization
- User acceptance testing

### Phase 4: Production Deployment (Week 4)
- Deploy to production environment
- Train users
- Monitor for issues
- Gather feedback

## Documentation Deliverables

1. **Implementation Guide** - Step-by-step coding instructions
2. **API Reference** - Complete method signatures and descriptions
3. **User Guide** - How to use migration features
4. **Troubleshooting Guide** - Common errors and solutions

## Success Criteria

- ✅ All placemark types (point, line, area) supported
- ✅ Coordinate transformation working correctly
- ✅ Error handling for invalid data
- ✅ Progress indicators for large files
- ✅ Statistics reporting
- ✅ Integration with work order dialog
- ✅ Performance < 2 minutes for 500 placemarks
- ✅ < 5% error rate on real-world data

## Dependencies

### Smallworld Collections Required
- `rw_point` - Point objects
- `rw_line` - Line objects
- `rw_area` - Area objects
- `sw_gis!coordinate_system` - Coordinate systems
- `sw_gis!world` - World definition

### Smallworld APIs Used
- `coordinate` - Coordinate objects
- `sector` - Geometry sectors
- `pseudo_area` - Area geometry
- `pseudo_chain` - Line geometry
- `transform` - Coordinate system transformation
- `record_transaction` - Database inserts

### Magik Modules Required
- `rwwi_astri_integration` - KML parser
- `gis_program_manager` - Database access

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Invalid coordinates | Medium | Validate and skip bad data |
| Memory issues on large files | Medium | Batch processing, progress saves |
| Coordinate transform errors | High | Validate CS setup, fallback handling |
| Database collection missing | High | Check collections on init, fail gracefully |
| Area closure failures | Low | Skip and log, continue processing |

## Next Steps

1. **Review this plan** with development team
2. **Create skeleton class** with method signatures
3. **Implement point creation** first (simplest)
4. **Test with small KML file**
5. **Implement line and area creation**
6. **Integrate with work order dialog**
7. **Full testing cycle**
8. **Production deployment**

---

## Summary

This plan provides a complete roadmap for implementing KML to temporary Smallworld objects migration. The approach is based on proven logic from `test_astri_kml_parser` and follows Smallworld GIS best practices.

**Ready for execution upon your approval!**
