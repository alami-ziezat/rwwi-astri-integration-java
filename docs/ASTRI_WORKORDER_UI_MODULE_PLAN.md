# ASTRI Work Order UI Module - Implementation Plan

**Created:** 2025-10-27
**Status:** DRAFT - AWAITING APPROVAL
**Purpose:** Display and manage ASTRI Work Orders from API and Database
**Location:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder`

---

## TABLE OF CONTENTS

1. [Executive Summary](#executive-summary)
2. [Requirements Analysis](#requirements-analysis)
3. [Architecture Design](#architecture-design)
4. [Module Structure](#module-structure)
5. [UI Design](#ui-design)
6. [Data Flow](#data-flow)
7. [Implementation Details](#implementation-details)
8. [Integration Points](#integration-points)
9. [Testing Strategy](#testing-strategy)
10. [Deployment Plan](#deployment-plan)

---

## EXECUTIVE SUMMARY

### Objective

Create a Smallworld GIS GUI module that displays ASTRI Work Order data from two sources:
1. **ASTRI API** - Real-time work orders via REST API
2. **Local Database** - Work orders marked as "under construction"

The module will provide filtering, refresh capabilities, and a rich table-based interface following Smallworld best practices.

### Key Features

✅ **Dual Data Source Support**
- Toggle between API and Database sources
- Unified display interface

✅ **Rich Filtering**
- Category, status, vendor, topology, cluster code filters
- Pagination controls (limit/offset)
- Dynamic filter application

✅ **Interactive UI**
- Table-based work order list with sortable columns
- Toolbar with source selector and refresh button
- Status indicators and row selection

✅ **Integration**
- Uses existing ASTRI Java interop layer
- Follows rwi_library_name architectural patterns
- Compatible with Smallworld framework

---

## REQUIREMENTS ANALYSIS

### Functional Requirements

| ID | Requirement | Priority | Source |
|----|-------------|----------|--------|
| FR-01 | Display work orders in table format | MUST | User Request |
| FR-02 | Toggle between API and Database sources | MUST | Requirement #3 |
| FR-03 | Apply filters matching API query parameters | MUST | Requirement #4 |
| FR-04 | Refresh data on button click | MUST | Requirement #5 |
| FR-05 | Mark database records as "under construction" | MUST | Requirement #3 |
| FR-06 | Show work order details (number, cluster, status, etc.) | MUST | Derived |
| FR-07 | Support pagination (limit/offset) | SHOULD | API Design |
| FR-08 | Enable column sorting | SHOULD | UX Best Practice |
| FR-09 | Display record count | SHOULD | UX Best Practice |
| FR-10 | Handle API errors gracefully | MUST | Robustness |

### Non-Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-01 | Response time < 5 seconds for API calls | SHOULD |
| NFR-02 | Module loads without errors in Smallworld | MUST |
| NFR-03 | Compatible with existing ASTRI integration | MUST |
| NFR-04 | Follow Smallworld naming conventions | MUST |
| NFR-05 | Localized UI strings (English/Bahasa) | SHOULD |
| NFR-06 | Minimal memory footprint | SHOULD |

### Technical Constraints

1. **Existing Infrastructure:**
   - Must use `rwwi_astri_integration` Java module
   - Must call `astri_get_work_orders()` global procedure
   - Must integrate with Smallworld plugin framework

2. **Data Constraints:**
   - API returns JSON strings (needs parsing)
   - Database schema must support "construction status" field
   - Work order UUID is unique identifier

3. **UI Constraints:**
   - Must fit standard Smallworld application window
   - Must use standard Smallworld UI components
   - Must follow PNI Custom UI guidelines

---

## ARCHITECTURE DESIGN

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  SMALLWORLD APPLICATION FRAMEWORK                                │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Plugin System (Databus, Actions, Menus)                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  RWWI ASTRI WORKORDER UI MODULE                                  │
│                                                                   │
│  ┌───────────────────┐      ┌───────────────────┐               │
│  │  Plugin Layer     │◄────►│  Dialog/UI Layer  │               │
│  │                   │      │                   │               │
│  │  - Actions        │      │  - Table          │               │
│  │  - Toolbar Defs   │      │  - Filters        │               │
│  │  - Databus        │      │  - Buttons        │               │
│  └────────┬──────────┘      └─────────┬─────────┘               │
│           │                           │                         │
│           └────────►┌─────────────────▼─────────┐               │
│                     │  Engine/Data Layer        │               │
│                     │                           │               │
│                     │  - API Client             │               │
│                     │  - DB Query Handler       │               │
│                     │  - JSON Parser            │               │
│                     │  - Data Transformation    │               │
│                     └──────────┬────────────────┘               │
└────────────────────────────────┼─────────────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
                 ▼                               ▼
   ┌──────────────────────────┐   ┌──────────────────────────┐
   │  ASTRI API Integration   │   │  Smallworld Database     │
   │                          │   │                          │
   │  - Java @MagikProc       │   │  - Template View         │
   │  - astri_get_work_orders │   │  - Work Order Collection │
   │  - HTTP Client           │   │  - Construction Status   │
   └──────────────────────────┘   └──────────────────────────┘
```

### Component Responsibilities

#### 1. Plugin Layer (`rwwi_astri_workorder_plugin.magik`)

**Responsibilities:**
- Define toolbar actions (Activate UI, Refresh)
- Subscribe to databus for design changes
- Manage dialog lifecycle (create, cache, activate)
- Synchronize state with dialog

**Key Methods:**
- `init_actions()` - Define toolbar actions
- `activate_workorder_ui()` - Show dialog
- `sw_databus_data_available()` - React to design changes

#### 2. Dialog/UI Layer (`rwwi_astri_workorder_dialog.magik`)

**Responsibilities:**
- Build and manage UI components
- Handle user interactions (button clicks, selections)
- Display work order data in table
- Manage filters and pagination controls

