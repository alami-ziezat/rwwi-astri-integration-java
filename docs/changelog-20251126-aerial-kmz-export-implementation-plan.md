# Aerial KMZ Export Implementation Plan

## Document Information
- **Purpose:** Implementation plan for new KML export following FORMAT KMZ AERIAL hierarchy
- **Target Module:** `rwi_export_to_kml`
- **New Exemplar:** `rwi_export_to_aerial_kmz`
- **Reference Documents:**
  - Source: `changelog-20251126-kml-export-structure.md` (existing export system)
  - Target: `changelog-20251126-format-kmz-aerial-hierarchy.md` (desired hierarchy)
- **Date:** 2025-11-26
- **Status:** PLANNING (Awaiting approval for execution)

---

## Executive Summary

This plan details the creation of a new KML export system (`rwi_export_to_aerial_kmz`) that generates KML files conforming to the FORMAT KMZ AERIAL specification. The new exporter will organize network objects into the standardized three-level hierarchy (FEEDER → SUBFEEDER → CLUSTER) with proper folder structures, styles, and naming conventions.

**Key Differences from Existing Exporter:**

| Aspect | Current (rwi_export_to_kml) | New (rwi_export_to_aerial_kmz) |
|--------|----------------------------|----------------------------------|
| **Hierarchy** | Flat by object type | 3-level network hierarchy |
| **Top Folders** | OLT, Underground Route, POLE, FAT, FDT, KABEL | FEEDER CODE.KMZ, SUBFEEDER CODE.KMZ, CLUSTER CODE.KMZ |
| **Pole Organization** | By status (Tiang Baru/Existing) | By type+owner+height+class |
| **Cable Organization** | By network type (Cluster/Feeder) | By network level + designator |
| **Cluster Structure** | Single level | **Dynamic:** One folder per FDT, each with 4 LINEs (A/B/C/D) |
| **HP Organization** | Single "Homepass" folder | HP COVER (5 zones) + HP UNCOVER per LINE |
| **Mixed Network Support** | N/A | **Auto-detects and exports all levels present in area** |
| **Style Template** | template.kml | sample.kml styles |
| **Export Modes** | Design & Survey, Homepass | Single KMZ with all detected network levels |

---

## Section 1: Requirements Analysis

### 1.1 Functional Requirements

#### FR-1: Network Level Export Modes
- **FR-1.1** Export FEEDER CODE.KMZ (backbone network only)
- **FR-1.2** Export SUBFEEDER CODE.KMZ (intermediate distribution)
- **FR-1.3** Export CLUSTER CODE.KMZ (last-mile customer access)
- **FR-1.4** Support export of single level or all levels combined

#### FR-2: Dynamic Folder Hierarchy Generation
- **FR-2.1** Create FEEDER CODE.KMZ with 15 standard folders
- **FR-2.2** Create SUBFEEDER CODE.KMZ with 14 standard folders
- **FR-2.3** Create CLUSTER CODE.KMZ with **DYNAMIC structure based on detected FDTs**:
  - BOUNDARY CLUSTER folder (named from design/scheme name)
  - One FDT_[FDT_NAME] folder per FDT detected in area (no "CLUSTER" prefix)
  - Each FDT folder contains 4 LINE folders (A, B, C, D) by default
  - 14 subfolders per LINE including HP COVER with 5 zones
  - **Note:** If area contains multiple FDTs, create separate FDT folders for each
  - **FDT Detection:** FDT can be either `mit_terminal_enclosure` OR `sheath_splice` with type="FDT"
- **FR-2.4** Support mixed network levels in single export area:
  - Detect all network levels present (FEEDER, SUBFEEDER, CLUSTER)
  - Generate appropriate folder structure for each level found
  - Example: If area has 1 feeder cable, 3 subfeeder cables, and 2 FDTs, generate folders for all three levels

#### FR-3: Object Type Mapping
- **FR-3.1** Map Smallworld objects to correct KML folders based on network level
- **FR-3.2** Apply correct folder naming conventions (e.g., "NEW POLE 7-4" not "Tiang Baru")
- **FR-3.3** Determine network level from object attributes (network_type, fttx_network_type)

#### FR-4: Pole Classification
- **FR-4.1** Extract pole height from attributes (7m, 9m)
- **FR-4.2** Extract pole class from attributes (2.5, 3, 4, 5)
- **FR-4.3** Extract ownership from attributes (EMR, PARTNER)
- **FR-4.4** Combine into folder name: "[STATUS] POLE [OWNER] [HEIGHT]-[CLASS]"
- **FR-4.5** Place pole in appropriate network level folders based on class

#### FR-5: Style Application
- **FR-5.1** Apply style definitions from sample.kml reference
- **FR-5.2** Use correct StyleMap IDs for each object type
- **FR-5.3** Generate BalloonStyle popups with object attributes
- **FR-5.4** Maintain normal/highlight state pairs for interactive elements

#### FR-6: Homepass Organization
- **FR-6.1** Determine serving LINE (A/B/C/D) for each HP point
- **FR-6.2** Classify as HP COVER or HP UNCOVER based on coverage status
- **FR-6.3** Assign to zone (01-05) within HP COVER based on geographic location
- **FR-6.4** Use zone naming: A01-A05, B01-B05, C01-C05, D01-D05

#### FR-7: Cable Organization
- **FR-7.1** Place feeder cables in FEEDER CODE.KMZ CABLE folder
- **FR-7.2** Place subfeeder cables in SUBFEEDER CODE.KMZ CABLE folder
- **FR-7.3** Place distribution cables in CLUSTER LINE [X] DISTRIBUTION CABLE folder
- **FR-7.4** Maintain cable-route relationships

#### FR-8: Equipment Placement
- **FR-8.1** Place OLT only in FEEDER CODE.KMZ
- **FR-8.2** Place FDT in CLUSTER CODE.KMZ:
  - FDT can be `mit_terminal_enclosure` OR `sheath_splice` with type="FDT"
  - Single FDT: Place in top-level FDT folder
  - Multiple FDTs: Place each in its own FDT_[NAME] folder
- **FR-8.3** Place FAT in appropriate LINE folder (FAT is `sheath_splice` with type="FAT")
- **FR-8.4** Place JOINT CLOSURE in FEEDER or SUBFEEDER based on network level:
  - Source: `sheath_splice` with type="Joint Closure" or "Closure"
- **FR-8.5** Place SLACK HANGER (Figure Eight) and SLING WIRE in appropriate folders:
  - Source: `mit_figure_eight` (Slack Hanger) and `mit_sling_wire` (Sling Wire)
  - Use `line_type` field to determine LINE assignment (A/B/C/D)
  - Slack Hanger: Present at all network levels (FEEDER, SUBFEEDER, CLUSTER)
  - Sling Wire: Only in CLUSTER network level
- **FR-8.6** Place HANDHOLE 80X80X130 in FEEDER only

#### FR-9: Boundary Generation
- **FR-9.1** Export network-level boundaries named from design/scheme:
  - FEEDER boundary → "FEEDER [DESIGN_NAME]" or "BOUNDARY FEEDER"
  - SUBFEEDER boundary → "SUBFEEDER [DESIGN_NAME]" or "BOUNDARY SUBFEEDER"
  - CLUSTER boundary → "CLUSTER [DESIGN_NAME]" or "BOUNDARY CLUSTER"
- **FR-9.2** Export BOUNDARY FAT polygons for each LINE:
  - Source: `ftth!zone` objects
  - Placement: CLUSTER > LINE [A/B/C/D] > BOUNDARY FAT
  - One BOUNDARY FAT polygon per LINE
- **FR-9.3** Use design/scheme name from Design Manager for boundary naming

#### FR-10: Object-to-FDT Assignment Using ring_name
- **FR-10.1** Use `ring_name` field for FDT assignment:
  - All objects (cables, poles, FAT, HP points) have `ring_name` attribute
  - `ring_name` identifies which FDT/ring the object belongs to
  - Match object's `ring_name` to FDT's name/ring
- **FR-10.2** Assignment priority:
  1. **Primary:** Match `ring_name` attribute
  2. **Fallback:** Use proximity to FDT (if ring_name unset or not matching)
  3. **Default:** Assign to first FDT if no match found
- **FR-10.3** Handle ring_name variations:
  - Case-insensitive matching
  - Partial name matching (e.g., ring_name="FDT_001" matches FDT name="FDT_001_MAIN")
  - Log unmatched ring_names for review

#### FR-11: Object-to-LINE Assignment
- **FR-11.1** Use `line_type` field for LINE assignment (A/B/C/D):
  - Standard objects: cables, poles, FAT, HP points, sling wire (mit_sling_wire), slack hanger (mit_figure_eight)
  - All these objects have `line_type` attribute
  - Valid values: "A", "B", "C", "D"
  - Assign object to corresponding LINE folder based on line_type value
- **FR-11.2** Special handling for zones and demand points:
  - **ftth!zone**: Extract LINE from `comments` field (e.g., "Line A" → LINE A)
  - **ftth!demand_point**: Extract LINE from `comments` field
  - Parse comments for patterns: "Line A", "LINE B", "line C", etc.
- **FR-11.3** Assignment priority:
  1. **Primary:** Use `line_type` attribute (for most objects)
  2. **For zones/demand_points:** Parse `comments` field
  3. **Fallback:** If no line info, assign to LINE A by default
  4. **Validation:** Log objects with invalid or missing line assignment

#### FR-12: File Naming
- **FR-12.1** Generate filename: `[PROJECT_NAME].KMZ` (single dynamic file)
- **FR-12.2** Sanitize project name (remove invalid characters)

### 1.2 Non-Functional Requirements

#### NFR-1: Performance
- **NFR-1.1** Export 1,000 objects within 30 seconds
- **NFR-1.2** Handle projects with up to 10,000 objects
- **NFR-1.3** Minimize memory usage through streaming where possible

#### NFR-2: Compatibility
- **NFR-2.1** Generate KML 2.2 compliant files
- **NFR-2.2** Verify correct rendering in Google Earth Pro 7.3+
- **NFR-2.3** Support WGS84 coordinate system output

#### NFR-3: Maintainability
- **NFR-3.1** Separate style generation from data export
- **NFR-3.2** Use configuration-driven folder structure
- **NFR-3.3** Provide clear method documentation
- **NFR-3.4** Follow existing code patterns from rwi_export_to_kml

