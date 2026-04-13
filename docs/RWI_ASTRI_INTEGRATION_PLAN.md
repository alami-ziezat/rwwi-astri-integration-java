# RWI MyRepublic ASTRI Integration - Implementation Plan

## Overview

This document outlines the implementation plan for integrating ASTRI API calls into the Smallworld GIS system. The feature will create a new product `rwi_myrepublic_custom` with a module `rwi_astri_integration` that interfaces Magik with Java HTTP client libraries to call multiple REST APIs:

1. **Work Order API** - Get work order list with filtering (JSON)
2. **Price List API** - Get device price list with filtering (JSON)
3. **KMZ Document Download API** - Download KMZ/KML documents from 4 different endpoints (Binary)

The first two APIs use the ASTRI API v2/v4 base URL, while the document download API uses the ASTRI DM v4 base URL. All APIs use Basic Authentication.

---

## API Specifications

### 1. Work Order API
- **Endpoint:** `/work-order/cluster/boq/simple/list/all/:limit/:offset`
- **Base URL:** `http://172.17.75.22/astri-api-v2/v4`
- **Method:** GET
- **Full Example:** `http://172.17.75.22/astri-api-v2/v4/work-order/cluster/boq/simple/list/all/100/0`
- **Query Parameters (Optional):**
  - `category_name` - WO Category Name (e.g., `cluster_boq`)
  - `target_cluster_code` - Cluster Code
  - `target_cluster_topology` - Cluster Topology
  - `latest_status_name` - WO Status (e.g., `in_progress`)
  - `target_cluster_name` - Cluster Name

### 2. Price List API
- **Endpoint:** `/device/price/list/all`
- **Base URL:** `http://172.17.75.22/astri-api-v2/v4`
- **Method:** GET
- **Full Example:** `http://172.17.75.22/astri-api-v2/v4/device/price/list/all`
- **Query Parameters (Optional):**
  - `vendor_name` - Vendor Name
  - `subcont_vendor_name` - Subcont Name
  - `valid_date_end` - Valid Date End (e.g., `2025-10-17`)
  - `project_type` - Project Type (ALL, CLUSTER)
  - `area` - Area

### 3. KMZ Document Download API

Downloads KMZ documents from ASTRI DM API with automatic conversion to KML format.

**Base URL:** `http://172.17.75.22/astri-dm/v4`
**Method:** GET
**Response Format:** Binary (KMZ file - compressed KML)

#### 3.1 Cluster Document Download
- **Endpoint:** `/osp/cluster/document/cluster/download/{uuid}`
- **Full Example:** `http://172.17.75.22/astri-dm/v4/osp/cluster/document/cluster/download/f2366e49-602c-4066-bc6d-95978cc8e456`
- **Path Parameter:** `uuid` - Cluster document UUID
- **Response:** KMZ file containing cluster geographic data

#### 3.2 Subfeeder Document Download
- **Endpoint:** `/osp/cluster/document/subfeeder/download/{uuid}`
- **Full Example:** `http://172.17.75.22/astri-dm/v4/osp/cluster/document/subfeeder/download/85403496-7f79-43e3-9cbb-9556f079e1d5`
- **Path Parameter:** `uuid` - Subfeeder document UUID
- **Response:** KMZ file containing subfeeder route data

#### 3.3 Feeder Document Download
- **Endpoint:** `/osp/cluster/document/feeder/download/{uuid}`
- **Full Example:** `http://172.17.75.22/astri-dm/v4/osp/cluster/document/feeder/download/28af9282-654f-4f6a-87f9-7ea761c0dd11`
- **Path Parameter:** `uuid` - Feeder document UUID
- **Response:** KMZ file containing feeder cable data

#### 3.4 OLT Site Document Download
- **Endpoint:** `/osp/cluster/document/olt/site/download/{uuid}`
- **Full Example:** `http://172.17.75.22/astri-dm/v4/osp/cluster/document/olt/site/download/3b2b87c9-3bb8-409a-8d23-d1cafdb4403c`
- **Path Parameter:** `uuid` - OLT site document UUID
- **Response:** KMZ file containing OLT site location data

**Note:** KMZ files are ZIP-compressed KML (Keyhole Markup Language) files. The implementation will:
1. Download the binary KMZ file
2. Automatically extract/uncompress to KML format
3. Return both KMZ (original) and KML (extracted) content

### Authentication
- **Type:** Basic Authentication
- **Username:** `smallworld`
- **Password:** `Smallworld@2025!`
- **Note:** Same credentials used for both ASTRI API v2/v4 and ASTRI DM v4

### Response Formats
- **Work Order API & Price List API:** JSON format
- **KMZ Document Download API:** Binary (KMZ file - compressed KML XML)

---

## Technical Approach

### Architecture
The implementation will follow Smallworld's Java interop pattern (similar to `core/interop.demo`):

1. **Java Layer** - Java classes to handle HTTP communication
   - Use Vert.x WebClient (already available in `core/sw_core/libs/`)
   - Handle Basic Authentication
   - Parse JSON responses using Jackson (already available)
   - Handle binary file downloads (KMZ files)
   - Uncompress KMZ to KML using Java ZIP libraries
   - Provide clean interface for Magik code

2. **Magik Layer** - Magik code to call Java classes
   - Define exemplars for API clients
   - Provide methods for calling Work Order, Price List, and KMZ Download APIs
   - Handle parameter building
   - Process JSON responses into Magik data structures
   - Handle file saving and management

3. **Configuration** - Externalized configuration
   - API base URLs (API v2/v4 and DM v4)
   - Authentication credentials
   - Timeout settings
   - Download directory path

### Libraries Used
**Available in Smallworld (core/sw_core/libs/):**
- `vertx-web-client-4.5.13.jar` - HTTP client
- `vertx-core-4.5.13.jar` - Core Vert.x framework
- `jackson-databind-2.18.3.jar` - JSON parsing
- `jackson-core-2.18.3.jar` - JSON core
- `jackson-annotations-2.18.3.jar` - JSON annotations
- `com.gesmallworld.magik.interop-5.3.6.0-412.jar` - Magik-Java interop

**Java Standard Libraries (Built-in):**
- `java.util.zip.*` - For KMZ (ZIP) file extraction
- `java.io.*` - For file I/O operations
- `java.nio.file.*` - For modern file operations

**Additional Libraries Needed:**
- None - all required libraries are already available (including Java standard libraries)

---

## Deliverables

### Directory Structure
```
rwi_myrepublic_custom/
├── product.def
├── libs/
│   └── rwi.astri.integration.jar        # Java JAR with HTTP client code
├── downloads/                            # KMZ/KML download directory (created at runtime)
└── modules/
    └── rwi_astri_integration/
        ├── module.def
        ├── source/
        │   ├── load_list.txt
        │   ├── astri_api_client.magik
        │   ├── work_order_api.magik
        │   ├── price_list_api.magik
        │   ├── kmz_download_api.magik      # NEW: KMZ download API wrapper
        │   └── astri_config.magik
        └── resources/
            └── base/
                ├── data/
                │   └── astri_config.properties
                └── messages/
                    └── en_gb/
                        └── astri_integration.msg
```

### 1. Product Definition
**File:** `rwi_myrepublic_custom/product.def`
```
rwi_myrepublic_custom layered_product

title
    RWI MyRepublic Custom Integration
end

description
    Custom integration for MyRepublic with ASTRI API
end
```

### 2. Module Definition
**File:** `rwi_myrepublic_custom/modules/rwi_astri_integration/module.def`
```
rwi_astri_integration 1

description
    ASTRI API integration module for Work Order and Price List APIs
end

requires
    base
end

requires_java
    rwi.astri.integration
end

language en_gb
```

### 3. Java Component (`rwi.astri.integration.jar`)

**Source files to compile into JAR:**

#### `AstriApiClient.java`
Main HTTP client wrapper using Vert.x WebClient:
- Initialize Vert.x WebClient
- Configure Basic Authentication
- Build request with query parameters
- Execute HTTP GET requests
- Handle responses and errors
- Parse JSON responses

#### `WorkOrderApiClient.java`
Work Order specific API client:
- Method: `getWorkOrders(limit, offset, queryParams)`
- Build endpoint with path parameters
- Add optional query parameters
- Return JSON response string

#### `PriceListApiClient.java`
Price List specific API client:
- Method: `getPriceList(queryParams)`
- Build endpoint
- Add optional query parameters
- Return JSON response string

#### `KmzDownloadApiClient.java` (NEW)
KMZ document download API client:
- Method: `downloadDocument(documentType, uuid, outputDirectory)`
- Document types: `cluster`, `subfeeder`, `feeder`, `olt-site`
- Downloads binary KMZ file from ASTRI DM API
- Automatically extracts KML from KMZ (unzip)
- Saves both KMZ and KML files to output directory
- Returns file paths as result object:
  - `kmzFilePath` - Path to downloaded KMZ file
  - `kmlFilePath` - Path to extracted KML file
  - `kmlContent` - KML content as string (optional)
- File naming: `{documentType}_{uuid}.kmz` and `{documentType}_{uuid}.kml`
- Error handling for download failures and extraction errors

**KMZ to KML Conversion Logic:**
1. Download KMZ file as byte array
2. Save KMZ to disk
3. Read KMZ as ZIP file (using `java.util.zip.ZipInputStream`)
4. Extract first `.kml` file found in ZIP
5. Save extracted KML to disk
6. Return both file paths

#### `AstriConfig.java`
Configuration holder:
- API base URLs (v2/v4 and DM v4)
- Authentication credentials
- Timeout settings
- Download directory path
- Load from properties file

### 4. Magik Components

#### `astri_config.magik`
Configuration management:
- Load configuration from properties file
- Provide getters for config values
- Allow runtime override of settings

#### `astri_api_client.magik`
Base API client exemplar:
- Initialize Java client
- Common error handling
- JSON parsing utilities
- Response wrapper

