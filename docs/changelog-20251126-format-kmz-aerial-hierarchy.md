# FORMAT KMZ AERIAL - Hierarchy Structure Analysis

## Document Information
- **Source File:** `astri_template.kml` (rev28102025)
- **Analysis Date:** 2025-11-26
- **Purpose:** Define standard KML folder structure for aerial FTTH network imports

---

## Overview

The FORMAT KMZ AERIAL defines a standardized hierarchical folder structure for organizing aerial fiber-to-the-home (FTTH) network data in KML/KMZ format. This structure is used by the ASTRI integration system to parse and migrate design data from Google Earth KML files into the Smallworld GIS database.

**Top-Level Path:** `Format KMZ Design (rev28102025) → FORMAT KMZ AERIAL`

---

## Complete Hierarchy Structure

### Level 1: Network Type Folders

The FORMAT KMZ AERIAL contains three main network type folders:

1. **FEEDER CODE.KMZ** - Backbone distribution network
2. **SUBFEEDER CODE.KMZ** - Intermediate distribution network
3. **CLUSTER CODE.KMZ** - Last-mile customer access network

---

## 1. FEEDER CODE.KMZ

### Purpose
Contains backbone fiber infrastructure connecting the central office (OLT) to intermediate distribution points.

### Folder Hierarchy

```
FEEDER CODE.KMZ
├── OLT
├── CABLE
├── EXISTING POLE EMR 7-3
├── EXISTING POLE EMR 7-4
├── EXISTING POLE EMR 7-5
├── EXISTING POLE EMR 9-5
├── EXISTING POLE EMR 9-4
├── EXISTING POLE PARTNER 7-4
├── EXISTING POLE PARTNER 9-4
├── NEW POLE 7-5
├── NEW POLE 7-4
├── NEW POLE 9-5
├── NEW POLE 9-4
├── JOINT CLOSURE
├── SLACK HANGER
└── HANDHOLE 80X80X130
```

### Folder Details

| Folder Name | Object Type | Description | Smallworld Table |
|-------------|-------------|-------------|------------------|
| **OLT** | Central Office | Optical Line Terminal building | `building` |
| **CABLE** | Fiber Cable | Feeder fiber optic cables | `sheath_with_loc` via `aerial_route` |
| **EXISTING POLE EMR 7-3** | Pole | EMR-owned pole, 7m height, class 3, existing | `pole` |
| **EXISTING POLE EMR 7-4** | Pole | EMR-owned pole, 7m height, class 4, existing | `pole` |
| **EXISTING POLE EMR 7-5** | Pole | EMR-owned pole, 7m height, class 5, existing | `pole` |
| **EXISTING POLE EMR 9-5** | Pole | EMR-owned pole, 9m height, class 5, existing | `pole` |
| **EXISTING POLE EMR 9-4** | Pole | EMR-owned pole, 9m height, class 4, existing | `pole` |
| **EXISTING POLE PARTNER 7-4** | Pole | Partner-owned pole, 7m height, class 4, existing | `pole` |
| **EXISTING POLE PARTNER 9-4** | Pole | Partner-owned pole, 9m height, class 4, existing | `pole` |
| **NEW POLE 7-5** | Pole | New pole to be installed, 7m height, class 5 | `pole` |
| **NEW POLE 7-4** | Pole | New pole to be installed, 7m height, class 4 | `pole` |
| **NEW POLE 9-5** | Pole | New pole to be installed, 9m height, class 5 | `pole` |
| **NEW POLE 9-4** | Pole | New pole to be installed, 9m height, class 4 | `pole` |
| **JOINT CLOSURE** | Splice | Cable splice enclosure | `sheath_splice` |
| **SLACK HANGER** | Accessory | Cable slack storage hanger | `mit_figure_eight` |
| **HANDHOLE 80X80X130** | Access Point | Underground access box (80x80x130cm) | `uub` |

### Pole Naming Convention

**Format:** `[STATUS] POLE [OWNER] [HEIGHT]-[CLASS]`

- **STATUS:** EXISTING | NEW
- **OWNER:** EMR (default) | PARTNER
- **HEIGHT:** 7 (7 meters) | 9 (9 meters)
- **CLASS:** 3 | 4 | 5 (strength/load rating)

**Examples:**
- `EXISTING POLE EMR 7-3` = Existing EMR pole, 7m tall, class 3
- `NEW POLE 9-5` = New pole to install, 9m tall, class 5
- `EXISTING POLE PARTNER 7-4` = Existing partner pole, 7m tall, class 4

---

## 2. SUBFEEDER CODE.KMZ

### Purpose
Contains intermediate distribution network connecting feeder to cluster distribution points.

### Folder Hierarchy

```
SUBFEEDER CODE.KMZ
├── JOINT CLOSURE
├── EXISTING POLE EMR 7-2.5
├── EXISTING POLE EMR 7-3
├── EXISTING POLE EMR 7-4
├── EXISTING POLE EMR 7-5
├── EXISTING POLE EMR 9-5
├── EXISTING POLE EMR 9-4
├── EXISTING POLE PARTNER 7-4
├── EXISTING POLE PARTNER 9-4
├── NEW POLE 7-5
├── NEW POLE 9-5
├── NEW POLE 7-4
├── NEW POLE 9-4
├── CABLE
└── SLACK HANGER
```

### Folder Details