#### NFR-4: Usability
- **NFR-4.1** Provide GUI action to invoke export
- **NFR-4.2** Display progress feedback during export
- **NFR-4.3** Show error messages for validation failures
- **NFR-4.4** Log export summary (object counts by type)

---

## Section 2: Architecture Design

### 2.1 Class Structure

#### New Exemplar: `rwi_export_to_aerial_kmz`

**Inheritance:** Inherits from `object` (or create base export class if refactoring)

**Slots:**
```magik
def_slotted_exemplar(:rwi_export_to_aerial_kmz,
    {
        {:gc, :unset},                    ## GIS case (dataset)
        {:coordinate_transform, :unset},  ## Coordinate transformation
        {:export_mode, :unset},           ## :feeder, :subfeeder, :cluster, :all
        {:project_name, :unset},          ## Project name for filenames
        {:project_area, :unset},          ## Design boundary geometry
        {:style_manager, :unset},         ## Style definition manager
        {:output_directory, :unset},      ## Target directory path
        {:line_config, :unset},           ## LINE A/B/C/D configuration
        {:network_level_cache, :unset}    ## Cache for network level determination
    },
    :object)
$
```

### 2.2 Module Structure

**Module:** `rwi_export_to_kml` (existing module, add new files)

**New Source Files:**
```
rwi_export_to_kml/source/
├── rwi_export_to_aerial_kmz.magik           # Main exemplar definition
├── rwi_aerial_kmz_style_manager.magik       # Style management
├── rwi_aerial_kmz_folder_builder.magik      # Folder hierarchy generation
├── rwi_aerial_kmz_object_writer.magik       # Object KML generation
├── rwi_aerial_kmz_line_assigner.magik       # LINE A/B/C/D assignment logic
└── rwi_aerial_kmz_helpers.magik             # Utility methods
```

**Resources:**
```
rwi_export_to_kml/resources/base/data/
├── aerial_kmz_styles.kml                    # Style template (from sample.kml)
├── aerial_kmz_config.xml                    # Configuration (folder structure)
└── aerial_kmz_line_config.xml               # LINE assignment rules
```

### 2.3 Key Classes and Responsibilities

#### Class: `rwi_export_to_aerial_kmz` (Main Controller)
**Responsibility:** Orchestrates the export process
**Key Methods:**
- `export(p_mode, p_output_dir)` - Main export entry point
- `export_feeder()` - Export FEEDER CODE.KMZ
- `export_subfeeder()` - Export SUBFEEDER CODE.KMZ
- `export_cluster()` - Export CLUSTER CODE.KMZ
- `validate_export_data()` - Pre-export validation

#### Class: `rwi_aerial_kmz_style_manager` (Style Management)
**Responsibility:** Manages KML style definitions
**Key Methods:**
- `load_styles_from_template()` - Load from aerial_kmz_styles.kml
- `get_style_for_object(p_object_type, p_attributes)` - Return StyleMap ID
- `write_style_definitions(p_stream)` - Write styles to KML
- `create_balloon_style(p_schema_name)` - Generate BalloonStyle

#### Class: `rwi_aerial_kmz_folder_builder` (Hierarchy Generation)
**Responsibility:** Builds folder hierarchy structure
**Key Methods:**
- `build_feeder_folders()` - Create FEEDER folder tree
- `build_subfeeder_folders()` - Create SUBFEEDER folder tree
- `build_cluster_folders()` - Create CLUSTER folder tree with 4 LINEs
- `create_folder_xml(p_name, p_visibility)` - Generate folder XML

#### Class: `rwi_aerial_kmz_object_writer` (Object Serialization)
**Responsibility:** Writes individual objects as KML placemarks
**Key Methods:**
- `write_pole(p_pole, p_stream)` - Write pole placemark
- `write_cable(p_cable, p_stream)` - Write cable LineString
- `write_equipment(p_equipment, p_stream)` - Write FAT/FDT/closure
- `write_hp_point(p_hp, p_stream)` - Write homepass point
- `write_boundary(p_boundary, p_stream)` - Write polygon boundary

#### Class: `rwi_aerial_kmz_line_assigner` (LINE Assignment Logic)
**Responsibility:** Assigns objects to LINE A/B/C/D
**Key Methods:**
- `assign_line_to_hp(p_hp_point)` - Determine LINE for HP point
- `assign_line_to_cable(p_cable)` - Determine LINE for distribution cable
- `assign_line_to_fat(p_fat)` - Determine LINE for FAT
- `get_line_boundary(p_line_id)` - Get LINE service area polygon

### 2.4 Data Flow Architecture

```
[User Invokes Export]
        ↓
[rwi_export_to_aerial_kmz.export(mode, output_dir)]
        ↓
[Validate Export Data] → Query objects from GIS case
        ↓
[Determine Network Levels] → Cache network level per object
        ↓
[Build Folder Structure] → rwi_aerial_kmz_folder_builder
        ↓
[Load Style Definitions] → rwi_aerial_kmz_style_manager
        ↓
[Export by Network Level]
        ├─→ [FEEDER] → Query feeder objects → Write to FEEDER CODE.KMZ
        ├─→ [SUBFEEDER] → Query subfeeder objects → Write to SUBFEEDER CODE.KMZ
        └─→ [CLUSTER] → Query cluster objects → Assign LINEs → Write to CLUSTER CODE.KMZ
                ↓
        [rwi_aerial_kmz_object_writer] writes placemarks
                ↓
        [Generate KMZ file] → ZIP KML + resources
                ↓
        [Display Summary] → Show object counts, file paths
```

---

## Section 3: Detailed Implementation Specification

### 3.1 Folder Structure Generation

#### 3.1.0 Mixed Network Level Detection and Export Strategy

**CRITICAL REQUIREMENT:** The export area may contain objects from multiple network levels simultaneously.

**Export Strategy:**

The exporter will generate a **SINGLE KMZ file** containing dynamic folder structures for ALL network levels detected in the export area:

```
[PROJECT_NAME].KMZ
├── FEEDER CODE (if feeder objects detected)
│   ├── OLT
│   ├── CABLE
│   └── ... (15 folders total)
├── SUBFEEDER CODE (if subfeeder objects detected)
│   ├── JOINT CLOSURE
│   ├── CABLE
│   └── ... (14 folders total)
└── CLUSTER CODE (if cluster objects detected)
    ├── BOUNDARY CLUSTER
    ├── FDT_[FDT_1_NAME] (if multiple FDTs)
    │   ├── FDT
    │   └── LINE A/B/C/D
    └── FDT_[FDT_2_NAME]
        └── ...
```

**Detection Logic:**

```magik
_method rwi_export_to_aerial_kmz.detect_network_levels_in_area(p_area)
    ## Scans export area and determines which network levels are present
    ## Returns: property_list with :feeder, :subfeeder, :cluster flags

    levels << property_list.new_with(
        :feeder, _false,
        :subfeeder, _false,
        :cluster, _false,
        :feeder_objects, rope.new(),
        :subfeeder_objects, rope.new(),
        :cluster_objects, rope.new(),
        :fdts, rope.new())

    # Query all objects in area
    all_cables << .gc[:sheath_with_loc].select(predicate.within(:route, p_area))
    all_equipment << .gc[:sheath_splice].select(predicate.within(:location, p_area))
    all_fdts << .gc[:mit_terminal_enclosure].select(predicate.within(:location, p_area))

    # Classify cables by network level
    _for cable _over all_cables.fast_elements()
    _loop
        net_level << _self.determine_network_level(cable)
        _if net_level _is :feeder
        _then
            levels[:feeder] << _true
            levels[:feeder_objects].add(cable)
        _elif net_level _is :subfeeder
        _then
            levels[:subfeeder] << _true
            levels[:subfeeder_objects].add(cable)
        _elif net_level _is :cluster
        _then
            levels[:cluster] << _true
            levels[:cluster_objects].add(cable)
        _endif
    _endloop

    # Check for FDTs (indicates cluster level)
    _if all_fdts.size > 0
    _then
        levels[:cluster] << _true
        levels[:fdts] << all_fdts
    _endif

    # Check for equipment
    _for equip _over all_equipment.fast_elements()
    _loop
        net_level << _self.determine_network_level(equip)
        _if net_level _is :feeder
        _then
            levels[:feeder] << _true
        _elif net_level _is :subfeeder
        _then
            levels[:subfeeder] << _true
        _elif net_level _is :cluster
        _then
            levels[:cluster] << _true
        _endif
    _endloop

    write("Network levels detected: ",
          "FEEDER=", levels[:feeder],
          ", SUBFEEDER=", levels[:subfeeder],
          ", CLUSTER=", levels[:cluster],
          ", FDT count=", levels[:fdts].size)

    >> levels
_endmethod
$
```

**Export Orchestration:**

```magik
_method rwi_export_to_aerial_kmz.export_mixed_network(p_area, p_output_file)
    ## Exports all detected network levels into single KMZ file

    # Detect what's in the area
    levels << _self.detect_network_levels_in_area(p_area)

    # Open KML output stream
    kml_stream << _self.create_kml_output_stream(p_output_file)

    # Write KML header and styles
    _self.write_kml_header(kml_stream)
    .style_manager.write_style_definitions(kml_stream)

    # Write Document opening
    kml_stream.write("<Document>", newline_char)
    kml_stream.write("  <name>", .project_name, "</name>", newline_char)

    # Conditionally write FEEDER section
    _if levels[:feeder]
    _then
        write("Exporting FEEDER CODE section...")
        _self.write_feeder_section(kml_stream, p_area)
    _endif

    # Conditionally write SUBFEEDER section
    _if levels[:subfeeder]
    _then
        write("Exporting SUBFEEDER CODE section...")
        _self.write_subfeeder_section(kml_stream, p_area)
    _endif

    # Conditionally write CLUSTER section(s)
    _if levels[:cluster]
    _then
        write("Exporting CLUSTER CODE section with ", levels[:fdts].size, " FDT(s)...")
        _self.write_cluster_section(kml_stream, p_area, levels[:fdts])
    _endif

    # Close document and KML
    kml_stream.write("</Document>", newline_char)
    kml_stream.write("</kml>", newline_char)
    kml_stream.close()

    # Create KMZ (zip KML file)
    kmz_file << _self.create_kmz_from_kml(p_output_file)

    write("Export complete: ", kmz_file)
    >> kmz_file
_endmethod
$
```

**Example Scenarios:**

**Scenario 1: Pure Cluster Area (1 FDT)**
- Detected: CLUSTER only
- Generated Structure:
  ```
  [PROJECT].KMZ
  └── CLUSTER CODE
      ├── BOUNDARY CLUSTER
      ├── FDT
      └── LINE A/B/C/D
  ```