#### `work_order_api.magik`
Work Order API interface:
```magik
_method astri_work_order_api.get_work_orders(limit, offset, _optional query_params)
    ## Get work orders from ASTRI API
    ##
    ## PARAMETERS:
    ## limit: Maximum number of records to return (integer)
    ## offset: Offset for pagination (integer)
    ## query_params: Property list with optional filters:
    ##   - :category_name - WO Category (e.g., "cluster_boq")
    ##   - :target_cluster_code - Cluster code (e.g., "JKT005514")
    ##   - :target_cluster_topology - Topology type (e.g., "AE")
    ##   - :latest_status_name - Status (e.g., "in_progress")
    ##   - :target_cluster_name - Cluster name
    ##
    ## RETURNS: Property list with structure:
    ##   :success - Boolean indicating success
    ##   :count - Number of records in current response
    ##   :count_all - Total number of records matching filter
    ##   :data - Simple vector of property lists, each containing:
    ##     CORE FIELDS (8):
    ##       :uuid, :number, :appointment_date, :appointment_slot_name,
    ##       :appointment_slot_label, :created_at, :updated_at, :closed_at
    ##
    ##     VENDOR FIELDS (6):
    ##       :assigned_vendor_name, :assigned_vendor_label,
    ##       :assigned_vendor_sap_vendor_code, :assigned_subcont_vendor_name,
    ##       :assigned_subcont_vendor_label, :assigned_subcont_vendor_sap_vendor_code
    ##
    ##     CATEGORY & STATUS FIELDS (6):
    ##       :category_name, :category_label, :latest_status_name,
    ##       :latest_status_label, :assigned_department_name, :assigned_department_label
    ##
    ##     CLUSTER INFO FIELDS (50+):
    ##       Basic: :target_cluster_code, :target_cluster_name, :target_cluster_area,
    ##              :target_cluster_drm_net_type, :target_cluster_drm_homepass,
    ##              :target_cluster_topology, :target_cluster_olt_name, etc.
    ##       Cost: :target_cluster_cost_per_homepass, :target_cluster_cost_per_port, etc.
    ##       DRM Values: :target_cluster_total_drm_value_cable, _fdt_fat, _olt,
    ##                   _pole, _permit, _project_management, etc.
    ##
    ##     DOCUMENT FIELDS (10):
    ##       :target_document_number, :target_document_uuid, :purchase_order_number, etc.
    ##
    ##     TRACKING FIELDS (3):
    ##       :latest_executor_username, :latest_executor_fullname, :is_deleted
    ##
    ##   :logs - Property list with query execution details
    ##
    ## EXAMPLE:
    ##   api << astri_work_order_api.new()
    ##   response << api.get_work_orders(10, 0)
    ##   _if response[:success] = _true
    ##   _then
    ##       response[:data].fast_elements_do(_proc(wo)
    ##           write(wo[:number], " - ", wo[:target_cluster_name])
    ##       _endproc)
    ##   _endif
```

#### `price_list_api.magik`
Price List API interface:
```magik
_method astri_price_list_api.get_price_list(_optional query_params)
    ## Get device price list from ASTRI API
    ##
    ## PARAMETERS:
    ## query_params: Property list with optional filters:
    ##   - :vendor_name - Vendor code (e.g., "zte_indonesia")
    ##   - :subcont_vendor_name - Subcontractor code
    ##   - :valid_date_end - Valid end date (e.g., "2025-01-01")
    ##   - :project_type - Project type ("ALL", "CLUSTER")
    ##   - :area - Area/region (e.g., "ALL", "JAKARTA")
    ##
    ## RETURNS: Property list with structure:
    ##   :success - Boolean indicating success
    ##   :count - Number of records in current response
    ##   :count_all - Total number of records matching filter
    ##   :data - Simple vector of property lists, each containing:
    ##     CORE FIELDS (2):
    ##       :uuid - Unique identifier
    ##       :created_at - Creation timestamp
    ##
    ##     PROJECT & VENDOR (6):
    ##       :project_type - Project type ("ALL", "CLUSTER")
    ##       :vendor_name - Vendor code
    ##       :vendor_label - Vendor display name
    ##       :subcont_vendor_name - Subcontractor code (nullable)
    ##       :subcont_vendor_label - Subcontractor display name (nullable)
    ##       :subcont_vendor_sap_code - Subcontractor SAP code (nullable)
    ##
    ##     EQUIPMENT & PRICING (4):
    ##       :equipment_name - Equipment/device code
    ##       :equipment_label - Equipment/device display name
    ##       :unit_price - Price per unit (numeric)
    ##       :unit_currency - Currency code (e.g., "IDR")
    ##
    ##     VALIDITY & LOCATION (3):
    ##       :valid_date_start - Start date (ISO format)
    ##       :valid_date_end - End date (ISO format)
    ##       :area - Geographic area
    ##
    ##   :logs - Property list with query execution details
    ##
    ## EXAMPLE:
    ##   api << astri_price_list_api.new()
    ##   response << api.get_price_list()
    ##   _if response[:success] = _true
    ##   _then
    ##       response[:data].fast_elements_do(_proc(price)
    ##           write(price[:equipment_label], " - ",
    ##                 price[:unit_price], " ", price[:unit_currency])
    ##       _endproc)
    ##   _endif
```

#### `kmz_download_api.magik` (NEW)
KMZ Document Download API interface:
```magik
_method astri_kmz_download_api.download_cluster_document(uuid, _optional output_dir)
    ## Download cluster KMZ document and convert to KML
    ##
    ## PARAMETERS:
    ## uuid: Document UUID (string)
    ## output_dir: Optional output directory (defaults to product downloads/)
    ##
    ## RETURNS: Property list with:
    ##   :success - Boolean indicating success
    ##   :document_type - "cluster"
    ##   :uuid - Document UUID
    ##   :kmz_file_path - Full path to downloaded KMZ file
    ##   :kml_file_path - Full path to extracted KML file
    ##   :kml_content - KML content as string (optional)
    ##
    ## EXAMPLE:
    ##   api << astri_kmz_download_api.new()
    ##   result << api.download_cluster_document("f2366e49-602c-4066-bc6d-95978cc8e456")
    ##   _if result[:success] = _true
    ##   _then
    ##       write("Downloaded to:", result[:kml_file_path])
    ##   _endif

_method astri_kmz_download_api.download_subfeeder_document(uuid, _optional output_dir)
    ## Download subfeeder KMZ document and convert to KML

_method astri_kmz_download_api.download_feeder_document(uuid, _optional output_dir)
    ## Download feeder KMZ document and convert to KML

_method astri_kmz_download_api.download_olt_site_document(uuid, _optional output_dir)
    ## Download OLT site KMZ document and convert to KML
```

**Common Implementation:**
- All methods use same pattern with different document types
- Calls Java `KmzDownloadApiClient.downloadDocument(type, uuid, dir)`
- Converts Java result to Magik property list
- Handles errors gracefully
- Returns file paths for both KMZ and KML

### 5. Configuration File
**File:** `rwi_myrepublic_custom/modules/rwi_astri_integration/resources/base/data/astri_config.properties`
```properties
# ASTRI API Configuration
astri.api.base_url=http://172.17.75.22/astri-api-v2/v4
astri.dm.base_url=http://172.17.75.22/astri-dm/v4
astri.api.username=smallworld
astri.api.password=Smallworld@2025!
astri.api.timeout_ms=30000
astri.api.connection_timeout_ms=10000

# KMZ Download Configuration
astri.api.download_directory=downloads
```

### 6. Message File
**File:** `rwi_myrepublic_custom/modules/rwi_astri_integration/resources/base/messages/en_gb/astri_integration.msg`
```
# ASTRI Integration Messages

# Error Messages
astri.error.connection_failed=Failed to connect to ASTRI API: {1}
astri.error.authentication_failed=Authentication failed for ASTRI API
astri.error.invalid_response=Invalid response from ASTRI API: {1}
astri.error.timeout=Request to ASTRI API timed out
astri.error.parse_json=Failed to parse JSON response: {1}
astri.error.download_failed=Failed to download KMZ document: {1}
astri.error.kmz_extraction_failed=Failed to extract KML from KMZ: {1}
astri.error.file_save_failed=Failed to save file: {1}

# Info Messages
astri.info.request_success=Successfully retrieved data from ASTRI API
astri.info.work_orders_retrieved=Retrieved {1} work orders (total: {2})
astri.info.prices_retrieved=Retrieved {1} prices (total: {2})
astri.info.kmz_downloaded=KMZ document downloaded: {1}
astri.info.kml_extracted=KML extracted from KMZ: {1}

# Warning Messages
astri.warning.no_results=No results found for the specified criteria
astri.warning.partial_results=Only partial results returned due to API limits
astri.warning.download_directory_created=Download directory created: {1}
```

### 7. Load List
**File:** `rwi_myrepublic_custom/modules/rwi_astri_integration/source/load_list.txt`
```
source/
```

### 8. Test Script
**File:** `rwi_myrepublic_custom/modules/rwi_astri_integration/source/test_astri_api.magik`
```magik
_pragma(classify_level=debug)
_global test_work_order_api << _proc()
    ## Test Work Order API call

    api << astri_work_order_api.new()

    # Test 1: Get first 10 work orders
    response << api.get_work_orders(10, 0)

    _if response[:success] = _true
    _then
        write("Success! Total count: ", response[:count_all])
        write("Returned: ", response[:count], " records")

        # Display first work order details
        _if response[:data].size > 0
        _then
            wo << response[:data].first
            write("First Work Order:")
            write("  UUID: ", wo[:uuid])
            write("  Number: ", wo[:number])
            write("  Cluster Code: ", wo[:target_cluster_code])
            write("  Cluster Name: ", wo[:target_cluster_name])
            write("  Status: ", wo[:latest_status_label])
            write("  Vendor: ", wo[:assigned_vendor_label])
            write("  Topology: ", wo[:target_cluster_topology])
            write("  Homepass: ", wo[:target_cluster_drm_homepass])
        _endif
    _else
        write("API call failed")
    _endif
_endproc
$

_global test_work_order_api_with_filters << _proc()
    ## Test Work Order API with filters

    api << astri_work_order_api.new()

    # Test with multiple filters
    filters << property_list.new_with(
        :category_name, "cluster_boq",
        :latest_status_name, "in_progress",
        :target_cluster_topology, "AE")

    response << api.get_work_orders(20, 0, filters)

    _if response[:success] = _true
    _then
        write("Filtered Results - Total: ", response[:count_all])

        # Iterate through all work orders
        response[:data].fast_elements_do(_proc(wo)
            write("WO: ", wo[:number],
                  " | Cluster: ", wo[:target_cluster_code],
                  " | Status: ", wo[:latest_status_label])
        _endproc)
    _endif
_endproc
$

_global test_price_list_api << _proc()
    ## Test Price List API call

    api << astri_price_list_api.new()

    # Test 1: Get all prices
    response << api.get_price_list()

    _if response[:success] = _true
    _then
        write("Success! Total prices: ", response[:count_all])
        write("Returned: ", response[:count], " records")

        # Display price list
        response[:data].fast_elements_do(_proc(price)
            write("Equipment: ", price[:equipment_label])
            write("  Vendor: ", price[:vendor_label])
            write("  Price: ", price[:unit_price], " ", price[:unit_currency])
            write("  Valid: ", price[:valid_date_start], " to ", price[:valid_date_end])
            write("  Project Type: ", price[:project_type])
            write("  Area: ", price[:area])
        _endproc)
    _else
        write("API call failed")
    _endif
_endproc
$

_global test_price_list_api_with_filters << _proc()
    ## Test Price List API with filters

    api << astri_price_list_api.new()

    # Test with filters
    filters << property_list.new_with(
        :project_type, "ALL",
        :valid_date_end, "2025-01-01")

    response << api.get_price_list(filters)

    _if response[:success] = _true
    _then
        write("Filtered Results - Total: ", response[:count_all])

        # Iterate through prices
        response[:data].fast_elements_do(_proc(price)
            write(price[:equipment_label], " - ",
                  price[:unit_price], " ", price[:unit_currency],
                  " | Vendor: ", price[:vendor_label])
        _endproc)
    _endif
_endproc
$

_global test_kmz_download_cluster << _proc()
    ## Test KMZ Download API - Cluster Document

    write("============================================================")
    write("Testing KMZ Download API - Cluster Document")
    write("============================================================")

    api << astri_kmz_download_api.new()

    # Test cluster document download
    write("Test: Download cluster document...")
    uuid << "f2366e49-602c-4066-bc6d-95978cc8e456"

    result << api.download_cluster_document(uuid)

    _if result[:success] = _true
    _then
        write("SUCCESS!")
        write("Document Type: ", result[:document_type])
        write("UUID: ", result[:uuid])
        write("KMZ File: ", result[:kmz_file_path])
        write("KML File: ", result[:kml_file_path])
        write("KML Size: ", result[:kml_content].size, " characters")
    _else
        write("FAILED: ", result[:error])
    _endif

    write("")
_endproc
$

_global test_kmz_download_subfeeder << _proc()
    ## Test KMZ Download API - Subfeeder Document

    write("============================================================")
    write("Testing KMZ Download API - Subfeeder Document")
    write("============================================================")

    api << astri_kmz_download_api.new()

    # Test subfeeder document download
    write("Test: Download subfeeder document...")
    uuid << "85403496-7f79-43e3-9cbb-9556f079e1d5"

    result << api.download_subfeeder_document(uuid)

    _if result[:success] = _true
    _then
        write("SUCCESS!")
        write("KMZ File: ", result[:kmz_file_path])
        write("KML File: ", result[:kml_file_path])
    _else
        write("FAILED: ", result[:error])
    _endif

    write("")
_endproc
$

_global test_kmz_download_feeder << _proc()
    ## Test KMZ Download API - Feeder Document

    write("============================================================")
    write("Testing KMZ Download API - Feeder Document")
    write("============================================================")

    api << astri_kmz_download_api.new()

    # Test feeder document download
    write("Test: Download feeder document...")
    uuid << "28af9282-654f-4f6a-87f9-7ea761c0dd11"

    result << api.download_feeder_document(uuid)

    _if result[:success] = _true
    _then
        write("SUCCESS!")
        write("KMZ File: ", result[:kmz_file_path])
        write("KML File: ", result[:kml_file_path])
    _else
        write("FAILED: ", result[:error])
    _endif

    write("")
_endproc
$

_global test_kmz_download_olt_site << _proc()
    ## Test KMZ Download API - OLT Site Document

    write("============================================================")
    write("Testing KMZ Download API - OLT Site Document")
    write("============================================================")

    api << astri_kmz_download_api.new()

    # Test OLT site document download
    write("Test: Download OLT site document...")
    uuid << "3b2b87c9-3bb8-409a-8d23-d1cafdb4403c"

    result << api.download_olt_site_document(uuid)

    _if result[:success] = _true
    _then
        write("SUCCESS!")
        write("KMZ File: ", result[:kmz_file_path])
        write("KML File: ", result[:kml_file_path])
    _else
        write("FAILED: ", result[:error])
    _endif

    write("")
_endproc
$

_global test_all_astri_apis << _proc()
    ## Run all ASTRI API tests

    write("============================================================")
    write("ASTRI API Integration - Full Test Suite")
    write("============================================================")
    write("")

    # Test JSON APIs
    test_work_order_api()
    test_work_order_api_with_filters()
    test_price_list_api()
    test_price_list_api_with_filters()

    # Test KMZ Download APIs
    test_kmz_download_cluster()
    test_kmz_download_subfeeder()
    test_kmz_download_feeder()
    test_kmz_download_olt_site()

    write("============================================================")
    write("All Tests Complete")
    write("============================================================")
_endproc
$
```

