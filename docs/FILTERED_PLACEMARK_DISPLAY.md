# Filtered Placemark Display Implementation

## Overview

Enhanced KML parsing display that filters and categorizes placemarks by type and parent folder, showing:
- **10 Points** (POLE, HP, FAT)
- **5 Lines** (Cables)
- **5 Areas** (Boundaries/Coverage)

Total: Up to 20 placemarks displayed in organized sections.

## Implementation Date
2025-10-30

## Filtering Strategy

### Primary Filter: Parent Folder
Uses the `:parent` field from parsed placemarks to identify categories based on folder hierarchy.

### Fallback Filter: Placemark Name
If parent folder is empty, falls back to placemark name matching.

### Geometry Type Filter
First filters by geometry type (`:type` field), then by parent/name patterns.

## Filter Categories

### 1. POINTS (10 maximum)

**Targeted Types:**
- POLE - Utility poles
- HP - Home Passed locations
- FAT - Fiber Access Terminals

**Parent Folder Patterns:**
```magik
pm_parent.matches?("pole") _orif
pm_parent.matches?("hp") _orif
pm_parent.matches?("fat") _orif
pm_parent.matches?("home") _orif
pm_parent.matches?("terminal")
```

**Example Folder Structures:**
- `ODPs|POLE`
- `Infrastructure|HP`
- `Terminals|FAT`
- `Home Passed`
- `Fiber Access Terminal`

### 2. LINES (5 maximum)

**Targeted Types:**
- Cables - Fiber/copper cables
- Segments - Cable segments
- Ducts - Underground ducts
- Fiber - Fiber strands

**Parent Folder Patterns:**
```magik
pm_parent.matches?("cable") _orif
pm_parent.matches?("segment") _orif
pm_parent.matches?("duct") _orif
pm_parent.matches?("fiber") _orif
pm_parent.matches?("line")
```

**Example Folder Structures:**
- `Cables|Distribution`
- `Network|Fiber Segments`
- `Infrastructure|Ducts`
- `Fiber Lines`

### 3. AREAS (5 maximum)

**Targeted Types:**
- Boundaries - Coverage boundaries
- Coverage - Service coverage zones
- Zones - Service zones
- Areas - General areas
- Polygons - Polygon boundaries

**Parent Folder Patterns:**
```magik
pm_parent.matches?("boundary") _orif
pm_parent.matches?("coverage") _orif
pm_parent.matches?("zone") _orif
pm_parent.matches?("area") _orif
pm_parent.matches?("polygon")
```

**Example Folder Structures:**
- `Coverage|Residential`
- `Service Areas|Zone A`
- `Boundaries|Property Lines`
- `Polygon|Coverage Areas`

## Implementation Details

### Helper Method: `format_placemark_display()`

**Location:** Lines 880-912

**Purpose:** Formats individual placemark data for consistent display

**Parameters:**
- `index` (integer) - Display index number
- `pm` (property_list) - Placemark data

**Returns:** Formatted string with:
- Index number
- Placemark name
- Type and parent folder
- First 50 chars of coordinates
- Up to 3 extended data attributes

**Code:**
```magik
_pragma(classify_level=debug, topic={astri_integration})
_private _method rwwi_astri_workorder_dialog.format_placemark_display(index, pm)
	_local msg << write_string(
		"[", index, "] ", pm[:name].default("unnamed"), %newline,
		"    Type: ", pm[:type].default("unknown"),
		"  |  Parent: ", pm[:parent].default(""), %newline,
		"    Coords: ", pm[:coord].default("").subseq(1, 50.min(pm[:coord].size)),
		_if pm[:coord].size > 50 _then >> "..." _else >> "" _endif, %newline)

	# Show extended data if present
	_if pm[:extended].size > 0
	_then
		msg << msg + write_string("    Extended: ")
		_local ext_count << 0
		_for key, val _over pm[:extended].fast_keys_and_elements()
		_loop
			ext_count +<< 1
			_if ext_count > 3 _then _leave _endif
			msg << msg + write_string(key, "=", val.default(""), " ")
		_endloop
		msg << msg + %newline
	_endif

	>> msg
_endmethod
```

### Filtering Logic

**Location:**
- `migrate_to_design()`: Lines 614-712
- `migrate_as_temporary()`: Lines 787-885

**Process:**
1. Create empty ropes for each category (points, lines, areas)
2. Iterate through all parsed placemarks
3. Check geometry type first
4. Check parent folder patterns
5. Fallback to name patterns if parent is empty
6. Add matching placemarks to appropriate rope