**Scenario 2: Pure Cluster Area (3 FDTs)**
- Detected: CLUSTER only
- Generated Structure:
  ```
  [PROJECT].KMZ
  └── CLUSTER CODE
      ├── BOUNDARY CLUSTER
      ├── FDT_FDT_001
      │   ├── FDT
      │   └── LINE A/B/C/D
      ├── FDT_FDT_002
      └── FDT_FDT_003
  ```

**Scenario 3: Mixed Feeder + Cluster (2 FDTs)**
- Detected: FEEDER + CLUSTER
- Generated Structure:
  ```
  [PROJECT].KMZ
  ├── FEEDER CODE
  │   ├── OLT
  │   ├── CABLE
  │   └── ... (15 folders)
  └── CLUSTER CODE
      ├── BOUNDARY CLUSTER
      ├── FDT_FDT_001
      └── FDT_FDT_002
  ```

**Scenario 4: Complete Mixed Network (All Levels, 1 FDT)**
- Detected: FEEDER + SUBFEEDER + CLUSTER
- Generated Structure:
  ```
  [PROJECT].KMZ
  ├── FEEDER CODE (15 folders)
  ├── SUBFEEDER CODE (14 folders)
  └── CLUSTER CODE
      ├── BOUNDARY CLUSTER
      ├── FDT
      └── LINE A/B/C/D
  ```

#### 3.1.1 FEEDER CODE.KMZ Folders

**Method:** `rwi_aerial_kmz_folder_builder.build_feeder_folders()`

**Folder Order (15 folders):**
1. OLT
2. CABLE
3. EXISTING POLE EMR 7-3
4. EXISTING POLE EMR 7-4
5. EXISTING POLE EMR 7-5
6. EXISTING POLE EMR 9-5
7. EXISTING POLE EMR 9-4
8. EXISTING POLE PARTNER 7-4
9. EXISTING POLE PARTNER 9-4
10. NEW POLE 7-5
11. NEW POLE 7-4
12. NEW POLE 9-5
13. NEW POLE 9-4
14. JOINT CLOSURE
15. SLACK HANGER
16. HANDHOLE 80X80X130

**Implementation:**
```magik
_method rwi_aerial_kmz_folder_builder.build_feeder_folders()
    ## Creates FEEDER CODE.KMZ folder structure

    folder_defs << rope.new()
    folder_defs.add({:name, "OLT", :visibility, _false})
    folder_defs.add({:name, "CABLE", :visibility, _false})

    # Existing poles by type
    _for pole_spec _over {"7-3", "7-4", "7-5", "9-5", "9-4"}.fast_elements()
    _loop
        folder_defs.add({:name, "EXISTING POLE EMR " + pole_spec, :visibility, _false})
    _endloop

    _for pole_spec _over {"7-4", "9-4"}.fast_elements()
    _loop
        folder_defs.add({:name, "EXISTING POLE PARTNER " + pole_spec, :visibility, _false})
    _endloop

    # New poles by type
    _for pole_spec _over {"7-5", "7-4", "9-5", "9-4"}.fast_elements()
    _loop
        folder_defs.add({:name, "NEW POLE " + pole_spec, :visibility, _false})
    _endloop

    folder_defs.add({:name, "JOINT CLOSURE", :visibility, _false})
    folder_defs.add({:name, "SLACK HANGER", :visibility, _false})
    folder_defs.add({:name, "HANDHOLE 80X80X130", :visibility, _false})

    >> folder_defs
_endmethod
$
```

#### 3.1.2 SUBFEEDER CODE.KMZ Folders

**Method:** `rwi_aerial_kmz_folder_builder.build_subfeeder_folders()`

**Folder Order (14 folders):**
1. JOINT CLOSURE
2. EXISTING POLE EMR 7-2.5
3. EXISTING POLE EMR 7-3
4. EXISTING POLE EMR 7-4
5. EXISTING POLE EMR 7-5
6. EXISTING POLE EMR 9-5
7. EXISTING POLE EMR 9-4
8. EXISTING POLE PARTNER 7-4
9. EXISTING POLE PARTNER 9-4
10. NEW POLE 7-5
11. NEW POLE 9-5
12. NEW POLE 7-4
13. NEW POLE 9-4
14. CABLE
15. SLACK HANGER

**Key Difference:** Includes 7-2.5 class, no HANDHOLE, JOINT CLOSURE moved to top

#### 3.1.3 CLUSTER CODE.KMZ Folders (DYNAMIC)

**Method:** `rwi_aerial_kmz_folder_builder.build_cluster_folders(p_fdts)`

**IMPORTANT:** Cluster structure is **DYNAMIC** based on FDTs detected in the export area.

**Top-Level Structure (Multiple FDTs):**
```
CLUSTER CODE.KMZ
├── BOUNDARY CLUSTER (overall cluster boundary)
├── CLUSTER [FDT_1_NAME]
│   ├── FDT (this specific FDT)
│   ├── LINE A (14 subfolders)
│   ├── LINE B (14 subfolders)
│   ├── LINE C (14 subfolders)
│   └── LINE D (14 subfolders)
├── CLUSTER [FDT_2_NAME]
│   ├── FDT
│   ├── LINE A (14 subfolders)
│   ├── LINE B (14 subfolders)
│   ├── LINE C (14 subfolders)
│   └── LINE D (14 subfolders)
└── CLUSTER [FDT_N_NAME]
    ├── FDT
    └── LINE A/B/C/D...
```

**Top-Level Structure (Single FDT - Simplified):**
```
CLUSTER CODE.KMZ
├── BOUNDARY CLUSTER
├── FDT (single FDT)
├── LINE A (14 subfolders)
├── LINE B (14 subfolders)
├── LINE C (14 subfolders)
└── LINE D (14 subfolders)
```

**Rules:**
1. If **1 FDT** detected → Use simplified structure (FDT at top level)
2. If **2+ FDTs** detected → Create separate "CLUSTER [FDT_NAME]" folder for each
3. Each cluster folder contains its own 4 LINEs (A/B/C/D)
4. Objects (FAT, HP, cables, poles) assigned to cluster based on nearest FDT

**LINE Subfolder Structure (14 folders × 4 LINEs = 56 total):**
1. BOUNDARY FAT
2. FAT
3. HP COVER
   - [LINE]01
   - [LINE]02
   - [LINE]03
   - [LINE]04
   - [LINE]05
4. HP UNCOVER
5. EXISTING POLE EMR 7-2.5
6. EXISTING POLE EMR 7-3
7. EXISTING POLE EMR 7-4
8. EXISTING POLE EMR 9-4
9. EXISTING POLE PARTNER 7-4
10. EXISTING POLE PARTNER 9-4
11. NEW POLE 7-2.5
12. NEW POLE 7-3
13. NEW POLE 7-4
14. NEW POLE 9-4
15. DISTRIBUTION CABLE
16. SLACK HANGER
17. SLING WIRE

**Implementation:**
```magik
_method rwi_aerial_kmz_folder_builder.build_cluster_folders(p_fdts)
    ## Creates CLUSTER CODE.KMZ folder structure dynamically based on detected FDTs
    ## Parameters:
    ##   p_fdts - collection of FDT objects found in export area
    ## Returns: folder definition rope

    folder_defs << rope.new()
    folder_defs.add({:name, "BOUNDARY CLUSTER", :visibility, _false})

    # Determine structure based on FDT count
    fdt_count << p_fdts.size

    _if fdt_count = 0
    _then
        # No FDTs - create minimal structure (rare case)
        write("WARNING: No FDTs detected in cluster export area")
        >> folder_defs

    _elif fdt_count = 1
    _then
        # Single FDT - simplified structure (FDT at top level)
        fdt << p_fdts.an_element()
        folder_defs.add({:name, "FDT", :visibility, _false, :fdt_object, fdt})

        # Add 4 LINEs at top level
        _for line_id _over {"A", "B", "C", "D"}.fast_elements()
        _loop
            line_folder << _self.build_line_folders(line_id, fdt)
            folder_defs.add(line_folder)
        _endloop

    _else
        # Multiple FDTs - create separate cluster folder for each
        _for fdt _over p_fdts.fast_elements()
        _loop
            fdt_name << fdt.name.default("FDT_" + fdt.id.write_string)

            # Create FDT_[FDT_NAME] folder (NO "CLUSTER" prefix)
            cluster_folder << property_list.new_with(
                :name, "FDT_" + fdt_name,
                :visibility, _false,
                :fdt_object, fdt,
                :subfolders, rope.new())

            # Add FDT folder inside cluster
            cluster_folder[:subfolders].add({:name, "FDT", :visibility, _false, :fdt_object, fdt})

            # Add 4 LINEs inside this cluster
            _for line_id _over {"A", "B", "C", "D"}.fast_elements()
            _loop
                line_folder << _self.build_line_folders(line_id, fdt)
                cluster_folder[:subfolders].add(line_folder)
            _endloop

            folder_defs.add(cluster_folder)
        _endloop
    _endif

    >> folder_defs
_endmethod
$

_method rwi_aerial_kmz_folder_builder.build_line_folders(p_line_id, p_fdt)
    ## Creates subfolder structure for a single LINE
    ## Parameters:
    ##   p_line_id - LINE identifier (A, B, C, or D)
    ##   p_fdt - FDT object this LINE belongs to (for object assignment)

    line_def << property_list.new_with(
        :name, "LINE " + p_line_id,
        :visibility, _false,
        :fdt_object, p_fdt,
        :subfolders, rope.new())

    subfolders << line_def[:subfolders]

    # Add standard subfolders
    subfolders.add({:name, "BOUNDARY FAT", :visibility, _false})
    subfolders.add({:name, "FAT", :visibility, _false})

    # HP COVER with 5 zones
    hp_cover << property_list.new_with(
        :name, "HP COVER",
        :visibility, _false,
        :subfolders, rope.new())

    _for zone_num _over 1.upto(5)
    _loop
        zone_name << p_line_id + zone_num.write_string.pad_leading(%0, 2)
        hp_cover[:subfolders].add({:name, zone_name, :visibility, _false})
    _endloop

    subfolders.add(hp_cover)
    subfolders.add({:name, "HP UNCOVER", :visibility, _false})

    # Poles (lighter duty for cluster)
    _for pole_spec _over {"7-2.5", "7-3", "7-4", "9-4"}.fast_elements()
    _loop
        subfolders.add({:name, "EXISTING POLE EMR " + pole_spec, :visibility, _false})
    _endloop

    _for pole_spec _over {"7-4", "9-4"}.fast_elements()
    _loop
        subfolders.add({:name, "EXISTING POLE PARTNER " + pole_spec, :visibility, _false})
    _endloop

    _for pole_spec _over {"7-2.5", "7-3", "7-4", "9-4"}.fast_elements()
    _loop
        subfolders.add({:name, "NEW POLE " + pole_spec, :visibility, _false})
    _endloop

    subfolders.add({:name, "DISTRIBUTION CABLE", :visibility, _false})
    subfolders.add({:name, "SLACK HANGER", :visibility, _false})
    subfolders.add({:name, "SLING WIRE", :visibility, _false})

    >> line_def
_endmethod
$
```