**Key Methods:**
- `activate_in(frame)` - Build UI in frame
- `build_toolbar()` - Create toolbar with source selector and filters
- `build_table()` - Create work order table
- `workorder_list_data()` - Populate table data
- `refresh_data()` - Trigger data reload
- `source_selection()` - Handle API/DB toggle
- `apply_filters()` - Apply filter changes

#### 3. Engine/Data Layer (`rwwi_astri_workorder_engine.magik`)

**Responsibilities:**
- Call ASTRI API via global procedures
- Query database for construction work orders
- Parse JSON responses
- Transform data to property lists
- Handle errors and timeouts

**Key Methods:**
- `get_workorders_from_api()` - Call API with filters
- `get_workorders_from_db()` - Query database
- `parse_api_response()` - Parse JSON to property list
- `transform_to_table_data()` - Convert to table format
- `mark_as_construction()` - Update DB status flag

---

## MODULE STRUCTURE

### Directory Layout

```
C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\
├── module.def                                    # Module metadata
├── load_list.txt                                 # Module load order
├── source\
│   ├── load_list.txt                             # Source file load order
│   ├── conditions.magik                          # Custom error conditions
│   ├── rwwi_astri_workorder_engine.magik        # Data access layer
│   ├── rwwi_astri_workorder_dialog.magik        # UI implementation
│   └── rwwi_astri_workorder_plugin.magik        # Plugin entry point
└── resources\
    └── en_gb\
        └── messages\
            ├── rwwi_astri_workorder_plugin.msg      # Plugin strings
            └── rwwi_astri_workorder_dialog.msg      # Dialog strings
```

### File Descriptions

| File | Purpose | Lines (Est.) |
|------|---------|--------------|
| `module.def` | Module registration, dependencies | ~20 |
| `load_list.txt` | Module-level load order | ~5 |
| `source/load_list.txt` | Source file sequence | ~5 |
| `conditions.magik` | Custom exception types | ~50 |
| `rwwi_astri_workorder_engine.magik` | Data layer | ~300 |
| `rwwi_astri_workorder_dialog.magik` | UI layer | ~500 |
| `rwwi_astri_workorder_plugin.magik` | Plugin layer | ~150 |
| Plugin message file | UI strings | ~20 |
| Dialog message file | UI strings | ~30 |

**Total Estimated Lines:** ~1,080 lines of Magik code

---

## UI DESIGN

### Main Window Layout

```
┌────────────────────────────────────────────────────────────────────┐
│  ASTRI Work Order Manager                                    [X]   │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────── TOOLBAR ──────────────────────┐│
│  │                                                                ││
│  │  [🔄 Refresh]  Source: [▼ ASTRI API     ]  [🔍 Apply Filters]││
│  │                                                                ││
│  │  Filters:  Category: [▼ All          ]  Status: [▼ All      ] ││
│  │            Vendor:   [▼ All          ]  Topology: [▼ All    ] ││
│  │            Cluster:  [______________ ]                        ││
│  │                                                                ││
│  │  Pagination:  Limit: [▼ 50  ]  Offset: [___0___] [◄] [►]    ││
│  │                                                                ││
│  │  📊 Total Records: 127                                        ││
│  │                                                                ││
│  └────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌──────────────────────────────────── WORK ORDER TABLE ────────────────────────────────────┐│
│  │ No │ WO Number              │ Code       │ Cluster Name        │ Category │ Status  │⬆│││
│  ├────┼────────────────────────┼────────────┼─────────────────────┼──────────┼─────────┤││
│  │ 1  │ WO/ALL/2025/DOCU/16/.. │ MNA000033  │ PADANG SIALANG RT.. │ Cluster..│ In Prog.│││
│  │ 2  │ WO/ALL/2025/DOCU/16/.. │ MNA000034  │ AREA JAYA RT 01..   │ Cluster..│ Draft   │││
│  │ 3  │ WO/ALL/2025/DOCU/15/.. │ BDG000001  │ BANDUNG CENTRAL ..  │ Install..│ Complet.│││
│  │... │ ...                    │ ...        │ ...                 │ ...      │ ...     │││
│  │ 50 │ WO/ALL/2025/DOCU/10/.. │ SBY000010  │ SURABAYA TIMUR ..   │ Mainten..│ In Prog.│││
│  │                                                                                        ││
│  └────────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌─────────────────────── DETAIL PANEL ───────────────────────────┐│
│  │                                                                 ││
│  │  Selected Work Order: WO/ALL/2025/DOCU/16/54556                ││
│  │  UUID: d35ed679-0b5e-4c33-953c-2740b5cc7772                    ││
│  │  Cluster: MNA000033 - PADANG SIALANG RT 01, 02, 03...         ││
│  │  Assigned Vendor: Yangtze Optical Fible and Cable             ││
│  │  Target Topology: AE  |  Category: Cluster BOQ                 ││
│  │  Created: 2025-10-28 14:28:08                                  ││
│  │                                                                 ││
│  │  [📋 View Details] [📥 Download KMZ] [🏗️ Mark Construction]   ││
│  │                                                                 ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  [ Close ]                                                          │
└────────────────────────────────────────────────────────────────────┘
```

### UI Components Breakdown

#### 1. Toolbar Section

**Components:**
- **Refresh Button** - Reload data from current source
- **Source Selector** - Dropdown: ["ASTRI API", "Database (Construction)"]
- **Apply Filters Button** - Execute filtered query

**Filter Controls:**
- **Category Dropdown** - Values: ["All", "cluster_boq", "installation", "maintenance"]
- **Status Dropdown** - Values: ["All", "draft", "in_progress", "completed", "cancelled"]
- **Vendor Dropdown** - Values: ["All", <dynamic vendor list>]
- **Topology Dropdown** - Values: ["All", "AE", "UG", "OH"]
- **Cluster Text Field** - Free text search
- **Limit Dropdown** - Values: [10, 25, 50, 100, 200]
- **Offset Text Field** - Numeric input
- **Pagination Buttons** - Previous/Next page

**Status Display:**
- **Record Count Label** - "Total Records: N"

#### 2. Work Order Table