| Folder Name | Object Type | Description | Smallworld Table |
|-------------|-------------|-------------|------------------|
| **JOINT CLOSURE** | Splice | Cable splice enclosure | `sheath_splice` |
| **EXISTING POLE EMR 7-2.5** | Pole | EMR-owned pole, 7m height, class 2.5, existing | `pole` |
| **EXISTING POLE EMR 7-3** | Pole | EMR-owned pole, 7m height, class 3, existing | `pole` |
| **EXISTING POLE EMR 7-4** | Pole | EMR-owned pole, 7m height, class 4, existing | `pole` |
| **EXISTING POLE EMR 7-5** | Pole | EMR-owned pole, 7m height, class 5, existing | `pole` |
| **EXISTING POLE EMR 9-5** | Pole | EMR-owned pole, 9m height, class 5, existing | `pole` |
| **EXISTING POLE EMR 9-4** | Pole | EMR-owned pole, 9m height, class 4, existing | `pole` |
| **EXISTING POLE PARTNER 7-4** | Pole | Partner-owned pole, 7m height, class 4, existing | `pole` |
| **EXISTING POLE PARTNER 9-4** | Pole | Partner-owned pole, 9m height, class 4, existing | `pole` |
| **NEW POLE 7-5** | Pole | New pole to be installed, 7m height, class 5 | `pole` |
| **NEW POLE 9-5** | Pole | New pole to be installed, 9m height, class 5 | `pole` |
| **NEW POLE 7-4** | Pole | New pole to be installed, 7m height, class 4 | `pole` |
| **NEW POLE 9-4** | Pole | New pole to be installed, 9m height, class 4 | `pole` |
| **CABLE** | Fiber Cable | Sub-feeder fiber optic cables | `sheath_with_loc` via `aerial_route` |
| **SLACK HANGER** | Accessory | Cable slack storage hanger | `mit_figure_eight` |

### Key Differences from Feeder
- **No OLT folder** (OLT only at feeder level)
- **No HANDHOLE folder** (no underground access points at this level)
- **Includes 7-2.5 pole class** (lighter-duty poles)
- **Folder order changed** (JOINT CLOSURE moved to top)

---

## 3. CLUSTER CODE.KMZ

### Purpose
Contains last-mile customer access network from FDT to individual customer premises (homepass).

### Top-Level Hierarchy

```
CLUSTER CODE.KMZ
├── BOUNDARY CLUSTER
├── FDT
├── LINE A
│   └── [14 subfolders - detailed below]
├── LINE B
│   └── [14 subfolders - identical structure to LINE A]
├── LINE C
│   └── [14 subfolders - identical structure to LINE A]
└── LINE D
    └── [14 subfolders - identical structure to LINE A]
```

### Cluster-Level Folders

| Folder Name | Object Type | Description | Smallworld Table |
|-------------|-------------|-------------|------------------|
| **BOUNDARY CLUSTER** | Boundary | Polygon defining cluster service area | Design area boundary |
| **FDT** | Terminal | Fiber Distribution Terminal (splitter location) | `mit_terminal_enclosure` |
| **LINE A** | Line Group | Distribution line A (serves specific geographic area) | Logical grouping |
| **LINE B** | Line Group | Distribution line B | Logical grouping |
| **LINE C** | Line Group | Distribution line C | Logical grouping |
| **LINE D** | Line Group | Distribution line D | Logical grouping |

### LINE Structure (Applies to LINE A, B, C, D)

Each LINE folder contains **14 subfolders** in this exact order:

```
LINE [A/B/C/D]
├── BOUNDARY FAT
├── FAT
├── HP COVER
│   ├── [A/B/C/D]01
│   ├── [A/B/C/D]02
│   ├── [A/B/C/D]03
│   ├── [A/B/C/D]04
│   └── [A/B/C/D]05
├── HP UNCOVER
├── EXISTING POLE EMR 7-2.5
├── EXISTING POLE EMR 7-3
├── EXISTING POLE EMR 7-4
├── EXISTING POLE EMR 9-4
├── EXISTING POLE PARTNER 7-4
├── EXISTING POLE PARTNER 9-4
├── NEW POLE 7-2.5
├── NEW POLE 7-3
├── NEW POLE 7-4
├── NEW POLE 9-4
├── DISTRIBUTION CABLE
├── SLACK HANGER
└── SLING WIRE
```

### LINE Subfolder Details

| Folder Name | Object Type | Description | Smallworld Table |
|-------------|-------------|-------------|------------------|
| **BOUNDARY FAT** | Boundary | Polygon defining FAT service area | Logical boundary |
| **FAT** | Splitter | Fiber Access Terminal (ODP/splitter) | `sheath_splice` |
| **HP COVER** | Coverage Group | Covered homepass (serviceable premises) | Container folder |
| **[LINE]01** to **[LINE]05** | Coverage Zone | Sub-zones within coverage area (A01-A05, B01-B05, etc.) | `ftth!demand_point` grouping |
| **HP UNCOVER** | Homepass | Uncovered homepass (not yet serviceable) | `ftth!demand_point` |
| **EXISTING POLE EMR 7-2.5** | Pole | EMR-owned pole, 7m height, class 2.5, existing | `pole` |
| **EXISTING POLE EMR 7-3** | Pole | EMR-owned pole, 7m height, class 3, existing | `pole` |
| **EXISTING POLE EMR 7-4** | Pole | EMR-owned pole, 7m height, class 4, existing | `pole` |
| **EXISTING POLE EMR 9-4** | Pole | EMR-owned pole, 9m height, class 4, existing | `pole` |
| **EXISTING POLE PARTNER 7-4** | Pole | Partner-owned pole, 7m height, class 4, existing | `pole` |
| **EXISTING POLE PARTNER 9-4** | Pole | Partner-owned pole, 9m height, class 4, existing | `pole` |
| **NEW POLE 7-2.5** | Pole | New pole to be installed, 7m height, class 2.5 | `pole` |
| **NEW POLE 7-3** | Pole | New pole to be installed, 7m height, class 3 | `pole` |
| **NEW POLE 7-4** | Pole | New pole to be installed, 7m height, class 4 | `pole` |
| **NEW POLE 9-4** | Pole | New pole to be installed, 9m height, class 4 | `pole` |
| **DISTRIBUTION CABLE** | Fiber Cable | Drop/distribution cables to customers | `sheath_with_loc` via `aerial_route` |
| **SLACK HANGER** | Accessory | Cable slack storage hanger | `mit_figure_eight` |
| **SLING WIRE** | Accessory | Support wire for cable attachment | `mit_sling_wire` |

### HP COVER Sub-Zones

Each **HP COVER** folder contains **5 sub-zone folders** labeled by line letter:

- **LINE A:** A01, A02, A03, A04, A05
- **LINE B:** B01, B02, B03, B04, B05
- **LINE C:** C01, C02, C03, C04, C05
- **LINE D:** D01, D02, D03, D04, D05

**Purpose:** Geographic subdivision of serviceable area for tracking/reporting purposes.

### Key Differences from Feeder/Subfeeder