### 3.2 Object Classification and Routing

#### 3.2.1 Network Level Determination

**Method:** `rwi_export_to_aerial_kmz.determine_network_level(p_object)`

**Logic:**
```magik
_method rwi_export_to_aerial_kmz.determine_network_level(p_object)
    ## Determines if object belongs to FEEDER, SUBFEEDER, or CLUSTER level
    ## Returns: :feeder, :subfeeder, or :cluster

    # Check cache first
    _if .network_level_cache _isnt _unset _andif
        (cached << .network_level_cache[p_object]) _isnt _unset
    _then
        _return cached
    _endif

    level << :cluster  # Default

    # For cables
    _if p_object.is_kind_of?(sheath_with_loc)
    _then
        net_type << p_object.sheath_network_type
        _if net_type _is _unset _orif net_type.default("").lowercase.includes_string?("cluster")
        _then
            level << :cluster
        _elif net_type.lowercase.includes_string?("feeder") _andif
              _not net_type.lowercase.includes_string?("sub")
        _then
            level << :feeder
        _elif net_type.lowercase.includes_string?("sub")
        _then
            level << :subfeeder
        _endif
    _endif

    # For equipment
    _if p_object.is_kind_of?(sheath_splice)
    _then
        net_type << p_object.fttx_network_type
        _if net_type _is _unset _orif net_type.default("").lowercase.includes_string?("cluster")
        _then
            level << :cluster
        _elif net_type.lowercase.includes_string?("feeder")
        _then
            level << :feeder
        _elif net_type.lowercase.includes_string?("sub")
        _then
            level << :subfeeder
        _endif
    _endif

    # For poles - check cable connections to infer level
    _if p_object.is_kind_of?(pole)
    _then
        # Find cables connected via aerial routes
        cables << _self.get_cables_at_pole(p_object)
        _if cables.empty?
        _then
            level << :cluster  # Default for standalone poles
        _else
            # Use highest level of connected cables
            _for cable _over cables.fast_elements()
            _loop
                cable_level << _self.determine_network_level(cable)
                _if cable_level _is :feeder
                _then
                    level << :feeder
                    _leave
                _elif cable_level _is :subfeeder _andif level _is :cluster
                _then
                    level << :subfeeder
                _endif
            _endloop
        _endif
    _endif

    # Cache result
    _if .network_level_cache _is _unset
    _then
        .network_level_cache << equality_hash_table.new()
    _endif
    .network_level_cache[p_object] << level

    _return level
_endmethod
$
```

#### 3.2.2 Pole Folder Assignment

**Method:** `rwi_export_to_aerial_kmz.get_pole_folder_name(p_pole)`

**Logic:**
```magik
_method rwi_export_to_aerial_kmz.get_pole_folder_name(p_pole)
    ## Returns folder name for pole: "[STATUS] POLE [OWNER] [HEIGHT]-[CLASS]"

    # Determine status
    _if p_pole.construction_status.default("").lowercase = "proposed"
    _then
        status << "NEW"
    _else
        status << "EXISTING"
    _endif

    # Determine ownership
    _if p_pole.ownership _isnt _unset _andif
        p_pole.ownership.lowercase.includes_string?("partner")
    _then
        owner << "PARTNER"
    _else
        owner << "EMR"
    _endif

    # Extract height (meters)
    height << _self.extract_pole_height(p_pole)  # Returns "7" or "9"

    # Extract class
    pole_class << _self.extract_pole_class(p_pole)  # Returns "2.5", "3", "4", or "5"

    # Build folder name
    _if owner = "EMR"
    _then
        folder_name << status + " POLE EMR " + height + "-" + pole_class
    _else
        folder_name << status + " POLE PARTNER " + height + "-" + pole_class
    _endif

    _return folder_name
_endmethod
$

_method rwi_export_to_aerial_kmz.extract_pole_height(p_pole)
    ## Extracts pole height from attributes
    ## Returns: "7" or "9" (default "7")

    # Check material_type or usage field for height info
    _if p_pole.material_type _isnt _unset
    _then
        mat_str << p_pole.material_type.write_string.lowercase
        _if mat_str.includes_string?("9m") _orif mat_str.includes_string?("9 m")
        _then
            _return "9"
        _endif
    _endif

    # Default
    _return "7"
_endmethod
$

_method rwi_export_to_aerial_kmz.extract_pole_class(p_pole)
    ## Extracts pole class from attributes
    ## Returns: "2.5", "3", "4", or "5" (default "4")

    # Check usage or material_type for class info
    _if p_pole.usage _isnt _unset
    _then
        usage_str << p_pole.usage.write_string
        # Look for patterns like "class 4", "4\"", etc.
        _if usage_str.includes_string?("2.5") _orif usage_str.includes_string?("2,5")
        _then
            _return "2.5"
        _elif usage_str.includes_string?("class 3") _orif usage_str.includes_string?("3\"")
        _then
            _return "3"
        _elif usage_str.includes_string?("class 5") _orif usage_str.includes_string?("5\"")
        _then
            _return "5"
        _endif
    _endif

    # Default
    _return "4"
_endmethod
$
```

#### 3.2.3 LINE Assignment for Cluster Objects

**Method:** `rwi_aerial_kmz_line_assigner.assign_line_to_object(p_object)`

**Primary Strategy: Use line_type Attribute**
```magik
_method rwi_aerial_kmz_line_assigner.assign_line_to_object(p_object)
    ## Assigns object to LINE A/B/C/D based on line_type field or comments
    ##
    ## For most objects: Uses line_type attribute
    ## For ftth!zone and ftth!demand_point: Parses comments field

    # Special handling for zone and demand_point objects
    _if p_object.class_name _is :ftth!zone _orif
        p_object.class_name _is :ftth!demand_point
    _then
        _return _self.extract_line_from_comments(p_object)
    _endif

    # Standard objects: use line_type field
    line_type << p_object.perform_safely(:line_type)

    _if line_type _isnt _unset _and
        {"A", "B", "C", "D"}.includes?(line_type.uppercase)
    _then
        _return line_type.uppercase
    _endif

    # Fallback: Default to LINE A
    write("WARNING: Object ", p_object.class_name, " ID ", p_object.id,
          " has no valid line_type, defaulting to LINE A")
    _return "A"
_endmethod
$
```

**Helper Method: Extract LINE from Comments**
```magik
_method rwi_aerial_kmz_line_assigner.extract_line_from_comments(p_object)
    ## Extracts LINE (A/B/C/D) from comments field
    ## Handles patterns: "Line A", "LINE B", "line C", etc.

    comments << p_object.perform_safely(:comments).default("")

    # Parse comments for "Line A", "LINE B", "line C", etc. (case-insensitive)
    _for line_letter _over {"A", "B", "C", "D"}.fast_elements()
    _loop
        # Match patterns: "Line A", "LINE A", "line A", "line_A", etc.
        pattern << write_string("(?i)line[\\s_]+(", line_letter, ")")

        _if comments.index_of_regex(pattern) _isnt _unset
        _then
            _return line_letter
        _endif
    _endloop

    # Fallback: Default to LINE A if no match found
    write("WARNING: Object ", p_object.class_name, " ID ", p_object.id,
          " has no LINE in comments, defaulting to LINE A")
    _return "A"
_endmethod
$
```

#### 3.2.4 HP Zone Assignment

**Method:** `rwi_aerial_kmz_line_assigner.assign_hp_zone(p_hp_point, p_line_id)`

**Strategy:**
```magik
_method rwi_aerial_kmz_line_assigner.assign_hp_zone(p_hp_point, p_line_id)
    ## Assigns HP point to zone 01-05 within LINE
    ## Returns: zone number string ("01" through "05") or _unset for HP UNCOVER

    # Check coverage status
    _if p_hp_point.coverage_status _isnt _unset _andif
        p_hp_point.coverage_status.lowercase.includes_string?("uncover")
    _then
        _return _unset  # Goes to HP UNCOVER folder
    _endif

    # Check if zone is encoded in name
    name << p_hp_point.name.default("")
    _for zone_num _over 1.upto(5)
    _loop
        zone_str << p_line_id + zone_num.write_string.pad_leading(%0, 2)  # "A01"
        _if name.includes_string?(zone_str)
        _then
            _return zone_num.write_string.pad_leading(%0, 2)
        _endif
    _endloop

    # Default: assign to zone 01
    _return "01"
_endmethod
$
```

### 3.3 Style Management

#### 3.3.1 Style Loading

**Method:** `rwi_aerial_kmz_style_manager.load_styles_from_template()`

**Implementation:**
```magik
_method rwi_aerial_kmz_style_manager.load_styles_from_template()
    ## Loads style definitions from aerial_kmz_styles.kml template

    template_path << _self.get_resource_file_path("aerial_kmz_styles.kml")

    _if _not system.file_exists?(template_path)
    _then
        condition.raise(:file_not_found, :string, "Style template not found: " + template_path)
    _endif

    # Parse KML template and extract <Style> and <StyleMap> elements
    template_xml << _self.parse_kml_file(template_path)

    # Store styles in hash table keyed by style ID
    .style_definitions << equality_hash_table.new()

    _for style_elem _over template_xml.elements_matching("Style").fast_elements()
    _loop
        style_id << style_elem.attribute("id")
        .style_definitions[style_id] << style_elem.xml_text
    _endloop

    _for stylemap_elem _over template_xml.elements_matching("StyleMap").fast_elements()
    _loop
        stylemap_id << stylemap_elem.attribute("id")
        .style_definitions[stylemap_id] << stylemap_elem.xml_text
    _endloop

    write("Loaded ", .style_definitions.size, " style definitions from template")
_endmethod
$
```