**Columns:**
| # | Column Name | Data Type | Width | Sortable | Filterable |
|---|-------------|-----------|-------|----------|------------|
| 1 | No | Integer | 50px | No | No |
| 2 | WO Number | String | 150px | Yes | Yes |
| 3 | Cluster Code | String | 120px | Yes | Yes |
| 4 | Cluster Name | String | 250px | Yes | Yes |
| 5 | Category | String | 120px | Yes | Yes |
| 6 | Status | String | 120px | Yes | Yes |
| 7 | Topology | String | 80px | Yes | Yes |
| 8 | Vendor | String | 200px | Yes | Yes |
| 9 | Created Date | Date | 120px | Yes | No |

**Table Configuration:**
- Row height: 20px
- Selection type: Single row
- Row stripes: Enabled
- Column lines: Enabled
- Enable sorting: Yes
- Enable column filter: Yes (selected columns)

#### 3. Detail Panel

**Components:**
- **Selected WO Label** - Show WO number and UUID
- **Detail Fields** - Show vendor, topology, dates
- **Action Buttons:**
  - View Details - Open full work order info
  - Download KMZ - Download cluster document
  - Mark Construction - Update DB status

---

## DATA FLOW

### Use Case 1: Load Work Orders from API

```
┌──────┐                ┌────────┐              ┌────────┐            ┌──────────┐
│ User │                │ Dialog │              │ Engine │            │ ASTRI API│
└──┬───┘                └───┬────┘              └───┬────┘            └────┬─────┘
   │                        │                       │                      │
   │ 1. Click Refresh       │                       │                      │
   ├───────────────────────►│                       │                      │
   │                        │ 2. get_workorders_    │                      │
   │                        │    from_api(filters)  │                      │
   │                        ├──────────────────────►│                      │
   │                        │                       │ 3. astri_get_work_   │
   │                        │                       │    orders(limit,     │
   │                        │                       │    offset, filters)  │
   │                        │                       ├─────────────────────►│
   │                        │                       │                      │
   │                        │                       │ 4. JSON Response     │
   │                        │                       │◄─────────────────────┤
   │                        │ 5. parse_api_         │                      │
   │                        │    response(json)     │                      │
   │                        │◄──────────────────────┤                      │
   │                        │ 6. Return property    │                      │
   │                        │    list rope          │                      │
   │ 7. Display in table    │                       │                      │
   │◄───────────────────────┤                       │                      │
```

### Use Case 2: Load Work Orders from Database

```
┌──────┐                ┌────────┐              ┌────────┐            ┌──────────┐
│ User │                │ Dialog │              │ Engine │            │ Database │
└──┬───┘                └───┬────┘              └───┬────┘            └────┬─────┘
   │                        │                       │                      │
   │ 1. Select "Database"   │                       │                      │
   │    from source dropdown│                       │                      │
   ├───────────────────────►│                       │                      │
   │                        │ 2. source_selection() │                      │
   │                        ├──────┐                │                      │
   │                        │      │                │                      │
   │                        │◄─────┘                │                      │
   │                        │ 3. get_workorders_    │                      │
   │                        │    from_db(filters)   │                      │
   │                        ├──────────────────────►│                      │
   │                        │                       │ 4. SQL Query         │
   │                        │                       │    (where            │
   │                        │                       │    construction_     │
   │                        │                       │    status = true)    │
   │                        │                       ├─────────────────────►│
   │                        │                       │                      │
   │                        │                       │ 5. Result Set        │
   │                        │                       │◄─────────────────────┤
   │                        │ 6. Return property    │                      │
   │                        │    list rope          │                      │
   │                        │◄──────────────────────┤                      │
   │ 7. Display in table    │                       │                      │
   │◄───────────────────────┤                       │                      │
```

### Use Case 3: Apply Filters

```
┌──────┐                ┌────────┐              ┌────────┐
│ User │                │ Dialog │              │ Engine │
└──┬───┘                └───┬────┘              └───┬────┘
   │                        │                       │
   │ 1. Change filter value │                       │
   ├───────────────────────►│                       │
   │                        │ 2. Store filter       │
   │                        │    state              │
   │                        ├──────┐                │
   │                        │      │                │
   │                        │◄─────┘                │
   │ 3. Click "Apply        │                       │
   │    Filters"            │                       │
   ├───────────────────────►│                       │
   │                        │ 4. build_filter_      │
   │                        │    params()           │
   │                        ├──────┐                │
   │                        │      │                │
   │                        │◄─────┘                │
   │                        │ 5. refresh_data()     │
   │                        ├──────────────────────►│
   │                        │ 6. Return filtered    │
   │                        │    data               │
   │                        │◄──────────────────────┤
   │ 7. Update table        │                       │
   │◄───────────────────────┤                       │
```

### Data Transformation Pipeline

```
API JSON Response
    │
    ▼
Parse JSON String
    │
    ▼
Extract "data" Array
    │
    ▼
For Each Work Order Object:
    {
      "uuid": "d35ed679-0b5e-4c33-953c-2740b5cc7772",
      "number": "WO/ALL/2025/DOCU/16/54556",
      "target_cluster_code": "MNA000033",
      "target_cluster_name": "PADANG SIALANG RT 01, 02, 03, 10, 11, DAN 12 BENGKULU SELATAN",
      "category_label": "Cluster BOQ",
      "latest_status_name": "in_progress",
      "target_cluster_topology": "AE",
      "assigned_vendor_label": "Yangtze Optical Fible and Cable",
      "created_at": "2025-10-28 14:28:08"
    }
    │
    ▼
Transform to Property List:
    property_list.new_with(
        :uuid, "d35ed679-0b5e-4c33-953c-2740b5cc7772",
        :wo_number, "WO/ALL/2025/DOCU/16/54556",
        :cluster_code, "MNA000033",
        :cluster_name, "PADANG SIALANG RT 01, 02, 03, 10, 11, DAN 12 BENGKULU SELATAN",
        :category, "Cluster BOQ",
        :status, "in_progress",
        :topology, "AE",
        :vendor, "Yangtze Optical Fible and Cable",
        :created_at, "2025-10-28 14:28:08")
    │
    ▼
Add to Rope
    │
    ▼
Return Rope of Property Lists
    │
    ▼
Dialog: Populate Table Cells
```