1. **LINE-based Organization** - Network divided into 4 distribution lines (A/B/C/D)
2. **BOUNDARY Folders** - Geographic boundaries for cluster and FAT service areas
3. **FDT Equipment** - Fiber Distribution Terminal (higher-level splitter)
4. **FAT Equipment** - Fiber Access Terminal (customer-side splitter)
5. **Homepass Folders** - Customer premises organized by coverage status
   - **HP COVER** - Serviceable premises (subdivided into zones)
   - **HP UNCOVER** - Not yet serviceable
6. **Lighter Poles** - Includes 7-2.5 and 7-3 classes (customer access network)
7. **SLING WIRE** - Additional accessory for last-mile installations
8. **DISTRIBUTION CABLE** - Named differently from CABLE to indicate last-mile function

---

## Network Hierarchy and Relationships

### Hierarchical Flow

```
OLT (Central Office)
  ↓ [FEEDER]
Intermediate Splicing Point
  ↓ [SUBFEEDER]
FDT (Fiber Distribution Terminal)
  ↓ [CLUSTER - LINE A/B/C/D]
FAT (Fiber Access Terminal / ODP)
  ↓ [DISTRIBUTION]
Customer Premises (Homepass)
```

### Geographic Coverage

```
BOUNDARY CLUSTER (Entire cluster service area)
  └── LINE A Service Area
      └── BOUNDARY FAT (FAT service area)
          └── HP COVER Zones (A01, A02, A03, A04, A05)
              └── Individual Customer Points
```

---

## Object Type Summary

### Infrastructure Objects

| Object Type | Found In | Status Variants | Size/Class Variants |
|-------------|----------|----------------|---------------------|
| **OLT** | Feeder | N/A | N/A |
| **FDT** | Cluster | N/A | N/A |
| **FAT/ODP** | Cluster (per LINE) | N/A | N/A |
| **Pole** | All levels | EXISTING, NEW | EMR/PARTNER, 7m/9m, 2.5/3/4/5 |
| **Handhole** | Feeder only | N/A | 80x80x130 |

### Network Objects

| Object Type | Found In | Description |
|-------------|----------|-------------|
| **CABLE** | Feeder, Subfeeder | Backbone/intermediate cables |
| **DISTRIBUTION CABLE** | Cluster (per LINE) | Last-mile customer cables |
| **JOINT CLOSURE** | Feeder, Subfeeder | Cable splices |
| **SLACK HANGER** | All levels | Cable slack storage |
| **SLING WIRE** | Cluster (per LINE) | Cable support wire |

### Logical Objects

| Object Type | Found In | Description |
|-------------|----------|-------------|
| **BOUNDARY CLUSTER** | Cluster | Cluster service area boundary |
| **BOUNDARY FAT** | Cluster (per LINE) | FAT service area boundary |
| **HP COVER** | Cluster (per LINE) | Covered homepass container |
| **HP UNCOVER** | Cluster (per LINE) | Uncovered homepass |
| **Zone Folders** | Under HP COVER | A01-A05, B01-B05, C01-C05, D01-D05 |

---

## Folder Ordering Rules

### Feeder Level Order
1. OLT (source)
2. CABLE (transmission medium)
3. Existing Poles (grouped by spec: height-class)
4. New Poles (grouped by spec: height-class)
5. Splicing Equipment (JOINT CLOSURE)
6. Accessories (SLACK HANGER)
7. Underground Access (HANDHOLE)

### Subfeeder Level Order
1. Splicing Equipment (JOINT CLOSURE)
2. Existing Poles (grouped by spec)
3. New Poles (grouped by spec)
4. CABLE (transmission medium)
5. Accessories (SLACK HANGER)

### Cluster Level Order (per LINE)
1. BOUNDARY FAT (service area definition)
2. FAT (distribution point)
3. HP COVER (serviceable premises with sub-zones)
4. HP UNCOVER (non-serviceable premises)
5. Existing Poles (grouped by spec)
6. New Poles (grouped by spec)
7. DISTRIBUTION CABLE (transmission medium)
8. Accessories (SLACK HANGER, SLING WIRE)

---

## Pole Classification System

### Pole Attributes Encoded in Folder Name

**Format:** `[STATUS] POLE [OWNER] [HEIGHT]-[CLASS]`

#### Status
- **EXISTING** - Currently installed pole (reused in design)
- **NEW** - Pole to be installed as part of project

#### Owner
- **EMR** - EMR-owned pole (default, often omitted in name)
- **PARTNER** - Third-party/partner-owned pole

#### Height (meters)
- **7** - 7 meter pole (standard low-rise)
- **9** - 9 meter pole (standard high-rise)

#### Class (Strength/Load Rating)
- **2.5** - Lightest duty (cluster distribution only)
- **3** - Light duty (cluster and subfeeder)
- **4** - Medium duty (all levels)
- **5** - Heavy duty (feeder and subfeeder)

### Complete Pole Type Matrix

| Pole Type | Feeder | Subfeeder | Cluster |
|-----------|--------|-----------|---------|
| EXISTING POLE EMR 7-2.5 | ❌ | ✅ | ✅ |
| EXISTING POLE EMR 7-3 | ✅ | ✅ | ✅ |
| EXISTING POLE EMR 7-4 | ✅ | ✅ | ✅ |
| EXISTING POLE EMR 7-5 | ✅ | ✅ | ❌ |
| EXISTING POLE EMR 9-4 | ✅ | ✅ | ✅ |
| EXISTING POLE EMR 9-5 | ✅ | ✅ | ❌ |
| EXISTING POLE PARTNER 7-4 | ✅ | ✅ | ✅ |
| EXISTING POLE PARTNER 9-4 | ✅ | ✅ | ✅ |
| NEW POLE 7-2.5 | ❌ | ❌ | ✅ |
| NEW POLE 7-3 | ❌ | ❌ | ✅ |
| NEW POLE 7-4 | ✅ | ✅ | ✅ |
| NEW POLE 7-5 | ✅ | ✅ | ❌ |
| NEW POLE 9-4 | ✅ | ✅ | ✅ |
| NEW POLE 9-5 | ✅ | ✅ | ❌ |