#### 3.3.2 Style Selection

**Method:** `rwi_aerial_kmz_style_manager.get_style_for_object(p_object_type, p_attributes)`

**Mapping Table:**
```magik
_method rwi_aerial_kmz_style_manager.get_style_for_object(p_object_type, p_attributes)
    ## Returns appropriate StyleMap ID for object type

    _if p_object_type _is :olt
    _then
        _return "icon-ranger_station.pngline-w31"

    _elif p_object_type _is :pole
    _then
        # NEW POLE 9M has special style
        _if p_attributes[:status] = "NEW" _andif p_attributes[:height] = "9"
        _then
            _return "icon-placemark_circle.png-7F0000"
        _else
            _return "pointStyleMap01"  # Generic pole style
        _endif

    _elif p_object_type _is :cable
    _then
        _return "msn_ylw-pushpin"

    _elif p_object_type _is :coil
    _then
        _return "icon-target.png-0000AA600528"

    _elif p_object_type _is :fdt
    _then
        _return "msn_triangle"

    _elif p_object_type _is :fat
    _then
        _return "msn_triangle"

    _elif p_object_type _is :closure
    _then
        _return "msn_cross-hairs"

    _elif p_object_type _is :hp_point
    _then
        _return "pointStyleMap145"

    _elif p_object_type _is :boundary
    _then
        _return "icon-00B371line-w4-00B371poly-00B371110000121101030250"

    _else
        # Default style
        _return "pointStyleMap01"
    _endif
_endmethod
$
```

### 3.4 KML Writing Methods

#### 3.4.1 Pole Writing

**Method:** `rwi_aerial_kmz_object_writer.write_pole(p_pole, p_stream)`

```magik
_method rwi_aerial_kmz_object_writer.write_pole(p_pole, p_stream)
    ## Writes pole as KML Placemark

    # Get coordinates
    coord << _self.convert_coord(p_pole.location.coord)

    # Get pole attributes
    name << p_pole.telco_pole_tag.default("Pole")
    status << p_pole.construction_status.default("In Service")
    material << p_pole.material_type.default("N/A")
    usage << p_pole.usage.default("N/A")

    # Get style
    pole_attrs << property_list.new_with(
        :status, _if status.lowercase = "proposed" _then "NEW" _else "EXISTING" _endif,
        :height, _self.extract_pole_height(p_pole))

    style_id << .style_manager.get_style_for_object(:pole, pole_attrs)

    # Write placemark XML
    p_stream.write("<Placemark>", newline_char)
    p_stream.write("  <name>", _self.escape_xml(name), "</name>", newline_char)
    p_stream.write("  <styleUrl>#", style_id, "</styleUrl>", newline_char)

    # Description with attributes
    p_stream.write("  <description><![CDATA[", newline_char)
    p_stream.write("    <b>Construction Status:</b> ", status, "<br/>", newline_char)
    p_stream.write("    <b>Material Type:</b> ", material, "<br/>", newline_char)
    p_stream.write("    <b>Usage:</b> ", usage, newline_char)
    p_stream.write("  ]]></description>", newline_char)

    # Point geometry
    p_stream.write("  <Point>", newline_char)
    p_stream.write("    <coordinates>", coord.x, ",", coord.y, ",0</coordinates>", newline_char)
    p_stream.write("  </Point>", newline_char)
    p_stream.write("</Placemark>", newline_char)
_endmethod
$
```

#### 3.4.2 Cable Writing

**Method:** `rwi_aerial_kmz_object_writer.write_cable(p_cable, p_stream)`

```magik
_method rwi_aerial_kmz_object_writer.write_cable(p_cable, p_stream)
    ## Writes cable as KML LineString

    # Get cable attributes
    name << p_cable.name.default("Cable")
    net_type << p_cable.sheath_network_type.default("N/A")
    spec << p_cable.specification.default("N/A")
    core_count << _self.extract_core_count(p_cable)
    length << p_cable.calculated_fiber_length.default(0)
    status << p_cable.construction_status.default("In Service")

    # Get style
    style_id << .style_manager.get_style_for_object(:cable, property_list.new())

    # Write placemark
    p_stream.write("<Placemark>", newline_char)
    p_stream.write("  <name>", _self.escape_xml(name), "</name>", newline_char)
    p_stream.write("  <styleUrl>#", style_id, "</styleUrl>", newline_char)

    # Description
    p_stream.write("  <description><![CDATA[", newline_char)
    p_stream.write("    <b>Network Type:</b> ", net_type, "<br/>", newline_char)
    p_stream.write("    <b>Specification:</b> ", spec, "<br/>", newline_char)
    p_stream.write("    <b>Number of Core:</b> ", core_count, "<br/>", newline_char)
    p_stream.write("    <b>Fiber Length:</b> ", length, " m<br/>", newline_char)
    p_stream.write("    <b>Construction Status:</b> ", status, newline_char)
    p_stream.write("  ]]></description>", newline_char)

    # LineString geometry from route sectors
    p_stream.write("  <LineString>", newline_char)
    p_stream.write("    <tessellate>1</tessellate>", newline_char)
    p_stream.write("    <coordinates>", newline_char)

    _for sector _over p_cable.route.route_sectors.fast_elements()
    _loop
        _for coord _over sector.geometry.coords.fast_elements()
        _loop
            wgs84_coord << _self.convert_coord(coord)
            p_stream.write("      ", wgs84_coord.x, ",", wgs84_coord.y, ",0 ")
        _endloop
    _endloop

    p_stream.write(newline_char, "    </coordinates>", newline_char)
    p_stream.write("  </LineString>", newline_char)
    p_stream.write("</Placemark>", newline_char)
_endmethod
$
```

#### 3.4.3 HP Point Writing

**Method:** `rwi_aerial_kmz_object_writer.write_hp_point(p_hp, p_stream)`

```magik
_method rwi_aerial_kmz_object_writer.write_hp_point(p_hp, p_stream)
    ## Writes homepass point as KML Placemark

    # Get coordinates
    coord << _self.convert_coord(p_hp.location.coord)

    # Get attributes
    name << p_hp.name.default("HP")

    # Get style
    style_id << .style_manager.get_style_for_object(:hp_point, property_list.new())

    # Write placemark with BalloonStyle data
    p_stream.write("<Placemark>", newline_char)
    p_stream.write("  <name>", _self.escape_xml(name), "</name>", newline_char)
    p_stream.write("  <styleUrl>#", style_id, "</styleUrl>", newline_char)

    # Extended data for BalloonStyle
    p_stream.write("  <ExtendedData>", newline_char)
    p_stream.write("    <SchemaData schemaUrl=\"#S_HP_SCHEMA\">", newline_char)
    p_stream.write("      <SimpleData name=\"LONGITUDE\">", coord.x, "</SimpleData>", newline_char)
    p_stream.write("      <SimpleData name=\"LATITUDE\">", coord.y, "</SimpleData>", newline_char)
    p_stream.write("      <SimpleData name=\"LABEL\">", _self.escape_xml(name), "</SimpleData>", newline_char)
    p_stream.write("      <SimpleData name=\"KML_FOLDER\">HP</SimpleData>", newline_char)
    p_stream.write("    </SchemaData>", newline_char)
    p_stream.write("  </ExtendedData>", newline_char)

    # Point geometry
    p_stream.write("  <Point>", newline_char)
    p_stream.write("    <coordinates>", coord.x, ",", coord.y, ",0</coordinates>", newline_char)
    p_stream.write("  </Point>", newline_char)
    p_stream.write("</Placemark>", newline_char)
_endmethod
$
```

---

## Section 4: Testing Strategy

### 4.1 Unit Tests

**Test File:** `rwi_export_to_aerial_kmz_test_case.magik`

**Test Cases:**

#### TC-1: Folder Structure Generation
```magik
_method rwi_export_to_aerial_kmz_test_case.test_feeder_folder_structure()
    ## Verifies FEEDER CODE.KMZ has correct 15 folders in order

    builder << rwi_aerial_kmz_folder_builder.new()
    folders << builder.build_feeder_folders()

    _self.assert_equals(15, folders.size, "FEEDER should have 15 folders")
    _self.assert_equals("OLT", folders[1][:name], "First folder should be OLT")
    _self.assert_equals("HANDHOLE 80X80X130", folders[15][:name], "Last folder should be HANDHOLE")
_endmethod
$
```

#### TC-2: Network Level Determination
```magik
_method rwi_export_to_aerial_kmz_test_case.test_network_level_feeder()
    ## Verifies feeder cable is classified as :feeder level

    exporter << rwi_export_to_aerial_kmz.new()

    # Create mock cable with feeder network type
    cable << _self.create_mock_cable(:network_type, "Feeder")

    level << exporter.determine_network_level(cable)

    _self.assert_equals(:feeder, level, "Feeder cable should be at :feeder level")
_endmethod
$
```

#### TC-3: Pole Folder Assignment
```magik
_method rwi_export_to_aerial_kmz_test_case.test_pole_folder_new_9m_class4()
    ## Verifies pole folder name generation

    exporter << rwi_export_to_aerial_kmz.new()

    # Create mock pole: NEW POLE 9M class 4
    pole << _self.create_mock_pole(
        :status, "Proposed",
        :height, "9m",
        :class, "4",
        :ownership, "EMR")

    folder_name << exporter.get_pole_folder_name(pole)

    _self.assert_equals("NEW POLE EMR 9-4", folder_name)
_endmethod
$
```

#### TC-4: LINE Assignment
```magik
_method rwi_export_to_aerial_kmz_test_case.test_hp_line_assignment()
    ## Verifies HP point is assigned to correct LINE

    assigner << rwi_aerial_kmz_line_assigner.new()

    # Create mock HP with LINE B in name
    hp << _self.create_mock_hp(:name, "PLB.LINE B.HP001")

    line_id << assigner.assign_line_to_hp(hp)

    _self.assert_equals("B", line_id, "HP should be assigned to LINE B")
_endmethod
$
```

#### TC-5: Style Selection
```magik
_method rwi_export_to_aerial_kmz_test_case.test_style_olt()
    ## Verifies correct style is selected for OLT

    style_mgr << rwi_aerial_kmz_style_manager.new()
    style_mgr.load_styles_from_template()

    style_id << style_mgr.get_style_for_object(:olt, property_list.new())

    _self.assert_equals("icon-ranger_station.pngline-w31", style_id)
_endmethod
$
```

### 4.2 Integration Tests