---

## IMPLEMENTATION DETAILS

### 1. Module Definition (`module.def`)

```
rwwi_astri_workorder 1

description
	ASTRI Work Order Manager
	Displays work orders from API and database with filtering capabilities.
end

requires
	base
	rwwi_astri_integration
end

language en_gb
```

**Key Points:**
- Module name: `rwwi_astri_workorder`
- Requires `rwwi_astri_integration` module (for Java interop)
- Version: 1

### 2. Load Order (`source/load_list.txt`)

```
conditions
rwwi_astri_workorder_engine
rwwi_astri_workorder_dialog
rwwi_astri_workorder_plugin
```

**Rationale:**
1. Conditions first (custom exceptions)
2. Engine second (data layer)
3. Dialog third (depends on engine)
4. Plugin last (depends on dialog)

### 3. Custom Conditions (`conditions.magik`)

```magik
#% text_encoding = iso8859_1

_package sw
$

## Custom conditions for ASTRI Work Order UI

condition.define_condition(
	:astri_workorder!api_call_failed,
	:mit!user_error,
	{:api_name, :error_message},
	write_string("API Call Failed", %newline,
		"API: ", %#1, %newline,
		"Error: ", %#2))
$

condition.define_condition(
	:astri_workorder!json_parse_error,
	:mit!user_error,
	{:json_string},
	write_string("Failed to parse JSON response", %newline,
		"JSON: ", %#1))
$

condition.define_condition(
	:astri_workorder!db_query_failed,
	:mit!user_error,
	{:query, :error_message},
	write_string("Database Query Failed", %newline,
		"Query: ", %#1, %newline,
		"Error: ", %#2))
$

condition.define_condition(
	:astri_workorder!no_data_source,
	:mit!user_error,
	{},
	write_string("No data source configured", %newline,
		"Please configure ASTRI API or database connection"))
$
```

### 4. Engine Layer (`rwwi_astri_workorder_engine.magik`)

```magik
#% text_encoding = iso8859_1

_package sw
$

## ASTRI Work Order Engine
## Data access layer for work order retrieval from API and database

def_slotted_exemplar(:rwwi_astri_workorder_engine,
{
	{:vda,         _unset, :writable},   # View/Design Admin (dataset)
	{:dialog,      _unset, :writable},   # Parent dialog reference
	{:current_source, :api, :writable},  # Current data source (:api or :db)
	{:cache,       _unset, :writable}    # Cached data
}, :engine_model)
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_engine.new()
	## Create new engine instance
	>> _clone.init()
_endmethod
$

_private _method rwwi_astri_workorder_engine.init()
	## Initialize engine

	.vda << gis_program_manager.cached_dataset(:design_admin)
	.cache << property_list.new()

	>> _super.init()
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_engine.get_workorders_from_api(
		limit, offset, _optional filters)
	## Get work orders from ASTRI API
	##
	## Parameters:
	##   limit (integer) - Maximum records to retrieve
	##   offset (integer) - Starting offset
	##   filters (property_list) - Optional filters:
	##     :category_name - Category filter (e.g., "cluster_boq")
	##     :latest_status_name - Status filter (e.g., "in_progress")
	##     :assigned_vendor_name - Vendor name filter
	##     :target_cluster_topology - Topology filter (AE/UG/OH)
	##     :target_cluster_code - Cluster code filter
	##
	## Returns:
	##   rope of property_lists with work order data
	##
	## Note: Filters use API field names (not label fields)

	_try _with cond
		# Call Java @MagikProc global procedure
		json_result << astri_get_work_orders(limit, offset, filters)

		_if json_result _is _unset _orif json_result = ""
		_then
			condition.raise(:astri_workorder!api_call_failed,
				:api_name, "astri_get_work_orders",
				:error_message, "Empty response from API")
			_return rope.new()
		_endif

		# Parse JSON response
		workorders << _self.parse_api_response(json_result)

		# Cache the result
		.cache[:last_api_result] << workorders
		.cache[:last_api_time] << date_time.now()

		>> workorders

	_when error
		write("ERROR in get_workorders_from_api:", cond.report_contents_string)
		condition.raise(:astri_workorder!api_call_failed,
			:api_name, "astri_get_work_orders",
			:error_message, cond.report_contents_string)
		>> rope.new()
	_endtry
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_engine.parse_api_response(json_string)
	## Parse JSON string response to rope of property_lists
	##
	## Expected JSON structure:
	## {
	##   "success": true,
	##   "count": 50,
	##   "count_all": 127,
	##   "data": [
	##     {
	##       "uuid": "d35ed679-0b5e-4c33-953c-2740b5cc7772",
	##       "number": "WO/ALL/2025/DOCU/16/54556",
	##       "target_cluster_code": "MNA000033",
	##       "target_cluster_name": "PADANG SIALANG RT 01, 02, 03...",
	##       "category_label": "Cluster BOQ",
	##       "latest_status_name": "in_progress",
	##       "target_cluster_topology": "AE",
	##       "assigned_vendor_label": "Yangtze Optical Fible and Cable",
	##       "created_at": "2025-10-28 14:28:08"
	##     }
	##   ]
	## }

	_local result_rope << rope.new()

	_try _with cond
		# Use simple_xml or json_parser for JSON parsing
		# For now, we'll use a simple regex-based approach
		# In production, use proper JSON parser

		# TODO: Implement proper JSON parsing using json_parser or simple_xml
		# This is a placeholder showing the data structure
		# In production, parse the JSON string to extract the "data" array
		# and iterate through each work order object

		# For demonstration, return sample data matching actual API structure
		_for i _over range(1, 10)
		_loop
			wo << property_list.new_with(
				:uuid, write_string("d35ed679-0b5e-4c33-953c-", i.write_string.pad_leading(%0, 12)),
				:wo_number, write_string("WO/ALL/2025/DOCU/16/", 54550 + i),
				:cluster_code, write_string("MNA", i.write_string.pad_leading(%0, 6)),
				:cluster_name, write_string("Sample Cluster Area RT ", i, " Region"),
				:category, "Cluster BOQ",
				:status, "in_progress",
				:topology, "AE",
				:vendor, "Yangtze Optical Fible and Cable",
				:created_at, "2025-10-28 14:28:08")

			result_rope.add(wo)
		_endloop

		>> result_rope

	_when error
		write("ERROR parsing JSON:", cond.report_contents_string)
		condition.raise(:astri_workorder!json_parse_error,
			:json_string, json_string.subseq(1, 100) + "...")
		>> rope.new()
	_endtry
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_engine.get_workorders_from_db(_optional filters)
	## Get work orders from database where construction_status = true
	##
	## Parameters:
	##   filters (property_list) - Optional filters
	##
	## Returns:
	##   rope of property_lists with work order data

	_local result_rope << rope.new()

	_try _with cond
		# Access work order collection in database
		# This assumes a work_order table exists with construction_status field

		_local wo_col << .vda.collections[:work_order]

		_if wo_col _is _unset
		_then
			write("WARNING: work_order collection not found in database")
			_return result_rope
		_endif

		# Predicate for construction status
		_local pred << predicate.eq(:construction_status, _true)

		# Apply additional filters if provided
		_if filters _isnt _unset
		_then
			_if filters[:category_name] _isnt _unset
			_then
				pred << pred _and predicate.eq(:category_name, filters[:category_name])
			_endif

			_if filters[:latest_status_name] _isnt _unset
			_then
				pred << pred _and predicate.eq(:status, filters[:latest_status_name])
			_endif
		_endif

		# Query database
		_for wo_rec _over wo_col.select(pred).fast_elements()
		_loop
			wo << property_list.new_with(
				:uuid, wo_rec.uuid,
				:wo_number, wo_rec.number,
				:cluster_code, wo_rec.target_cluster_code,
				:cluster_name, wo_rec.target_cluster_name,
				:category, wo_rec.category_label,
				:status, wo_rec.latest_status_name,
				:topology, wo_rec.target_cluster_topology,
				:vendor, wo_rec.assigned_vendor_label,
				:created_at, wo_rec.created_at,
				:construction_status, wo_rec.construction_status)

			result_rope.add(wo)
		_endloop

		>> result_rope

	_when error
		write("ERROR querying database:", cond.report_contents_string)
		condition.raise(:astri_workorder!db_query_failed,
			:query, "work_order collection query",
			:error_message, cond.report_contents_string)
		>> rope.new()
	_endtry
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_engine.mark_as_construction(wo_uuid)
	## Mark work order as under construction in database
	##
	## Parameters:
	##   wo_uuid (string) - Work order UUID
	##
	## Returns:
	##   boolean - success flag

	_try _with cond
		_local wo_col << .vda.collections[:work_order]

		_if wo_col _is _unset
		_then
			write("WARNING: work_order collection not found")
			_return _false
		_endif

		# Find work order by UUID
		_local pred << predicate.eq(:uuid, wo_uuid)
		_local wo_rec << wo_col.select(pred).an_element()

		_if wo_rec _is _unset
		_then
			write("WARNING: Work order not found:", wo_uuid)
			_return _false
		_endif

		# Update construction status
		wo_rec.construction_status << _true
		wo_rec.modified_date << date_time.now()

		write("Work order marked as construction:", wo_uuid)
		>> _true

	_when error
		write("ERROR marking as construction:", cond.report_contents_string)
		>> _false
	_endtry
_endmethod
$
```