### Observations
- **Cluster networks** use lighter poles (2.5, 3, 4) - shorter spans, lighter cables
- **Feeder networks** use heavier poles (3, 4, 5) - longer spans, heavier cables
- **Class 5 poles** not used in cluster (over-engineered for short drops)
- **Class 2.5 poles** only in cluster (insufficient for backbone cables)

---

## Homepass Coverage System

### Coverage Classification

#### HP COVER (Covered Homepass)
- **Definition:** Customer premises within serviceable range of FAT
- **Structure:** Organized into 5 sub-zones per line
- **Zones:** [LINE]01 through [LINE]05
- **Purpose:**
  - Track serviceable premises count
  - Organize by geographic/logical zones
  - Facilitate construction phasing
  - Enable targeted marketing

#### HP UNCOVER (Uncovered Homepass)
- **Definition:** Customer premises NOT within serviceable range
- **Structure:** Single flat folder (no sub-zones)
- **Purpose:**
  - Identify coverage gaps
  - Plan network extensions
  - Track total addressable market
  - Prioritize future expansion

### Zone Numbering System

Each LINE has 5 zones numbered sequentially:

- **LINE A:** A01, A02, A03, A04, A05
- **LINE B:** B01, B02, B03, B04, B05
- **LINE C:** C01, C03, C03, C04, C05
- **LINE D:** D01, D02, D03, D04, D05

**Numbering Purpose:**
- Geographic subdivision (e.g., street blocks)
- Construction phasing (e.g., A01 phase 1, A02 phase 2)
- Splitter port grouping (e.g., ports 1-8, 9-16, etc.)
- Administrative districts

---

## Implementation Notes

### KML Parsing Requirements

1. **Exact Folder Name Matching** - Parser must match folder names exactly (case-sensitive)
2. **Hierarchical Traversal** - Must traverse hierarchy in correct order
3. **Object Type Inference** - Folder name determines Smallworld object type
4. **Attribute Extraction** - Parse folder name to extract object attributes
5. **Placemark Processing** - Read placemarks within each folder for actual objects

### Folder Name to Object Type Mapping

```magik
# Pseudo-code mapping logic
_if folder_name.matches?("OLT")
_then object_type << :building
_elif folder_name.matches?("CABLE") _orif folder_name.matches?("DISTRIBUTION CABLE")
_then object_type << :sheath_with_loc
_elif folder_name.matches?("*POLE*")
_then object_type << :pole
_elif folder_name.matches?("FAT")
_then object_type << :sheath_splice  # FAT/ODP
_elif folder_name.matches?("FDT")
_then object_type << :mit_terminal_enclosure
_elif folder_name.matches?("JOINT CLOSURE")
_then object_type << :sheath_splice
_elif folder_name.matches?("SLACK HANGER")
_then object_type << :mit_figure_eight
_elif folder_name.matches?("SLING WIRE")
_then object_type << :mit_sling_wire
_elif folder_name.matches?("HANDHOLE*")
_then object_type << :uub
_elif folder_name.matches?("HP COVER") _orif folder_name.matches?("HP UNCOVER")
_then object_type << :ftth!demand_point
_elif folder_name.matches?("BOUNDARY*")
_then object_type << :boundary
_endif
```

### Attribute Extraction from Folder Names

#### Pole Attributes
```magik
# Example: "EXISTING POLE EMR 7-4"
construction_status << _if folder_name.includes?("EXISTING") _then "In Service" _else "Proposed" _endif
ownership << _if folder_name.includes?("PARTNER") _then "Partner" _else "EMR" _endif
height << extract_number_before_hyphen(folder_name)  # "7"
pole_class << extract_number_after_hyphen(folder_name)  # "4"
material_type << "Concrete"  # Default or from placemark
```

#### Handhole Attributes
```magik
# Example: "HANDHOLE 80X80X130"
type << "Handhole"
dimensions << extract_dimensions(folder_name)  # "80x80x130"
```

#### Network Level
```magik
# Folder parent determines network level
_if parent_folder = "FEEDER CODE.KMZ"
_then network_type << "Feeder"
_elif parent_folder = "SUBFEEDER CODE.KMZ"
_then network_type << "Sub Feeder"
_elif parent_folder.includes?("LINE")
_then network_type << "Cluster"
_endif
```

---

## Validation Rules

### Structural Validation

1. **Required Folders** - All standard folders must be present
2. **Folder Order** - Folders should appear in documented order (non-critical)
3. **Naming Convention** - Folder names must match exactly
4. **Hierarchy Depth** - Maximum depth validated per network level

### Content Validation

1. **Placemark Geometry** - Points for infrastructure, Lines for cables
2. **Coordinate System** - Must be WGS84 (lon/lat)
3. **Required Attributes** - Name at minimum for all placemarks
4. **Network Consistency** - Cables must connect existing infrastructure

### Logical Validation

1. **Coverage Consistency** - HP COVER zones don't overlap HP UNCOVER
2. **Line Assignment** - Each FAT serves only one LINE
3. **Boundary Containment** - Objects within BOUNDARY FAT must be inside polygon
4. **Network Topology** - Cables form connected path from OLT to customer

---

## Migration Mapping

### Aerial Route Migration

**Source:** `CABLE` or `DISTRIBUTION CABLE` folders
**Target:** `aerial_route` table in Smallworld
**Process:**
1. Create aerial route with designator from cable name
2. Parse cable LineString geometry
3. Create route sectors from line segments
4. Attach cable (sheath_with_loc) to route

### Pole Migration

**Source:** `*POLE*` folders
**Target:** `pole` table in Smallworld
**Process:**
1. Parse folder name for attributes (status, owner, height, class)
2. Extract location from placemark coordinates
3. Set construction_status from STATUS (EXISTING/NEW)
4. Set material_type, usage from parsed attributes or defaults
5. Set telco_pole_tag from placemark name

### Equipment Migration

**Source:** `FAT`, `FDT`, `JOINT CLOSURE` folders
**Target:** `sheath_splice` or `mit_terminal_enclosure` tables
**Process:**
1. Determine object type from folder name
2. Extract location from placemark coordinates
3. Set network_type from parent folder hierarchy
4. Set specification from placemark name or description
5. Link to parent structure (pole/handhole) if within proximity

### Demand Point Migration