**Test File:** `rwi_export_to_aerial_kmz_integration_test.magik`

#### IT-1: End-to-End FEEDER Export
```magik
_method rwi_export_to_aerial_kmz_integration_test.test_export_feeder_kmz()
    ## Tests complete FEEDER CODE.KMZ export

    exporter << rwi_export_to_aerial_kmz.new()
    exporter.gc << gis_program_manager.cached_dataset(:gis)
    exporter.project_name << "TEST_PROJECT"
    exporter.output_directory << system.temp_directory_name

    # Set project area
    exporter.project_area << _self.get_test_boundary()

    # Export
    output_file << exporter.export(:feeder)

    # Verify file exists
    _self.assert_true(system.file_exists?(output_file), "KMZ file should exist")

    # Verify KML structure
    kml_content << _self.extract_kml_from_kmz(output_file)
    _self.assert_true(kml_content.includes_string?("<Folder><name>OLT</name>"))
    _self.assert_true(kml_content.includes_string?("<Folder><name>CABLE</name>"))

    # Cleanup
    system.unlink(output_file)
_endmethod
$
```

#### IT-2: Cluster Export with 4 LINEs
```magik
_method rwi_export_to_aerial_kmz_integration_test.test_export_cluster_4_lines()
    ## Tests CLUSTER CODE.KMZ export with all 4 LINEs

    exporter << rwi_export_to_aerial_kmz.new()
    exporter.gc << gis_program_manager.cached_dataset(:gis)
    exporter.project_name << "TEST_CLUSTER"
    exporter.output_directory << system.temp_directory_name
    exporter.project_area << _self.get_test_boundary()

    output_file << exporter.export(:cluster)

    # Verify LINE folders exist
    kml_content << _self.extract_kml_from_kmz(output_file)

    _for line_id _over {"A", "B", "C", "D"}.fast_elements()
    _loop
        line_folder << "<Folder><name>LINE " + line_id + "</name>"
        _self.assert_true(kml_content.includes_string?(line_folder),
            "LINE " + line_id + " folder should exist")

        # Verify HP COVER zones
        _for zone _over 1.upto(5)
        _loop
            zone_folder << line_id + zone.write_string.pad_leading(%0, 2)
            _self.assert_true(kml_content.includes_string?(zone_folder),
                "Zone " + zone_folder + " should exist")
        _endloop
    _endloop

    system.unlink(output_file)
_endmethod
$
```

### 4.3 Validation Tests

#### VT-1: KML Well-Formedness
```magik
_method rwi_export_to_aerial_kmz_integration_test.test_kml_xml_valid()
    ## Verifies exported KML is valid XML

    exporter << rwi_export_to_aerial_kmz.new()
    # ... setup ...
    output_file << exporter.export(:feeder)

    kml_content << _self.extract_kml_from_kmz(output_file)

    # Parse XML to verify well-formedness
    _try
        xml_doc << simple_xml.read_document_from(external_text_input_stream.new(kml_content))
        _self.assert_true(_true, "KML should be valid XML")
    _when error
        _self.fail("KML is not valid XML: " + error.report_contents_string)
    _endtry

    system.unlink(output_file)
_endmethod
$
```

#### VT-2: Google Earth Compatibility
**Manual Test:** Open generated KMZ in Google Earth Pro 7.3 and verify:
- All folders appear in correct hierarchy
- Icons display correctly
- Colors match specification
- BalloonStyle popups show data
- Coordinates display in correct locations

---

## Section 5: Configuration Management

### 5.1 Configuration Files

#### File: `aerial_kmz_config.xml`

**Purpose:** Define folder structure configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<aerial_kmz_config>
  <network_level id="feeder">
    <name>FEEDER CODE.KMZ</name>
    <folders>
      <folder name="OLT" visibility="false" />
      <folder name="CABLE" visibility="false" />
      <folder name="EXISTING POLE EMR 7-3" visibility="false" />
      <folder name="EXISTING POLE EMR 7-4" visibility="false" />
      <folder name="EXISTING POLE EMR 7-5" visibility="false" />
      <folder name="EXISTING POLE EMR 9-5" visibility="false" />
      <folder name="EXISTING POLE EMR 9-4" visibility="false" />
      <folder name="EXISTING POLE PARTNER 7-4" visibility="false" />
      <folder name="EXISTING POLE PARTNER 9-4" visibility="false" />
      <folder name="NEW POLE 7-5" visibility="false" />
      <folder name="NEW POLE 7-4" visibility="false" />
      <folder name="NEW POLE 9-5" visibility="false" />
      <folder name="NEW POLE 9-4" visibility="false" />
      <folder name="JOINT CLOSURE" visibility="false" />
      <folder name="SLACK HANGER" visibility="false" />
      <folder name="HANDHOLE 80X80X130" visibility="false" />
    </folders>
  </network_level>

  <network_level id="subfeeder">
    <name>SUBFEEDER CODE.KMZ</name>
    <folders>
      <folder name="JOINT CLOSURE" visibility="false" />
      <folder name="EXISTING POLE EMR 7-2.5" visibility="false" />
      <!-- ... -->
    </folders>
  </network_level>

  <network_level id="cluster">
    <name>CLUSTER CODE.KMZ</name>
    <folders>
      <folder name="BOUNDARY CLUSTER" visibility="false" />
      <folder name="FDT" visibility="false" />
      <line_template id="A" />
      <line_template id="B" />
      <line_template id="C" />
      <line_template id="D" />
    </folders>
  </network_level>

  <line_template id="default">
    <folder name="BOUNDARY FAT" visibility="false" />
    <folder name="FAT" visibility="false" />
    <folder name="HP COVER" visibility="false">
      <subfolder name="{LINE}01" visibility="false" />
      <subfolder name="{LINE}02" visibility="false" />
      <subfolder name="{LINE}03" visibility="false" />
      <subfolder name="{LINE}04" visibility="false" />
      <subfolder name="{LINE}05" visibility="false" />
    </folder>
    <folder name="HP UNCOVER" visibility="false" />
    <!-- ... poles ... -->
    <folder name="DISTRIBUTION CABLE" visibility="false" />
    <folder name="SLACK HANGER" visibility="false" />
    <folder name="SLING WIRE" visibility="false" />
  </line_template>
</aerial_kmz_config>
```

#### File: `aerial_kmz_line_config.xml`

**Purpose:** Configure LINE assignment rules

```xml
<?xml version="1.0" encoding="UTF-8"?>
<line_assignment_config>
  <rules>
    <!-- Rule 1: Assign by name pattern -->
    <rule priority="1" type="name_pattern">
      <pattern>LINE A|_A\.|\.A\.</pattern>
      <line>A</line>
    </rule>
    <rule priority="1" type="name_pattern">
      <pattern>LINE B|_B\.|\.B\.</pattern>
      <line>B</line>
    </rule>
    <rule priority="1" type="name_pattern">
      <pattern>LINE C|_C\.|\.C\.</pattern>
      <line>C</line>
    </rule>
    <rule priority="1" type="name_pattern">
      <pattern>LINE D|_D\.|\.D\.</pattern>
      <line>D</line>
    </rule>

    <!-- Rule 2: Assign by proximity to FAT -->
    <rule priority="2" type="proximity">
      <method>nearest_fat</method>
    </rule>

    <!-- Rule 3: Default fallback -->
    <rule priority="3" type="default">
      <line>A</line>
    </rule>
  </rules>
</line_assignment_config>
```

### 5.2 Resource File Extraction

**Method:** `rwi_export_to_aerial_kmz.get_resource_file_path(p_filename)`

```magik
_method rwi_export_to_aerial_kmz.get_resource_file_path(p_filename)
    ## Returns full path to resource file in module resources

    module << sw_module_manager.module(:rwi_export_to_kml)
    resource_dir << module.get_resource_directory("base", "data")

    _if resource_dir _is _unset
    _then
        condition.raise(:resource_not_found, :string, "Resource directory not found")
    _endif

    file_path << system.pathname_down(resource_dir, p_filename)

    _if _not system.file_exists?(file_path)
    _then
        condition.raise(:file_not_found, :string, "Resource file not found: " + file_path)
    _endif

    _return file_path
_endmethod
$
```

---

## Section 6: User Interface Integration

### 6.1 Integration with ASTRI Work Order Dialog

**Overview:**
The KML export is triggered from `rwwi_astri_workorder_dialog` through a new button, similar to the existing "Generate BoQ" functionality.

**Design Principles:**
- Button labeled "Export Smallworld KML"
- Only enabled when selected work order has a design
- Exports based on design area and infrastructure type from work order
- Single KMZ file with dynamic network level detection

### 6.2 Dialog Button Addition

**File:** `rwwi_astri_workorder/source/rwwi_astri_workorder_dialog.magik`

**Add Button to Detail Panel:**
```magik
_private _method rwwi_astri_workorder_dialog.build_detail_panel(parent)
    ## Build detail panel with buttons (existing method - add new button)

    # ... existing code for container setup ...

    # Existing BoQ buttons
    .items[:boq_excel_btn] << sw_button_item.new(button_con,
        :label, "Generate BoQ as Excel",
        :model, _self,
        :selector, :generate_boq_excel|()|)
    .items[:boq_excel_btn].enabled? << _false

    .items[:boq_json_btn] << sw_button_item.new(button_con,
        :label, "Generate BoQ as JSON",
        :model, _self,
        :selector, :generate_boq_json|()|)
    .items[:boq_json_btn].enabled? << _false

    # NEW: Export KML button
    .items[:export_kml_btn] << sw_button_item.new(button_con,
        :label, "Export Smallworld KML",
        :model, _self,
        :selector, :export_smallworld_kml|()|)
    .items[:export_kml_btn].enabled? << _false
_endmethod
$
```

### 6.3 Button Enable Logic

**Update Detail Panel Method:**
```magik
_method rwwi_astri_workorder_dialog.update_detail_panel(wo)
    ## Update detail panel with selected work order info (existing method - update)

    # ... existing code ...

    _if wo _isnt _unset
    _then
        # ... existing code to display WO details ...

        # Enable BoQ and KML export buttons only if project and design exist
        _local (has_project, has_design) << _self.check_project_and_design_exist(wo)
        .items[:boq_excel_btn].enabled? << has_project _and has_design
        .items[:boq_json_btn].enabled? << has_project _and has_design

        # NEW: Enable KML export button
        .items[:export_kml_btn].enabled? << has_project _and has_design
    _endif