### 5. Dialog Layer (Outline - Full implementation ~500 lines)

**UI Component Reference:** See `C:\Smallworld\core\sw_core\modules\sw_swift\magik_gui_components_examples` for comprehensive examples of Smallworld UI components.

```magik
#% text_encoding = iso8859_1

_package sw
$

def_slotted_exemplar(:rwwi_astri_workorder_dialog,
{
	{:engine,     _unset, :writable},       # Engine instance
	{:items,      property_list.new()},     # UI components cache
	{:plugin,     _unset, :writable},       # Parent plugin
	{:filters,    property_list.new()},     # Current filter values
	{:selected_wo, _unset, :writable}       # Currently selected work order
}, :model)
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.new(plugin)
	## Create new dialog
	>> _clone.init(plugin)
_endmethod
$

_private _method rwwi_astri_workorder_dialog.init(plugin)
	.plugin << plugin
	.engine << rwwi_astri_workorder_engine.new()
	.engine.dialog << _self

	# Initialize default filters
	.filters[:source] << :api
	.filters[:limit] << 50
	.filters[:offset] << 0

	>> _super.init()
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.activate_in(frame)
	## Build and activate UI in frame

	frame.title << "ASTRI Work Order Manager"

	# Build main container
	.items[:top] << top_c << sw_canvas_container.new(
		frame, 1, 1,
		:width, 900,
		:height, 600,
		:outer_spacing, 3,
		:row_spacing, 3,
		:identifier, :top_container,
		:model, _self)

	# Build toolbar with filters
	_self.build_toolbar(top_c)

	# Build work order table
	_self.build_table(top_c)

	# Build detail panel
	_self.build_detail_panel(top_c)

	# Initial data load
	_self.refresh_data()
_endmethod
$

_private _method rwwi_astri_workorder_dialog.build_toolbar(parent)
	## Build toolbar with source selector and filters

	toolbar_con << sw_toolbar_container.new(parent, _false,
		:background_type, :container,
		:show_separators?, _true)

	a_toolbar << toolbar_con.create_toolbar()

	# Refresh button
	.items[:refresh_btn] << sw_image_button_item.new(
		a_toolbar,
		:refresh,
		_self, :|refresh_data()|,
		:insensitive_image_file_name, :refresh,
		:tooltip, "Refresh data")

	# Source selector
	sw_label_item.new(a_toolbar, "  Source:  ")
	.items[:source] << sw_text_item.new(a_toolbar,
		:model, _self,
		:display_length, 20,
		:editable?, _false,
		:change_selector, {:|source_selection()|})
	.items[:source].text_items << {"ASTRI API", "Database (Construction)"}
	.items[:source].value << "ASTRI API"

	# Filter button
	.items[:apply_filters_btn] << sw_button_item.new(
		a_toolbar, "Apply Filters",
		_self, :|apply_filters()|,
		:tooltip, "Apply current filters")

	# Filters
	sw_label_item.new(a_toolbar, "  Category:  ")
	.items[:filter_category] << sw_text_item.new(a_toolbar,
		:model, _self,
		:display_length, 15,
		:editable?, _false)
	.items[:filter_category].text_items << {
		"All", "cluster_boq", "installation", "maintenance"}
	.items[:filter_category].value << "All"

	sw_label_item.new(a_toolbar, "  Status:  ")
	.items[:filter_status] << sw_text_item.new(a_toolbar,
		:model, _self,
		:display_length, 15,
		:editable?, _false)
	.items[:filter_status].text_items << {
		"All", "draft", "in_progress", "completed", "cancelled"}
	.items[:filter_status].value << "All"

	# Add more filters...
	# Pagination controls
	# Record count label
_endmethod
$

_private _method rwwi_astri_workorder_dialog.build_table(parent)
	## Build work order table

	.items[:table] << sw_table.new(parent,
		:row_height, 20,
		:selection_type, :row,
		:selection_mode, :one,
		:model, _self,
		:aspect, :workorder_list,
		:row_lines?, _true,
		:column_lines?, _true,
		:row_stripes?, _true,
		:data_selector, :|workorder_list_data()|,
		:enable_manage_columns?, _true,
		:enable_sort?, _true)

	.items[:table].set_column_labels({
		"No", "WO Number", "Cluster Code", "Cluster Name", "Category",
		"Status", "Topology", "Vendor", "Created"})

	.items[:table].col_resize_values << {0, 1, 0.8, 2, 1, 1, 0.5, 1.5, 1}

	# Enable column filters
	.items[:table].enable_column_filter(2, _true)  # WO Number
	.items[:table].enable_column_filter(3, _true)  # Cluster Code
	.items[:table].enable_column_filter(4, _true)  # Cluster Name
	.items[:table].enable_column_filter(6, _true)  # Status
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.workorder_list_data()
	## Populate table with work order data

	# Get data from engine based on current source
	_local workorders << _if .filters[:source] _is :api
	_then
		>> .engine.get_workorders_from_api(
			.filters[:limit],
			.filters[:offset],
			_self.build_filter_params())
	_else
		>> .engine.get_workorders_from_db(_self.build_filter_params())
	_endif

	# Populate table
	row << 0
	_for wo _over workorders.fast_elements()
	_loop
		row +<< 1
		.items[:table].add_cell(row, 1, {}, {:label, row.write_string})
		.items[:table].add_cell(row, 2, {}, {:label, wo[:wo_number]})
		.items[:table].add_cell(row, 3, {}, {:label, wo[:cluster_code]})
		.items[:table].add_cell(row, 4, {}, {:label, wo[:cluster_name]})
		.items[:table].add_cell(row, 5, {}, {:label, wo[:category]})
		.items[:table].add_cell(row, 6, {}, {:label, wo[:status]})
		.items[:table].add_cell(row, 7, {}, {:label, wo[:topology]})
		.items[:table].add_cell(row, 8, {}, {:label, wo[:vendor]})
		.items[:table].add_cell(row, 9, {}, {:label, wo[:created_at]})
	_endloop

	# Update record count
	.items[:record_count].value << write_string("Total Records: ", row)
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.refresh_data()
	## Refresh table data from current source

	write("Refreshing work order data...")
	_self.changed(:workorder_list, :renew)
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.source_selection()
	## Handle source dropdown change

	_local source_text << .items[:source].value

	.filters[:source] << _if source_text = "ASTRI API"
	_then
		>> :api
	_else
		>> :db
	_endif

	write("Source changed to:", .filters[:source])
	_self.refresh_data()
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_dialog.apply_filters()
	## Apply current filter selections

	# Build filter property list
	_self.build_filter_params()

	# Refresh data
	_self.refresh_data()
_endmethod
$

_private _method rwwi_astri_workorder_dialog.build_filter_params()
	## Build filter property list from UI controls

	_local filters << property_list.new()

	_local category << .items[:filter_category].value
	_if category _isnt _unset _and category <> "All"
	_then
		filters[:category_name] << category
	_endif

	_local status << .items[:filter_status].value
	_if status _isnt _unset _and status <> "All"
	_then
		filters[:latest_status_name] << status
	_endif

	# Add more filter params...

	>> filters
_endmethod
$

# Additional methods for detail panel, pagination, etc.
# ... (~200 more lines)
```

