# Duplicate Handling for KML Migration

## Overview

Enhanced KML migration to handle duplicate records intelligently. When a placemark with the same UUID, name, and parent folder already exists in the database, the system updates its geometry instead of creating a duplicate record.

## Implementation Date
2025-10-31

## Scope

**Applies to:** `migrate_as_temporary()` only
**Not applied to:** `migrate_to_design()` (will be implemented later)

## Duplicate Detection Strategy

### Matching Criteria

A record is considered a duplicate if **all three** of the following match:
1. **UUID** - The KMZ document UUID
2. **Name** - The placemark name
3. **Parent** - The parent folder path

### Logic Flow

```
For each placemark:
  1. Parse coordinates and transform to local CS
  2. Query database for existing record with same (uuid, name, parent)
  3. If found:
       - Update geometry/location
       - Increment update counter
       - Log "updated" message
     Else:
       - Create new record
       - Increment create counter
       - Log "created" message
```

## Implementation Details

### 1. Helper Method: `find_existing_record()`

**Location:** `astri_kml_migrator.magik` lines 155-179

**Purpose:** Find existing record by uuid, name, and parent

**Code:**
```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_migrator.find_existing_record(collection, name, parent)
	## Find existing record with matching uuid, name, and parent
	##
	## Parameters:
	##   collection - Database collection to search
	##   name - Placemark name
	##   parent - Parent folder path
	##
	## Returns:
	##   Existing record or _unset if not found

	_local pred << predicate.new(:uuid, :eq, .uuid) _and
		       predicate.new(:name, :eq, name) _and
		       predicate.new(:folders, :eq, parent)

	_local existing_recs << collection.select(pred)

	_if existing_recs.size > 0
	_then
		>> existing_recs.an_element()
	_else
		>> _unset
	_endif
_endmethod
```

**Predicate Logic:**
- `:uuid :eq .uuid` - Match current KMZ UUID
- `:name :eq name` - Match placemark name
- `:folders :eq parent` - Match parent folder path
- Combined with `_and` - All three must match

### 2. Updated Statistics

**New Fields Added:**
```magik
.stats << property_list.new_with(
	:points_created, 0,
	:points_updated, 0,   # NEW
	:lines_created, 0,
	:lines_updated, 0,    # NEW
	:areas_created, 0,
	:areas_updated, 0,    # NEW
	:errors, 0,
	:skipped, 0
)
```

**Tracking:**
- `points_updated` - Number of point locations updated
- `lines_updated` - Number of line routes updated
- `areas_updated` - Number of area geometries updated

### 3. Renamed Methods

**Old Methods:**
- `create_point_object(pm)` → `create_or_update_point(pm)`
- `create_area_object(pm)` → `create_or_update_area(pm)`
- `create_line_object(pm)` → `create_or_update_line(pm)`

**New Return Values:**
- `:created` - New record was inserted
- `:updated` - Existing record was updated

### 4. Point Update Logic

**Location:** `astri_kml_migrator.magik` lines 182-230

**Process:**
```magik
# Parse and transform coordinates
_local coord_parts << pm[:coord].split_by(",")
_local lon << coord_parts[1].as_number()
_local lat << coord_parts[2].as_number()
_local wgs84_coord << coordinate.new(lon, lat)
_local local_coord << .transform.convert(wgs84_coord)

# Check for existing record
_local existing_rec << _self.find_existing_record(.point_col, pm[:name], pm[:parent])

_if existing_rec _isnt _unset
_then
	# UPDATE existing location
	_local rec_trans << record_transaction.new_update(existing_rec,
		property_list.new_with(:location, local_coord))
	rec_trans.run()
	write("  ↻ Point updated: ", pm[:name])
	>> :updated
_else
	# CREATE new record
	_local prop_values << property_list.new_with(
		:location, local_coord,
		:name, pm[:name],
		:folders, pm[:parent],
		:uuid, .uuid)
	_local rec_trans << record_transaction.new_insert(.point_col, prop_values)
	rec_trans.run()
	write("  ✓ Point created: ", pm[:name])
	>> :created
_endif
```

**Updated Field:** `:location` (coordinate)

**Console Output:**
- Create: `✓ Point created: POLE-001`
- Update: `↻ Point updated: POLE-001`

### 5. Area Update Logic

**Location:** `astri_kml_migrator.magik` lines 233-306