### 9. Documentation
**File:** `rwi_myrepublic_custom/README.md`
- Overview of the integration
- API documentation
- Usage examples
- Configuration guide
- Troubleshooting

---

## Technical Debt & Known Limitations

### 1. Security Concerns
**Issue:** Credentials stored in plain text properties file
**Risk:** High - credentials are visible to anyone with file access
**Mitigation Options:**
- Use Smallworld's authorization system (ACE) for credential storage
- Implement encrypted properties file
- Use environment variables
- Integrate with external secrets management (HashiCorp Vault, etc.)
**Recommended Action:** Implement in Phase 2

### 2. Error Handling
**Issue:** Initial implementation has basic error handling
**Risk:** Medium - may not cover all edge cases
**Areas to Improve:**
- Network timeouts
- Connection pooling errors
- Malformed JSON responses
- HTTP status code handling (4xx, 5xx)
- Retry logic for transient failures
**Recommended Action:** Enhance based on production usage patterns

### 3. No Connection Pooling
**Issue:** Each request creates new connection
**Risk:** Medium - performance impact under high load
**Impact:** Slower response times, higher resource usage
**Mitigation:** Vert.x WebClient handles connection pooling, but configuration may need tuning
**Recommended Action:** Monitor performance and tune if needed

### 4. Synchronous API Calls
**Issue:** API calls block the Magik thread
**Risk:** Low to Medium - UI freezing during long API calls
**Impact:** Poor user experience if API is slow
**Mitigation Options:**
- Use Vert.x async patterns with callbacks
- Implement background processing
- Add loading indicators in UI
**Recommended Action:** Implement async pattern in Phase 2

### 5. No Caching
**Issue:** Every call hits the API
**Risk:** Low - but impacts performance
**Impact:** Unnecessary API load, slower response
**Mitigation Options:**
- Implement time-based cache for price list (changes infrequently)
- Cache work orders with invalidation strategy
- Use ETags if API supports them
**Recommended Action:** Implement selective caching in Phase 2

### 6. Limited Testing
**Issue:** Only manual test scripts provided
**Risk:** Medium - regression risk during changes
**Mitigation:** Need comprehensive test suite
**Required:**
- Unit tests for Magik code
- Integration tests with mock API
- Load testing for performance validation
**Recommended Action:** Implement test suite in Phase 2

### 7. No Logging/Monitoring
**Issue:** Limited visibility into API calls
**Risk:** Medium - difficult to troubleshoot issues
**Required:**
- Log all API requests/responses
- Track response times
- Monitor error rates
- Alert on failures
**Recommended Action:** Implement comprehensive logging immediately

### 8. Hard-coded API Version
**Issue:** API version (v4) is part of base URL
**Risk:** Low - but requires code change for API version upgrades
**Mitigation:** Externalize version to configuration
**Recommended Action:** Include in initial implementation

### 9. No Rate Limiting
**Issue:** No protection against excessive API calls
**Risk:** Low - but could overwhelm API
**Impact:** API may block requests or degrade performance
**Mitigation:** Implement client-side rate limiting
**Recommended Action:** Implement if API has rate limits

### 10. JSON Parsing Limitations
**Issue:** Large JSON responses may cause memory issues
**Risk:** Medium - Work Order objects have 70+ fields each
**Impact:**
- Each work order object contains extensive cluster information
- Response includes nested objects (logs)
- Large result sets (count_all can be 100+) may consume significant memory
**Mitigation:**
- Implement streaming JSON parser for large responses
- Use appropriate pagination (keep limit reasonable, e.g., 50-100)
- Consider selective field parsing (only parse needed fields)
- Cache parsed results if reused
**Recommended Action:**
- Start with limit of 50-100 records per request
- Monitor memory usage
- Implement streaming parser if issues arise

### 11. No HTTPS Support
**Issue:** API uses HTTP (not HTTPS)
**Risk:** HIGH - credentials and data sent in clear text
**Impact:** Security vulnerability - credentials can be intercepted
**Critical:** If API moves to HTTPS, need to update implementation
**Recommended Action:**
- Request API team to implement HTTPS
- Document security risk
- Consider VPN or secure network requirement

### 12. No Request Timeout Configuration
**Issue:** Fixed timeout values
**Risk:** Low - but may need adjustment per API endpoint
**Mitigation:** Make timeouts configurable per API call
**Recommended Action:** Include in Phase 2

### 13. Binary File Size Limitations (NEW - KMZ Downloads)
**Issue:** No file size checks or limits on KMZ downloads
**Risk:** Medium - large KMZ files may cause memory issues
**Impact:**
- KMZ files can vary significantly in size (10 KB to 10+ MB)
- Very large files may exhaust heap memory during download
- No streaming download implementation
**Mitigation Options:**
- Add file size checking before download
- Implement streaming download for large files
- Set maximum file size limit in configuration
- Add progress monitoring for large downloads
**Recommended Action:** Implement file size limits and warnings in Phase 2

### 14. Disk Space Management (NEW - KMZ Downloads)
**Issue:** No disk space checking before downloads
**Risk:** Low to Medium - disk full errors
**Impact:**
- Download directory grows without limit
- No cleanup of old files
- Potential disk space exhaustion
**Mitigation Options:**
- Check available disk space before download
- Implement automatic cleanup of old downloads
- Add configurable retention period for downloaded files
- Compress old KMZ/KML files or move to archive
**Recommended Action:**
- Implement disk space check immediately
- Add cleanup strategy in Phase 2

### 15. KMZ Extraction Error Handling (NEW)
**Issue:** Limited error handling for corrupted KMZ files
**Risk:** Medium - corrupt ZIP files may cause extraction failures
**Impact:**
- Corrupted KMZ files won't extract properly
- Invalid KML inside KMZ may cause parsing errors
- Missing KML file in KMZ (edge case)
**Mitigation Options:**
- Validate KMZ file structure before extraction
- Check for KML file existence in ZIP
- Validate extracted KML XML structure
- Provide detailed error messages for different failure modes
**Recommended Action:** Implement robust validation in initial implementation

### 16. Concurrent Download Handling (NEW)
**Issue:** No protection against concurrent downloads of same file
**Risk:** Low - but may cause file access conflicts
**Impact:**
- Multiple threads downloading same UUID simultaneously
- Race conditions during file writes
- Temporary file naming conflicts
**Mitigation Options:**
- Add file locking mechanism
- Use unique temporary file names (UUID + timestamp)
- Implement download queue/serialization
**Recommended Action:** Use UUID-based file naming, add locking in Phase 2

### 17. KML Content Validation (NEW)
**Issue:** No validation of KML XML structure
**Risk:** Low to Medium - invalid KML may cause downstream issues
**Impact:**
- Invalid XML in extracted KML
- Missing required KML elements
- Smallworld GIS may fail to import invalid KML
**Mitigation Options:**
- Validate KML XML structure after extraction
- Check for required KML elements (placemark, coordinates, etc.)
- Provide XML validation errors to user
**Recommended Action:** Add basic XML validation in Phase 2

### 18. Download Directory Security (NEW)
**Issue:** Downloaded files stored without encryption
**Risk:** Medium - sensitive geographic data exposed
**Impact:**
- KML files contain sensitive location data
- Files readable by anyone with file system access
- No audit trail of file access
**Mitigation Options:**
- Encrypt downloaded files at rest
- Use OS-level permissions to restrict access
- Implement audit logging for file access
- Consider temporary files with automatic deletion
**Recommended Action:** Document security requirements, implement in Phase 2

---

## Implementation Phases

### Phase 1: Core Implementation (Initial Delivery)
**Scope:**
- Complete directory structure
- Java HTTP client implementation
- Magik API wrappers
- Basic configuration
- Test scripts
- Basic documentation

**Estimated Effort:** 3-5 days