### 6. Plugin Layer (`rwwi_astri_workorder_plugin.magik`)

```magik
#% text_encoding = iso8859_1

_package sw
$

## ASTRI Work Order Plugin

def_slotted_exemplar(:rwwi_astri_workorder_plugin,
{
	{:dialog, _unset}
}, :plugin)
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_plugin.init_actions()
	## Initialize plugin actions

	_super.init_actions()

	_self.add_action(
		sw_action.new(:activate_workorder,
			:engine, _self,
			:enabled?, _true,
			:tooltip, _self.message(:activate_workorder),
			:action_message, :activate_workorder|()| ))
_endmethod
$

_pragma(classify_level=debug, topic={astri_integration})
_method rwwi_astri_workorder_plugin.activate_workorder()
	## Show work order dialog

	_if .dialog _is _unset
	_then
		.dialog << rwwi_astri_workorder_dialog.new(_self)
	_endif

	_if .dialog.active?.not
	_then
		.dialog.activate()
	_endif
_endmethod
$
```

---

## INTEGRATION POINTS

### 1. ASTRI Java Integration

**Dependency:** `rwwi_astri_integration` module must be loaded

**Global Procedures Used:**
- `astri_get_work_orders(limit, offset, filters)` - Returns JSON string

**Integration Pattern:**
```magik
# In engine layer
json_result << astri_get_work_orders(50, 0, filters)
```

