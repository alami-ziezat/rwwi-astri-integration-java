# KML Parsing and Migration Implementation

## Overview

Implementation of KML parsing functionality for migrate operations using the `astri_kml_parser` to extract and display placemarks from downloaded KML files.

## Implementation Date
2025-10-30

## Features Implemented

### 1. KMZ UUID-Based Button Enabling

**Problem:** All buttons were enabled when a work order was selected, even if it had no KMZ UUID.

**Solution:** Conditional button enabling based on `kmz_uuid` presence.

**Implementation:** `update_detail_panel()` method (Lines 439-475)

```magik
# Always enable detail and construction buttons
.items[:detail_btn].enabled? << _true
.items[:construction_btn].enabled? << _true

# Enable KMZ-related buttons only if kmz_uuid is present
_local kmz_uuid << wo[:kmz_uuid]
_local has_kmz << kmz_uuid _isnt _unset _andif kmz_uuid <> ""

.items[:kmz_btn].enabled? << has_kmz
.items[:migrate_design_btn].enabled? << has_kmz
.items[:migrate_temp_btn].enabled? << has_kmz
```

**Behavior:**
- ✅ **Detail View Button** - Always enabled (shows work order info)
- ✅ **Mark Construction Button** - Always enabled (DB operation)
- ⚠️ **Download KMZ Button** - Only enabled if `kmz_uuid` exists
- ⚠️ **Migrate to Design Button** - Only enabled if `kmz_uuid` exists
- ⚠️ **Migrate as Temporary Button** - Only enabled if `kmz_uuid` exists

### 2. KML Parsing with astri_kml_parser

**Integration:** Both migrate methods now use `astri_kml_parser` to parse downloaded KML files.

**Parser Usage:**
```magik
# Parse KML file
_local parser << astri_kml_parser.new(kml_file_path)
_local placemarks << parser.parse()
```

**Returns:** rope of property_lists with structure:
- `:name` - Placemark name
- `:desc` - Description
- `:coord` - Coordinates string (lon,lat,elevation)
- `:type` - Geometry type ("point", "line", or "area")
- `:id` - Placemark ID (if present)
- `:parent` - Parent folder path (e.g., "Folder1|Folder2")
- `:extended` - Extended data as property_list

### 3. Display First 20 Placemarks

**Implementation:** Both `migrate_to_design()` and `migrate_as_temporary()` display first 20 placemarks.

**Output Format:**
```
=== KML Parsed Successfully (Design Migration) ===
File: C:\Users\user\AppData\Local\Temp\cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kml
Total Placemarks: 156
Displaying first 20 placemarks:

[1] ODP-01
    Type: point  |  Parent: ODPs|Primary
    Coords: 106.827356,- 6.175234,0.000000...
    Extended: odp_code=ODP-01 status=active type=primary
[2] Cable-Segment-01
    Type: line  |  Parent: Cables|Distribution
    Coords: 106.827356,-6.175234,0.000000 106.827445,-...
    Extended: cable_type=fiber length=125.5 capacity=48
[3] Coverage-Area-01
    Type: area  |  Parent: Coverage|Residential
    Coords: 106.827356,-6.175234,0.000000 106.827445,-...
    Extended: area_type=residential homes_passed=250

... (up to 20 placemarks)

TODO: Create actual Smallworld design objects from placemarks
```

**Display Details:**
- Shows placemark number [1-20]
- Shows name, type, and parent folder
- Shows first 50 characters of coordinates (with "..." if longer)
- Shows up to 3 extended data attributes
- Includes total count and TODO reminder

## Modified Methods

### 1. `migrate_to_design()` (Lines 568-671)

**Changes:**
- Removed: `read_kml_file()` call
- Added: `astri_kml_parser` integration
- Added: Display first 20 parsed placemarks
- Added: Extended data preview

**Flow:**
```
Download KMZ → Get KML file path → Parse with astri_kml_parser → Display 20 placemarks
```

### 2. `migrate_as_temporary()` (Lines 674-777)

**Changes:**
- Removed: `read_kml_file()` call
- Added: `astri_kml_parser` integration
- Added: Display first 20 parsed placemarks
- Added: Extended data preview

**Flow:**
```
Download KMZ → Get KML file path → Parse with astri_kml_parser → Display 20 placemarks
```

### 3. `update_detail_panel()` (Lines 439-475)

**Changes:**
- Added: KMZ UUID checking logic
- Modified: Conditional button enabling
- Separated: Always-enabled buttons vs KMZ-dependent buttons

## Testing Scenarios

### Test 1: Work Order WITHOUT KMZ UUID
1. Select work order with no `kmz_uuid`
2. Expected Button States:
   - ✅ Detail View - Enabled
   - ✅ Mark Construction - Enabled
   - ❌ Download KMZ - Disabled
   - ❌ Migrate to Design - Disabled
   - ❌ Migrate as Temporary - Disabled

### Test 2: Work Order WITH KMZ UUID
1. Select work order with `kmz_uuid` = "2989eecc-3fd0-402c-bd81-99e02caa7ef5"
2. Expected Button States:
   - ✅ Detail View - Enabled
   - ✅ Mark Construction - Enabled
   - ✅ Download KMZ - Enabled
   - ✅ Migrate to Design - Enabled
   - ✅ Migrate as Temporary - Enabled

### Test 3: Migrate to Design with Small KML
1. Select dummy work order (has KMZ UUID)
2. Click "Migrate to Design"
3. Expected Output:
   - File path displayed
   - Total placemark count shown
   - Up to 20 placemarks listed with details
   - Extended data shown (if present)
   - TODO message at end