**Source:** `HP COVER` and `HP UNCOVER` folders
**Target:** `ftth!demand_point` table
**Process:**
1. Extract location from placemark coordinates
2. Determine coverage status from parent folder
3. Extract zone from folder path (e.g., HP COVER → A01)
4. Set attributes from placemark description
5. Link to serving FAT (nearest or from description)

---

## Version History

### rev28102025 (Current)
- Initial documented structure
- 3 network levels: Feeder, Subfeeder, Cluster
- 4 distribution lines per cluster: A, B, C, D
- 5 coverage zones per line
- Standardized pole classification system

---

## Key Takeaways

1. **Strict Hierarchy** - 3-level structure (Feeder → Subfeeder → Cluster)
2. **Folder Name = Metadata** - Object attributes encoded in folder names
3. **Status Separation** - EXISTING vs NEW infrastructure in separate folders
4. **Pole Classification** - Height and class encoded in folder name
5. **Line-Based Distribution** - Cluster divided into 4 lines (A/B/C/D)
6. **Zone-Based Coverage** - Each line has 5 coverage zones
7. **Equipment Hierarchy** - OLT → FDT → FAT → Customer
8. **Boundary Definition** - Cluster and FAT boundaries explicitly defined
9. **Coverage Tracking** - HP COVER (serviceable) vs HP UNCOVER (not yet serviceable)
10. **Comprehensive Object Types** - Infrastructure, network, equipment, logical boundaries

---

## Usage in ASTRI Integration System

### KML Import Process

1. **Load KML File** - Parse KML structure
2. **Validate Structure** - Check for FORMAT KMZ AERIAL hierarchy
3. **Traverse Hierarchy** - Walk folder tree in order
4. **Extract Objects** - Read placemarks from appropriate folders
5. **Map to Smallworld** - Convert to Smallworld object types
6. **Create Objects** - Insert into database with relationships
7. **Validate Topology** - Ensure network connectivity

### File Naming Convention

Expected KMZ file names:
- `[PROJECT_NAME] FEEDER CODE.KMZ`
- `[PROJECT_NAME] SUBFEEDER CODE.KMZ`
- `[PROJECT_NAME] CLUSTER CODE.KMZ`

### Reference Implementation

The KML parser in `rwwi_astri_integration_java` module uses this hierarchy structure to:
- Identify object types from folder context
- Extract attributes from folder names
- Determine network relationships from hierarchy
- Validate completeness of design data

---

# PART 2: KML VISUAL STYLING REFERENCE

## Overview

This section documents the complete visual styling system used in KMZ AERIAL format files, based on analysis of `sample.kml` (5MB, 146,810 lines, 1,000+ network elements). The styling system provides visual clarity through distinct icons, colors, and interactive highlighting for all network object types.

---

## Visual Style Components

### Icon Library

The KML uses these Google Maps icon shapes:

| Icon Shape | URL | Primary Use |
|------------|-----|-------------|
| **Placemark Circle** | `http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png` | Poles, Demand Points |
| **Target** | `http://maps.google.com/mapfiles/kml/shapes/target.png` | Coils/Slack Hangers |
| **Ranger Station** | `https://maps.google.com/mapfiles/kml/shapes/ranger_station.png` | OLT Buildings |
| **Yellow Pushpin** | `http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png` | Cables |
| **Triangle** | `http://maps.google.com/mapfiles/kml/shapes/triangle.png` | FDT, Network Segments |
| **Cross-hairs** | `http://maps.google.com/mapfiles/kml/shapes/cross-hairs.png` | Joint Closures |
| **Arrow** | `http://maps.google.com/mapfiles/kml/shapes/arrow.png` | Cable Risers |
| **Paddle Letters** | `http://maps.google.com/mapfiles/kml/paddle/[A-Z].png` | Site Identifiers |

### Color Palette

Colors in KML use AABBGGRR format (Alpha, Blue, Green, Red):

| Color Code | Hex RGB | Color Name | Usage |
|------------|---------|------------|-------|
| `ff0000aa` | #AA0000 | Dark Red | Coils, Special Markers |
| `ff7f0000` | #00007F | Dark Blue | NEW POLE 9M |
| `ff00ff00` | #00FF00 | Green | Cables, Standard Poles |
| `ff00ffff` | #00FFFF | Cyan | Cable Highlights |
| `ff00b371` | #00B371 | Teal Green | Service Area Lines |
| `ff0000ff` | #FF0000 | Red | OLT Connections |
| `ffff00aa` | #AA00FF | Magenta | Special Features |
| `8000b371` | #00B371 (50% alpha) | Semi-transparent Teal | Polygon Fills |

---

## Object Type Styling Details

### 1. OLT (Optical Line Terminal)

**Visual Appearance:**
```
Icon: Ranger Station (building)
Scale: 1.2
Line Color: ff0000ff (Red)
Line Width: 3 pixels
```

**Style Definition:**
```xml
<Style id="icon-ranger_station.pngline-w3">
  <IconStyle>
    <scale>1.2</scale>
    <Icon>
      <href>https://maps.google.com/mapfiles/kml/shapes/ranger_station.png</href>
    </Icon>
  </IconStyle>
  <LineStyle>
    <color>ff0000ff</color>
    <width>3</width>
  </LineStyle>
</Style>
```

**StyleMap ID:** `icon-ranger_station.pngline-w31`

---

### 2. Poles (NEW POLE, EXISTING POLE)

**Visual Appearance:**
```
Icon: Placemark Circle
Scale: 0.9
Colors:
  - NEW POLE 9M: ff7f0000 (Dark Blue)
  - Standard Poles: ff00ff00 (Green)
Highlight Scale: 0.9
Label Scale: 0.9
```

**Common StyleMap IDs:**
- `pointStyleMap01` through `pointStyleMap91021` (many variants)
- `msn_placemark_circle111`
- `icon-placemark_circle.png-7F0000` (NEW POLE 9M specific)

**Example Style:**
```xml
<Style id="normPointStyle0">
  <IconStyle>
    <color>ff7f0000</color>
    <scale>0.9</scale>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href>
    </Icon>
  </IconStyle>
  <LabelStyle>
    <scale>0.9</scale>
  </LabelStyle>
</Style>
```