### 2. Database Integration

**Collections Required:**
- `work_order` collection with fields:
  - `uuid` (string)
  - `number` (string) - Work order number
  - `target_cluster_code` (string) - Cluster code
  - `target_cluster_name` (string) - Cluster name
  - `category_label` (string) - Category label (e.g., "Cluster BOQ")
  - `latest_status_name` (string) - Status (e.g., "in_progress")
  - `target_cluster_topology` (string) - Topology (AE/UG/OH)
  - `assigned_vendor_label` (string) - Vendor name
  - `created_at` (timestamp) - Creation timestamp
  - `updated_at` (timestamp) - Last update timestamp
  - `construction_status` (boolean) ⬅️ NEW FIELD

**Database Schema Update Required:**
```sql
ALTER TABLE work_order
ADD COLUMN construction_status BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_work_order_construction_status
ON work_order(construction_status);
```

### 3. Smallworld Framework Integration

**Plugin Registration:**
- Extends `:plugin` exemplar
- Registered in module via `load_list.txt`
- Actions defined in `init_actions()`

**UI Components Used:**
For comprehensive examples and detailed usage, refer to:
`C:\Smallworld\core\sw_core\modules\sw_swift\magik_gui_components_examples`

Key components:
- `sw_canvas_container` - Main container layout
- `sw_toolbar_container` - Toolbar management
- `sw_table` - Data table with sorting/filtering
- `sw_text_item` - Text input and dropdowns
- `sw_label_item` - Static labels
- `sw_button_item` - Action buttons
- `sw_image_button_item` - Icon buttons

---

## TESTING STRATEGY

### Unit Testing

**Test Files:** Create `test/test_rwwi_astri_workorder_engine.magik`

```magik
#% text_encoding = iso8859_1

_package sw
$

_pragma(classify_level=debug, topic={astri_integration, test})
_global test_astri_workorder_engine << _proc()
	## Test engine API calls

	engine << rwwi_astri_workorder_engine.new()

	# Test API call
	workorders << engine.get_workorders_from_api(10, 0)

	write("API Test - Retrieved", workorders.size, "work orders")

	_if workorders.size > 0
	_then
		wo << workorders.an_element()
		write("Sample WO:", wo[:wo_number], wo[:cluster_code])
	_endif
_endproc
$
```

### Integration Testing

**Test Scenarios:**

| Test ID | Scenario | Expected Result |
|---------|----------|-----------------|
| INT-01 | Load module in Smallworld | No errors, plugin available |
| INT-02 | Activate UI from toolbar | Dialog opens successfully |
| INT-03 | Call API with no filters | Table populates with data |
| INT-04 | Apply category filter | Table shows filtered results |
| INT-05 | Switch to Database source | Table shows DB records |
| INT-06 | Click refresh button | Data reloads |
| INT-07 | Select work order row | Detail panel updates |
| INT-08 | Mark as construction | DB updated, confirmation shown |

### User Acceptance Testing

**UAT Checklist:**
- [ ] UI loads without errors
- [ ] Table displays work orders correctly
- [ ] Filters work as expected
- [ ] Refresh button reloads data
- [ ] Source toggle switches between API and DB
- [ ] Pagination controls work
- [ ] Selection highlights row
- [ ] Detail panel shows correct info
- [ ] Mark construction updates DB
- [ ] Error messages are clear

---

## DEPLOYMENT PLAN

### Pre-Deployment Checklist

- [ ] Java interop module (`rwwi_astri_integration`) is deployed
- [ ] Database schema updated with `construction_status` field
- [ ] Message files created for localization
- [ ] All source files created and tested
- [ ] Module definition correct
- [ ] Load order verified

### Deployment Steps

#### Step 1: Create Module Directory

```bash
cd C:\Smallworld\pni_custom\modules
mkdir rwwi_astri_workorder
mkdir rwwi_astri_workorder\source
mkdir rwwi_astri_workorder\resources
mkdir rwwi_astri_workorder\resources\en_gb
mkdir rwwi_astri_workorder\resources\en_gb\messages
```

#### Step 2: Create Module Files

1. Create `module.def`
2. Create root `load_list.txt`
3. Create `source/load_list.txt`
4. Create all `.magik` source files
5. Create message files

#### Step 3: Update Database Schema

```sql
-- Add construction_status field to work_order table
ALTER TABLE work_order
ADD COLUMN construction_status BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_work_order_construction_status
ON work_order(construction_status)
WHERE construction_status = TRUE;
```

#### Step 4: Update pni_custom Module

Edit `C:\Smallworld\pni_custom\module.def`:

```
pni_custom 1

description
	Top level module for site specific custom image builds.
end

requires
	pni_base
	...
	rwwi_astri_integration
	rwwi_astri_workorder    # ADD THIS LINE
end
```

#### Step 5: Load and Test

```magik
# In Smallworld session
sw_module_manager.load_module(:rwwi_astri_workorder)

# Test engine
test_astri_workorder_engine()

# Activate UI
# (Via toolbar or)
smallworld_product.applications[:rwwi_astri_workorder_plugin].activate_workorder()
```

### Post-Deployment Verification

- [ ] Module loads without errors
- [ ] Plugin appears in application
- [ ] UI activates successfully
- [ ] API calls return data
- [ ] Database queries work
- [ ] No memory leaks detected
- [ ] Performance acceptable (<5s response time)

---

## RISKS AND MITIGATION

### Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| JSON parsing complexity | High | Medium | Use robust JSON library, add error handling |
| API timeout/failure | High | Low | Add timeout handling, show user-friendly errors |
| Database schema incompatibility | Medium | Low | Document schema requirements clearly |
| UI performance with large datasets | Medium | Medium | Implement pagination, limit rows to 100 max |
| Module loading order issues | High | Low | Follow reference module patterns strictly |