**Code Pattern:**
```magik
# Categorize placemarks by type and parent folder
_local points << rope.new()
_local lines << rope.new()
_local areas << rope.new()

_for pm _over placemarks.fast_elements()
_loop
	_local pm_type << pm[:type].default("unknown")
	_local pm_name << pm[:name].default("").lowercase
	_local pm_parent << pm[:parent].default("").lowercase

	_if pm_type = "point"
	_then
		# Filter points by parent folder
		_if pm_parent.matches?("pole") _orif ...
		_then
			points.add_last(pm)
		_endif
	_elif pm_type = "line"
	_then
		# Filter lines by parent folder
		...
	_elif pm_type = "area"
	_then
		# Filter areas by parent folder
		...
	_endif
_endloop
```

## Display Format

### Header Section
```
=== KML Parsed Successfully (Design Migration) ===
File: C:\...\cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kml
Total Placemarks: 156
  - Points: 45 (showing up to 10: POLE, HP, FAT)
  - Lines: 28 (showing up to 5: Cables)
  - Areas: 12 (showing up to 5: Boundaries)
```

### Points Section
```
=== POINTS (10/45) ===
[1] POLE-001
    Type: point  |  Parent: Infrastructure|POLE
    Coords: 106.827356,-6.175234,0.000000...
    Extended: pole_type=wood height=12 status=active

[2] HP-ABC-001
    Type: point  |  Parent: Home Passed|Residential
    Coords: 106.827445,-6.175289,0.000000...
    Extended: address=Jl. Merdeka No.123 status=passed

[3] FAT-T01
    Type: point  |  Parent: Terminals|FAT
    Coords: 106.827512,-6.175345,0.000000...
    Extended: capacity=8 type=outdoor status=active

... (up to 10 points)
```

### Lines Section
```
=== LINES (5/28) ===
[11] Cable-Segment-A1
    Type: line  |  Parent: Cables|Distribution
    Coords: 106.827356,-6.175234,0.000000 106.827445,-...
    Extended: cable_type=fiber capacity=48 length=125.5

[12] Fiber-Link-02
    Type: line  |  Parent: Network|Fiber
    Coords: 106.827445,-6.175289,0.000000 106.827512,-...
    Extended: fiber_count=24 status=installed

... (up to 5 lines)
```

### Areas Section
```
=== AREAS (5/12) ===
[16] Coverage-Zone-A
    Type: area  |  Parent: Coverage|Residential
    Coords: 106.827356,-6.175234,0.000000 106.827445,-...
    Extended: area_type=residential homes_passed=250

[17] Property-Boundary-01
    Type: area  |  Parent: Boundaries|Property Lines
    Coords: 106.827512,-6.175345,0.000000 106.827601,-...
    Extended: owner=PT ABC area_sqm=1500

... (up to 5 areas)
```

### Footer
```
Total displayed: 20 placemarks

TODO: Create actual Smallworld design objects from placemarks
```

## Benefits

### 1. Organized Display
- Placemarks grouped by category
- Clear separation between types
- Shows both filtered count and total count

### 2. Targeted Preview
- Focus on relevant infrastructure types
- POLE/HP/FAT are key network components
- Cables and boundaries provide context

### 3. Performance
- Filters only what's needed
- Limits display to 20 items total
- Fast iteration through placemarks

### 4. Flexibility
- Parent folder-based filtering is extensible
- Easy to add new patterns
- Fallback to name matching ensures coverage

### 5. Information Density
- Shows sufficient detail per placemark
- Extended data provides key attributes
- Coordinate preview confirms location

## Testing Scenarios

### Test 1: KML with All Categories
**Setup:** KML file with 50+ poles, 30+ cables, 20+ boundaries

**Expected Output:**
```
Total Placemarks: 120
  - Points: 54 (showing up to 10: POLE, HP, FAT)
  - Lines: 32 (showing up to 5: Cables)
  - Areas: 22 (showing up to 5: Boundaries)

=== POINTS (10/54) ===
[1] ... [10]

=== LINES (5/32) ===
[11] ... [15]

=== AREAS (5/22) ===
[16] ... [20]

Total displayed: 20 placemarks
```

### Test 2: KML with Only Points
**Setup:** KML file with 100 poles, 0 cables, 0 boundaries

**Expected Output:**
```
Total Placemarks: 100
  - Points: 100 (showing up to 10: POLE, HP, FAT)
  - Lines: 0 (showing up to 5: Cables)
  - Areas: 0 (showing up to 5: Boundaries)

=== POINTS (10/100) ===
[1] ... [10]

Total displayed: 10 placemarks
```