**Pole Type Color Coding:**
- **NEW POLE 9M 4"** - Dark Blue circle
- **NEW POLE 7M 4"** - Green circle
- **EXISTING POLE EMR** - Green circle
- **EXISTING POLE PARTNER** - Green circle
- **EXT POLE** (Extension) - Standard green circle

---

### 3. CABLE / KABEL

**Visual Appearance:**
```
Icon: Yellow Pushpin
Scale: 1.1 (normal) / 1.3 (highlight)
Line Color: ff00ffff (Cyan) or ff00ff00 (Green)
Line Width: 3 pixels
HotSpot: x=20, y=2 pixels
```

**Common StyleMap IDs:**
- `msn_ylw-pushpin`
- `m_ylw-pushpin0` through `m_ylw-pushpin5`
- `icon-ylwline-w331001`

**Style Definition:**
```xml
<Style id="sn_ylw-pushpin">
  <IconStyle>
    <scale>1.1</scale>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>
    </Icon>
    <hotSpot x="20" y="2" xunits="pixels" yunits="pixels"/>
  </IconStyle>
  <LineStyle>
    <color>ff00ffff</color>
    <width>3</width>
  </LineStyle>
</Style>

<Style id="sh_ylw-pushpin"> <!-- Highlight -->
  <IconStyle>
    <scale>1.3</scale>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>
    </Icon>
    <hotSpot x="20" y="2" xunits="pixels" yunits="pixels"/>
  </IconStyle>
  <LineStyle>
    <color>ff00ffff</color>
    <width>3</width>
  </LineStyle>
</Style>
```

---

### 4. COIL (Slack Hanger / Figure Eight)

**Visual Appearance:**
```
Icon: Target (crosshair)
Scale: 0.9
Icon Color: ff0000aa (Dark Red)
```

**StyleMap ID:** `icon-target.png-0000AA600528` (and variants)

**Style Definition:**
```xml
<Style id="icon-target.png-0000AA6108021">
  <IconStyle>
    <color>ff0000aa</color>
    <scale>0.9</scale>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/shapes/target.png</href>
    </Icon>
  </IconStyle>
</Style>
```

---

### 5. FDT (Fiber Distribution Terminal)

**Visual Appearance:**
```
Icon: Triangle
Scale: 1.1
Color: Variable (often red/cyan)
```

**StyleMap ID:** `msn_triangle`

**Style Definition:**
```xml
<Style id="sn_triangle">
  <IconStyle>
    <scale>1.1</scale>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/shapes/triangle.png</href>
    </Icon>
  </IconStyle>
</Style>
```

---

### 6. CLOSURE (Joint Closure)

**Visual Appearance:**
```
Icon: Cross-hairs
Scale: 1.0
Color: Variable
```

**StyleMap ID:** `msn_cross-hairs`

**Style Definition:**
```xml
<Style id="sn_cross-hairs">
  <IconStyle>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/shapes/cross-hairs.png</href>
    </Icon>
  </IconStyle>
</Style>
```

---

### 7. HP (Homepass / Demand Points)

**Visual Appearance:**
```
Icon: Placemark Circle
Scale: 0.9
Normal Color: ff00ff00 (Green)
Highlight Color: ff7f0000 (Dark Blue)
```

**Most Common StyleMap ID:** `pointStyleMap145` (160 instances)

**Style Definition with BalloonStyle:**
```xml
<Style id="normPointStyle94000">
  <IconStyle>
    <color>ff00ff00</color>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href>
    </Icon>
  </IconStyle>
  <BalloonStyle>
    <text><![CDATA[<table border="0">
      <tr><td><b>LONGITUDE</b></td><td>$[ALL POLE HP/LONGITUDE]</td></tr>
      <tr><td><b>LATITUDE</b></td><td>$[ALL POLE HP/LATITUDE]</td></tr>
      <tr><td><b>LABEL</b></td><td>$[ALL POLE HP/LABEL]</td></tr>
      <tr><td><b>KML_FOLDER</b></td><td>$[ALL POLE HP/KML_FOLDER]</td></tr>
    </table>]]></text>
  </BalloonStyle>
</Style>
```

---

### 8. Network Segments / Service Areas

**Visual Appearance:**
```
Icon: Triangle
Icon Color: ff00ffff (Cyan)
Line Color: ff00b371 (Teal)
Line Width: 4 pixels
Polygon Fill: 8000b371 (Semi-transparent Teal, 50% alpha)
```

**StyleMap ID Pattern:** `icon-00B371line-w4-00B371poly-00B371*` (long numeric suffix)

**Style Definition:**
```xml
<Style id="icon-00B371line-w4-00B371poly-00B371110000121101030250">
  <IconStyle>
    <color>ff00ffff</color>
    <Icon>
      <href>http://maps.google.com/mapfiles/kml/shapes/triangle.png</href>
    </Icon>
  </IconStyle>
  <LineStyle>
    <color>ff00b371</color>
    <width>4</width>
  </LineStyle>
  <PolyStyle>
    <color>8000b371</color>
  </PolyStyle>
</Style>
```

---

### 9. Cable Riser

**Visual Appearance:**
```
Icon: Arrow
HotSpot: x=32, y=1 pixels
```

**StyleMap ID:** `icon-ylw22181`

---

## Styling Patterns and Conventions

### StyleMap Pattern

All interactive elements use StyleMap with two states for user interaction:

```xml
<StyleMap id="identifier">
  <Pair>
    <key>normal</key>
    <styleUrl>#normal_style_id</styleUrl>
  </Pair>
  <Pair>
    <key>highlight</key>
    <styleUrl>#highlight_style_id</styleUrl>
  </Pair>
</StyleMap>
```

**States:**
- **Normal:** Default display when not selected
- **Highlight:** Display when user clicks/hovers (typically larger scale, brighter color)

### Style ID Naming Conventions