**Process:**
```magik
# Parse coordinates, build sector, validate closure
# ... (same as before)

# Create pseudo_area
_local p_area << pseudo_area.new(sector_rope)
p_area.world << .database.world

# Check for existing record
_local existing_rec << _self.find_existing_record(.area_col, pm[:name], pm[:parent])

_if existing_rec _isnt _unset
_then
	# UPDATE existing area geometry
	_local rec_trans << record_transaction.new_update(existing_rec,
		property_list.new_with(:area, p_area))
	rec_trans.run()
	write("  ↻ Area updated: ", pm[:name])
	>> :updated
_else
	# CREATE new record
	_local prop_values << property_list.new_with(
		:area, p_area,
		:name, pm[:name],
		:folders, pm[:parent],
		:uuid, .uuid)
	_local rec_trans << record_transaction.new_insert(.area_col, prop_values)
	rec_trans.run()
	write("  ✓ Area created: ", pm[:name])
	>> :created
_endif
```

**Updated Field:** `:area` (pseudo_area)

**Console Output:**
- Create: `✓ Area created: Coverage-Zone-A`
- Update: `↻ Area updated: Coverage-Zone-A`

### 6. Line Update Logic

**Location:** `astri_kml_migrator.magik` lines 309-375

**Process:**
```magik
# Parse coordinates, build sector
# ... (same as before)

# Create pseudo_chain
_local p_chain << pseudo_chain.new(sector_rope)
p_chain.world << .database.world

# Check for existing record
_local existing_rec << _self.find_existing_record(.line_col, pm[:name], pm[:parent])

_if existing_rec _isnt _unset
_then
	# UPDATE existing route geometry
	_local rec_trans << record_transaction.new_update(existing_rec,
		property_list.new_with(:route, p_chain))
	rec_trans.run()
	write("  ↻ Line updated: ", pm[:name])
	>> :updated
_else
	# CREATE new record
	_local prop_values << property_list.new_with(
		:route, p_chain,
		:name, pm[:name],
		:folders, pm[:parent],
		:uuid, .uuid)
	_local rec_trans << record_transaction.new_insert(.line_col, prop_values)
	rec_trans.run()
	write("  ✓ Line created: ", pm[:name])
	>> :created
_endif
```

**Updated Field:** `:route` (pseudo_chain)

**Console Output:**
- Create: `✓ Line created: Cable-Segment-A1`
- Update: `↻ Line updated: Cable-Segment-A1`

### 7. Migration Loop Update

**Location:** `astri_kml_migrator.magik` lines 92-133

**Changes:**
```magik
_for i, pm _over placemarks.fast_keys_and_elements()
_loop
	_try
		_local action << _unset

		_if pm[:type] = "point"
		_then
			action << _self.create_or_update_point(pm)
			_if action = :created
			_then
				.stats[:points_created] +<< 1
			_elif action = :updated
			_then
				.stats[:points_updated] +<< 1
			_endif

		_elif pm[:type] = "area"
		_then
			action << _self.create_or_update_area(pm)
			_if action = :created
			_then
				.stats[:areas_created] +<< 1
			_elif action = :updated
			_then
				.stats[:areas_updated] +<< 1
			_endif

		_elif pm[:type] = "line"
		_then
			action << _self.create_or_update_line(pm)
			_if action = :created
			_then
				.stats[:lines_created] +<< 1
			_elif action = :updated
			_then
				.stats[:lines_updated] +<< 1
			_endif

		_else
			write("WARNING: Unknown type '", pm[:type], "' for placemark: ", pm[:name])
			.stats[:skipped] +<< 1
		_endif

	_when error
		write("ERROR creating object for placemark ", i, " (", pm[:name], "): ",
		      condition.report_contents_string)
		.stats[:errors] +<< 1
	_endtry
_endloop
```

**Key Change:** Methods now return `:created` or `:updated`, and statistics are incremented accordingly.

### 8. Statistics Display

**Console Output (via `print_statistics()`):**
```
============================================================
Migration Statistics
============================================================
Points created:  30
Points updated:  5
Lines created:   10
Lines updated:   2
Areas created:   5
Areas updated:   1
Errors:          0
Skipped:         0

Total created:   45
Total updated:   8
Total objects:   53
============================================================
```

**User Info Dialog (via `migrate_as_temporary()`):**
```
=== Temporary Migration Complete! ===
File: C:\Smallworld\temp\kml\cluster_xxx.kml
==================================================
Points created:  30
Points updated:  5
Lines created:   10
Lines updated:   2
Areas created:   5
Areas updated:   1
Errors:          0
Skipped:         0
==================================================
Total created:   45
Total updated:   8
Total objects:   53
==================================================
```