_endmethod
$
```

### 6.4 Export KML Action Handler

**File:** `rwwi_astri_workorder/source/rwwi_astri_workorder_dialog.magik`

**Add Export Method:**
```magik
_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.export_smallworld_kml()
    ## Export Smallworld objects to KML based on selected work order
    ## Exports design area with infrastructure type filtering

    _if .selected_wo _is _unset
    _then
        _self.show_alert("No Work Order Selected",
            "Please select a work order to export")
        _return
    _endif

    # Get work order details
    wo_number << .selected_wo[:wo_number]
    infra_type << .selected_wo[:infrastructure_type].default("cluster")

    write("Exporting KML for WO:", wo_number, " Infrastructure:", infra_type)

    # Get project and design
    _try _with errCond
        project << .engine.get_project_by_name(wo_number)
        _if project _is _unset
        _then
            _self.show_alert("Project Not Found",
                write_string("Project '", wo_number, "' not found"))
            _return
        _endif

        design << .engine.get_design_by_project(project)
        _if design _is _unset
        _then
            _self.show_alert("Design Not Found",
                write_string("No design found for project '", wo_number, "'"))
            _return
        _endif

        # Get design boundary
        design_area << .engine.get_design_boundary(design)
        _if design_area _is _unset
        _then
            _self.show_alert("Design Area Not Found",
                "Design has no boundary area defined")
            _return
        _endif

        # Show file chooser dialog
        output_file << _self.get_output_file_from_user(wo_number + ".kmz")
        _if output_file _is _unset
        _then
            _return  # User cancelled
        _endif

        # Create exporter
        exporter << rwi_export_to_aerial_kmz.new()
        exporter.gc << gis_program_manager.cached_dataset(:gis)
        exporter.project_name << wo_number
        exporter.project_area << design_area
        exporter.infrastructure_type << infra_type.as_symbol()

        # Show progress
        _self.append_text(write_string("Exporting KML for ", wo_number, "...", newline_char))

        # Export with dynamic network detection
        output_path << exporter.export_mixed_network(design_area, output_file)

        # Success message
        _self.show_message("Export Complete",
            write_string("Created: ", output_path))
        _self.append_text(write_string("Export completed: ", output_path, newline_char))

    _when error
        _self.show_alert("Export Failed", errCond.report_contents_string)
        _self.append_text(write_string("Export failed: ", errCond.report_contents_string, newline_char))
    _endtry
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.get_output_file_from_user(default_filename)
    ## Shows file chooser dialog for KMZ output

    file_chooser << file_dialog.new("Save KMZ File",
        _self.top_frame,
        system.temp_directory_name,
        default_filename,
        :save,
        "*.kmz")

    _return file_chooser.activate()
_endmethod
$
```

### 6.5 Helper Methods in Engine

**File:** `rwwi_astri_workorder/source/rwwi_astri_workorder_engine.magik`

**Add Design Boundary Extraction:**
```magik
_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_engine.get_design_boundary(design)
    ## Extract boundary polygon from design
    ##
    ## Parameters:
    ##   design - Smallworld design object
    ##
    ## Returns:
    ##   pseudo_polygon - Design boundary area for spatial filtering

    _try _with errCond
        # Get design boundary from design manager
        _if design.respond_to?(:boundary)
        _then
            boundary << design.boundary
            _if boundary _isnt _unset
            _then
                _return boundary.area
            _endif
        _endif

        # Alternative: Get from project boundary
        project << design.project
        _if project _isnt _unset _and project.respond_to?(:boundary)
        _then
            proj_boundary << project.boundary
            _if proj_boundary _isnt _unset
            _then
                _return proj_boundary.area
            _endif
        _endif

        _return _unset

    _when error
        write("Error getting design boundary:", errCond.report_contents_string)
        _return _unset
    _endtry
_endmethod
$
```

### 6.6 Infrastructure Type Filtering

**Overview:**
The exporter filters objects based on the infrastructure type from the work order.

**Infrastructure Type Mapping:**
- **cluster** → Export CLUSTER network level only
- **feeder** → Export FEEDER network level only
- **subfeeder** → Export SUBFEEDER network level only
- **all** or unset → Export all detected network levels (mixed network)

**File:** `rwi_export_to_kml/source/rwi_export_to_aerial_kmz.magik`

**Add Infrastructure Type Slot:**
```magik
def_slotted_exemplar(:rwi_export_to_aerial_kmz,
    {
        {:gc, _unset, :writable},                    # GIS case
        {:project_name, _unset, :writable},          # Project name
        {:project_area, _unset, :writable},          # Design boundary area
        {:output_directory, _unset, :writable},      # Output directory
        {:infrastructure_type, _unset, :writable}    # NEW: Infrastructure type filter
    },
    {:object})
$
```

**Update Export Method to Use Infrastructure Type:**
```magik
_method rwi_export_to_aerial_kmz.export_mixed_network(p_area, p_output_file)
    ## Export with infrastructure type filtering

    # Detect network levels in area
    levels << _self.detect_network_levels_in_area(p_area)

    # Filter based on infrastructure type
    _if .infrastructure_type _isnt _unset _and
        .infrastructure_type <> :all
    _then
        # Filter to specific infrastructure type
        _if .infrastructure_type _is :cluster
        _then
            levels[:feeder] << _false
            levels[:subfeeder] << _false
        _elif .infrastructure_type _is :feeder
        _then
            levels[:subfeeder] << _false
            levels[:cluster] << _false
        _elif .infrastructure_type _is :subfeeder
        _then
            levels[:feeder] << _false
            levels[:cluster] << _false
        _endif
    _endif

    # Export only enabled levels
    # ... existing export logic ...
