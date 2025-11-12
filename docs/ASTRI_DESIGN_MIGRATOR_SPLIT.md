# ASTRI Design Migrator - File Structure Documentation

## Date: 2025-11-10
## Status: ✅ COMPLETED

## Overview

The ASTRI Design Migrator codebase has been refactored from a single monolithic file (2,860 lines) into 12 modular files organized by functionality. This improves code maintainability, readability, and testability.

## File Organization

### Main Entry Point

**astri_design_migrator.magik** (460 lines)
- **Purpose**: Main coordinator and entry point
- **Responsibilities**:
  - Exemplar definition with all instance slots
  - Initialization (`new()`, `init()`)
  - Project and design creation (`create_project_and_design()`)
  - Migration orchestration (`migrate_placemarks()`)
  - Statistics reporting (`print_statistics()`)
  - Boundary area extraction helpers

### Utility Methods

**astri_migrator_utilities.magik** (740 lines)
- **Purpose**: Shared utility methods used by all migrators
- **Contents**:
  - **Geometry Parsing**: `parse_point_geometry()`, `parse_line_geometry()`, `parse_area_geometry()`
  - **Type Classification**: `is_pole?()`, `is_cable?()`, `is_demand_point?()`, `is_splice?()`, `is_figure_eight?()`, `is_micro_cell?()`, `is_olt?()`, `is_riser?()`, `is_access_point?()`, `is_sling_wire?()`
  - **Matching Methods**: `match_pole_type()`, `match_pole_status()`, `match_line()`, `match_segment()`, `match_cable_core()`, `match_closure_type()`, `match_core()`
  - **Scanning Methods**: `scan_pole_st()`, `scan_olt()`, `scan_ar_on_design()`, `splitter_inside_cell()`
  - **Helper Methods**: `get_latlong()`, `truncate_folders()`

### Object-Specific Migrators

#### 1. **astri_pole_migrator.magik** (206 lines)
- **Purpose**: Pole creation from KMZ placemarks
- **Method**: `create_pole(pm)`
- **Features**:
  - Folder validation for pole patterns
  - Pole type detection and STF item code mapping
  - Cable snapping to nearby aerial routes (500m buffer)
  - Duplicate detection within 200m radius
  - Update existing placeholder poles or create new poles
  - Statistics tracking (created, updated, skipped)

#### 2. **astri_aerial_route_migrator.magik** (393 lines)
- **Purpose**: Aerial route and cable creation
- **Method**: `create_aerial_route(pm)`
- **Features**:
  - Cable folder validation (CABLE, KABEL, DISTRIBUTION CABLE, SLING WIRE)
  - Cable core detection (12-576 cores)
  - Pole detection at start/end points (500m radius)
  - Placeholder pole creation when poles not found
  - Additional AR segments to connect to poles
  - Sheath creation with structure placement
  - Sling wire support

#### 3. **astri_demand_point_migrator.magik** (240 lines)
- **Purpose**: Demand point (homepass) creation
- **Method**: `create_demand_point(pm)`
- **Features**:
  - Multi-level folder validation (homepass, hp, reduce, customer)
  - Annotation geometry creation
  - Micro cell detection for splitter_id
  - Optical splitter lookup for splice coordinates
  - Boundary intersection for administrative data (kecamatan, kelurahan, kabupaten, etc.)
  - Customer premise creation with full attributes
  - Optional building creation for GEDUNG type
  - Duplicate detection

#### 4. **astri_splice_migrator.magik** (288 lines)
- **Purpose**: Sheath splice (closure/FDT/FAT) creation
- **Method**: `create_sheath_splice(pm)`
- **Features**:
  - Closure type detection (Join Closure, FDT, FAT)
  - Core count detection (24-576 cores)
  - Pole association (200m radius)
  - Cable snapping (500m buffer)
  - Optical splitter creation for FDT
  - Duplicate detection

#### 5. **astri_figure_eight_migrator.magik** (137 lines)
- **Purpose**: Figure eight (slack/coil) creation
- **Method**: `create_figure_eight(pm)`
- **Features**:
  - Pattern detection (slack, slack hanger, coil)
  - Pole search (700m radius)
  - Aerial route snapping
  - Cable connection
  - Duplicate detection