## Use Cases

### Use Case 1: Re-importing Updated KML

**Scenario:**
1. Import KML file `cluster_abc.kmz` (UUID: `abc123`)
2. Creates 100 objects (50 points, 30 lines, 20 areas)
3. KML file is updated externally (coordinates changed)
4. Re-import same KML file

**Expected Result:**
```
Points created:  0
Points updated:  50
Lines created:   0
Lines updated:   30
Areas created:   0
Areas updated:   20
Total created:   0
Total updated:   100
Total objects:   100
```

**Benefit:** No duplicate records, geometries updated to new coordinates

### Use Case 2: Partial KML Update

**Scenario:**
1. Import KML file with 100 placemarks
2. Later, import updated KML with:
   - 80 existing placemarks (same name, parent)
   - 20 new placemarks

**Expected Result:**
```
Points created:  15
Points updated:  40
Lines created:   3
Lines updated:   25
Areas created:   2
Areas updated:   15
Total created:   20
Total updated:   80
Total objects:   100
```

**Benefit:** Existing objects updated, new objects created

### Use Case 3: Different KMZ Files

**Scenario:**
1. Import `cluster_a.kmz` (UUID: `uuid-a`)
   - Creates POLE-001 (uuid=uuid-a, name=POLE-001, parent=Infrastructure)
2. Import `cluster_b.kmz` (UUID: `uuid-b`)
   - Has POLE-001 (uuid=uuid-b, name=POLE-001, parent=Infrastructure)

**Expected Result:**
```
Points created:  1  (new record because UUID is different)
Points updated:  0
```

**Benefit:** Different KMZ files maintain separate objects even with same names

### Use Case 4: Same Name, Different Parent

**Scenario:**
1. Import KML with:
   - POLE-001 (parent: Infrastructure|POLE)
   - POLE-001 (parent: Network|POLE)

**Expected Result:**
```
Points created:  2  (different parent folder paths)
Points updated:  0
```

**Benefit:** Placemarks with same name but different folder hierarchy are kept separate

## Testing Strategy

### Test 1: First Import
**Setup:** Clean database, import KML with 50 placemarks

**Expected:**
```
Points created:  30
Points updated:  0
Lines created:   15
Lines updated:   0
Areas created:   5
Areas updated:   0
Total created:   50
Total updated:   0
```

### Test 2: Re-import Unchanged
**Setup:** Re-import exact same KML file

**Expected:**
```
Points created:  0
Points updated:  30
Lines created:   0
Lines updated:   15
Areas created:   0
Areas updated:   5
Total created:   0
Total updated:   50
```

### Test 3: Re-import with Coordinate Changes
**Setup:**
1. Import KML
2. Modify coordinates in KML
3. Re-import

**Expected:**
- All records updated with new coordinates
- No new records created
- Geometries reflect new coordinates

**Verification:**
- Query specific object (e.g., POLE-001)
- Check location matches new KML coordinates

### Test 4: Partial Update
**Setup:**
1. Import KML with 100 placemarks
2. Create new KML with:
   - 80 placemarks from original (modified coordinates)
   - 20 new placemarks

**Expected:**
```
Total created:   20
Total updated:   80
Total objects:   100
```

### Test 5: Different UUID, Same Names
**Setup:**
1. Import cluster_a.kmz (UUID: uuid-a) with POLE-001, POLE-002
2. Import cluster_b.kmz (UUID: uuid-b) with POLE-001, POLE-002

**Expected:**
- 4 total records created (2 from each KMZ)
- 0 records updated
- Each KMZ's objects are separate

## Benefits

### 1. Prevents Duplicate Records
- Same placemark re-imported multiple times doesn't create duplicates
- Database stays clean

### 2. Enables Incremental Updates
- Import updated KML files to refresh geometries
- No need to manually delete old records first

### 3. Maintains Data Integrity
- UUID + Name + Parent ensures correct matching
- Different KMZ files maintain separate objects

### 4. User Visibility
- Clear statistics show creates vs updates
- Console logs indicate which records were updated (↻ symbol)

### 5. Idempotent Operations
- Re-running migration produces same end result
- Safe to retry on errors

## Limitations

### 1. Scope: Temporary Migration Only
- Only applies to `migrate_as_temporary()`
- `migrate_to_design()` still shows simple display (to be implemented later)