| Pattern | Example | Meaning |
|---------|---------|---------|
| `icon-{shape}.png{numbers}` | `icon-target.png-0000AA6` | Basic icon style |
| `icon-{shape}line-w{width}` | `icon-ylwline-w3` | Icon with line styling |
| `icon-{color}line-w{width}-{color}poly-{color}{id}` | `icon-00B371line-w4-00B371poly-00B371110...` | Complex multi-element (icon + line + polygon) |
| `{state}PointStyle{numbers}` | `normPointStyle0`, `hlightPointStyle` | Point marker state |
| `pointStyleMap{numbers}` | `pointStyleMap145` | Point style mapping |
| `msn_{shape}{numbers}` | `msn_placemark_circle531` | Managed style normal |
| `sn_{shape}` | `sn_ylw-pushpin` | Style normal |
| `sh_{shape}` | `sh_ylw-pushpin` | Style highlight |
| `m_{shape}{numbers}` | `m_ylw-pushpin5` | Mapped style variant |

### Scale Hierarchy

| Scale Value | Usage |
|-------------|-------|
| 0.8 | Small/secondary features |
| 0.9 | Standard poles, coils, HP points |
| 1.0 | Default (not explicitly set) |
| 1.1 | Normal state for cables, icons |
| 1.2 | OLT (prominent infrastructure) |
| 1.3 | Highlight state for cables |

### Line Width Standards

| Width | Usage |
|-------|-------|
| 3 pixels | Standard cables, connections |
| 4 pixels | Network segment boundaries |

---

## Complete Style Reference Matrix

| Object Type | Folder Name | Icon | Icon Color | Scale | Line Color | Line Width | StyleMap ID |
|-------------|-------------|------|------------|-------|------------|------------|-------------|
| **OLT** | OLT | Ranger Station | Default | 1.2 | ff0000ff (Red) | 3 | icon-ranger_station.pngline-w31 |
| **NEW POLE 9M** | NEW POLE 9m4" | Circle | ff7f0000 (Dark Blue) | 0.9 | - | - | icon-placemark_circle.png-7F0000 |
| **NEW POLE 7M** | NEW POLE 7m4" | Circle | ff00ff00 (Green) | 0.9 | - | - | pointStyleMap* |
| **EXISTING POLE EMR** | EXISTING POLE EMR 7-4 | Circle | ff00ff00 (Green) | 0.9 | - | - | pointStyleMap* |
| **EXISTING POLE PARTNER** | EXISTING POLE PARTNER 7-4 | Circle | ff00ff00 (Green) | 0.9 | - | - | pointStyleMap* |
| **EXT POLE** | EXT POLE | Circle | Variable | 0.9 | - | - | msn_placemark_circle2512 |
| **CABLE** | KABEL | Pushpin | Default Yellow | 1.1/1.3 | ff00ffff (Cyan) | 3 | msn_ylw-pushpin |
| **DISTRIBUTION CABLE** | DISTRIBUTION CABLE | Pushpin | Default Yellow | 1.1/1.3 | ff00ff00 (Green) | 3 | icon-ylwline-w331001 |
| **COIL** | COIL | Target | ff0000aa (Dark Red) | 0.9 | - | - | icon-target.png-0000AA600528 |
| **FDT** | FDT | Triangle | Variable | 1.1 | - | - | msn_triangle |
| **CLOSURE** | CLOSURE | Cross-hairs | Variable | 1.0 | - | - | msn_cross-hairs |
| **CABLE RISER** | (various) | Arrow | Default | 1.0 | - | - | icon-ylw22181 |
| **HP COVER** | HP COVER | Circle | ff00ff00 (Green) | 0.9 | - | - | pointStyleMap145 |
| **HP UNCOVER** | HP UNCOVER | Circle | ff00ff00 (Green) | 0.9 | - | - | pointStyleMap9101 |
| **BOUNDARY FAT** | BOUNDARY FAT | Triangle | ff00ffff (Cyan) | 1.0 | ff00b371 (Teal) | 4 | icon-00B371line-w4-* |
| **BOUNDARY CLUSTER** | BOUNDARY CLUSTER | Triangle | ff00ffff (Cyan) | 1.0 | ff00b371 (Teal) | 4 | icon-00B371line-w4-* |

---

## Color Coding Semantic Meaning

| Color | Hex | Semantic Purpose |
|-------|-----|------------------|
| **Red** | #FF0000 | Critical infrastructure (OLT) |
| **Dark Red** | #AA0000 | Service points (coils, storage) |
| **Dark Blue** | #00007F | New installations (NEW POLE 9M) |
| **Green** | #00FF00 | Active/operational (cables, poles) |
| **Cyan** | #00FFFF | Highlighted features, boundaries |
| **Teal** | #00B371 | Service area boundaries |
| **Magenta** | #AA00FF | Special features/annotations |

---

## Visual Examples

### Network Hierarchy Differentiation

**Main Feeder Level:**
- OLT: Large red ranger station (scale 1.2)
- Main cables: Yellow pushpin + cyan line (width 3)
- Primary poles: Dark blue/green circles (scale 0.9)

**Sub Feeder Level:**
- Distribution cables: Yellow pushpin + green line (width 3)
- Secondary poles: Green circles (scale 0.9)
- FDT: Triangle markers

**Cluster Level:**
- Drop cables: Thinner visual emphasis
- FAT: Triangle markers
- HP: Small green circles (scale 0.9)
- Coverage zones: Semi-transparent teal polygons

---

## BalloonStyle Information Popups

Poles and HP points include interactive popups showing detailed attributes:

```xml
<BalloonStyle>
  <text><![CDATA[<table border="0">
    <tr><td><b>LONGITUDE</b></td><td>$[schema/LONGITUDE]</td></tr>
    <tr><td><b>LATITUDE</b></td><td>$[schema/LATITUDE]</td></tr>
    <tr><td><b>LABEL</b></td><td>$[schema/LABEL]</td></tr>
    <tr><td><b>KML_FOLDER</b></td><td>$[schema/KML_FOLDER]</td></tr>
  </table>]]></text>
</BalloonStyle>
```

**Purpose:** Provides detailed object information when user clicks on icon in Google Earth.

---

## Usage Statistics (Object Count by Style)

| Style Reference | Count | Object Type |
|----------------|-------|-------------|
| `mm_marker_color_30` | 522 | Cable markers |
| `pointStyleMap01` | 472 | Poles (general) |
| `msn_placemark_circle111` | 336 | Managed poles |
| `pointStyleMap145` | 160 | **HP (Homepass)** |
| `msn_ylw-pushpin` | ~200 | Cables |
| `icon-target.png-*` | ~23 | Coils |
| `icon-ranger_station.pngline-w31` | 1 | OLT |