#### 6. **astri_olt_migrator.magik** (77 lines)
- **Purpose**: Optical Line Terminal creation
- **Method**: `create_olt(pm)`
- **Features**:
  - Name-based detection ("*OLT*")
  - Duplicate detection (500m radius)
  - mit_hub collection usage
  - Statistics tracking

#### 7. **astri_riser_migrator.magik** (60 lines)
- **Purpose**: Riser cable creation
- **Method**: `create_riser(pm)`
- **Features**:
  - Name-based detection ("*riser*", case-insensitive)
  - Point-based riser object representation
  - Uses riser collection (not sheath_with_loc)
  - Duplicate detection
  - Statistics tracking

#### 8. **astri_access_point_migrator.magik** (57 lines)
- **Purpose**: Generic access point creation
- **Method**: `create_access_point(pm)`
- **Features**:
  - Catch-all for unclassified point types
  - Exclusion logic (not pole, splice, figure_eight, OLT, riser, demand_point)
  - Duplicate detection
  - Statistics tracking

#### 9. **astri_sheath_migrator.magik** (29 lines)
- **Purpose**: Sheath/splice area creation
- **Method**: `create_sheath(pm)`
- **Features**:
  - Area-based sheath creation
  - Folder filtering
  - Statistics tracking

#### 10. **astri_micro_cell_migrator.magik** (183 lines)
- **Purpose**: Micro cell and macro cell (zone) creation
- **Methods**:
  - `create_micro_cell(pm)` - Micro cell creation with LINE pattern detection
  - `create_area_based_object(pm)` - Generic area-based object creation
- **Features**:
  - LINE pattern detection (Line A-F)
  - Boundary pattern matching for macro cells
  - Splitter association
  - ftth!zone collection usage
  - Statistics tracking

## Migration Process Flow

The `migrate_placemarks()` method orchestrates a **6-pass migration process**:

### Pass 1: Cables and Aerial Routes
- **Why first?** Other objects (poles, splices, figure eights) need to snap to aerial routes
- **Creates**: Aerial routes, sheaths, cables
- **Method**: `create_aerial_route(pm)`

### Pass 2: Poles
- **Why second?** Poles are created after aerial routes so they can snap to cables
- **Creates**: Poles (updates placeholder poles from Pass 1)
- **Method**: `create_pole(pm)`

### Pass 3: Point-Based Objects
- **Creates**: Sheath splices, figure eights, OLTs, demand points
- **Methods**: `create_sheath_splice()`, `create_figure_eight()`, `create_olt()`, `create_demand_point()`

### Pass 4: Area-Based Objects (Micro Cells)
- **Creates**: Micro cells, macro cells (zones)
- **Method**: `create_micro_cell()`

### Pass 5: Risers
- **Creates**: Riser cables
- **Method**: `create_riser()`

### Pass 6: Access Points
- **Why last?** Catch-all for any remaining unclassified point objects
- **Creates**: Access points
- **Method**: `create_access_point()`

## Load Order (load_list.txt)

Files must be loaded in this order to ensure all dependencies are met:

```
# Main entry point - defines exemplar
source/astri_design_migrator.magik

# Utility methods - needed by all migrators
source/astri_migrator_utilities.magik

# Object-specific migrators (order doesn't matter)
source/astri_pole_migrator.magik
source/astri_aerial_route_migrator.magik
source/astri_demand_point_migrator.magik
source/astri_splice_migrator.magik
source/astri_figure_eight_migrator.magik
source/astri_olt_migrator.magik
source/astri_riser_migrator.magik
source/astri_access_point_migrator.magik
source/astri_sheath_migrator.magik
source/astri_micro_cell_migrator.magik
```

## Statistics Tracked

The migrator tracks comprehensive statistics for each object type:

- **Poles**: created, updated, skipped, errors
- **Aerial Routes**: created, errors
- **Demand Points**: created, skipped, errors
- **Sheath Splices**: created, skipped, errors
- **Figure Eights**: created, skipped, errors
- **OLTs**: created, skipped, errors
- **Risers**: created, skipped, errors
- **Access Points**: created, skipped, errors
- **Sheaths**: created, errors
- **Micro Cells**: created, skipped, errors

Statistics are printed at the end of migration via `print_statistics()`.

## Key Design Patterns

### 1. Duplicate Detection
Most migrators use spatial buffers to detect nearby existing objects:
- Poles: 200m radius
- Splices: 200m radius
- OLT: 500m radius
- Figure Eights: 700m radius
- Aerial routes: 500m buffer for pole snapping

### 2. Folder-Based Classification
Objects are classified based on KMZ folder hierarchy:
- **Poles**: `*|pole|*`
- **Cables**: `*cable*`, `*kabel*`, `*sling wire*`
- **Demand Points**: `*homepass*`, `*hp*`, `*reduce*`, `*customer*`
- **Splices**: `*closure*`, `*joint*`, `*fdt*`, `*fat*`
- **Figure Eights**: `*slack*`, `*coil*`

### 3. Wildcard Matching
All folder matching uses Magik's `matches?()` with wildcards (`*`) for flexible pattern matching.

### 4. Error Handling
All create methods use `_try _with errCon` blocks to catch and log errors without stopping migration.

### 5. Geometry Conversion
WGS84 coordinates from KMZ are converted to local coordinate system using transforms:
```magik
_local cs_wgs84 << .ace_view.collections[:sw_gis!coordinate_system].at(:world_longlat_wgs84_degree)
_local cs_local << .database.world.coordinate_system
.transform << transform.new_converting_cs_to_cs(cs_wgs84, cs_local)
```

## Benefits of Splitting

### Before (Single File)
- ❌ 2,860 lines in one file
- ❌ Difficult to navigate and maintain
- ❌ Hard to understand individual object migration logic
- ❌ Risk of merge conflicts in team development
- ❌ Difficult to test individual migrators

### After (12 Files)
- ✅ Average 200 lines per file
- ✅ Clear separation of concerns
- ✅ Easy to find and modify specific object migration
- ✅ Reduced merge conflict risk
- ✅ Easier to test individual migrators
- ✅ Main file shows high-level orchestration clearly
- ✅ Utilities are reusable across all migrators

## Maintenance Guidelines

### Adding New Object Type
1. Create new file: `astri_[type]_migrator.magik`
2. Add `create_[type](pm)` method
3. Add `is_[type]?(pm)` to `astri_migrator_utilities.magik`
4. Update `migrate_placemarks()` to call new method
5. Add statistics tracking
6. Update `load_list.txt`
7. Update this documentation

### Modifying Existing Migrator
1. Locate correct file (e.g., `astri_pole_migrator.magik`)
2. Modify method
3. Test changes
4. Update documentation if logic changes

### Adding Utility Method
1. Add to `astri_migrator_utilities.magik`
2. Follow existing patterns (pragma, documentation)
3. Make method private (`_private _method`)
4. Add to appropriate section (geometry, matching, scanning, etc.)

## Testing

Test the migration with sample KMZ files:
```magik
# Create migrator
_local db << gis_program_manager.databases[:gis]
_local migrator << astri_design_migrator.new(db, "WO-12345", "uuid-12345", "POP001")

# Parse KMZ
_local parser << astri_kmz_parser.new("C:\\path\\to\\file.kmz")
_local placemarks << parser.parse()

# Create project and design
migrator.create_project_and_design(placemarks, "WO-12345", "ABC123", "Test Cluster")

# Migrate objects
migrator.migrate_placemarks(placemarks)

# Print statistics
migrator.print_statistics()
```

## References

- **Original file**: `astri_design_migrator.magik` (2,860 lines) - archived
- **Refactored files**: 12 files totaling ~2,850 lines
- **Module**: rwwi_astri_integration
- **Package**: sw
- **Classification level**: basic
- **Topic**: astri_integration

---

**Created**: 2025-11-10
**Last Updated**: 2025-11-10
**Author**: Claude Code (AI Assistant)