### Test 4: Migrate to Design with Large KML (>20 placemarks)
1. Select work order with large KML (e.g., 156 placemarks)
2. Click "Migrate to Design"
3. Expected Output:
   - Shows "Total Placemarks: 156"
   - Shows "Displaying first 20 placemarks:"
   - Only lists 20 placemarks
   - TODO message at end

### Test 5: Migrate as Temporary
1. Select work order with KMZ UUID
2. Click "Migrate as Temporary"
3. Expected Output:
   - Same format as Design migration
   - Header says "Temporary Migration"
   - TODO mentions "temporary objects"

## Console Output

### When Parsing KML
```
Migrating KML to Design objects for UUID: 2989eecc-3fd0-402c-bd81-99e02caa7ef5
=== SCENARIO 2: Saving files to disk ===
Output directory: C:\Users\user\AppData\Local\Temp
KMZ file saved: C:\Users\user\AppData\Local\Temp\cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kmz
KML file saved: C:\Users\user\AppData\Local\Temp\cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kml
KML file downloaded to: C:\Users\user\AppData\Local\Temp\cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kml
Parsing KML file...
KML parsing complete. Found 156 placemarks
```

## Parser Integration Details

### astri_kml_parser Location
`C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_kml_parser.magik`

### Parser Features Used
1. **File-based parsing:** `astri_kml_parser.new(kml_file_path)`
2. **Placemark extraction:** `parser.parse()`
3. **Hierarchy support:** Parent folder paths
4. **Geometry types:** Points, Lines, Areas
5. **Extended data:** Custom attributes

### Placemark Property List Structure

```magik
property_list.new_with(
	:name,     "ODP-01",                    # Placemark name
	:desc,     "Optical Distribution...",   # Description
	:coord,    "106.827,-6.175,0.0",        # Coordinates
	:type,     "point",                     # Geometry type
	:id,       "placemark-001",             # ID attribute
	:parent,   "ODPs|Primary",              # Folder hierarchy
	:extended, property_list.new_with(      # Custom attributes
		:odp_code, "ODP-01",
		:status, "active",
		:capacity, "8"
	)
)
```

## Benefits

### 1. Better User Experience
- Buttons only enabled when actions are possible
- Clear indication of missing KMZ UUID
- Prevents unnecessary error messages

### 2. Structured Data Access
- Parsed placemarks in standard format
- Easy to iterate and process
- Access to both geometry and attributes

### 3. Preview Before Migration
- See placemark count and types
- Verify data quality
- Understand folder structure

### 4. Extensibility
- Easy to add more placemark details
- Can filter by type or parent
- Ready for actual object creation

## Next Steps (Future Implementation)

### 1. Object Creation from Placemarks

```magik
_for pm _over placemarks.fast_elements()
_loop
	_if pm[:type] = "point"
	_then
		# Create point object (e.g., ODP, pole, manhole)
		_self.create_point_object(pm)
	_elif pm[:type] = "line"
	_then
		# Create linear object (e.g., cable, duct)
		_self.create_line_object(pm)
	_elif pm[:type] = "area"
	_then
		# Create area object (e.g., coverage zone)
		_self.create_area_object(pm)
	_endif
_endloop
```

### 2. Extended Data Mapping

```magik
_method rwwi_astri_workorder_dialog.create_point_object(pm)
	# Map KML extended data to SW object fields
	_local rwo << .vda.collections[:odp].new_detached_record()

	rwo.odp_code << pm[:extended][:odp_code]
	rwo.status << pm[:extended][:status]
	rwo.capacity << pm[:extended][:capacity].as_number()

	# Parse coordinates
	_local coords << _self.parse_coordinates(pm[:coord])
	rwo.location << coords

	rwo.source_detached()
_endmethod
```

### 3. Progress Indicator

```magik
_for i, pm _over placemarks.fast_keys_and_elements()
_loop
	_self.update_progress(i, placemarks.size)
	_self.create_object_from_placemark(pm)
_endloop
```

### 4. Error Handling per Placemark

```magik
_local success_count << 0
_local error_count << 0

_for pm _over placemarks.fast_elements()
_loop
	_try
		_self.create_object_from_placemark(pm)
		success_count +<< 1
	_when error
		error_count +<< 1
		write("Failed to create object:", pm[:name], condition.report_contents_string)
	_endtry
_endloop

write("Migration complete:", success_count, "created,", error_count, "errors")
```

## Files Modified

1. **rwwi_astri_workorder_dialog.magik**
   - Lines 439-475: `update_detail_panel()` - KMZ UUID button logic
   - Lines 568-671: `migrate_to_design()` - Parser integration + display
   - Lines 674-777: `migrate_as_temporary()` - Parser integration + display

2. **Documentation Created**
   - `docs/KML_PARSING_IMPLEMENTATION.md` (this file)

3. **Documentation Moved**
   - All existing `.md` files moved to `docs/` folder

## Dependencies

### Magik Modules
- `rwwi_astri_integration` - Provides `astri_kml_parser`
- `rwwi_astri_workorder` - Work order manager UI

### Java Integration
- `AstriKmzDownloadProcs.java` - Download KMZ files
- `KmzDownloadClient.java` - HTTP client for API

## Configuration

No new configuration required. Uses existing:
- `TEMP` environment variable for file downloads
- ASTRI API credentials from `astri_config.properties`

## Summary

This implementation completes the migration workflow by:
1. ✅ Enabling buttons only when KMZ UUID is available
2. ✅ Parsing KML files using standard parser
3. ✅ Displaying first 20 placemarks with full details
4. ✅ Providing structured data ready for object creation
5. ✅ Organizing documentation in `docs/` folder

The foundation is now in place to implement actual Smallworld object creation from parsed placemarks.
