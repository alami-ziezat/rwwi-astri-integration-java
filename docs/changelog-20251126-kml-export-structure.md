# Smallworld to KML Export System - Complete Structure Documentation

## Table of Contents
1. [System Overview](#system-overview)
2. [Smallworld Object Types Exported](#smallworld-object-types-exported)
3. [KML Structure and Folder Hierarchy](#kml-structure-and-folder-hierarchy)
4. [Styling and Visual Representation](#styling-and-visual-representation)
5. [Export Modes](#export-modes)
6. [Technical Implementation Details](#technical-implementation-details)

---

## System Overview

The `rwi_export_to_kml` module exports Smallworld Physical Network Inventory (PNI) objects to KML format for visualization in Google Earth/Google Maps. The system supports comprehensive network documentation and demand point analysis for fiber-to-the-home (FTTH) networks.

**Module Location:** `pni_custom\rwwi_astri_integration_java\magik\rwi_export_to_kml`

**Primary Class:** `rwi_export_to_kml` (exemplar)

**Export Modes:**
- **Design & Survey** - Full network infrastructure export
- **Homepass** - Demand points only for coverage analysis

---

## Smallworld Object Types Exported

### 1. Infrastructure Objects

#### 1.1 Building (STO/OLT - Central Office)
- **Smallworld Table:** `building`
- **Filter Criteria:** `type = "STO"`
- **Geometry Type:** Point (from `location.coord`)
- **KML Folder:** "OLT"
- **Exported Attributes:**
  - Name
  - Construction Status
- **Icon:** Building icon (homegardenbusiness.png)
- **Purpose:** Represents main distribution frame or OLT locations

#### 1.2 MIT Tower
- **Smallworld Table:** `mit_tower`
- **Geometry Type:** Point
- **KML Folder:** "Tower"
- **Exported Attributes:**
  - Name
  - Construction Status
  - Specification ID
- **Icon:** Letter "T" paddle
- **Purpose:** Microwave or antenna tower locations

#### 1.3 Pole (Tiang)
- **Smallworld Table:** `pole`
- **Geometry Type:** Point
- **KML Folder:** "POLE"
- **Subfolder Structure:**
  - **"Tiang Baru"** - New/planned poles (`construction_status = "Proposed"`)
  - **"Tiang Existing"** - In-service poles (`construction_status = "In Service"`)
- **Exported Attributes:**
  - Name (telco_pole_tag)
  - Construction Status
  - Material Type
  - Usage
- **Icons:**
  - New: Solid black dot (shaded_dot.png)
  - Existing: Black circle outline (donut.png)
- **Purpose:** Aerial cable support structures

#### 1.4 Underground Utility Box (UUB)
- **Smallworld Table:** `uub`
- **Types Supported:** Manhole, Handhole
- **Geometry Type:** Point
- **KML Folder:** "Manhole/Handhole"
- **Exported Attributes:**
  - Type (Manhole/Handhole)
  - Construction Status
  - Manhole Type (if applicable)
  - Position
  - CAD-n-CORE (calculated from upstream cables)
- **Icons:**
  - Manhole: Square icon
  - Handhole: Square with placemark
- **Purpose:** Underground cable access points

---

### 2. Network Equipment Objects

#### 2.1 Sheath Splice (FAT/ODP - Fiber Access Terminal)
- **Smallworld Table:** `sheath_splice`
- **Geometry Type:** Point (from `location.coord` or parent structure)
- **KML Folder:** "FAT"
- **Subfolder Structure by FTTx Network Type:**
  - Cluster
  - Feeder
  - Sub Feeder
  - Main Feeder
  - Hub Feeder
- **Special Naming Rule:** Feeder objects use `spec_id` instead of `name`
- **Exported Attributes:**
  - Name or Specification ID
  - Network Type (fttx_network_type)
  - Specification ID
  - Object Type (sheath_splice_object_type)
  - Construction Status
  - Construction Type
  - Splice Type
  - Splice Method
- **Icons:**
  - Default: Yellow star paddle (ylw-stars.png)
  - Special: Red forbidden sign for SC-OF-SM splice type (Feeder network + spec_id starts with "SC-OF-SM")
- **Purpose:** Optical splitters and distribution points

#### 2.2 Terminal Enclosure (FDT/ODC)
- **Smallworld Table:** `mit_terminal_enclosure`
- **Geometry Type:** Point
- **KML Folder:** "FDT"
- **Exported Attributes:**
  - Name
  - Specification
  - Construction Status
- **Icon:** Red triangle
- **Purpose:** Fiber distribution terminals

---

### 3. Cable Objects

#### 3.1 Sheath With Location (Kabel - Fiber Optic Cable)
- **Smallworld Table:** Accessed via `aerial_route.cables.sheath_with_loc`
- **Geometry Type:** LineString (from route_sectors)
- **KML Folder:** "KABEL"
- **Subfolder Structure by Network Type:**
  - Cluster
  - Feeder
  - Sub Feeder
  - Main Feeder
  - Hub Feeder
- **Exported Attributes:**
  - Name
  - Network Type (sheath_network_type)
  - Specification
  - Number of Core (extracted from spec_id)
  - Fiber Length (calculated_fiber_length in meters)
  - Construction Status
- **Color Coding:** Based on cable designator (D01-D12) extracted from cable name
- **Line Width:**
  - Planned cables: 6 pixels (distribution), 8 pixels (feeder)
  - Existing cables: 3 pixels (distribution), 4 pixels (feeder)
- **Purpose:** Fiber optic cable routes

---

### 4. Route Objects

#### 4.1 Underground Route
- **Smallworld Table:** `underground_route`
- **Geometry Type:** LineString (from route.sectors)
- **KML Folder:** "Underground Route"
- **Subfolder Structure:** Grouped by designator
- **Exported Attributes:**
  - Designator
  - Construction Status
  - Type (underground_route_type)
  - Length (calculated_length in mm)
  - Diameter (in mm)
  - Center Point Depth (in mm)
  - Width (in mm)
  - Upper Material Depth (in mm)
  - Core Material Depth (in mm)
- **Style:** Purple line, 11 pixels wide
- **Purpose:** Underground conduit/duct paths

#### 4.2 Aerial Route
- **Smallworld Table:** `aerial_route`
- **Note:** Used to discover cables but routes themselves not directly exported
- **Purpose:** Reference for finding cables within export area

---

### 5. Demand Points (Homepass Export Only)

#### 5.1 FTTH Demand Point
- **Smallworld Table:** `ftth!demand_point`
- **Geometry Type:** Point
- **KML Folder:** "Homepass"
- **Exported Attributes:**
  - Name (minimal description)
- **Icon:** White open diamond
- **Export Mode:** Only in "Homepass" mode
- **Purpose:** Customer premises or potential service locations

---

### 6. Project Area

#### 6.1 Design Area Boundary
- **Source:** Design Manager project area boundary
- **Geometry Type:** Polygon
- **KML Folder:** "Misc"
- **Exported Attributes:**
  - Project Name (inf_project)
  - Jumlah Homepass (homepass count, in homepass mode only)
- **Style:** Green outline, no fill
- **Purpose:** Defines project extent

---

## KML Structure and Folder Hierarchy

### Complete Folder Structure

#### Design & Survey Mode
```
Document
├── OLT
│   └── [STO Building Placemarks]
├── Underground Route
│   ├── [Designator 1]
│   │   └── [Route Placemarks]
│   ├── [Designator 2]
│   └── ...
├── POLE
│   ├── Tiang Baru (New/Planned)
│   │   └── [Pole Placemarks]
│   └── Tiang Existing (In Service)
│       └── [Pole Placemarks]
├── Manhole/Handhole
│   └── [UUB Placemarks]
├── FAT
│   ├── Cluster
│   │   └── [Sheath Splice Placemarks]
│   ├── Feeder
│   ├── Sub Feeder
│   ├── Main Feeder
│   └── Hub Feeder
├── FDT
│   └── [Terminal Enclosure Placemarks]
├── Tower
│   └── [Tower Placemarks]
├── KABEL
│   ├── Cluster
│   │   └── [Cable LineStrings]
│   ├── Feeder
│   ├── Sub Feeder
│   ├── Main Feeder
│   └── Hub Feeder
└── Misc
    ├── [Design Area Polygon]
    └── [Multi-Cable Info Points]
```

#### Homepass Mode
```
Document
├── Homepass
│   └── [Demand Point Placemarks]
└── Misc
    └── [Design Area Polygon with homepass count]
```

### Export Order (Design & Survey Mode)
1. STO (OLT)
2. Underground Route
3. Pole (separated by construction status)
4. UUB (Manhole/Handhole)
5. Sheath Splice (FAT/ODP)
6. Terminal Enclosure (FDT/ODC)
7. Tower
8. Sheath With Location (Cables)
9. Misc (Design Area, Info Points)

---

## Styling and Visual Representation

### Point Feature Styles

| Object Type | Icon | Color | Size (Normal) | Size (Highlight) |
|-------------|------|-------|---------------|------------------|
| **STO Building** | homegardenbusiness.png | Default | 1.1 | 1.3 |
| **Tiang Planned** | shaded_dot.png | Black | 1.1 | 1.3 |
| **Tiang Existing** | donut.png | Black | 0.9 | 1.1 |
| **ODP (Default)** | ylw-stars.png | Yellow | 1.1 | 1.3 |
| **ODP (SC-OF-SM)** | forbidden.png | Red | 1.1 | 1.3 |
| **ODC** | triangle.png | Red | 2.0 | 2.36 |
| **Manhole** | square.png | Default | 1.1 | 1.3 |
| **Handhole** | placemark_square.png | Default | 1.1 | 1.3 |
| **Tower** | T.png | Default | 1.1 | 1.3 |
| **Multi Cable Info** | info-i.png | Default | 1.1 | 1.3 |
| **Demand Point** | open-diamond.png | White | 1.1 | 1.3 |

### Line Feature Styles

#### Underground Route
- **Color:** Purple (ff7800F0)
- **Width:** 11 pixels
- **Style ID:** m_urute

#### Cables - Planned (Construction Status ≠ "In Service")

| Designator | Color | Hex Color | Width | Style ID |
|------------|-------|-----------|-------|----------|
| Default/Unset | Cyan | ff00ffff | 6px | m_kabel_unset |
| D01 | Red | ffff0000 | 6px | m_kabel_1 |
| D02 | Orange | ff0055ff | 6px | m_kabel_2 |
| D03 | Green | ff00ff55 | 6px | m_kabel_3 |
| D04 | Brown | ff0055aa | 6px | m_kabel_4 |
| D05 | Gray | ffaaaaaa | 6px | m_kabel_5 |
| D06 | White | ffffffff | 6px | m_kabel_6 |
| D07 | Blue | ff0000ff | 6px | m_kabel_7 |
| D08 | Black | ff000000 | 6px | m_kabel_8 |
| D09 | Cyan | ff00ffff | 6px | m_kabel_9 |
| D10 | Pink | ffb95eff | 6px | m_kabel_10 |
| D11 | Magenta | ffff00aa | 6px | m_kabel_11 |
| D12 | Light Yellow | ffffffaa | 6px | m_kabel_12 |

#### Cables - Existing (Construction Status = "In Service")
- Same colors as planned cables
- **Width:** 3 pixels (half of planned)
- **Style ID:** Append "_e" to planned style (e.g., m_kabel_e_1)

#### Feeder Cables
- **Color:** Dark Red (FFF00014)
- **Width:** 8 pixels (planned), 4 pixels (existing)
- **Style ID:** m_kabel_f (planned), m_kabel_f_e (existing)

### Style Selection Logic

#### Cable Style Selection Algorithm:
```
1. Determine network type:
   - If "Feeder" → Use feeder style base
   - Else → Use distribution style base

2. Check construction status:
   - If "In Service" → Append "_e" to style ID
   - Else → Use base style ID

3. For distribution cables (non-feeder):
   - Extract designator from cable name:
     * Pattern: [xxx]/[Dnn].[xxx]
     * Example: "ABC/D05.XYZ" → "5"
   - Append designator number to style
   - If extraction fails → Use default/unset style

4. Resulting Style IDs:
   - Feeder Planned: m_kabel_f
   - Feeder Existing: m_kabel_f_e
   - Distribution D05 Planned: m_kabel_5
   - Distribution D05 Existing: m_kabel_e_5
   - Distribution Unset Planned: m_kabel_unset
   - Distribution Unset Existing: m_kabel_e_unset
```

#### Pole Style Selection:
```
If construction_status = "Proposed"
    → m_tiang (solid black dot)
Else if construction_status = "In Service"
    → m_tiang_e (black circle outline)
Else
    → m_tiang_e (default to existing)
```

#### ODP Style Selection:
```
If fttx_network_type = "Feeder" AND spec_id starts with "SC-OF-SM"
    → m_odp_scofsm (forbidden sign)
Else
    → m_odp (yellow star)
```

---

## Export Modes

### 1. Design & Survey Mode

**Purpose:** Comprehensive network documentation for construction, maintenance, and planning.

**Exported Objects:**
- All infrastructure (Buildings, Towers, Poles, UUBs)
- All network equipment (FAT/ODP, FDT/ODC)
- All cables and routes
- Project boundary

**Filename Pattern:** `[project_name].kml`

**Use Cases:**
- Site survey planning
- Construction documentation
- Network asset inventory
- Field crew reference
- As-built documentation

**Visual Features:**
- Color-coded cables by distribution path
- Status differentiation (planned vs. existing)
- Hierarchical organization by object type and network layer

---

### 2. Homepass Mode

**Purpose:** Coverage and demand analysis for marketing and planning.

**Exported Objects:**
- Demand points (FTTH premises)
- Project boundary with homepass count

**Filename Pattern:** `[project_name]_homepass.kml`

**Use Cases:**
- Coverage map generation
- Marketing territory planning
- Demand analysis
- Sales target visualization
- Coverage gap identification

**Visual Features:**
- Minimal clutter (only demand points shown)
- Aggregate count in project boundary description
- White diamond icons for easy visibility

---

## Technical Implementation Details

### 1. Coordinate Transformation

**Source Coordinate System:** Project-specific (typically UTM or local grid)

**Target Coordinate System:** WGS84 Geographic (world_longlat_wgs84_degree)

**Transformation Method:**
```magik
convert_coord(p_coord)
    # Uses Smallworld coordinate system transformation
    _local target_cs << coordinate_system.new_proj_long_lat_from_dataset(
        .gc, :world_longlat_wgs84_degree)
    _local wgs84_coord << .coordinate_transform.convert(p_coord, target_cs)
    _return wgs84_coord
```

**Output Format:** `longitude,latitude,0` (KML standard)

---

### 2. Duplicate Detection

**Problem:** Cables may appear on multiple routes, leading to duplicates in export.

**Solution:** `cek_duplikat()` method
```magik
_method rwi_export_to_kml.cek_duplikat(p_swl, p_collection)
    ## Checks if cable (p_swl) already exists in collection
    ## Returns true if duplicate found, false otherwise
    _for swl _over p_collection.fast_elements()
    _loop
        _if swl = p_swl
        _then
            _return _true
        _endif
    _endloop
    _return _false
_endmethod
```

**Usage:** Before adding cable to export collection, check if it's already present.

---

### 3. Spatial Selection Method

All objects selected using predicate-based spatial queries:

```magik
# Point objects (buildings, poles, towers, etc.)
.gc[:table_name].select(predicate.within(:location, p_area))

# Linear objects (routes)
.gc[:table_name].select(predicate.within(:route, p_area))
```

**Where:**
- `p_area` is the design boundary (boundary geometry)
- `:location` or `:route` is the geometry field name
- `predicate.within()` tests if geometry falls within boundary

---

### 4. Cable Discovery Pattern

**Challenge:** Cables are not spatially indexed directly; they're associated with routes.

**Solution:** Two-step process
1. Find all aerial routes within the export area
2. For each route, iterate through associated cables
3. Extract sheath_with_loc geometry from each cable
4. Apply duplicate detection before adding to export collection

```magik
_for aroute _over .gc[:aerial_route].select(
    predicate.within(:route, p_area)).fast_elements()
_loop
    _for kabel _over aroute.cables.fast_elements()
    _loop
        swl << kabel.sheath_with_loc
        _if swl _isnt _unset _andif _not _self.cek_duplikat(swl, all_cables)
        _then
            all_cables.add(swl)
        _endif
    _endloop
_endloop
```

---

### 5. Network Type Grouping

Objects organized by network hierarchy using hash tables:

```magik
htswl << hash_table.new()
htswl[:Cluster] << rope.new()
htswl[:Feeder] << rope.new()
htswl[:"Sub Feeder"] << rope.new()
htswl[:"Main Feeder"] << rope.new()
htswl[:"Hub Feeder"] << rope.new()

_for swl _over all_cables.fast_elements()
_loop
    net_type << swl.sheath_network_type.default(:Cluster)
    htswl[net_type].add(swl)
_endloop
```

**Result:** Separate folders in KML for each network type.

---

### 6. XML Special Character Handling

**Problem:** Attribute values may contain XML special characters.

**Solution:** `replace_special_xml_char()` method

```magik
_method rwi_export_to_kml.replace_special_xml_char(p_string)
    ## Escapes XML special characters in string
    _local result << p_string.copy()
    result << result.substitute_string("&", "&amp;")
    result << result.substitute_string("\"", "&quot;")
    result << result.substitute_string("'", "&apos;")
    result << result.substitute_string("<", "&lt;")
    result << result.substitute_string(">", "&gt;")
    _return result
_endmethod
```

**Character Mappings:**
- `&` → `&amp;`
- `"` → `&quot;`
- `'` → `&apos;`
- `<` → `&lt;`
- `>` → `&gt;`

---

### 7. Filename Sanitization

**Problem:** Project names may contain characters invalid for filenames.

**Solution:** Character replacement

```magik
p_name.substitute_character(%\, %_)
p_name.substitute_character(%/, %_)
p_name.substitute_character(%:, %_)
p_name.substitute_character(%*, %_)
p_name.substitute_character(%?, %_)
p_name.substitute_character(%", %_)
p_name.substitute_character(%<, %_)
p_name.substitute_character(%>, %_)
p_name.substitute_character(%|, %_)
```

**Restricted Characters:** `\ / : * ? " < > |`

---

### 8. CAD Core Information

**Purpose:** Show available fiber capacity at underground structures.

**Calculation Method:**
1. Get all cables entering the structure (UUB/manhole)
2. For each cable, call `unset_loc_size()` to get unused fiber locations
3. Format as "CAD-n-CORE" where n is the total unset locations

**Example Output:**
- "CAD-24-CORE" means 24 fiber cores available
- "CAD-0-CORE" means fully utilized

**Added To:** UUB (Manhole/Handhole) descriptions

---

### 9. Multi-Cable Information Points

**Purpose:** Mark locations where route congestion occurs (multiple cables on same route).

**Logic:**
1. For each underground/aerial route segment
2. Count number of cables on that segment
3. If count > 1, create info placemark at segment midpoint
4. List all cable names and specs in description

**Icon:** Information "i" icon

**Status:** Code exists but partially commented out in current implementation.

---

### 10. Error Handling

**Pattern:** Try-catch blocks around attribute extraction

```magik
_try
    value << object.attribute_name
_when error
    value << "N/A"
_endtry
```

**Purpose:** Prevent export failure when optional attributes are missing or unset.

**Result:** Missing values displayed as "N/A" in KML descriptions.

---

## Key Design Decisions

### 1. FTTx-Focused Architecture
System designed specifically for fiber-to-the-home networks with focus on:
- Optical splitter locations (FAT/ODP)
- Distribution cable paths (color-coded by path)
- Customer demand points (homepass)
- Network hierarchy (Cluster/Feeder/Sub Feeder)

### 2. Indonesian Terminology
Labels use Indonesian telecom industry terms:
- **STO** (Sentral Telepon Otomat) = Central Office
- **FAT** (Fiber Access Terminal) = ODP (Optical Distribution Point)
- **FDT** (Fiber Distribution Terminal) = ODC (Optical Distribution Cabinet)
- **Tiang** = Pole
- **Kabel** = Cable

### 3. Status-Based Visual Differentiation
Clear visual distinction between planned and existing infrastructure:
- **Planned:** Thicker lines, solid icons, full opacity
- **Existing:** Thinner lines, outline icons, indicating already built

### 4. Color-Coded Distribution Paths
12 distinct colors for cable designators (D01-D12) enable:
- Visual path tracing from central office to customer
- Quick identification of cable distribution hierarchy
- Easy correlation with construction drawings

### 5. Hierarchical Folder Organization
Deep folder structure improves usability in Google Earth:
- Toggle entire object types on/off
- Focus on specific network layers
- Separate planned from existing infrastructure
- Group by geographic designators

### 6. Template-Based Styling
External KML template (`template.kml`) contains all style definitions:
- Consistent visual standards
- Easy style updates without code changes
- Reusable across multiple projects

### 7. Design Manager Integration
Tight coupling with Smallworld Design Manager:
- Export triggered from design context
- Boundary derived from design area
- Construction status from design workflow
- Supports change management workflows

---

## Limitations and Incomplete Features

### 1. Aerial Route Export
- Code includes `write_aroute()` method
- Method is defined but not called in main export flow
- Aerial routes currently only used to discover cables

### 2. Multi-Cable Information Points
- Logic partially implemented
- Some code sections commented out
- May not generate info points in all cases

### 3. Single Export Area
- Exports one design area at a time
- No support for multi-area or portfolio exports
- Boundary must be defined in Design Manager

### 4. Static Style Template
- Styles defined in external template file
- Adding new object types requires template modification
- No dynamic style generation

### 5. Fixed Designator Range
- Supports only D01-D12 designators
- Additional designators fall back to default color
- No automatic color assignment for new designators

---

## File Structure

### Module Directory Layout
```
rwi_export_to_kml/
├── module.def                    # Module metadata
├── source/
│   ├── load_list.txt            # Source loading order
│   ├── rwi_export_to_kml.magik  # Main export class
│   └── ...
└── resources/
    └── base/
        └── data/
            └── template.kml      # KML style template
```

### Key Files

**rwi_export_to_kml.magik**
- Main export class definition
- Coordinate transformation logic
- KML generation methods
- Object iteration and formatting

**template.kml**
- Style definitions (IconStyle, LineStyle, PolyStyle)
- Icon references
- Color definitions
- Base KML document structure

---

## Usage Workflow

### 1. User Initiates Export
- User opens design in Design Manager
- Selects export option (Design & Survey or Homepass)
- Specifies output location

### 2. System Processes Export
1. Get design boundary geometry
2. Transform boundary to database coordinate system
3. Query all objects within boundary
4. Group objects by type and network hierarchy
5. Transform coordinates to WGS84
6. Format KML with styles and descriptions
7. Write output file

### 3. User Views in Google Earth
1. Open generated KML file in Google Earth
2. Navigate folder structure
3. Toggle layers on/off as needed
4. Inspect object attributes in descriptions
5. Export/share as needed

---

## Integration Points

### Smallworld Components Used
- **Design Manager** - Project area, construction status
- **PNI Data Model** - Infrastructure tables
- **FTTH Module** - Demand points, network types
- **Geometry Engine** - Coordinate transformation, spatial predicates
- **Datamodel Engine** - Object queries, attribute access

### External Dependencies
- **Google Earth/Maps** - KML rendering
- **Template File** - Style definitions (template.kml)
- **WGS84 Coordinate System** - Geographic coordinate system definition

---

## Maintenance Notes

### Adding New Object Types
1. Create query method to select objects
2. Add coordinate transformation logic
3. Create KML formatting method
4. Add style definition to template.kml
5. Add folder creation to main export method
6. Update documentation

### Modifying Styles
1. Edit template.kml to update colors/icons
2. Ensure style IDs match code references
3. Test with sample export
4. No code changes needed if style IDs unchanged

### Extending Network Types
1. Add new network type to hash table initialization
2. Update grouping logic
3. Add folder creation for new type
4. Update documentation

---

## Performance Considerations

### Large Datasets
- Spatial predicates limit query scope to export area
- Duplicate detection prevents redundant processing
- Rope collections used for efficient growth
- Hash tables for O(1) grouping operations

### Memory Usage
- All objects loaded into memory during export
- Consider export area size for very large projects
- No streaming or chunked processing

### Export Time
- Depends on object count within export area
- Coordinate transformation for each point/vertex
- XML character escaping for all attributes
- Typical export: seconds to minutes

---

## Conclusion

The `rwi_export_to_kml` system provides comprehensive KML export capabilities for Smallworld FTTH networks, with sophisticated styling, hierarchical organization, and dual export modes. The system is mature and feature-complete for its intended use cases, with some room for enhancement in aerial route export and multi-cable information points.

**Key Strengths:**
- Comprehensive object type coverage
- Sophisticated visual differentiation
- Hierarchical organization
- Dual export modes for different use cases
- Robust error handling

**Primary Use Cases:**
- Construction documentation
- Field crew reference
- Network planning
- Coverage analysis
- Marketing territory planning

---

**Document Version:** 1.0
**Last Updated:** 2025-11-26
**Author:** Generated from codebase analysis
**Module Version:** Smallworld 5.x