### Operational Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| User confusion with filters | Low | Medium | Add tooltips, clear labels, help documentation |
| Incorrect construction marking | High | Low | Add confirmation dialog, audit trail |
| Data inconsistency between API and DB | Medium | Medium | Add sync mechanism, clear status indicators |

---

## SUCCESS CRITERIA

### Must Have (MVP)

✅ Module loads without errors
✅ UI displays work orders from API
✅ Filters work (category, status)
✅ Refresh button reloads data
✅ Source toggle switches between API and DB
✅ Construction marking updates database

### Should Have

✅ All filters implemented (vendor, topology, cluster)
✅ Pagination controls functional
✅ Column sorting enabled
✅ Detail panel shows selected work order
✅ Error handling for API failures

### Nice to Have

✅ Export to CSV/Excel
✅ Advanced search
✅ Work order history tracking
✅ Bulk operations (mark multiple as construction)
✅ Real-time refresh (auto-refresh every N seconds)

---

## TIMELINE ESTIMATE

| Phase | Tasks | Duration | Dependencies |
|-------|-------|----------|--------------|
| **Phase 1: Setup** | Create directories, module.def, load order | 1 hour | None |
| **Phase 2: Engine** | Implement data access layer (300 lines) | 4 hours | Phase 1 |
| **Phase 3: Dialog** | Implement UI layer (500 lines) | 8 hours | Phase 2 |
| **Phase 4: Plugin** | Implement plugin layer (150 lines) | 2 hours | Phase 3 |
| **Phase 5: Testing** | Unit tests, integration tests | 4 hours | Phase 4 |
| **Phase 6: Refinement** | Bug fixes, UI polish | 3 hours | Phase 5 |
| **Phase 7: Documentation** | User guide, inline comments | 2 hours | Phase 6 |
| **TOTAL** | | **24 hours** | **(~3 days)** |

---

## APPENDICES

### Appendix A: Sample JSON Response (Actual API Structure)

```json
{
  "success": true,
  "count": 50,
  "count_all": 127,
  "data": [
    {
      "uuid": "d35ed679-0b5e-4c33-953c-2740b5cc7772",
      "number": "WO/ALL/2025/DOCU/16/54556",
      "appointment_date": "2025-10-28",
      "appointment_slot_name": "slot_14_to_17",
      "appointment_slot_label": "Slot 14 to 17",
      "assigned_vendor_name": "yangtze_optical_fible_and_cable",
      "assigned_vendor_label": "Yangtze Optical Fible and Cable",
      "assigned_vendor_sap_vendor_code": "1000001432",
      "category_name": "cluster_boq",
      "category_label": "Cluster BOQ",
      "target_cluster_code": "MNA000033",
      "target_cluster_name": "PADANG SIALANG RT 01, 02, 03, 10, 11, DAN 12 BENGKULU SELATAN",
      "target_cluster_drm_net_type": "AERIAL",
      "target_cluster_drm_homepass": 262,
      "target_cluster_area": "BENGKULU SELATAN",
      "target_cluster_topology": "AE",
      "target_cluster_olt_name": "olt_kota_manna",
      "target_cluster_olt_label": "OLT Kota Manna",
      "target_cluster_latest_status": "BOUNDARY APPROVED",
      "latest_status_name": "in_progress",
      "latest_status_label": "In Progress",
      "assigned_department_name": "planning",
      "assigned_department_label": "Planning",
      "latest_executor_username": "budi.id",
      "latest_executor_fullname": "budi.id",
      "is_deleted": "0",
      "created_at": "2025-10-28 14:28:08",
      "closed_at": null,
      "updated_at": "2025-10-28 14:47:39"
    }
  ],
  "error": null
}
```

**Field Mapping (API JSON → Property List → Table Column):**

| API JSON Field | Property List Key | Table Column | Notes |
|----------------|-------------------|--------------|-------|
| `uuid` | `:uuid` | (internal) | Unique identifier |
| `number` | `:wo_number` | WO Number | Format: WO/ALL/2025/DOCU/16/54556 |
| `target_cluster_code` | `:cluster_code` | Cluster Code | e.g., MNA000033 |
| `target_cluster_name` | `:cluster_name` | Cluster Name | Full cluster name |
| `category_label` | `:category` | Category | Display label (e.g., "Cluster BOQ") |
| `latest_status_name` | `:status` | Status | e.g., "in_progress" |
| `target_cluster_topology` | `:topology` | Topology | AE/UG/OH |
| `assigned_vendor_label` | `:vendor` | Vendor | Display vendor name |
| `created_at` | `:created_at` | Created Date | Timestamp |

**Important Notes:**
- Use `*_label` fields for display (e.g., `category_label`, `assigned_vendor_label`)
- Use `*_name` fields for filtering (e.g., `category_name`, `assigned_vendor_name`)
- Filters in API calls use the `_name` fields, not `_label` fields

### Appendix B: Message File Templates

**`rwwi_astri_workorder_plugin.msg`:**
```
:activate_workorder   ASTRI Work Orders...
```

**`rwwi_astri_workorder_dialog.msg`:**
```
:title                  ASTRI Work Order Manager
:refresh_tooltip        Refresh work order data
:apply_filters_tooltip  Apply current filters
:source_api             ASTRI API
:source_db              Database (Construction)
:record_count           Total Records: #1
:mark_construction      Mark as Construction
:confirm_mark           Are you sure you want to mark this work order as under construction?
```

### Appendix C: Keyboard Shortcuts (Future Enhancement)

| Shortcut | Action |
|----------|--------|
| F5 | Refresh data |
| Ctrl+F | Focus filter field |
| Ctrl+R | Reset filters |
| Enter (on row) | Show details |
| Ctrl+M | Mark as construction |

---

## APPROVAL SIGNATURES

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Plan Author | Claude Code | 2025-10-27 | ✍️ |
| Technical Reviewer | | | |
| User Acceptance | | | |
| Deployment Approval | | | |

---

**Document Version:** 1.0
**Created:** 2025-10-27
**Last Updated:** 2025-10-27
**Status:** 📋 AWAITING APPROVAL - DO NOT IMPLEMENT YET

---

**END OF PLAN**