### 2. Update Fields Limited
Points, areas, and lines update **only geometry fields**:
- Points: `:location` only
- Areas: `:area` only
- Lines: `:route` only

**Not updated:**
- Extended data attributes
- Other metadata fields

**Rationale:** Geometry changes are primary use case for re-import

### 3. Deletion Not Handled
If a placemark is removed from KML and file is re-imported:
- Existing record remains in database
- No automatic deletion

**Workaround:** Manual deletion or implement cleanup logic based on UUID

### 4. Name/Parent Changes Not Tracked
If placemark name or parent folder changes in KML:
- Treated as new placemark (creates new record)
- Old record remains unchanged

**Example:**
1. First import: POLE-001 (parent: Infrastructure)
2. KML updated: POLE-001 renamed to POLE-001-OLD
3. Re-import: Creates new POLE-001-OLD, leaves POLE-001 unchanged

## Performance Considerations

### Query Performance
Each placemark triggers a database query:
```magik
collection.select(
	predicate.new(:uuid, :eq, uuid) _and
	predicate.new(:name, :eq, name) _and
	predicate.new(:folders, :eq, parent)
)
```

**For Large Datasets (1000+ placemarks):**
- 1000 queries to check for duplicates
- May impact performance

**Optimization Options:**
1. **Index Fields:** Ensure `:uuid`, `:name`, `:folders` are indexed
2. **Batch Queries:** Pre-load all existing records for UUID, then search in-memory
3. **Caching:** Cache query results during migration

**Current Implementation:**
- Simple, straightforward approach
- Acceptable for typical datasets (< 500 placemarks)
- Can be optimized if needed

### Transaction Performance
**Current:** Individual `record_transaction` per placemark

**Alternative:** Batch transactions (not implemented)

**Trade-off:** Simpler error handling with individual transactions

## Future Enhancements

### 1. Extended Data Updates
Update extended data attributes in addition to geometry:
```magik
property_list.new_with(
	:location, local_coord,
	:attr1, pm[:extended][:attr1],
	:attr2, pm[:extended][:attr2]
)
```

### 2. Configurable Update Fields
Allow user to specify which fields to update:
```magik
_local update_fields << property_list.new_with(:geometry, :extended_data)
```

### 3. Orphan Cleanup
Delete records for placemarks no longer in KML:
```magik
# After migration, find records with UUID that weren't updated/created
# Delete those records
```

### 4. Change Detection
Track what actually changed:
```magik
_if existing_rec.location.distance_to(local_coord) > tolerance
_then
	write("  ⚠ Significant coordinate change detected")
_endif
```

### 5. Audit Trail
Log all updates with timestamp:
```magik
:updated_at, date_time.now(),
:updated_by, gis_program_manager.login_name
```

## Files Modified

### 1. `astri_kml_migrator.magik`
**Changes:**
- Lines 60-69: Added update statistics
- Lines 92-133: Updated migration loop to track creates/updates
- Lines 155-179: Added `find_existing_record()` helper
- Lines 182-230: Renamed `create_point_object()` → `create_or_update_point()`
- Lines 233-306: Renamed `create_area_object()` → `create_or_update_area()`
- Lines 309-375: Renamed `create_line_object()` → `create_or_update_line()`
- Lines 378-401: Updated `print_statistics()` to show creates/updates

### 2. `rwwi_astri_workorder_dialog.magik`
**Changes:**
- Lines 614-641: Rolled back `migrate_to_design()` to simple display
- Lines 725-747: Updated `migrate_as_temporary()` result display to show creates/updates

### 3. Documentation Created
- `docs/DUPLICATE_HANDLING.md` (this file)

## Summary

The duplicate handling enhancement provides:

✅ **Intelligent Duplicate Detection**
- Matches by UUID, name, and parent folder
- Prevents duplicate records

✅ **Update Existing Geometries**
- Points: Location updated
- Areas: Geometry updated
- Lines: Route updated

✅ **Comprehensive Statistics**
- Separate counts for creates and updates
- Clear user feedback

✅ **Console Logging**
- ✓ for creates
- ↻ for updates
- Clear indication of action taken

✅ **Idempotent Operations**
- Re-running migration produces consistent results
- Safe to retry on errors

✅ **Scope: Temporary Migration Only**
- `migrate_to_design()` rolled back to simple display
- Will be implemented separately later

Ready for testing with real KML files!