### Test 3: KML with Unmatched Folders
**Setup:** KML with folders like "Equipment", "Markers", "Misc"

**Expected Output:**
```
Total Placemarks: 80
  - Points: 0 (showing up to 10: POLE, HP, FAT)
  - Lines: 0 (showing up to 5: Cables)
  - Areas: 0 (showing up to 5: Boundaries)

Total displayed: 0 placemarks
```

### Test 4: Mixed Folder Hierarchies
**Setup:** KML with deep hierarchies like "Network|Infrastructure|POLE|Active"

**Expected Output:**
Matches successfully because `pm_parent` contains full path and `matches?()` finds "POLE" within it.

### Test 5: Fallback to Name Matching
**Setup:** KML with placemarks named "POLE-001" but no parent folder

**Expected Output:**
Still matches because fallback logic checks `pm_name.matches?("pole")`

## Edge Cases Handled

### 1. Empty Parent Folder
```magik
(pm_parent = "" _andif (pm_name.matches?("pole") _orif ...))
```
Falls back to name matching.

### 2. Mixed Case Folders
```magik
_local pm_parent << pm[:parent].default("").lowercase
```
All comparisons use lowercase.

### 3. Partial Matches
```magik
pm_parent.matches?("pole")
```
Matches "POLE", "Poles", "Utility Poles", etc.

### 4. Missing Coordinates
```magik
pm[:coord].default("").subseq(1, 50.min(pm[:coord].size))
```
Handles empty coordinates gracefully.

### 5. No Extended Data
```magik
_if pm[:extended].size > 0
```
Only displays extended data if present.

## Performance Considerations

### Single Pass Filter
- One loop through all placemarks
- No re-scanning or multiple iterations
- O(n) complexity where n = total placemarks

### Early Termination
Could be optimized to stop filtering once limits reached:
```magik
_if points.size >= 10 _andif lines.size >= 5 _andif areas.size >= 5
_then
	_leave
_endif
```

### Memory Efficient
- Only stores filtered subsets
- Original placemarks rope not duplicated
- Display strings built incrementally

## Customization Options

### Adjusting Limits
Change the maximum display counts:
```magik
_local point_limit << 15.min(points.size)  # Show 15 points instead of 10
_local line_limit << 10.min(lines.size)    # Show 10 lines instead of 5
_local area_limit << 5.min(areas.size)     # Keep 5 areas
```

### Adding New Categories
Add new filter patterns:
```magik
_elif pm_type = "point"
_then
	_if pm_parent.matches?("odp") _orif
	    pm_parent.matches?("splice")
	_then
		points.add_last(pm)
	_endif
```

### Changing Filter Logic
Use different matching criteria:
```magik
# Exact match instead of pattern
_if pm_parent = "POLE"
_then
	points.add_last(pm)
_endif

# Regular expression
_if pm_parent.matches_regex?("^POLE-[0-9]+$")
_then
	points.add_last(pm)
_endif
```

## Integration with Object Creation

When implementing actual object creation, the filtered ropes can be processed directly:

```magik
# Create design objects from filtered placemarks
_for pm _over points.fast_elements()
_loop
	_if pm[:parent].lowercase.matches?("pole")
	_then
		_self.create_pole_object(pm)
	_elif pm[:parent].lowercase.matches?("fat")
	_then
		_self.create_fat_object(pm)
	_endif
_endloop

_for pm _over lines.fast_elements()
_loop
	_self.create_cable_object(pm)
_endloop

_for pm _over areas.fast_elements()
_loop
	_self.create_boundary_object(pm)
_endloop
```

## Files Modified

1. **rwwi_astri_workorder_dialog.magik**
   - Lines 614-712: `migrate_to_design()` - Added filtering and sectioned display
   - Lines 787-885: `migrate_as_temporary()` - Added filtering and sectioned display
   - Lines 880-912: `format_placemark_display()` - New helper method

2. **Documentation Created**
   - `docs/FILTERED_PLACEMARK_DISPLAY.md` (this file)

## Summary

The filtered placemark display provides:
- ✅ Organized view of 10 points (POLE/HP/FAT)
- ✅ Organized view of 5 lines (Cables)
- ✅ Organized view of 5 areas (Boundaries)
- ✅ Parent folder-based filtering (primary)
- ✅ Name-based filtering (fallback)
- ✅ Clear category sections
- ✅ Total counts and display limits
- ✅ Reusable formatting helper
- ✅ Ready for object creation workflow

This structured approach makes it easy to understand the KML content and implement targeted object creation logic for each category.