_endmethod
$
```

---

## Section 7: Implementation Schedule

### Phase 1: Foundation (Week 1)
- **Day 1-2:** Create module structure and skeleton classes
- **Day 3:** Implement folder builder with configuration loading
- **Day 4:** Implement style manager and template loading
- **Day 5:** Write unit tests for folder structure

### Phase 2: Core Export Logic (Week 2)
- **Day 1-2:** Implement network level determination
- **Day 3:** Implement pole classification and folder assignment
- **Day 4:** Implement LINE assignment logic
- **Day 5:** Write unit tests for classification logic

### Phase 3: Object Writers (Week 3)
- **Day 1:** Implement pole writer
- **Day 2:** Implement cable writer
- **Day 3:** Implement equipment writers (FAT/FDT/closure)
- **Day 4:** Implement HP writer
- **Day 5:** Write unit tests for object writers

### Phase 4: Export Orchestration (Week 4)
- **Day 1:** Implement FEEDER export
- **Day 2:** Implement SUBFEEDER export
- **Day 3:** Implement CLUSTER export with 4 LINEs
- **Day 4:** Integration testing
- **Day 5:** Bug fixes and optimization

### Phase 5: UI and Polish (Week 5)
- **Day 1:** Implement GUI actions
- **Day 2:** Implement progress dialogs
- **Day 3:** Create resource files (styles, config)
- **Day 4:** End-to-end testing with real data
- **Day 5:** Documentation and code review

---

## Section 8: File Checklist

### Source Files to Create

- [ ] `rwi_export_to_aerial_kmz.magik` (Main exemplar, ~800 lines)
- [ ] `rwi_aerial_kmz_style_manager.magik` (Style management, ~300 lines)
- [ ] `rwi_aerial_kmz_folder_builder.magik` (Folder generation, ~400 lines)
- [ ] `rwi_aerial_kmz_object_writer.magik` (Object serialization, ~600 lines)
- [ ] `rwi_aerial_kmz_line_assigner.magik` (LINE assignment, ~300 lines)
- [ ] `rwi_aerial_kmz_helpers.magik` (Utilities, ~200 lines)
- [ ] `rwi_export_to_aerial_kmz_action.magik` (GUI action handler, ~200 lines)
- [ ] `rwi_export_to_aerial_kmz_test_case.magik` (Unit tests, ~400 lines)
- [ ] `rwi_export_to_aerial_kmz_integration_test.magik` (Integration tests, ~300 lines)

**Total Estimated Lines of Code:** ~3,500 lines

### Resource Files to Create

- [ ] `resources/base/data/aerial_kmz_styles.kml` (Style template from sample.kml)
- [ ] `resources/base/data/aerial_kmz_config.xml` (Folder structure configuration)
- [ ] `resources/base/data/aerial_kmz_line_config.xml` (LINE assignment rules)
- [ ] `resources/base/data/gui.xml` (GUI action definitions) - **UPDATE EXISTING**

### Documentation Files to Create

- [ ] `docs/changelog-20251126-aerial-kmz-export-implementation-summary.md` (After implementation)
- [ ] `docs/aerial-kmz-export-user-guide.md` (User documentation)

---

## Section 9: Risk Assessment and Mitigation

### Risk 1: Incomplete Attribute Data
**Risk:** Pole height/class data may not be consistently populated
**Impact:** High - Incorrect folder assignment
**Mitigation:**
- Implement robust attribute extraction with fallback defaults
- Add validation report showing objects with missing attributes
- Allow manual override via configuration

### Risk 2: LINE Assignment Ambiguity
**Risk:** HP points may not have clear LINE identifiers
**Impact:** Medium - Objects in wrong LINE folders
**Mitigation:**
- Implement multiple assignment strategies (name pattern, proximity, manual)
- Log assignment decisions for review
- Provide tool to reassign objects after export

### Risk 3: Performance with Large Datasets
**Risk:** Export may be slow for 10,000+ objects
**Impact:** Medium - Poor user experience
**Mitigation:**
- Implement streaming KML writing (don't buffer entire file)
- Cache network level determinations
- Add progress feedback

### Risk 4: Style Template Compatibility
**Risk:** Styles from sample.kml may not work for all object types
**Impact:** Low - Visual inconsistency
**Mitigation:**
- Create comprehensive style template covering all objects
- Implement fallback styles
- Test in Google Earth before deployment

### Risk 5: Coordinate Transformation Errors
**Risk:** Coordinate conversion may fail for some geometries
**Impact:** High - Objects in wrong locations
**Mitigation:**
- Reuse proven coordinate transformation from existing exporter
- Add validation to check coordinate ranges
- Log transformation failures

---

## Section 10: Success Criteria

### Functional Success Criteria
- [ ] Export generates valid KML 2.2 XML
- [ ] KMZ opens correctly in Google Earth Pro 7.3+
- [ ] All 15 FEEDER folders present and populated
- [ ] All 14 SUBFEEDER folders present and populated
- [ ] CLUSTER has 4 LINEs with 14 subfolders each (58 total)
- [ ] HP COVER has 5 zones per LINE (20 zones total)
- [ ] Poles assigned to correct folders based on type/owner/height/class
- [ ] Objects use correct styles from sample.kml reference
- [ ] BalloonStyle popups display attributes
- [ ] Coordinates in WGS84 match original database locations

### Performance Success Criteria
- [ ] Export 1,000 objects in < 30 seconds
- [ ] Export 10,000 objects in < 5 minutes
- [ ] Memory usage < 500MB during export

### Quality Success Criteria
- [ ] All unit tests pass (>20 test cases)
- [ ] All integration tests pass (>5 test cases)
- [ ] Code review approved by senior developer
- [ ] User acceptance testing completed
- [ ] Documentation complete and reviewed

---

## Section 11: Open Questions (To Be Resolved)

1. **Q:** Should we support mixed-mode export (e.g., FEEDER + SUBFEEDER in one file)?
   **A:** TBD - Recommend separate files for clarity

2. **Q:** How should we handle objects with ambiguous network level (e.g., pole connected to both feeder and cluster cables)?
   **A:** TBD - Propose placing in highest level and referencing in lower levels

3. **Q:** Should HP COVER zones be assigned automatically or require manual definition?
   **A:** TBD - Recommend automatic assignment with manual override option

4. **Q:** What should happen if project has < 4 LINEs (e.g., only LINE A and LINE B)?
   **A:** TBD - Propose still creating all 4 folders but leaving unused ones empty

5. **Q:** Should we support exporting to KML format specification other than 2.2?
   **A:** TBD - KML 2.2 is current standard, no need for other versions

6. **Q:** How should we handle SLING WIRE objects (do they exist in database)?
   **A:** TBD - Check if `mit_sling_wire` table exists, otherwise skip folder

---

## Section 12: Approval and Sign-Off

### Stakeholders

| Role | Name | Approval Required | Status |
|------|------|-------------------|--------|
| **Product Owner** | TBD | Yes | PENDING |
| **Technical Lead** | TBD | Yes | PENDING |
| **GIS Architect** | TBD | Yes | PENDING |
| **QA Lead** | TBD | Yes | PENDING |

### Approval Workflow

1. **Initial Review** - Technical Lead reviews plan for feasibility
2. **Stakeholder Review** - All stakeholders review requirements and design
3. **Approval Meeting** - Discuss open questions and finalize decisions
4. **Sign-Off** - All stakeholders approve to proceed with implementation

---

## Appendices

### Appendix A: Reference Mapping Table

**Existing Export → Aerial KMZ Mapping:**

| Existing Folder | Current Objects | Aerial KMZ Folder(s) | Network Level |
|----------------|----------------|---------------------|---------------|
| OLT | buildings (type=STO) | FEEDER > OLT | Feeder only |
| Underground Route | underground_route | (Not in aerial format) | N/A |
| POLE > Tiang Baru | poles (proposed) | NEW POLE [OWNER] [HEIGHT]-[CLASS] | All levels |
| POLE > Tiang Existing | poles (in service) | EXISTING POLE [OWNER] [HEIGHT]-[CLASS] | All levels |
| Manhole/Handhole | uub | FEEDER > HANDHOLE 80X80X130 | Feeder only |
| FDT | mit_terminal_enclosure OR sheath_splice (type=FDT) | CLUSTER > FDT (or CLUSTER > FDT_[NAME] if multiple) | Cluster only |
| FAT | sheath_splice (type=FAT) | CLUSTER > LINE [X] > FAT (under FDT hierarchy) | Cluster only |
| FAT Zone | ftth!zone | CLUSTER > LINE [X] > BOUNDARY FAT | Cluster only |
| Joint Closure | sheath_splice (type=Joint Closure or Closure) | FEEDER > JOINT CLOSURE / SUBFEEDER > JOINT CLOSURE | Feeder/Subfeeder only |
| Tower | mit_tower | (Not in aerial format) | N/A |
| KABEL | sheath_with_loc | CABLE / DISTRIBUTION CABLE | All levels |
| Boundary | project boundary | BOUNDARY FEEDER / SUBFEEDER / CLUSTER (named from design/scheme) | All levels |
| Slack Hanger | mit_figure_eight | SLACK HANGER (uses line_type field) | All levels |
| Sling Wire | mit_sling_wire | SLING WIRE (uses line_type field) | Cluster only |
| Homepass | ftth!demand_point | HP COVER / HP UNCOVER (uses comments field) | Cluster only |

### Appendix B: Example File Names

**Generated KMZ File (Single, Dynamic):**
- `PLB_PROJECT_001.KMZ` - Contains all detected network levels dynamically

**Old Approach (Deprecated):**
- ~~`PLB_PROJECT_001 FEEDER CODE.KMZ`~~ (separate files)
- ~~`PLB_PROJECT_001 SUBFEEDER CODE.KMZ`~~ (separate files)
- ~~`PLB_PROJECT_001 CLUSTER CODE.KMZ`~~ (separate files)

**New Approach:** Single KMZ with dynamic top-level folders based on what's detected in area

### Appendix C: Glossary

| Term | Definition |
|------|------------|
| **AERIAL** | Above-ground network infrastructure (poles, aerial cables) |
| **FAT** | Fiber Access Terminal (same as ODP - Optical Distribution Point) |
| **FDT** | Fiber Distribution Terminal (same as ODC - Optical Distribution Cabinet) |
| **HP** | Homepass - Customer premises or potential service location |
| **LINE** | Distribution line serving specific geographic area (A/B/C/D) |
| **KMZ** | Zipped KML file format (KML + optional resources) |
| **OLT** | Optical Line Terminal - Network origin point (Central Office) |
| **StyleMap** | KML element pairing normal and highlight styles for interactivity |

---

**End of Implementation Plan**

---

**Document Status:** ✅ READY FOR REVIEW (UPDATED with dynamic structure requirements)
**Next Step:** Stakeholder review and approval meeting
**Implementation Start:** Pending approval
**Estimated Completion:** 5 weeks from approval
**Document Version:** 1.1
**Last Updated:** 2025-11-27
**Revision Notes:**
- **UI Integration:** Changed from standalone actions to ASTRI Work Order Dialog integration
- **Trigger:** Export button in astri_workorder_dialog (similar to "Generate BoQ")
- **Button Enable:** Only enabled when selected work order has a design
- **Design Area Filtering:** Exports only objects within design boundary area
- **Infrastructure Type Filtering:** Filters network levels based on WO infrastructure type (cluster/feeder/subfeeder)
- Added dynamic FDT-based cluster folder generation (multiple FDTs → separate FDT folders)
- Changed FDT folder naming from "CLUSTER [FDT_NAME]" to "FDT_[FDT_NAME]" (removed CLUSTER prefix)
- Added mixed network level detection and export (single KMZ contains all detected levels)
- Updated export strategy from 3 separate files to 1 dynamic file
- Added FDT detection from TWO object types: mit_terminal_enclosure OR sheath_splice (type=FDT)
- Added Joint Closure detection from sheath_splice (type=Joint Closure or Closure) - FEEDER/SUBFEEDER only
- Added FAT Zone boundary export from ftth!zone objects to BOUNDARY FAT folders
- Added LINE assignment logic: line_type field for standard objects (including sling wire, slack hanger), comments field for zones/demand_points
- Updated boundary naming to use design/scheme name (BOUNDARY FEEDER/SUBFEEDER/CLUSTER)
- Added ring_name-based object assignment with 3-level priority (ring_name → proximity → default)
- Added example scenarios for various network configurations

---

## CRITICAL IMPLEMENTATION NOTES (v1.1)

### Key Changes from Original Plan:

1. **UI Integration** ⭐ NEW
   - Original: Standalone GUI actions
   - **New: Integrated into ASTRI Work Order Dialog**
   - Button: "Export Smallworld KML" (similar to "Generate BoQ")
   - Enable condition: Selected work order must have a design
   - Automatic extraction of design area and infrastructure type from work order

2. **Design Area-Based Export** ⭐ NEW
   - Original: User-defined area selection
   - **New: Automatic filtering to design boundary**
   - Exports only objects within design area
   - Design boundary extracted from Design Manager
   - Uses work order number to locate project and design

3. **Infrastructure Type Filtering** ⭐ NEW
   - Original: Manual selection of network level
   - **New: Automatic filtering based on WO infrastructure type**
   - cluster → CLUSTER network only
   - feeder → FEEDER network only
   - subfeeder → SUBFEEDER network only
   - all/unset → Mixed network (all detected levels)

4. **Dynamic Cluster Structure**
   - Original: Fixed 4 LINEs at top level
   - **New: Dynamic FDT_[FDT_NAME] folders based on FDT count**
   - If 1 FDT → simplified structure (FDT at top level)
   - If 2+ FDTs → separate FDT_[NAME] folder per FDT (NO "CLUSTER" prefix)

5. **Mixed Network Support**
   - Original: Export one network level per file
   - **New: Detect all levels in area, export all in single KMZ**
   - Possible combinations: FEEDER only, SUBFEEDER only, CLUSTER only, FEEDER+CLUSTER, SUBFEEDER+CLUSTER, or all three

6. **File Output**
   - Original: 3 separate KMZ files (FEEDER CODE.KMZ, SUBFEEDER CODE.KMZ, CLUSTER CODE.KMZ)
   - **New: 1 dynamic KMZ file ([WO_NUMBER].KMZ) with top-level folders for detected network levels**

7. **Object Assignment Logic**
   - **Primary: Use ring_name field** to identify which FDT each object belongs to
   - **LINE Assignment**: Use line_type field (standard objects), comments field (zones/demand_points)
   - Fallback: Use FDT proximity calculation for cluster object assignment
   - Objects assigned to nearest FDT when ring_name unavailable

8. **Enhanced Object Detection**
   - FDT can be EITHER `mit_terminal_enclosure` OR `sheath_splice` with type="FDT"
   - Joint Closure detected from `sheath_splice` with type="Joint Closure" or "Closure" (FEEDER/SUBFEEDER only)
   - FAT Zone from `ftth!zone` exported to BOUNDARY FAT folders
   - Sling Wire and Slack Hanger use line_type field for LINE assignment
   - Boundary naming uses design/scheme name (not generic "PROJECT BOUNDARY")

These changes ensure the export integrates seamlessly with the ASTRI work order workflow and adapts to real-world project scenarios where areas contain mixed network types and variable numbers of distribution terminals.