### Phase 2: Enhancements (Future)
**Scope:**
- Secure credential storage
- Async API calls
- Comprehensive error handling
- Caching layer
- Full test suite
- Advanced logging/monitoring

**Estimated Effort:** 5-7 days

### Phase 3: Production Hardening (Future)
**Scope:**
- Load testing
- Performance optimization
- Connection pool tuning
- Retry logic with exponential backoff
- Circuit breaker pattern
- API health checks

**Estimated Effort:** 3-4 days

---

## How to Build

### 1. Java Component Build

**Prerequisites:**
- JDK 17 (must match Smallworld's Java version)
- Maven or Gradle build tool
- Access to Smallworld libs directory

**Maven Build (Recommended):**

Create `pom.xml`:
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.rwi.myrepublic</groupId>
    <artifactId>astri-integration</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <vertx.version>4.5.13</vertx.version>
        <jackson.version>2.18.3</jackson.version>
    </properties>

    <dependencies>
        <!-- Vert.x WebClient -->
        <dependency>
            <groupId>io.vertx</groupId>
            <artifactId>vertx-web-client</artifactId>
            <version>${vertx.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <outputDirectory>${project.basedir}/../rwi_myrepublic_custom/libs</outputDirectory>
                    <finalName>rwi.astri.integration</finalName>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Build Commands:**
```bash
# From Java source directory
mvn clean package

# JAR will be output to: rwi_myrepublic_custom/libs/rwi.astri.integration.jar
```

**Alternative: Manual Build with javac:**
```bash
# Compile
javac -cp "C:\Smallworld\core\sw_core\libs\*" \
      -d build/classes \
      src/com/rwi/myrepublic/astri/*.java

# Create JAR
jar cvf C:\Smallworld\rwi_myrepublic_custom\libs\rwi.astri.integration.jar \
    -C build/classes .
```

### 2. Magik Component (No Build Required)
Magik code compiles automatically at runtime when module loads.

---

## How to Install

### 1. Copy Product Directory
```bash
# Copy entire product directory to Smallworld installation
cp -r rwi_myrepublic_custom C:\Smallworld\
```

### 2. Verify Directory Structure
```
C:\Smallworld\rwi_myrepublic_custom\
├── product.def
├── libs\
│   └── rwi.astri.integration.jar
└── modules\
    └── rwi_astri_integration\
        ├── module.def  
        ├── source\
        │   ├── load_list.txt
        │   ├── astri_config.magik
        │   ├── astri_api_client.magik
        │   ├── work_order_api.magik
        │   ├── price_list_api.magik
        │   └── test_astri_api.magik
        └── resources\
            └── base\
                ├── data\
                │   └── astri_config.properties
                └── messages\
                    └── en_gb\
                        └── astri_integration.msg
```

### 3. Configure Product Path
Edit `C:\Smallworld\core\config\environment.bat`:
```batch
set SW_PRODUCTS_PATH=C:\Smallworld\rwi_myrepublic_custom;%SW_PRODUCTS_PATH%
```

### 4. Update Configuration
Edit `rwi_myrepublic_custom\modules\rwi_astri_integration\resources\base\data\astri_config.properties`:
- Verify base URL is correct
- Update credentials if different
- Adjust timeouts if needed

---

## How to Run

### Method 1: Start with Module Loaded

**Option A: Modify Existing Session**
Edit your Magik session configuration to include the module:
```
# In your gis_aliases or session config
load_module("rwi_astri_integration")
```

**Option B: Load at Runtime**
```magik
# In Magik prompt
smallworld_product.add_product("rwi_myrepublic_custom")
sw_module_manager.load_module(:rwi_astri_integration)
```

### Method 2: Test from Magik Prompt

**Start Smallworld:**
```bash
C:\Smallworld\core\bin\x86\gis.exe
```

**Load Module:**
```magik
# Load the product
smallworld_product.add_product("rwi_myrepublic_custom")

# Load the module
sw_module_manager.load_module(:rwi_astri_integration)
```

**Test Work Order API:**
```magik
# Run test procedure
test_work_order_api()

# Or call directly
api << astri_work_order_api.new()
response << api.get_work_orders(10, 0)

# Check if successful
_if response[:success] = _true
_then
    write("Total records: ", response[:count_all])
    write("Retrieved: ", response[:count])

    # Access work order data
    response[:data].fast_elements_do(_proc(wo)
        write("WO Number: ", wo[:number])
        write("  Cluster: ", wo[:target_cluster_code], " - ", wo[:target_cluster_name])
        write("  Status: ", wo[:latest_status_label])
        write("  Vendor: ", wo[:assigned_vendor_label])
        write("  Area: ", wo[:target_cluster_area])
        write("  Net Type: ", wo[:target_cluster_drm_net_type])
        write("  Homepass: ", wo[:target_cluster_drm_homepass])
        write("  Topology: ", wo[:target_cluster_topology])
    _endproc)
_endif

# With filters
filters << property_list.new_with(
    :category_name, "cluster_boq",
    :latest_status_name, "in_progress",
    :target_cluster_topology, "AE"
)
response << api.get_work_orders(50, 0, filters)

# Process specific fields
_if response[:success] = _true
_then
    response[:data].fast_elements_do(_proc(wo)
        # Access cluster information
        cluster_code << wo[:target_cluster_code]
        cluster_name << wo[:target_cluster_name]
        homepass << wo[:target_cluster_drm_homepass]
        olt_name << wo[:target_cluster_olt_label]

        # Access vendor information
        vendor << wo[:assigned_vendor_label]
        subcont << wo[:assigned_subcont_vendor_label]

        # Access status
        status << wo[:latest_status_label]

        write("Cluster: ", cluster_code, " has ", homepass, " homepass")
    _endproc)
_endif
```

**Test Price List API:**
```magik
# Run test procedure
test_price_list_api()

# Or call directly
api << astri_price_list_api.new()
response << api.get_price_list()

# Check if successful
_if response[:success] = _true
_then
    write("Total prices: ", response[:count_all])
    write("Retrieved: ", response[:count])

    # Access price data
    response[:data].fast_elements_do(_proc(price)
        write("Equipment: ", price[:equipment_label])
        write("  Code: ", price[:equipment_name])
        write("  Vendor: ", price[:vendor_label], " (", price[:vendor_name], ")")
        write("  Subcont: ", price[:subcont_vendor_label].default("None"))
        write("  Price: ", price[:unit_price], " ", price[:unit_currency])
        write("  Valid: ", price[:valid_date_start], " to ", price[:valid_date_end])
        write("  Project Type: ", price[:project_type])
        write("  Area: ", price[:area])
    _endproc)
_endif

# With filters
filters << property_list.new_with(
    :project_type, "ALL",
    :vendor_name, "zte_indonesia",
    :valid_date_end, "2025-01-01"
)
response << api.get_price_list(filters)

# Process specific fields
_if response[:success] = _true
_then
    response[:data].fast_elements_do(_proc(price)
        # Access equipment and pricing info
        equipment << price[:equipment_label]
        unit_price << price[:unit_price]
        currency << price[:unit_currency]
        vendor << price[:vendor_label]

        # Calculate or display
        write(equipment, ": ", unit_price, " ", currency, " from ", vendor)
    _endproc)
_endif
```

**Test KMZ Download API:**
```magik
# Run individual test procedures
test_kmz_download_cluster()
test_kmz_download_subfeeder()
test_kmz_download_feeder()
test_kmz_download_olt_site()

# Or call directly - Cluster Document
api << astri_kmz_download_api.new()
result << api.download_cluster_document("f2366e49-602c-4066-bc6d-95978cc8e456")

_if result[:success] = _true
_then
    write("Downloaded cluster document:")
    write("  Document Type: ", result[:document_type])
    write("  UUID: ", result[:uuid])
    write("  KMZ File: ", result[:kmz_file_path])
    write("  KML File: ", result[:kml_file_path])
    write("  KML Size: ", result[:kml_content].size, " characters")

    # KML content is available for processing
    kml_content << result[:kml_content]
    # Process KML content or import into Smallworld GIS
_else
    write("Download failed: ", result[:error])
_endif

# Download subfeeder document
result << api.download_subfeeder_document("85403496-7f79-43e3-9cbb-9556f079e1d5")

# Download feeder document
result << api.download_feeder_document("28af9282-654f-4f6a-87f9-7ea761c0dd11")

# Download OLT site document
result << api.download_olt_site_document("3b2b87c9-3bb8-409a-8d23-d1cafdb4403c")

# Specify custom output directory
result << api.download_cluster_document("f2366e49-602c-4066-bc6d-95978cc8e456",
                                        "C:\\temp\\astri_downloads")

# Get document UUID from work order, then download
wo_api << astri_work_order_api.new()
wo_response << wo_api.get_work_orders(1, 0)

_if wo_response[:success] = _true _andif wo_response[:count] > 0
_then
    wo << wo_response[:data].first
    doc_uuid << wo[:target_document_uuid]

    _if doc_uuid _isnt _unset _andif doc_uuid ~= ""
    _then
        # Download the document associated with this work order
        kmz_api << astri_kmz_download_api.new()
        result << kmz_api.download_cluster_document(doc_uuid)

        _if result[:success] = _true
        _then
            write("Downloaded document for WO: ", wo[:number])
            write("  KML File: ", result[:kml_file_path])
        _endif
    _endif
_endif
```

### Method 3: Integration in Application

**Add to Your Application Module:**
```magik
# In your application startup
_method my_application.init_astri_integration()
    ## Initialize ASTRI API clients

    .work_order_api << astri_work_order_api.new()
    .price_list_api << astri_price_list_api.new()

    >> _self
_endmethod
$

_method my_application.fetch_work_orders(limit, offset, _optional filters)
    ## Fetch work orders from ASTRI

    >> .work_order_api.get_work_orders(limit, offset, filters)
_endmethod
$

_method my_application.fetch_prices(_optional filters)
    ## Fetch device prices from ASTRI

    >> .price_list_api.get_price_list(filters)
_endmethod
$
```

---

## Testing & Validation

### 1. Unit Testing
```magik
# Test configuration loading
config << astri_config.get_instance()
write("Base URL:", config.base_url)
write("Username:", config.username)

# Test API client creation
api << astri_work_order_api.new()
write("API Client created:", api)
```

### 2. Integration Testing
```magik
# Test Work Order API without filters
response << astri_work_order_api.new().get_work_orders(5, 0)
write("Work Orders Success:", response[:success])
write("Total Count:", response[:count_all])
write("Retrieved:", response[:count])

# Test Work Order API with each filter individually
filters << property_list.new_with(:category_name, "cluster_boq")
response << astri_work_order_api.new().get_work_orders(10, 0, filters)
write("Cluster BOQ count:", response[:count_all])

# Test filtering by status
filters << property_list.new_with(:latest_status_name, "in_progress")
response << astri_work_order_api.new().get_work_orders(10, 0, filters)
write("In Progress count:", response[:count_all])

# Test filtering by topology
filters << property_list.new_with(:target_cluster_topology, "AE")
response << astri_work_order_api.new().get_work_orders(10, 0, filters)
write("AE Topology count:", response[:count_all])

# Test filtering by cluster code
filters << property_list.new_with(:target_cluster_code, "JKT005514")
response << astri_work_order_api.new().get_work_orders(10, 0, filters)
_if response[:count] > 0
_then
    wo << response[:data].first
    write("Found cluster:", wo[:target_cluster_name])
_endif

# Test Price List API without filters
response << astri_price_list_api.new().get_price_list()
write("Price List Success:", response[:success])
write("Total Count:", response[:count_all])
write("Retrieved:", response[:count])

# Test Price List API with filters
filters << property_list.new_with(:project_type, "ALL")
response << astri_price_list_api.new().get_price_list(filters)
write("ALL project type count:", response[:count_all])

# Test filtering by vendor
filters << property_list.new_with(:vendor_name, "zte_indonesia")
response << astri_price_list_api.new().get_price_list(filters)
write("ZTE vendor count:", response[:count_all])

# Test filtering by valid date
filters << property_list.new_with(:valid_date_end, "2025-01-01")
response << astri_price_list_api.new().get_price_list(filters)
_if response[:count] > 0
_then
    price << response[:data].first
    write("Found price:", price[:equipment_label],
          " at ", price[:unit_price], " ", price[:unit_currency])
_endif

# Test filtering by area
filters << property_list.new_with(:area, "ALL")
response << astri_price_list_api.new().get_price_list(filters)
write("ALL area count:", response[:count_all])
```

### 3. Error Handling Testing
```magik
# Test with invalid credentials (modify config temporarily)

# Test with unreachable URL

# Test with invalid filter values

# Test with very large limit/offset
```

### 4. Performance Testing
```magik
# Measure response time for work orders
start << system.elapsed_milliseconds()
response << astri_work_order_api.new().get_work_orders(100, 0)
elapsed << system.elapsed_milliseconds() - start
write("Elapsed:", elapsed, "ms for", response[:count], "records")
write("Total available:", response[:count_all], "records")
write("Avg time per record:", elapsed / response[:count].as_float, "ms")

# Test pagination performance
_for offset _over range(0, 1000, 100)
_loop
    start << system.elapsed_milliseconds()
    response << astri_work_order_api.new().get_work_orders(100, offset)
    elapsed << system.elapsed_milliseconds() - start
    write("Offset", offset, "- Elapsed:", elapsed, "ms")
_endloop
```

---

## Troubleshooting

### Issue 1: Module Not Loading
**Symptom:** Module not found or fails to load
**Solutions:**
1. Check `SW_PRODUCTS_PATH` includes `rwi_myrepublic_custom`
2. Verify `product.def` and `module.def` syntax
3. Check `load_list.txt` exists and contains `source/`
4. Restart Smallworld GIS

### Issue 2: Java Class Not Found
**Symptom:** `java.lang.ClassNotFoundException`
**Solutions:**
1. Verify `rwi.astri.integration.jar` exists in `libs/` directory
2. Check `module.def` has `requires_java` section
3. Verify JAR was compiled with Java 17
4. Check JAR contains expected classes: `jar tf rwi.astri.integration.jar`

### Issue 3: Connection Failed
**Symptom:** Cannot connect to API
**Solutions:**
1. Verify API URL is accessible: `ping 172.17.75.22`
2. Check firewall rules
3. Verify network connectivity
4. Test with curl: `curl http://172.17.75.22/astri-api-v2/v4/device/price/list/all -u smallworld:Smallworld@2025!`

### Issue 4: Authentication Failed
**Symptom:** HTTP 401 Unauthorized
**Solutions:**
1. Verify credentials in `astri_config.properties`
2. Check for special characters in password
3. Test credentials with curl
4. Verify Basic Auth header is correctly encoded

### Issue 5: JSON Parsing Error
**Symptom:** Error parsing JSON response
**Solutions:**
1. Check API response format
2. Verify Jackson libraries are loaded
3. Check for non-UTF8 characters in response
4. Log raw response for inspection

### Issue 6: Timeout
**Symptom:** Request times out
**Solutions:**
1. Increase timeout in `astri_config.properties`
2. Check network latency
3. Verify API is responding (test with curl)
4. Check for large response sizes

---

## Dependencies Summary

### Required (Already Available in Smallworld)
- ✅ Java 17 Runtime
- ✅ Vert.x Core 4.5.13
- ✅ Vert.x Web Client 4.5.13
- ✅ Jackson Databind 2.18.3
- ✅ Jackson Core 2.18.3
- ✅ Magik Interop Library
- ✅ Base Smallworld modules

### Required (To Be Created)
- ❌ `rwi.astri.integration.jar` (Java component)
- ❌ Magik source files
- ❌ Configuration files
- ❌ Module and product definitions

### Optional (For Development)
- Maven or Gradle (for Java build)
- Git (for version control)
- Text editor or IDE (for development)

---

## Success Criteria

### Phase 1 Completion Checklist
- [ ] Product directory structure created
- [ ] Java JAR compiled and placed in `libs/`
- [ ] All Magik source files created
- [ ] Module loads without errors
- [ ] Configuration file is readable
- [ ] Work Order API returns data successfully
- [ ] Price List API returns data successfully
- [ ] Query parameters work correctly
- [ ] Basic error handling functions
- [ ] Test scripts execute successfully
- [ ] README documentation complete

### Acceptance Testing
- [ ] Can call Work Order API with limit=10, offset=0
- [ ] Can call Work Order API with category_name filter
- [ ] Can call Work Order API with multiple filters
- [ ] Can call Price List API without filters
- [ ] Can call Price List API with project_type filter
- [ ] JSON responses parse correctly into Magik data structures
- [ ] Error messages are clear and helpful
- [ ] Configuration can be updated without code changes

---

## Next Steps After Plan Approval

1. **Review and Approve Plan** - Stakeholder sign-off
2. **Set Up Development Environment** - Java IDE, Maven, etc.
3. **Implement Java Component** - HTTP client classes
4. **Implement Magik Component** - API wrapper classes
5. **Unit Testing** - Test individual components
6. **Integration Testing** - Test end-to-end flow
7. **Documentation** - Complete README and inline docs
8. **Code Review** - Peer review before deployment
9. **Deployment to Test Environment** - Install and test
10. **User Acceptance Testing** - Validate with end users
11. **Production Deployment** - Deploy to production
12. **Monitor** - Track usage and errors

---

## Appendix A: Example API Responses

### Work Order API Response (Actual)
```json
{
  "success": true,
  "count": 10,
  "count_all": 136,
  "data": [
    {
      "uuid": "f2366e49-602c-4066-bc6d-95978cc8e456",
      "number": "WO/ALL/2025/DOCU/16/53457",
      "appointment_date": "2025-10-14",
      "appointment_slot_name": "slot_14_to_17",
      "appointment_slot_label": "Slot 14 to 17",
      "assigned_vendor_name": "fiberhome",
      "assigned_vendor_label": "Fiberhome",
      "assigned_vendor_sap_vendor_code": "1000000650",
      "assigned_subcont_vendor_name": null,
      "assigned_subcont_vendor_label": null,
      "assigned_subcont_vendor_sap_vendor_code": null,
      "category_name": "cluster_boq",
      "category_label": "Cluster BOQ",
      "target_cluster_code": "JKT005514",
      "target_cluster_name": "RAWA TERATE RW 04",
      "target_cluster_drm_net_type": "AERIAL",
      "target_cluster_drm_homepass": 305,
      "target_cluster_area": "JAKARTA TIMUR",
      "target_cluster_area_plant_code": "3103",
      "target_cluster_topology": "AE",
      "target_cluster_asset_number": null,
      "target_cluster_purchase_requisition_number": null,
      "target_cluster_purchase_order_number": null,
      "target_cluster_latest_ntp_number": "REQ/9476/ROLL OUT/PROCUREMENT/IV/2025",
      "target_cluster_olt_name": "jkt-jtn-olt1-hw",
      "target_cluster_olt_label": "OLT Jatinegara",
      "target_cluster_latest_status": "BOUNDARY APPROVED",
      "target_cluster_total_drm_value": null,
      "target_cluster_cost_per_homepass": null,
      "target_cluster_cost_per_port_aerial": null,
      "target_cluster_cost_per_port_underground": null,
      "target_cluster_cost_per_port": null,
      "target_cluster_total_port_aerial": null,
      "target_cluster_total_port_underground": null,
      "target_cluster_total_drm_value_cable": null,
      "target_cluster_total_drm_value_fdt_fat": null,
      "target_cluster_total_drm_value_olt": null,
      "target_cluster_total_drm_value_other": null,
      "target_cluster_total_drm_value_permit": null,
      "target_cluster_total_drm_value_pole": null,
      "target_cluster_total_drm_value_project_management": null,
      "target_cluster_total_drm_value_pengamanan_and_persiapan": null,
      "target_cluster_total_drm_value_sub_duct": null,
      "target_cluster_total_drm_value_trenching_boring": null,
      "target_cluster_cost_per_homepass_cable": null,
      "target_cluster_cost_per_homepass_fdt_fat": null,
      "target_cluster_cost_per_homepass_olt": null,
      "target_cluster_cost_per_homepass_other": null,
      "target_cluster_cost_per_homepass_permit": null,
      "target_cluster_cost_per_homepass_pole": null,
      "target_cluster_cost_per_homepass_project_management": null,
      "target_cluster_cost_per_homepass_pengamanan_and_persiapan": null,
      "target_cluster_cost_per_homepass_sub_duct": null,
      "target_cluster_cost_per_homepass_trenching_boring": null,
      "target_document_number": null,
      "target_document_uuid": null,
      "target_document_number_verified_by": null,
      "target_document_number_verified_by_username": null,
      "target_document_number_verified_by_fullname": null,
      "target_document_number_verified_at": null,
      "target_document_description": null,
      "target_document_cluster_code": "JKT005514",
      "target_document_cluster_name": "RAWA TERATE RW 04",
      "target_document_cluster_area": "JAKARTA TIMUR",
      "target_document_cluster_drm_net_type": "AERIAL",
      "target_document_cluster_drm_homepass": "305",
      "target_document_cluster_completeness_phase3_status": null,
      "purchase_order_number": null,
      "purchase_requisition_number": null,
      "latest_status_name": "in_progress",
      "latest_status_label": "In Progress",
      "assigned_department_name": "planning",
      "assigned_department_label": "Planning",
      "latest_executor_username": "aditya.syah",
      "latest_executor_fullname": "aditya.syah",
      "is_deleted": "0",
      "created_at": "2025-10-19 12:04:18",
      "closed_at": null,
      "updated_at": "2025-10-20 15:52:52"
    }
  ],
  "logs": {
    "get_cluster_boq": {
      "get": {
        "filters": {
          "latest_status_name": "in_progress",
          "category_name": "cluster_boq",
          "target_cluster_code": "--not null",
          "target_cluster_topology": "--not HRB",
          "appointment_type_name": "document_process",
          "is_deleted": 0
        },
        "limit": 10,
        "offset": 1
      }
    }
  }
}
```

**Response Structure:**
- `success` (boolean) - Indicates if request was successful
- `count` (integer) - Number of records in current response
- `count_all` (integer) - Total number of records matching filter
- `data` (array) - Array of work order objects
- `logs` (object) - Query execution details and applied filters

**Work Order Object Fields (70+ fields):**

*Core Fields:*
- `uuid` - Unique identifier
- `number` - Work order number
- `appointment_date` - Scheduled date
- `appointment_slot_name/label` - Time slot information

*Vendor Assignment:*
- `assigned_vendor_name/label/sap_vendor_code` - Primary vendor
- `assigned_subcont_vendor_name/label/sap_vendor_code` - Subcontractor

*Category & Status:*
- `category_name/label` - Work order category
- `latest_status_name/label` - Current status
- `assigned_department_name/label` - Assigned department

*Target Cluster Information (40+ fields):*
- `target_cluster_code/name/area` - Cluster identification
- `target_cluster_drm_net_type` - Network type (AERIAL/UNDERGROUND)
- `target_cluster_drm_homepass` - Number of home passes
- `target_cluster_topology` - Topology type (AE, etc.)
- `target_cluster_olt_name/label` - OLT information
- `target_cluster_latest_status` - Cluster status
- Cost breakdown fields (per homepass, per port, by category)
- DRM value fields (cable, FDT/FAT, OLT, pole, permit, etc.)

*Document Information:*
- `target_document_*` - Related document fields
- `purchase_order_number` - PO number
- `purchase_requisition_number` - PR number

*Tracking:*
- `latest_executor_username/fullname` - Current assignee
- `is_deleted` - Deletion flag
- `created_at/updated_at/closed_at` - Timestamps

### Price List API Response (Actual)
```json
{
  "success": true,
  "count": 4,
  "count_all": 4,
  "data": [
    {
      "uuid": "2f99684f-a028-4675-bc63-337defdf4f39",
      "project_type": "ALL",
      "vendor_name": "zte_indonesia",
      "vendor_label": "ZTE Indonesia",
      "subcont_vendor_name": null,
      "subcont_vendor_label": null,
      "subcont_vendor_sap_code": null,
      "equipment_name": "1",
      "equipment_label": "Training",
      "unit_price": 100,
      "unit_currency": "IDR",
      "valid_date_start": "2025-01-01",
      "valid_date_end": "2025-01-01",
      "area": "ALL",
      "created_at": "2025-01-31 10:04:29"
    },
    {
      "uuid": "85403496-7f79-43e3-9cbb-9556f079e1d5",
      "project_type": "ALL",
      "vendor_name": "zte_indonesia",
      "vendor_label": "ZTE Indonesia",
      "subcont_vendor_name": null,
      "subcont_vendor_label": null,
      "subcont_vendor_sap_code": null,
      "equipment_name": "1",
      "equipment_label": "Training",
      "unit_price": 500,
      "unit_currency": "IDR",
      "valid_date_start": "2025-01-01",
      "valid_date_end": "2025-01-01",
      "area": "ALL",
      "created_at": "2025-01-31 10:04:28"
    }
  ],
  "logs": {
    "get_price_list": {
      "get": {
        "filters": {
          "valid_date_end": "2025-01-01"
        },
        "limit": null,
        "offset": null,
        "count": 4
      }
    }
  }
}
```

**Response Structure:**
- `success` (boolean) - Indicates if request was successful
- `count` (integer) - Number of records in current response
- `count_all` (integer) - Total number of records matching filter
- `data` (array) - Array of price list objects
- `logs` (object) - Query execution details and applied filters

**Price List Object Fields (15 fields):**

*Core Fields:*
- `uuid` - Unique identifier
- `created_at` - Creation timestamp

*Project & Vendor:*
- `project_type` - Project type (ALL, CLUSTER)
- `vendor_name` - Vendor code
- `vendor_label` - Vendor display name
- `subcont_vendor_name` - Subcontractor code (nullable)
- `subcont_vendor_label` - Subcontractor display name (nullable)
- `subcont_vendor_sap_code` - Subcontractor SAP code (nullable)

*Equipment & Pricing:*
- `equipment_name` - Equipment/device code
- `equipment_label` - Equipment/device display name
- `unit_price` - Price per unit (numeric)
- `unit_currency` - Currency code (e.g., "IDR")

*Validity & Location:*
- `valid_date_start` - Start date for price validity
- `valid_date_end` - End date for price validity
- `area` - Geographic area (e.g., "ALL", specific region)

---

## Appendix B: Work Order Field Reference

### Complete Work Order Object Field List

Based on the actual API response, each work order object contains the following fields:

**Core Identification (8 fields):**
- `uuid` - Unique identifier (String)
- `number` - Work order number (String, e.g., "WO/ALL/2025/DOCU/16/53457")
- `appointment_date` - Appointment date (String, ISO date)
- `appointment_slot_name` - Slot code (String)
- `appointment_slot_label` - Slot display name (String)
- `created_at` - Creation timestamp (String)
- `updated_at` - Last update timestamp (String)
- `closed_at` - Close timestamp (String, nullable)

**Vendor Assignment (6 fields):**
- `assigned_vendor_name` - Vendor code name (String)
- `assigned_vendor_label` - Vendor display name (String)
- `assigned_vendor_sap_vendor_code` - SAP vendor code (String)
- `assigned_subcont_vendor_name` - Subcontractor code (String, nullable)
- `assigned_subcont_vendor_label` - Subcontractor name (String, nullable)
- `assigned_subcont_vendor_sap_vendor_code` - Subcontractor SAP code (String, nullable)

**Category & Status (6 fields):**
- `category_name` - Category code (String)
- `category_label` - Category display (String)
- `latest_status_name` - Status code (String)
- `latest_status_label` - Status display (String)
- `assigned_department_name` - Department code (String)
- `assigned_department_label` - Department display (String)

**Target Cluster - Basic Info (10 fields):**
- `target_cluster_code` - Cluster code (String)
- `target_cluster_name` - Cluster name (String)
- `target_cluster_area` - Area/region (String)
- `target_cluster_area_plant_code` - Plant code (String)
- `target_cluster_drm_net_type` - Network type (String: "AERIAL", "UNDERGROUND")
- `target_cluster_drm_homepass` - Number of home passes (Integer)
- `target_cluster_topology` - Topology type (String: "AE", etc.)
- `target_cluster_olt_name` - OLT code (String)
- `target_cluster_olt_label` - OLT display name (String)
- `target_cluster_latest_status` - Cluster status (String)

**Target Cluster - Purchase/Order Info (4 fields):**
- `target_cluster_asset_number` - Asset number (String, nullable)
- `target_cluster_purchase_requisition_number` - PR number (String, nullable)
- `target_cluster_purchase_order_number` - PO number (String, nullable)
- `target_cluster_latest_ntp_number` - NTP number (String)

**Target Cluster - Cost Analysis (13 fields):**
- `target_cluster_total_drm_value` - Total DRM value (Number, nullable)
- `target_cluster_cost_per_homepass` - Cost per homepass (Number, nullable)
- `target_cluster_cost_per_port` - Cost per port (Number, nullable)
- `target_cluster_cost_per_port_aerial` - Cost per aerial port (Number, nullable)
- `target_cluster_cost_per_port_underground` - Cost per underground port (Number, nullable)
- `target_cluster_total_port_aerial` - Total aerial ports (Number, nullable)
- `target_cluster_total_port_underground` - Total underground ports (Number, nullable)
- `target_cluster_cost_per_homepass_cable` - Cable cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_fdt_fat` - FDT/FAT cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_olt` - OLT cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_pole` - Pole cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_permit` - Permit cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_project_management` - PM cost per homepass (Number, nullable)

**Target Cluster - DRM Values by Category (10 fields):**
- `target_cluster_total_drm_value_cable` - Cable total value (Number, nullable)
- `target_cluster_total_drm_value_fdt_fat` - FDT/FAT total value (Number, nullable)
- `target_cluster_total_drm_value_olt` - OLT total value (Number, nullable)
- `target_cluster_total_drm_value_other` - Other total value (Number, nullable)
- `target_cluster_total_drm_value_permit` - Permit total value (Number, nullable)
- `target_cluster_total_drm_value_pole` - Pole total value (Number, nullable)
- `target_cluster_total_drm_value_project_management` - PM total value (Number, nullable)
- `target_cluster_total_drm_value_pengamanan_and_persiapan` - Security & prep value (Number, nullable)
- `target_cluster_total_drm_value_sub_duct` - Sub-duct total value (Number, nullable)
- `target_cluster_total_drm_value_trenching_boring` - Trenching/boring value (Number, nullable)

**Additional Cost Breakdown (3 fields):**
- `target_cluster_cost_per_homepass_other` - Other cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_pengamanan_and_persiapan` - Security cost (Number, nullable)
- `target_cluster_cost_per_homepass_sub_duct` - Sub-duct cost per homepass (Number, nullable)
- `target_cluster_cost_per_homepass_trenching_boring` - Trenching cost per homepass (Number, nullable)

**Document Information (11 fields):**
- `target_document_number` - Document number (String, nullable)
- `target_document_uuid` - Document UUID (String, nullable)
- `target_document_number_verified_by` - Verifier (String, nullable)
- `target_document_number_verified_by_username` - Verifier username (String, nullable)
- `target_document_number_verified_by_fullname` - Verifier full name (String, nullable)
- `target_document_number_verified_at` - Verification timestamp (String, nullable)
- `target_document_description` - Document description (String, nullable)
- `target_document_cluster_code` - Document cluster code (String)
- `target_document_cluster_name` - Document cluster name (String)
- `target_document_cluster_area` - Document cluster area (String)
- `target_document_cluster_drm_net_type` - Document net type (String)
- `target_document_cluster_drm_homepass` - Document homepass (String)
- `target_document_cluster_completeness_phase3_status` - Phase 3 status (String, nullable)

**Purchase Information (2 fields):**
- `purchase_order_number` - PO number (String, nullable)
- `purchase_requisition_number` - PR number (String, nullable)

**Tracking & Status (3 fields):**
- `latest_executor_username` - Current assignee username (String)
- `latest_executor_fullname` - Current assignee full name (String)
- `is_deleted` - Deletion flag (String: "0" or "1")

**Total: 70+ fields per work order object**

---

## Appendix B1: Price List Field Reference

### Complete Price List Object Field List

Based on the actual API response, each price list object contains the following fields:

**Core Identification (2 fields):**
- `uuid` - Unique identifier (String)
- `created_at` - Creation timestamp (String, ISO format)

**Project & Vendor Information (6 fields):**
- `project_type` - Project type (String: "ALL", "CLUSTER")
- `vendor_name` - Vendor code (String, e.g., "zte_indonesia")
- `vendor_label` - Vendor display name (String, e.g., "ZTE Indonesia")
- `subcont_vendor_name` - Subcontractor code (String, nullable)
- `subcont_vendor_label` - Subcontractor display name (String, nullable)
- `subcont_vendor_sap_code` - Subcontractor SAP code (String, nullable)

**Equipment & Pricing Information (4 fields):**
- `equipment_name` - Equipment/device code (String)
- `equipment_label` - Equipment/device display name (String, e.g., "Training")
- `unit_price` - Price per unit (Number, e.g., 100)
- `unit_currency` - Currency code (String, e.g., "IDR")

**Validity & Location (3 fields):**
- `valid_date_start` - Price validity start date (String, ISO date format: "2025-01-01")
- `valid_date_end` - Price validity end date (String, ISO date format: "2025-01-01")
- `area` - Geographic area/region (String, e.g., "ALL", "JAKARTA")

**Total: 15 fields per price list object**

**Note:** Price list objects are much simpler than work order objects (15 fields vs 70+ fields), making them easier to process and requiring less memory.

---

## Appendix B2: Configuration Reference

### astri_config.properties Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `astri.api.base_url` | String | Yes | - | Base URL for ASTRI API |
| `astri.api.username` | String | Yes | - | Basic auth username |
| `astri.api.password` | String | Yes | - | Basic auth password |
| `astri.api.timeout_ms` | Integer | No | 30000 | Request timeout (milliseconds) |
| `astri.api.connection_timeout_ms` | Integer | No | 10000 | Connection timeout (milliseconds) |

---

## Appendix C: Best Practices for API Data Handling

### Working with Price List Objects

Price list objects are simpler (15 fields) than work orders, but still require proper handling:

**1. Basic Price List Access**
```magik
# Simple iteration through price list
response[:data].fast_elements_do(_proc(price)
    equipment << price[:equipment_label]
    vendor << price[:vendor_label]
    unit_price << price[:unit_price]
    currency << price[:unit_currency]

    write(equipment, " from ", vendor, ": ", unit_price, " ", currency)
_endproc)
```

**2. Handle Nullable Subcontractor Fields**
```magik
# Safely access nullable subcontractor information
response[:data].fast_elements_do(_proc(price)
    vendor << price[:vendor_label]
    subcont << price[:subcont_vendor_label].default("Direct")

    _if price[:subcont_vendor_name] _isnt _unset
    _then
        write("Price from ", vendor, " via ", subcont)
    _else
        write("Price from ", vendor, " (direct)")
    _endif
_endproc)
```

**3. Filter by Date Validity**
```magik
# Find prices valid on a specific date
target_date << "2025-10-15"

valid_prices << response[:data].select(_proc(price)
    # Check if target date is within validity period
    >> price[:valid_date_start] <= target_date _andif
       price[:valid_date_end] >= target_date
_endproc)

write("Found ", valid_prices.size, " valid prices for ", target_date)
```

**4. Group Prices by Vendor**
```magik
# Group prices by vendor for comparison
_method price_list_helper.group_by_vendor(prices)
    ## Group price list by vendor

    vendor_map << hash_table.new()

    prices.fast_elements_do(_proc(price)
        vendor << price[:vendor_name]
        _if vendor_map[vendor] _is _unset
        _then
            vendor_map[vendor] << rope.new()
        _endif
        vendor_map[vendor].add_last(price)
    _endproc)

    >> vendor_map
_endmethod

# Usage
vendor_groups << price_list_helper.group_by_vendor(response[:data])
vendor_groups.fast_keys_and_elements_do(_proc(vendor, prices)
    write("Vendor: ", vendor, " has ", prices.size, " prices")
_endproc)
```

**5. Find Best Price for Equipment**
```magik
# Find lowest price for specific equipment
_method price_list_helper.find_best_price(prices, equipment_name)
    ## Find lowest price for given equipment

    matching_prices << prices.select(_proc(p)
        >> p[:equipment_name] = equipment_name
    _endproc)

    _if matching_prices.empty?
    _then
        >> _unset
    _endif

    best << matching_prices.first
    matching_prices.fast_elements_do(_proc(p)
        _if p[:unit_price] < best[:unit_price]
        _then
            best << p
        _endif
    _endproc)

    >> best
_endmethod
```

**6. Convert to Lookup Table**
```magik
# Create fast lookup table for prices
_method price_list_helper.create_price_lookup(api_response)
    ## Create lookup table: equipment_name -> price info

    lookup << hash_table.new()

    api_response[:data].fast_elements_do(_proc(price)
        key << price[:equipment_name]
        lookup[key] << property_list.new_with(
            :price, price[:unit_price],
            :currency, price[:unit_currency],
            :vendor, price[:vendor_label],
            :valid_start, price[:valid_date_start],
            :valid_end, price[:valid_date_end]
        )
    _endproc)

    >> lookup
_endmethod

# Usage
price_lookup << price_list_helper.create_price_lookup(response)
price_info << price_lookup["1"]  # Get price for equipment "1"
```

---

### Working with Work Order Objects

Given that Work Order objects contain 70+ fields, here are recommended approaches:

**1. Access Only Needed Fields**
```magik
# Instead of processing all fields, access only what you need
response[:data].fast_elements_do(_proc(wo)
    # Essential fields only
    cluster_code << wo[:target_cluster_code]
    cluster_name << wo[:target_cluster_name]
    status << wo[:latest_status_label]
    homepass << wo[:target_cluster_drm_homepass]

    # Process these specific fields
    process_cluster(cluster_code, cluster_name, status, homepass)
_endproc)
```

**2. Create Helper Methods for Common Data Access**
```magik
_method work_order_helper.get_cluster_info(wo)
    ## Extract cluster information from work order
    >> property_list.new_with(
        :code, wo[:target_cluster_code],
        :name, wo[:target_cluster_name],
        :area, wo[:target_cluster_area],
        :topology, wo[:target_cluster_topology],
        :homepass, wo[:target_cluster_drm_homepass],
        :net_type, wo[:target_cluster_drm_net_type]
    )
_endmethod

_method work_order_helper.get_vendor_info(wo)
    ## Extract vendor information from work order
    >> property_list.new_with(
        :vendor, wo[:assigned_vendor_label],
        :vendor_code, wo[:assigned_vendor_sap_vendor_code],
        :subcont, wo[:assigned_subcont_vendor_label]
    )
_endmethod

_method work_order_helper.get_cost_summary(wo)
    ## Extract cost information from work order
    >> property_list.new_with(
        :total_drm, wo[:target_cluster_total_drm_value],
        :cost_per_homepass, wo[:target_cluster_cost_per_homepass],
        :cost_cable, wo[:target_cluster_total_drm_value_cable],
        :cost_fdt, wo[:target_cluster_total_drm_value_fdt_fat],
        :cost_olt, wo[:target_cluster_total_drm_value_olt]
    )
_endmethod
```

**3. Handle Null Values**
```magik
# Many fields can be null - always check before using
response[:data].fast_elements_do(_proc(wo)
    # Safe access with default value
    homepass << wo[:target_cluster_drm_homepass].default(0)
    vendor << wo[:assigned_vendor_label].default("Unassigned")
    subcont << wo[:assigned_subcont_vendor_label].default("None")

    # Conditional processing
    _if wo[:purchase_order_number] _isnt _unset _andif
        wo[:purchase_order_number] _isnt ""
    _then
        process_with_po(wo)
    _endif
_endproc)
```

**4. Filter Response Data Early**
```magik
# Filter for specific conditions immediately after receiving response
_if response[:success] = _true
_then
    # Only process work orders with certain criteria
    aerial_clusters << response[:data].select(_proc(wo)
        >> wo[:target_cluster_drm_net_type] = "AERIAL"
    _endproc)

    # Only process in-progress work orders
    in_progress_wo << response[:data].select(_proc(wo)
        >> wo[:latest_status_name] = "in_progress"
    _endproc)

    # Process filtered results
    aerial_clusters.fast_elements_do(_proc(wo)
        # Process only aerial clusters
    _endproc)
_endif
```

**5. Pagination Strategy**
```magik
# Process large datasets in batches
_method work_order_processor.process_all_work_orders(filters)
    ## Process all work orders matching filters using pagination

    api << astri_work_order_api.new()
    limit << 50  # Reasonable batch size
    offset << 0
    total_processed << 0

    _loop
        response << api.get_work_orders(limit, offset, filters)

        _if response[:success] _isnt _true _orif response[:count] = 0
        _then
            _leave
        _endif

        # Process this batch
        response[:data].fast_elements_do(_proc(wo)
            _self.process_single_work_order(wo)
            total_processed +<< 1
        _endproc)

        write("Processed ", total_processed, " of ", response[:count_all])

        # Check if we've processed all records
        _if offset + limit >= response[:count_all]
        _then
            _leave
        _endif

        offset +<< limit
    _endloop

    >> total_processed
_endmethod
```

**6. Error-Tolerant Field Access**
```magik
# Create a safe field accessor
_method work_order_helper.safe_get(wo, field_name, _optional default)
    ## Safely get field value with optional default
    _local val << wo[field_name]
    _if val _is _unset _orif val = ""
    _then
        >> default.default("N/A")
    _else
        >> val
    _endif
_endmethod

# Usage
cluster_code << work_order_helper.safe_get(wo, :target_cluster_code)
homepass << work_order_helper.safe_get(wo, :target_cluster_drm_homepass, 0)
```

**7. Convert to Domain Objects**
```magik
# Map API response to Smallworld domain objects
_method work_order_mapper.to_cluster_object(wo_data)
    ## Convert work order cluster data to Smallworld cluster object

    cluster << cluster_object.new(
        wo_data[:target_cluster_code],
        wo_data[:target_cluster_name]
    )

    cluster.area << wo_data[:target_cluster_area]
    cluster.topology << wo_data[:target_cluster_topology]
    cluster.homepass << wo_data[:target_cluster_drm_homepass]
    cluster.net_type << wo_data[:target_cluster_drm_net_type]
    cluster.olt_name << wo_data[:target_cluster_olt_label]

    >> cluster
_endmethod
```

### Performance Recommendations

**Recommended Limits:**
- For UI display: 20-50 records per page
- For batch processing: 50-100 records per batch
- For exports: Process in chunks of 100, write incrementally

**Memory Management:**
- Don't load all records at once if count_all > 500
- Process and discard each batch before fetching next
- Clear references to large datasets after processing

**Response Size Estimation:**
- Each work order: ~2-3 KB (70+ fields)
- 100 records: ~200-300 KB
- 1000 records: ~2-3 MB (may cause performance issues)

## Appendix D: Query Parameter Reference

### Work Order API Query Parameters

| Parameter | Type | Example Values | Description |
|-----------|------|----------------|-------------|
| `category_name` | String | `cluster_boq` | Work order category filter |
| `target_cluster_code` | String | `JKT005514`, `JKT005515` | Cluster code filter (exact match) |
| `target_cluster_topology` | String | `AE`, `HRB`, `Ring` | Topology type filter |
| `latest_status_name` | String | `in_progress`, `completed`, `pending` | Current work order status |
| `target_cluster_name` | String | `RAWA TERATE RW 04` | Cluster name filter (may support partial match) |

**Note:** From the API logs, filters can use special syntax:
- `--not null` - Filter for non-null values
- `--not VALUE` - Exclude specific value (e.g., `--not HRB`)

### Price List API Query Parameters

| Parameter | Type | Example Values | Description |
|-----------|------|----------------|-------------|
| `vendor_name` | String | `zte_indonesia`, `fiberhome` | Vendor code filter (exact match) |
| `subcont_vendor_name` | String | `subcont_vendor_code` | Subcontractor code filter |
| `valid_date_end` | String (Date) | `2025-01-01`, `2025-10-17` | Valid end date filter (ISO format: YYYY-MM-DD) |
| `project_type` | String | `ALL`, `CLUSTER` | Project type filter |
| `area` | String | `ALL`, `JAKARTA`, `JAKARTA TIMUR` | Geographic area/region filter |

**Common Values from Sample Data:**
- **project_type:** `ALL`, `CLUSTER`
- **vendor_name:** `zte_indonesia`, `fiberhome`
- **area:** `ALL`, `JAKARTA`, `JAKARTA TIMUR`, etc.
- **valid_date_end:** ISO date format (e.g., `2025-01-01`)

### API Pagination (Work Order Only)

The Work Order API uses path parameters for pagination:

| Parameter | Type | Position | Example | Description |
|-----------|------|----------|---------|-------------|
| `limit` | Integer | Path param 1 | `10`, `50`, `100` | Maximum number of records to return |
| `offset` | Integer | Path param 2 | `0`, `50`, `100` | Number of records to skip |

**Endpoint format:** `/work-order/cluster/boq/simple/list/all/:limit/:offset`

**Examples:**
- First page (10 records): `/work-order/cluster/boq/simple/list/all/10/0`
- Second page (10 records): `/work-order/cluster/boq/simple/list/all/10/10`
- Get 50 records starting from record 100: `/work-order/cluster/boq/simple/list/all/50/100`

**Note:** Price List API does not use pagination parameters in the sample response (limit and offset are null).

---

## Appendix E: API Comparison Summary

### Quick Reference Comparison

| Feature | Work Order API | Price List API |
|---------|----------------|----------------|
| **Endpoint** | `/work-order/cluster/boq/simple/list/all/:limit/:offset` | `/device/price/list/all` |
| **Pagination** | Required (limit, offset in path) | Not used (limit/offset null in logs) |
| **Fields per Object** | 70+ fields | 15 fields |
| **Object Complexity** | High (nested cluster info, costs, DRM values) | Low (flat structure) |
| **Response Size** | ~2-3 KB per record | ~0.5 KB per record |
| **Query Filters** | 5 filters (category, cluster code/name/topology, status) | 5 filters (vendor, subcont, date, project type, area) |
| **Use Case** | Complex work order tracking with extensive cluster details | Simple equipment pricing lookup |
| **Memory Impact** | Medium-High (limit to 50-100 records) | Low (can handle more records) |
| **Recommended Page Size** | 20-50 for UI, 50-100 for batch | All at once or filter by vendor/area |

### Response Structure Consistency

Both APIs share the same response wrapper:
```javascript
{
  "success": true,         // Boolean - success indicator
  "count": 10,            // Integer - records in this response
  "count_all": 136,       // Integer - total matching records
  "data": [...],          // Array - actual data objects
  "logs": {...}           // Object - query execution details
}
```

### Field Naming Patterns

**Both APIs use consistent naming:**
- `_name` suffix for codes (e.g., `vendor_name`, `equipment_name`)
- `_label` suffix for display values (e.g., `vendor_label`, `equipment_label`)
- `uuid` for unique identifiers
- `created_at` for timestamps (ISO format)
- Nullable fields for optional data

### Common Filtering Patterns

**Vendor Information (Both APIs):**
- `vendor_name` - Vendor code
- `vendor_label` - Vendor display name
- `subcont_vendor_name/label` - Subcontractor info (nullable)

**Project Scope:**
- Work Order: Uses `category_name` for classification
- Price List: Uses `project_type` ("ALL" or "CLUSTER")

**Geographic Filtering:**
- Work Order: Uses `target_cluster_area` and cluster-specific fields
- Price List: Uses `area` field directly

### Integration Recommendations

**For Displaying Work Orders:**
1. Use pagination (limit 20-50 per page)
2. Filter by status (`latest_status_name`) for active work
3. Display essential fields only: number, cluster code/name, status, vendor
4. Provide drill-down for full details (70+ fields)

**For Price Lookup:**
1. Load all prices or filter by vendor/project type
2. Cache results (prices change less frequently)
3. Create lookup tables for fast access
4. Group by vendor or equipment type for comparison

**For Combined Use:**
1. Fetch work order to get cluster and vendor info
2. Use vendor from work order to filter price list
3. Match equipment in price list to work order requirements
4. Calculate costs using price list unit prices and work order quantities

---

## Appendix F: KMZ File Format Reference (NEW)

### What is KMZ?

KMZ (Keyhole Markup Zipped) is a compressed file format used for geographic data visualization. It is essentially a ZIP archive containing:
- One or more KML files (Keyhole Markup Language - XML-based)
- Optional supporting files (images, icons, overlays, etc.)

### KMZ Structure

```
cluster_document.kmz (ZIP archive)
├── doc.kml              # Main KML document (required)
├── images/              # Optional image resources
│   ├── icon1.png
│   └── overlay.jpg
└── styles/              # Optional style resources
    └── style.kml
```

**Key Points:**
- KMZ is a standard ZIP file (can be opened with any ZIP tool)
- Contains at least one `.kml` file (usually named `doc.kml`)
- KML uses XML format with geographic markup
- Smaller file size than uncompressed KML (typical compression: 10:1)

### KML Content Structure

KML files contain geographic features in XML format:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Cluster JKT005514</name>
    <description>RAWA TERATE RW 04</description>

    <Placemark>
      <name>Cluster Boundary</name>
      <description>Cluster geographic boundary</description>
      <Polygon>
        <outerBoundaryIs>
          <LinearRing>
            <coordinates>
              106.8950,-6.2146,0
              106.8960,-6.2146,0
              106.8960,-6.2156,0
              106.8950,-6.2156,0
              106.8950,-6.2146,0
            </coordinates>
          </LinearRing>
        </outerBoundaryIs>
      </Polygon>
    </Placemark>

    <Placemark>
      <name>OLT Location</name>
      <Point>
        <coordinates>106.8955,-6.2151,0</coordinates>
      </Point>
    </Placemark>
  </Document>
</kml>
```

### Common KML Elements

**Document Structure:**
- `<Document>` - Root container for features
- `<Folder>` - Group related features
- `<Placemark>` - Individual geographic feature

**Geometry Types:**
- `<Point>` - Single location (e.g., OLT sites)
- `<LineString>` - Path or route (e.g., cable routes, feeders)
- `<Polygon>` - Area boundary (e.g., cluster boundaries)
- `<MultiGeometry>` - Multiple geometries combined

**Coordinate Format:**
- Format: `longitude,latitude,altitude`
- Example: `106.8955,-6.2151,0`
- Altitude is optional (usually 0 for ground-level features)

### ASTRI Document Types and Expected Content

**1. Cluster Documents**
- **Geometry:** Typically Polygons defining cluster boundaries
- **Data:** Cluster code, name, homepass count, area
- **Use Case:** Visualize cluster coverage areas
- **Example Features:**
  - Cluster boundary polygon
  - Cluster center point
  - Service area boundaries

**2. Subfeeder Documents**
- **Geometry:** LineStrings showing cable routes
- **Data:** Subfeeder ID, cable type, length
- **Use Case:** Display distribution cable paths
- **Example Features:**
  - Cable route from FDT to distribution points
  - Connection points along route

**3. Feeder Documents**
- **Geometry:** LineStrings showing main cable paths
- **Data:** Feeder ID, cable specifications, capacity
- **Use Case:** Display main fiber backbone
- **Example Features:**
  - Main trunk cable from OLT
  - Feeder cable segments
  - Splitter locations

**4. OLT Site Documents**
- **Geometry:** Points marking site locations
- **Data:** OLT name, address, capacity
- **Use Case:** Display central office / OLT locations
- **Example Features:**
  - OLT site point with icon
  - Coverage radius polygon

### KMZ Processing in Implementation

**Java Layer Processing:**
```java
// 1. Download KMZ as binary
byte[] kmzData = downloadFromApi(url);

// 2. Save KMZ file
Files.write(kmzPath, kmzData);

// 3. Extract KML from KMZ (ZIP)
try (ZipInputStream zis = new ZipInputStream(new FileInputStream(kmzFile))) {
    ZipEntry entry;
    while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().endsWith(".kml")) {
            // Found KML file
            String kmlContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(kmlPath, kmlContent);
            break;
        }
    }
}