**Total Network Elements:** 1,000+ objects across 300+ unique style definitions

---

## Implementation Notes for KML Generation

### 1. Style Definition Location

All styles should be defined in the `<Document>` section before any placemarks:

```xml
<Document>
  <name>Project Name</name>
  <!-- All <Style> and <StyleMap> definitions here -->

  <Folder>
    <name>OLT</name>
    <!-- Placemarks reference styles via styleUrl -->
  </Folder>
</Document>
```

### 2. Style Reference in Placemarks

Placemarks reference styles using `<styleUrl>`:

```xml
<Placemark>
  <name>OLT Pangkalan Balai</name>
  <styleUrl>#icon-ranger_station.pngline-w31</styleUrl>
  <Point>
    <coordinates>104.123,−2.456,0</coordinates>
  </Point>
</Placemark>
```

### 3. HotSpot for Icon Alignment

HotSpot defines which pixel of the icon corresponds to the geographic coordinate:

```xml
<hotSpot x="20" y="2" xunits="pixels" yunits="pixels"/>
```

Common values:
- **Pushpin:** x=20, y=2 (stem of pin)
- **Circle:** x=16, y=16 (center, for 32x32 icon)
- **Arrow:** x=32, y=1 (arrow tip)

### 4. Color Format

KML colors use AABBGGRR hex format (opposite of standard HTML #RRGGBB):

| HTML Color | KML Color | Conversion |
|------------|-----------|------------|
| `#FF0000` (Red) | `ff0000ff` | Swap RR↔BB, add FF alpha |
| `#00FF00` (Green) | `ff00ff00` | Already symmetric |
| `#0000FF` (Blue) | `ffff0000` | Swap RR↔BB, add FF alpha |
| `#00FFFF` (Cyan) | `ffffff00` | Swap RR↔BB, add FF alpha |

### 5. Polygon Transparency

Use alpha channel (first two hex digits) for polygon transparency:

- `ff` = 100% opaque
- `80` = 50% transparent
- `40` = 25% transparent
- `00` = fully transparent

Example: `8000b371` = 50% transparent teal green

---

## Style Completeness Checklist

| Object Type | Style Defined | Icon | Color | Complete |
|-------------|---------------|------|-------|----------|
| OLT | ✓ | Ranger Station | Red | ✓ |
| Poles (all types) | ✓ | Circle | Blue/Green | ✓ |
| Cables | ✓ | Pushpin | Cyan/Green | ✓ |
| Coils | ✓ | Target | Dark Red | ✓ |
| FDT | ✓ | Triangle | Variable | ✓ |
| Closures | ✓ | Cross-hairs | Variable | ✓ |
| HP Points | ✓ | Circle | Green | ✓ |
| Network Segments | ✓ | Triangle + Polygon | Teal | ✓ |
| Cable Risers | ✓ | Arrow | Default | ✓ |
| Handhole | ? | ? | ? | ⚠️ |

**Status:** 9/10 object types have complete styling (90% coverage)

---

## Visual Design Principles

### 1. Consistency
- All poles use circle icons
- All cables use pushpin icons
- All equipment uses geometric shapes (triangle, cross-hairs)

### 2. Hierarchy
- Larger scale (1.2) for critical infrastructure (OLT)
- Standard scale (0.9-1.1) for network elements
- Smaller scale (0.8) for annotations

### 3. Status Differentiation
- Dark Blue = New installations
- Green = Existing/operational
- Dark Red = Service/storage points

### 4. Interactive Feedback
- All objects have normal + highlight states
- Highlight typically 1.2× larger scale
- Provides clear visual feedback on selection

### 5. Geographic Accuracy
- HotSpot ensures icon anchors to exact coordinate
- Important for alignment with aerial imagery
- Critical for accurate distance measurements

---

## Recommendations for KML Export Implementation

### 1. Use StyleMap for All Interactive Elements
Always define paired normal/highlight styles:
```xml
<StyleMap id="my_object_style">
  <Pair><key>normal</key><styleUrl>#my_object_normal</styleUrl></Pair>
  <Pair><key>highlight</key><styleUrl>#my_object_highlight</styleUrl></Pair>
</StyleMap>
```

### 2. Define Styles Once, Reference Many Times
Place all style definitions in document header, reference via `<styleUrl>` in placemarks.

### 3. Use Semantic Style IDs
Prefer descriptive names over generated hashes:
- Good: `pole_new_9m`, `cable_feeder`, `hp_covered`
- Poor: `__managed_style_0B3B04E5DA398470BA35`

### 4. Include BalloonStyle for Data Display
Add BalloonStyle to show attributes in popup:
```xml
<BalloonStyle>
  <text><![CDATA[
    <b>Name:</b> $[name]<br/>
    <b>Type:</b> $[type]<br/>
    <b>Status:</b> $[status]
  ]]></text>
</BalloonStyle>
```

### 5. Maintain Color Consistency
Use the established color palette for visual consistency across all KML exports.

### 6. Test in Google Earth
Always verify styling appears correctly in Google Earth before deployment.

---

## Summary

The KMZ AERIAL format uses a comprehensive, well-structured visual styling system that:

1. **Provides Visual Clarity** - Distinct icons and colors for each object type
2. **Supports Interaction** - Hover/click highlighting via StyleMaps
3. **Scales Appropriately** - Icon sizes emphasize hierarchy
4. **Uses Color Semantically** - Colors convey status and function
5. **Delivers Information** - BalloonStyle popups provide detailed data
6. **Maintains Consistency** - Follows Google Earth style conventions

The style system is production-ready with 300+ style definitions covering 90%+ of required object types, supporting complex multi-level FTTH network visualization.

---

**Document Version:** 1.0 (Updated with Style Analysis)
**Last Updated:** 2025-11-26
**Author:** Generated from astri_template.kml and sample.kml analysis
**Source Files:**
- Hierarchy: `astri_template.kml` (rev28102025)
- Styles: `sample.kml` (5MB, 146,810 lines)
**Analysis Coverage:** 1,000+ network elements, 300+ style definitions