// 4. Return both file paths and KML content
```

**Magik Layer Usage:**
```magik
# Download and extract
result << api.download_cluster_document(uuid)

# Access extracted KML content
kml_content << result[:kml_content]  # XML string

# Parse KML for import to Smallworld GIS
# (Implementation depends on GIS import requirements)
```

### File Size Considerations

**Typical Sizes:**
- **Cluster KMZ:** 10-50 KB (simple boundary polygon)
- **Subfeeder KMZ:** 50-200 KB (cable routes with multiple segments)
- **Feeder KMZ:** 100-500 KB (long routes, multiple cables)
- **OLT Site KMZ:** 5-20 KB (point location with icon)

**Compression Ratios:**
- KML (uncompressed): 100%
- KMZ (compressed): ~10-15% of original KML size
- Example: 500 KB KML → 50 KB KMZ

### Integration with Smallworld GIS

**Potential Uses:**
1. **Import as Graphics** - Display KML geometry in map views
2. **Create Database Objects** - Convert KML to Smallworld topology
3. **Overlay Visualization** - Show ASTRI data over existing GIS data
4. **Export Comparison** - Compare ASTRI data with Smallworld design

**Import Considerations:**
- KML coordinates are WGS84 (EPSG:4326) - may need coordinate transformation
- Smallworld may have existing KML import utilities
- May need custom parser to extract attributes from KML

### Validation Recommendations

**KMZ Validation:**
- Check file is valid ZIP archive
- Verify at least one `.kml` file exists
- Check file size is reasonable (< 10 MB)

**KML Validation:**
- Verify valid XML structure
- Check for required KML namespace
- Verify coordinates are valid (longitude: -180 to 180, latitude: -90 to 90)
- Check geometry types are supported

### Error Scenarios

**Common Issues:**
1. **Corrupted KMZ** - ZIP extraction fails
2. **No KML in KMZ** - Archive doesn't contain KML file
3. **Invalid KML XML** - Malformed XML structure
4. **Missing Coordinates** - Geometry has no coordinate data
5. **Invalid Coordinate Format** - Wrong number format or order
6. **Unsupported Features** - KML features not compatible with target system

**Error Handling Strategy:**
- Validate ZIP integrity before extraction
- Check for KML file existence
- Validate XML structure
- Provide clear error messages for each failure type

### Reference Resources

**Standards:**
- KML 2.2 Specification: http://www.opengeospatial.org/standards/kml
- KML Tutorial: https://developers.google.com/kml/documentation/kml_tut

**Tools:**
- Google Earth - View/edit KML/KMZ files
- QGIS - GIS software with KML support
- Any ZIP tool - Extract KMZ to examine contents

---

**Document Version:** 1.2
**Last Updated:** 2025-10-23 (Added KMZ Document Download API specifications)
**Created:** 2025-10-23
**Status:** UPDATED - Ready for Review and Approval
**Next Action:** Review KMZ download additions, approve full plan, then begin implementation
