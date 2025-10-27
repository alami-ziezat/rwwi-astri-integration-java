# RWI ASTRI Integration - Implementation Plan
**Date:** 2025-10-27 (Updated)
**Status:** ✅ IMPLEMENTED AND ACTIVE
**Architecture:** Proper Magik-Java Interop using @MagikProc Annotations
**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\`

---

## TABLE OF CONTENTS

1. [Implemented APIs Summary](#implemented-apis-summary)
2. [API Reference](#api-reference)
3. [Critical Learnings](#critical-learnings-from-interop_demo-source-code)
4. [Architecture](#revised-architecture-annotation-based-interop)
5. [Implementation Details](#java-implementation-details)
6. [Testing](#testing-plan)

---

## IMPLEMENTED APIS SUMMARY

As of 2025-10-27, the following APIs have been implemented and are available as global Magik procedures:

### Work Order APIs (3)
- `astri_get_work_orders()` - Get list of work orders with pagination and filters
- `astri_get_work_order()` - Get single work order by UUID
- `astri_update_work_order()` - Update work order status (NEW)

### Price List API (1)
- `astri_get_price_list()` - Get equipment price list with optional filters

### KMZ Download APIs (4)
- `astri_download_cluster_kmz()` - Download cluster KMZ/KML document
- `astri_download_subfeeder_kmz()` - Download subfeeder KMZ/KML document
- `astri_download_feeder_kmz()` - Download feeder KMZ/KML document
- `astri_download_olt_site_kmz()` - Download OLT site KMZ/KML document

### Vendor API (1 - NEW)
- `astri_get_vendor_list()` - Get vendor list with pagination and filters

### BOQ DRM API (1 - NEW)
- `astri_add_boq_drm_cluster()` - Add BOQ DRM cluster data

### OLT Rollout API (1 - NEW)
- `astri_get_olt_list()` - Get OLT rollout list with pagination and filters

**Total:** 12 Magik procedures exposed via @MagikProc annotations

---

## API REFERENCE

### 1. Work Order APIs

#### astri_get_work_orders(limit, offset, _optional filters)

**Description:** Retrieves a paginated list of work orders from the ASTRI API.

**Parameters:**
- `limit` (integer) - Maximum number of records to retrieve
- `offset` (integer) - Starting offset for pagination
- `filters` (property_list, optional) - Filter parameters:
  - `:category_name` - Category filter
  - `:latest_status_name` - Status filter
  - `:assigned_vendor_name` - Vendor filter
  - `:target_cluster_topology` - Topology filter
  - `:target_cluster_code` - Cluster code filter

**Returns:** JSON string containing work order data

**Java Implementation:** `AstriWorkOrderProcs.java` → `WorkOrderClient.java`

**API Endpoint:** `POST /v4/work-order/list/all/{limit}/{offset}`

**Example:**
```magik
# Basic call
json_result << astri_get_work_orders(10, 0)

# With filters
filters << property_list.new_with(:category_name, "cluster_boq")
json_result << astri_get_work_orders(20, 0, filters)
```

---

#### astri_get_work_order(uuid)

**Description:** Retrieves a single work order by UUID.

**Parameters:**
- `uuid` (string) - Work order UUID

**Returns:** JSON string containing work order details

**Java Implementation:** `AstriWorkOrderProcs.java` → `WorkOrderClient.java`

**API Endpoint:** `POST /v4/work-order/view/{uuid}`

**Example:**
```magik
uuid << "example-uuid-here"
json_result << astri_get_work_order(uuid)
```

---

#### astri_update_work_order(number, latest_status_name, detail) **[NEW]**

**Description:** Updates a work order's status and detail information.

**Parameters:**
- `number` (string, required) - Work order number
- `latest_status_name` (string, required) - New status name
- `detail` (string, required) - Update detail/description

**Returns:** JSON string containing update response

**Java Implementation:** `AstriWorkOrderUpdateProcs.java` → `WorkOrderUpdateClient.java`

**API Endpoint:** `PUT /v4/work-order/update`

**Example:**
```magik
json_result << astri_update_work_order("WO-001", "In Progress", "Work started")
```

---

### 2. Price List API

#### astri_get_price_list(_optional filters)

**Description:** Retrieves equipment price list from ASTRI API.

**Parameters:**
- `filters` (property_list, optional) - Filter parameters:
  - `:project_type` - Project type filter
  - `:vendor_name` - Vendor filter
  - `:equipment_name` - Equipment filter
  - `:valid_date_start` - Start date filter
  - `:valid_date_end` - End date filter

**Returns:** JSON string containing price list data

**Java Implementation:** `AstriPriceListProcs.java` → `PriceListClient.java`

**API Endpoint:** `POST /v4/price/list/all`

**Example:**
```magik
json_result << astri_get_price_list()
```

---

### 3. KMZ Download APIs

#### astri_download_cluster_kmz(uuid, _optional output_dir)

**Description:** Downloads cluster KMZ document, extracts KML, and returns file path.

**Parameters:**
- `uuid` (string) - Document UUID
- `output_dir` (string, optional) - Output directory path (defaults to config setting)

**Returns:** String - KML file path or empty string on error

**Java Implementation:** `AstriKmzDownloadProcs.java` → `KmzDownloadClient.java`

**API Endpoint:** `GET /v4/document-management/cluster/download/{uuid}`

**Example:**
```magik
uuid << "2989eecc-3fd0-402c-bd81-99e02caa7ef5"
kml_path << astri_download_cluster_kmz(uuid)
```

---

#### astri_download_subfeeder_kmz(uuid, _optional output_dir)

**Description:** Downloads subfeeder KMZ document.

**Parameters:** Same as `astri_download_cluster_kmz()`

**Returns:** XML string with download results

**Java Implementation:** `AstriKmzDownloadProcs.java` → `KmzDownloadClient.java`

**API Endpoint:** `GET /v4/document-management/subfeeder/download/{uuid}`

---

#### astri_download_feeder_kmz(uuid, _optional output_dir)

**Description:** Downloads feeder KMZ document.

**Parameters:** Same as `astri_download_cluster_kmz()`

**Returns:** XML string with download results

**Java Implementation:** `AstriKmzDownloadProcs.java` → `KmzDownloadClient.java`

**API Endpoint:** `GET /v4/document-management/feeder/download/{uuid}`

---

#### astri_download_olt_site_kmz(uuid, _optional output_dir)

**Description:** Downloads OLT site KMZ document.

**Parameters:** Same as `astri_download_cluster_kmz()`

**Returns:** XML string with download results

**Java Implementation:** `AstriKmzDownloadProcs.java` → `KmzDownloadClient.java`

**API Endpoint:** `GET /v4/document-management/olt-site/download/{uuid}`

---

### 4. Vendor API **[NEW]**

#### astri_get_vendor_list(limit, offset, _optional name, _optional subcont_vendor_name, _optional label, _optional sap_vendor_code)

**Description:** Retrieves a paginated list of vendors from ASTRI API.

**Parameters:**
- `limit` (integer) - Maximum number of records to retrieve
- `offset` (integer) - Starting offset for pagination
- `name` (string, optional) - Vendor name filter
- `subcont_vendor_name` (string, optional) - Subcontractor vendor name filter
- `label` (string, optional) - Label filter
- `sap_vendor_code` (string, optional) - SAP vendor code filter

**Returns:** JSON string containing vendor list data

**Java Implementation:** `AstriVendorProcs.java` → `VendorClient.java`

**API Endpoint:** `POST /v4/vendor/list/all/{limit}/{offset}?[filters]`

**Example:**
```magik
# Basic call
json_result << astri_get_vendor_list(10, 0)

# With filters
json_result << astri_get_vendor_list(10, 0, "PT Test", _unset, _unset, _unset)
```

---

### 5. BOQ DRM API **[NEW]**

#### astri_add_boq_drm_cluster(cluster_code, vendor_name, subcont_vendor_name, equipment_name, description, quantity_material, quantity_service, remarks, phase, area, area_plant_code, override_price_material, override_price_service)

**Description:** Adds a new BOQ (Bill of Quantities) DRM cluster entry.

**Parameters:**
- `cluster_code` (string) - Cluster code
- `vendor_name` (string) - Vendor name
- `subcont_vendor_name` (string) - Subcontractor vendor name
- `equipment_name` (string) - Equipment name
- `description` (string) - Description
- `quantity_material` (integer) - Material quantity
- `quantity_service` (integer) - Service quantity
- `remarks` (string) - Remarks
- `phase` (string) - Phase
- `area` (string) - Area
- `area_plant_code` (string) - Area plant code
- `override_price_material` (integer) - Override material price
- `override_price_service` (integer) - Override service price

**Returns:** JSON string containing operation result

**Java Implementation:** `AstriBoqProcs.java` → `BoqClient.java`

**API Endpoint:** `POST /v4/osp/cluster/boq/add`

**Example:**
```magik
json_result << astri_add_boq_drm_cluster(
    "CLUSTER-001",
    "PT Vendor",
    "PT Subcont",
    "Equipment",
    "Description",
    100,
    50,
    "Remarks",
    "Phase 1",
    "Area 1",
    "AREA-001",
    1000000,
    500000
)
```

---

### 6. OLT Rollout API **[NEW]**

#### astri_get_olt_list(limit, offset, _optional device_code, _optional name, _optional label)

**Description:** Retrieves a paginated list of OLT (Optical Line Terminal) rollout devices.

**Parameters:**
- `limit` (integer) - Maximum number of records to retrieve
- `offset` (integer) - Starting offset for pagination
- `device_code` (string, optional) - Device code filter
- `name` (string, optional) - Name filter
- `label` (string, optional) - Label filter

**Returns:** JSON string containing OLT list data

**Java Implementation:** `AstriOltProcs.java` → `OltClient.java`

**API Endpoint:** `POST /v4/olt/rollout/list/all/{limit}/{offset}?[filters]`

**Example:**
```magik
# Basic call
json_result << astri_get_olt_list(10, 0)

# With filters
json_result << astri_get_olt_list(10, 0, _unset, "Test OLT", _unset)
```

---

## CRITICAL LEARNINGS FROM interop_demo SOURCE CODE

After studying the complete source code at `C:\Smallworld\core\interop.demo\src\`, I've identified the **correct patterns**:

### Key Discoveries:

1. **MagikVectorUtils.getObjectArray()** - Converts Magik vectors to Java Object[]
   ```java
   Object[] magikArray = MagikVectorUtils.getObjectArray(graphData);
   for (Object innerObj : magikArray) {
       Object[] inner = MagikVectorUtils.getObjectArray(innerObj);
   }
   ```

2. **MagikInteropUtils conversion methods**:
   - `MagikInteropUtils.fromMagikString(obj)` - Magik string → Java String
   - `MagikInteropUtils.fromMagikInteger(obj)` - Magik integer → Java int
   - These handle Magik wrapper objects automatically

3. **Helper classes are NOT annotated**:
   - `CustomBarChart`, `CustomLineChart`, `CustomPieChart` have NO annotations
   - They're plain Java classes used internally
   - Only the `@MagikProc` and `@MagikExemplar` classes are exposed

4. **Return values**:
   - Procs can return `null` (becomes `_unset` in Magik)
   - Procs can return Java objects (like JFugue Pattern)
   - Procs can return simple types (String, int, etc.)

5. **Pattern for nested structures**:
   - Magik `{{10,5},{11,6}}` → `getObjectArray()` twice
   - Each level unwraps one dimension

### TWO IMPLEMENTATION APPROACHES:

#### **Approach A: Return JSON Strings (Simpler)**
- Java procs return JSON strings
- Magik parses JSON using existing JSON parser
- Less Java code, more Magik code
- Pattern:
  ```java
  @MagikProc(@Name("astri_get_work_orders"))
  public static Object getWorkOrders(Object proc, Object limit, Object offset) {
      return "{\"success\":true,\"count\":10,\"data\":[...]}";
  }
  ```

#### **Approach B: Return Java Collections (More Complex)**
- Java procs return `Map<String, Object>` or `List<Map>`
- Interop framework converts to Magik property_list/rope automatically
- More Java code, less Magik code
- Pattern:
  ```java
  @MagikProc(@Name("astri_get_work_orders"))
  public static Object getWorkOrders(Object proc, Object limit, Object offset) {
      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      result.put("count", 10);
      return result; // Auto-converts to property_list in Magik
  }
  ```

**RECOMMENDATION: Use Approach A (JSON Strings)**
- Simpler Java implementation
- Leverage existing Magik JSON parsing
- Less complex type conversion
- Follows REST API pattern

---

## CRITICAL ANALYSIS OF PREVIOUS APPROACH

### What Was Wrong?

The previous implementation (v1.0) had **fundamental architectural flaws**:

1. **❌ WRONG: Manual Java Class Access**
   - Used `com.rwi.myrepublic.astri.AstriConfig.getInstance()` directly in Magik
   - Created manual wrapper classes (`astri_config`, `astri_api_client`)
   - Required complex Java-to-Magik conversion code
   - Did NOT use Smallworld's interop annotation system

2. **❌ WRONG: No Annotation-Based Registration**
   - Java classes were plain POJOs without `@MagikProc` or `@MagikExemplar`
   - No automatic Magik procedure/method generation
   - Manual method wrapping in Magik code

3. **❌ WRONG: Complex Data Conversion**
   - Manual JSON parsing using Jackson
   - Hand-written recursive Java-to-Magik converters
   - Manually converting property_list to HashMap

4. **❌ WRONG: Architecture Pattern**
   - Followed a "Java library with Magik wrapper" pattern
   - Should have followed "Annotated Java methods AS Magik procs/methods" pattern

### What the Interop Demo Shows (CORRECT Approach)

**Key Discovery:** The interop demo uses **annotations** to automatically expose Java methods as Magik procedures and exemplars:

```java
@MagikProc(@Name("draw_pie_chart"))
public static Object drawPieChartProc(Object proc, Object data, Object chartName) {
    // Java implementation
}
```

This automatically creates a **global Magik procedure** `draw_pie_chart()` that can be called directly:

```magik
draw_pie_chart({{"magik", 80}, {"Java", 20}}, "chart", "window")
```

**No wrapper code needed!** The interop framework handles everything.

Similarly for exemplars:

```java
@MagikExemplar(@Name("fugue_player"))
public class FugueProcs {
    @MagikMethod("new()")
    public static Object _new(Object self) { ... }

    @MagikMethod("play()")
    public final void play(@Optional Object param) { ... }
}
```

Creates a Magik exemplar:

```magik
player << fugue_player.new()
player.play("C D E F G")
```

---

## REVISED ARCHITECTURE: ANNOTATION-BASED INTEROP

### Design Principles

1. ✅ **Use `@MagikProc` for global procedures** (API calls)
2. ✅ **Use `@MagikExemplar` for exemplars** (API clients with state)
3. ✅ **Let Smallworld handle type conversion** via `MagikInteropUtils`
4. ✅ **Minimal Magik code** - just convenient wrappers if needed
5. ✅ **No manual JSON parsing** - return Java objects, let interop convert
6. ✅ **Use standard Java HTTP client** - Vert.x WebClient (already available)

### Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  LAYER 1: MAGIK CALLING CODE (User Code)                   │
│  - Call annotated procs/methods directly                   │
│  - Example: result << astri_get_work_orders(10, 0)        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LAYER 2: JAVA INTEROP METHODS (Annotated)                 │
│  - @MagikProc annotated static methods                     │
│  - @MagikExemplar annotated classes                        │
│  - Auto-registered as Magik procs/exemplars                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LAYER 3: JAVA IMPLEMENTATION (Pure Java)                  │
│  - HTTP client logic (Vert.x WebClient)                    │
│  - API configuration                                        │
│  - Response parsing                                         │
└─────────────────────────────────────────────────────────────┘
```

---

## DELIVERABLES

### Directory Structure (Current Implementation)

```
C:\Smallworld\pni_custom\
├── modules\
│   └── rwwi_astri_integration\          # Module name
│       ├── module.def                    # Module definition
│       ├── load_list.txt                 # Load order
│       ├── source\
│       │   ├── load_list.txt            # Source load order
│       │   ├── test_astri_procs.magik   # Test procedures (16 tests)
│       │   ├── astri_kml_parser.magik   # KML parser
│       │   └── astri_kmz_handler.magik  # KMZ handler
│       └── resources\
│           └── astri_config.properties   # Configuration file
├── libs\
│   └── pni_custom.rwwi.astri.integration.1.jar  # Built JAR (Active)
└── rwwi_astri_integration_java\          # Java source (for building)
    ├── pom.xml                           # Maven build config
    └── src\main\java\com\rwi\myrepublic\astri\
        ├── AstriConfig.java              # Configuration singleton
        ├── AstriWorkOrderProcs.java      # @MagikProc for work orders (2 APIs)
        ├── AstriWorkOrderUpdateProcs.java # @MagikProc for WO update [NEW]
        ├── AstriPriceListProcs.java      # @MagikProc for price list
        ├── AstriKmzDownloadProcs.java    # @MagikProc for KMZ download (4 APIs)
        ├── AstriVendorProcs.java         # @MagikProc for vendors [NEW]
        ├── AstriBoqProcs.java            # @MagikProc for BOQ DRM [NEW]
        ├── AstriOltProcs.java            # @MagikProc for OLT rollout [NEW]
        └── internal\
            ├── WorkOrderClient.java      # Internal HTTP client
            ├── WorkOrderUpdateClient.java # Internal HTTP client [NEW]
            ├── PriceListClient.java      # Internal HTTP client
            ├── KmzDownloadClient.java    # Internal HTTP client
            ├── VendorClient.java         # Internal HTTP client [NEW]
            ├── BoqClient.java            # Internal HTTP client [NEW]
            └── OltClient.java            # Internal HTTP client [NEW]
```

**Total Java Files:** 15 (8 @MagikProc classes + 7 internal clients + AstriConfig)

### Module Location: pni_custom Product

**CRITICAL:** Module will be created under `pni_custom` product, NOT as a separate product.

**Rationale:**
1. `pni_custom` is the top-level custom product already loaded
2. `pni_custom/module.def` already has `requires` list for custom modules
3. No need to modify `SW_PRODUCTS_PATH` environment variable
4. Simpler deployment and testing
5. Follows existing project pattern (rwi_kml_data_loader, rwwi_migration_tools, etc.)

---

## JAVA IMPLEMENTATION DETAILS

### 1. Configuration Class (AstriConfig.java)

**Purpose:** Singleton to load configuration from properties file

```java
package com.rwi.myrepublic.astri;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AstriConfig {
    private static AstriConfig instance;
    private Properties props;

    private AstriConfig() {
        props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("astri_config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // Use defaults
        }
    }

    public static AstriConfig getInstance() {
        if (instance == null) {
            instance = new AstriConfig();
        }
        return instance;
    }

    public String getApiBaseUrl() {
        return props.getProperty("astri.api.base.url",
            "http://172.17.75.22/astri-api-v2/v4");
    }

    public String getDmBaseUrl() {
        return props.getProperty("astri.dm.base.url",
            "http://172.17.75.22/astri-dm/v4");
    }

    public String getUsername() {
        return props.getProperty("astri.username", "smallworld");
    }

    public String getPassword() {
        return props.getProperty("astri.password", "Smallworld@2025!");
    }

    public long getRequestTimeout() {
        return Long.parseLong(props.getProperty("astri.timeout.request", "30000"));
    }

    public long getConnectionTimeout() {
        return Long.parseLong(props.getProperty("astri.timeout.connection", "10000"));
    }

    public String getDownloadDir() {
        return props.getProperty("astri.download.dir", "downloads");
    }
}
```

**Key Points:**
- Singleton pattern
- Loads from classpath resource
- Provides sensible defaults
- No Magik exposure needed (used internally only)

---

### 2. Work Order Procedures (AstriWorkOrderProcs.java)

**Purpose:** Expose Work Order API as global Magik procedures using `@MagikProc`

```java
package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.WorkOrderClient;

import java.util.HashMap;
import java.util.Map;

public class AstriWorkOrderProcs {

    /**
     * Get work orders from ASTRI API
     *
     * @param proc The Magik proc object (always first parameter)
     * @param limit Number of records to fetch (Magik integer)
     * @param offset Starting offset (Magik integer)
     * @param filters Optional property_list - can be null if not provided
     * @return String - JSON response from API (will be parsed in Magik)
     */
    @MagikProc(@Name("astri_get_work_orders"))
    public static Object getWorkOrders(Object proc, Object limit, Object offset,
                                       @Optional Object filters) {
        WorkOrderClient client = null;
        try {
            // Convert Magik integers to Java int
            int limitInt = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);

            // Convert Magik property_list to Java Map (if provided)
            Map<String, String> filterMap = new HashMap<>();
            if (filters != null) {
                // Magik property_list can be iterated as Map in Java
                // The interop framework handles this automatically
                filterMap = convertMagikMapToJavaMap(filters);
            }

            // Create client and make API call
            client = new WorkOrderClient();
            String jsonResponse = client.getWorkOrders(limitInt, offsetInt, filterMap);

            // Return JSON string - Magik will parse it
            return jsonResponse;

        } catch (Exception e) {
            // Return error as JSON string
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * Get single work order by UUID
     *
     * @param proc The Magik proc object
     * @param uuid Work order UUID (Magik string)
     * @return String - JSON response from API
     */
    @MagikProc(@Name("astri_get_work_order"))
    public static Object getWorkOrder(Object proc, Object uuid) {
        WorkOrderClient client = null;
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);

            client = new WorkOrderClient();
            String jsonResponse = client.getWorkOrder(uuidStr);

            return jsonResponse;

        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * Helper to convert Magik property_list/Map to Java HashMap
     *
     * Magik property_list with Symbol keys can be passed to Java.
     * The keys will come as Magik Symbol objects, values as Magik objects.
     */
    private static Map<String, String> convertMagikMapToJavaMap(Object magikMap) {
        Map<String, String> javaMap = new HashMap<>();

        // Note: The actual implementation will iterate the Magik map
        // This is a placeholder showing the pattern
        // Real implementation will use reflection or Magik interop APIs

        return javaMap;
    }
}
```

**CRITICAL INSIGHT FROM INTEROP DEMO:**
The demo returns **simple types** (null, String, Java objects) and lets the calling code handle complexity. We should return JSON strings and parse them in Magik, OR return Java Maps that interop converts automatically to property_lists.

**Key Points:**
- `@MagikProc` creates global Magik procedures automatically
- First parameter is always the proc object
- Use `@Optional` for optional parameters
- Use `MagikInteropUtils` for type conversion
- Return Java Map - interop will convert to property_list in Magik
- No need for Magik wrapper code!

---

### 3. Price List Procedures (AstriPriceListProcs.java)

**Purpose:** Expose Price List API as global Magik procedures

```java
package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.PriceListClient;

import java.util.HashMap;
import java.util.Map;

public class AstriPriceListProcs {

    /**
     * Get price list from ASTRI API
     *
     * @param proc The Magik proc object
     * @param filters Optional property_list of filter parameters
     * @return Java Map with keys: :success, :count, :count_all, :data, :error
     */
    @MagikProc(@Name("astri_get_price_list"))
    public static Object getPriceList(Object proc, @Optional Object filters) {
        try {
            // Convert filters
            Map<String, String> filterMap = new HashMap<>();
            if (filters != null) {
                filterMap = convertMagikPropsToJavaMap(filters);
            }

            // Call internal client
            PriceListClient client = new PriceListClient();
            Map<String, Object> result = client.getPriceList(filterMap);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    private static Map<String, String> convertMagikPropsToJavaMap(Object magikProps) {
        return new HashMap<>();
    }
}
```

---

### 4. KMZ Download Procedures (AstriKmzDownloadProcs.java)

**Purpose:** Expose KMZ document download as global Magik procedures

```java
package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.KmzDownloadClient;

import java.util.HashMap;
import java.util.Map;

public class AstriKmzDownloadProcs {

    /**
     * Download cluster KMZ document
     *
     * @param proc The Magik proc object
     * @param uuid Document UUID (Magik string)
     * @param outputDir Optional output directory (Magik string)
     * @return Java Map with keys: :success, :kmz_file_path, :kml_file_path,
     *                             :kml_content, :error
     */
    @MagikProc(@Name("astri_download_cluster_kmz"))
    public static Object downloadClusterKmz(Object proc, Object uuid,
                                            @Optional Object outputDir) {
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                MagikInteropUtils.fromMagikString(outputDir) : null;

            KmzDownloadClient client = new KmzDownloadClient();
            Map<String, Object> result = client.downloadClusterDocument(uuidStr, dirStr);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    @MagikProc(@Name("astri_download_subfeeder_kmz"))
    public static Object downloadSubfeederKmz(Object proc, Object uuid,
                                              @Optional Object outputDir) {
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                MagikInteropUtils.fromMagikString(outputDir) : null;

            KmzDownloadClient client = new KmzDownloadClient();
            Map<String, Object> result = client.downloadSubfeederDocument(uuidStr, dirStr);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    @MagikProc(@Name("astri_download_feeder_kmz"))
    public static Object downloadFeederKmz(Object proc, Object uuid,
                                           @Optional Object outputDir) {
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                MagikInteropUtils.fromMagikString(outputDir) : null;

            KmzDownloadClient client = new KmzDownloadClient();
            Map<String, Object> result = client.downloadFeederDocument(uuidStr, dirStr);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    @MagikProc(@Name("astri_download_olt_site_kmz"))
    public static Object downloadOltSiteKmz(Object proc, Object uuid,
                                            @Optional Object outputDir) {
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                MagikInteropUtils.fromMagikString(outputDir) : null;

            KmzDownloadClient client = new KmzDownloadClient();
            Map<String, Object> result = client.downloadOltSiteDocument(uuidStr, dirStr);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }
}
```

---

### 5. Internal HTTP Clients (Pure Java - No Annotations)

These are internal implementation classes - NOT exposed to Magik:

#### WorkOrderClient.java

```java
package com.rwi.myrepublic.astri.internal;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import com.rwi.myrepublic.astri.AstriConfig;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Base64;

public class WorkOrderClient {
    private Vertx vertx;
    private WebClient client;
    private AstriConfig config;

    public WorkOrderClient() {
        this.config = AstriConfig.getInstance();
        this.vertx = Vertx.vertx();

        WebClientOptions options = new WebClientOptions()
            .setConnectTimeout((int)config.getConnectionTimeout())
            .setIdleTimeout((int)config.getRequestTimeout());

        this.client = WebClient.create(vertx, options);
    }

    public Map<String, Object> getWorkOrders(int limit, int offset,
                                             Map<String, String> filters) {
        // Implementation using Vert.x WebClient
        // Parse JSON response
        // Return as Map
    }

    public Map<String, Object> getWorkOrder(String uuid) {
        // Implementation
    }

    private String getAuthHeader() {
        String credentials = config.getUsername() + ":" + config.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    public void close() {
        if (client != null) client.close();
        if (vertx != null) vertx.close();
    }
}
```

#### PriceListClient.java

Similar structure to WorkOrderClient.

#### KmzDownloadClient.java

Similar structure but handles binary file download and ZIP extraction.

---

## MAGIK IMPLEMENTATION DETAILS

### 1. Module Definition (module.def)

```
rwwi_astri_integration 1

description
	ASTRI API integration using proper Magik-Java interop.
	Provides global procedures for Work Order API, Price List API, and KMZ downloads.
end

requires
	base
end

requires_java
	rwwi.astri.integration
end

language en_gb
```

**Key Points:**
- Module name: `rwwi_astri_integration` (double 'w' to match existing naming)
- `requires_java rwwi.astri.integration` - matches Bundle-SymbolicName in JAR
- No product definition - module goes under `pni_custom`

---

### 2. Load List (load_list.txt)

```
source/load_list.txt
```

### 3. Source Load List (source/load_list.txt)

```
test_astri_procs.magik
```

---

### 4. Test Procedures (test_astri_procs.magik)

```magik
#% text_encoding = iso8859_1

_package sw
$

## Test procedures for ASTRI API integration
## These demonstrate how to call the annotated Java procedures

_pragma(classify_level=debug, topic={astri_integration})
_global test_astri_work_orders << _proc()
	## Test Work Order API

	write("=" * 60)
	write("Testing ASTRI Work Order API")
	write("=" * 60)

	# Call the @MagikProc directly - no wrapper needed!
	result << astri_get_work_orders(10, 0)

	_if result[:success] = _true
	_then
		write("SUCCESS! Retrieved ", result[:count], " work orders")
		write("Total available: ", result[:count_all])

		# Display first work order
		_if result[:data].size > 0
		_then
			wo << result[:data][1]
			write("")
			write("First Work Order:")
			write("  UUID: ", wo[:uuid])
			write("  Number: ", wo[:number])
			write("  Cluster: ", wo[:target_cluster_code])
			write("  Status: ", wo[:latest_status_label])
		_endif
	_else
		write("FAILED: ", result[:error])
	_endif

	write("")
_endproc
$

_pragma(classify_level=debug, topic={astri_integration})
_global test_astri_work_orders_filtered << _proc()
	## Test Work Order API with filters

	write("=" * 60)
	write("Testing ASTRI Work Order API with Filters")
	write("=" * 60)

	# Create filter property_list
	filters << property_list.new_with(
		:category_name, "cluster_boq",
		:latest_status_name, "in_progress",
		:target_cluster_topology, "AE")

	write("Filters: ", filters)
	write("")

	# Call with filters
	result << astri_get_work_orders(20, 0, filters)

	_if result[:success] = _true
	_then
		write("SUCCESS! Filtered results: ", result[:count], " work orders")

		_for wo _over result[:data].fast_elements()
		_loop
			write("WO: ", wo[:number], " | ", wo[:target_cluster_code])
		_endloop
	_else
		write("FAILED: ", result[:error])
	_endif

	write("")
_endproc
$

_pragma(classify_level=debug, topic={astri_integration})
_global test_astri_price_list << _proc()
	## Test Price List API

	write("=" * 60)
	write("Testing ASTRI Price List API")
	write("=" * 60)

	# Call the @MagikProc directly
	result << astri_get_price_list()

	_if result[:success] = _true
	_then
		write("SUCCESS! Retrieved ", result[:count], " price records")

		# Display first few prices
		_for i, price _over result[:data].fast_elements().numbered_elements()
		_loop
			_if i > 5 _then _leave _endif

			write("Equipment: ", price[:equipment_label])
			write("  Price: ", price[:unit_price], " ", price[:unit_currency])
			write("  Vendor: ", price[:vendor_label])
			write("")
		_endloop
	_else
		write("FAILED: ", result[:error])
	_endif

	write("")
_endproc
$

_pragma(classify_level=debug, topic={astri_integration})
_global test_astri_kmz_download << _proc()
	## Test KMZ Download

	write("=" * 60)
	write("Testing ASTRI KMZ Download")
	write("=" * 60)

	uuid << "f2366e49-602c-4066-bc6d-95978cc8e456"

	# Call the @MagikProc directly
	result << astri_download_cluster_kmz(uuid)

	_if result[:success] = _true
	_then
		write("SUCCESS! Downloaded KMZ file")
		write("  KMZ Path: ", result[:kmz_file_path])
		write("  KML Path: ", result[:kml_file_path])
		write("  KML Size: ", result[:kml_content].size, " bytes")
	_else
		write("FAILED: ", result[:error])
	_endif

	write("")
_endproc
$

_pragma(classify_level=debug, topic={astri_integration})
_global test_all_astri_apis << _proc()
	## Run all ASTRI API tests

	write("")
	write("#" * 60)
	write("## ASTRI API Integration Test Suite (v2.0)")
	write("#" * 60)
	write("")

	test_astri_work_orders()
	test_astri_work_orders_filtered()
	test_astri_price_list()
	test_astri_kmz_download()

	write("#" * 60)
	write("## All tests completed")
	write("#" * 60)
	write("")
_endproc
$
```

**Key Points:**
- **NO exemplar definitions** - just test procedures
- **Calls annotated procs directly**: `astri_get_work_orders(10, 0)`
- **No manual conversion** - interop handles it automatically
- **Simple and clean** - much less code than v1.0

---

## MAVEN BUILD CONFIGURATION

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.rwi.myrepublic</groupId>
    <artifactId>astri-integration</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>RWI ASTRI Integration v2</name>
    <description>ASTRI API integration using proper Magik-Java interop</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <vertx.version>4.5.13</vertx.version>
    </properties>

    <dependencies>
        <!-- Vert.x Web Client - provided by Smallworld -->
        <dependency>
            <groupId>io.vertx</groupId>
            <artifactId>vertx-web-client</artifactId>
            <version>${vertx.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- Vert.x Core - provided by Smallworld -->
        <dependency>
            <groupId>io.vertx</groupId>
            <artifactId>vertx-core</artifactId>
            <version>${vertx.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- Magik Interop Annotations - provided by Smallworld -->
        <dependency>
            <groupId>com.gesmallworld.magik</groupId>
            <artifactId>magik-interop</artifactId>
            <version>5.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Maven Compiler Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>

            <!-- Maven JAR Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <outputDirectory>../libs</outputDirectory>
                    <finalName>pni_custom.rwwi.astri.integration.1</finalName>
                    <archive>
                        <manifestEntries>
                            <Bundle-ManifestVersion>2</Bundle-ManifestVersion>
                            <Bundle-Name>RWI ASTRI Integration</Bundle-Name>
                            <Bundle-SymbolicName>rwwi.astri.integration</Bundle-SymbolicName>
                            <Bundle-Version>1.0.0</Bundle-Version>
                            <Bundle-Activator>com.gesmallworld.magik.interop.JavaToMagikActivator</Bundle-Activator>
                            <Import-Package>com.gesmallworld.magik.interop;version="[1.0,2)",com.gesmallworld.magik.commons.interop.annotations;version="[1.0,2)"</Import-Package>
                            <Export-Package>com.rwi.myrepublic.astri</Export-Package>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>

            <!-- Maven Resources Plugin - Copy config to resources -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.0</version>
                <executions>
                    <execution>
                        <id>copy-resources</id>
                        <phase>process-resources</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${project.build.outputDirectory}</outputDirectory>
                            <resources>
                                <resource>
                                    <directory>../modules/rwwi_astri_integration/resources</directory>
                                    <includes>
                                        <include>astri_config.properties</include>
                                    </includes>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Key Points:**
- Output JAR name: `pni_custom.rwwi.astri.integration.1.jar`
- Bundle-SymbolicName: `rwwi.astri.integration` (matches module.def)
- Uses `JavaToMagikActivator` (required for interop annotations)
- Copies `astri_config.properties` into JAR classpath

---

## CONFIGURATION FILE

### astri_config.properties

```properties
# ASTRI API Configuration

# API Base URLs
astri.api.base.url=http://172.17.75.22/astri-api-v2/v4
astri.dm.base.url=http://172.17.75.22/astri-dm/v4

# Authentication
astri.username=smallworld
astri.password=Smallworld@2025!

# Timeouts (milliseconds)
astri.timeout.request=30000
astri.timeout.connection=10000

# KMZ Download
astri.download.dir=downloads
```

**Location:** `modules/rwwi_astri_integration/resources/astri_config.properties`

---

## UPDATE pni_custom/module.def

Add the new module to the requires list:

```
pni_custom 1

description
	Top level module for site specific custom image builds. Load modules as requirements of this module.
end

requires
	# The top of this list details GE SW Physical NI product modules

	pni_base # Top level Physical NI product
	pni_default_address_system
	# mit_dxf                    # DXF translator
	# pni_custom_config		   # Contains configurable application glue files
	# pni_api

	# All custom modules should be added below this line
	mclib_kml_export 4000
	mclib_kml_import 4100
	rwi_kml_data_loader
	rwwi_migration_tools
	rwwi_apd_tools
	rwwi_ring_viewer
	rwi_astri_integration        # OLD - remove this
	rwwi_astri_integration       # NEW - add this

end
```

---

## COMPARISON: V1.0 vs V2.0 (Current)

| Aspect | v1.0 (WRONG) | v2.0 (CURRENT) |
|--------|--------------|----------------|
| **Java Classes** | 5 classes | 15 classes |
| **Magik Files** | 6 files | 3 files |
| **Lines of Magik** | ~618 lines | ~500 lines (includes tests) |
| **Magik Procedures** | N/A | 12 global procedures |
| **Architecture** | Manual wrappers | Annotation-based |
| **JSON Parsing** | Manual Jackson code | Return JSON strings |
| **Type Conversion** | Manual recursive converter | `MagikInteropUtils` |
| **Magik API** | Exemplar-based OOP | Global procedures |
| **HTTP Client** | Manual | Java 11+ HttpClient |
| **API Coverage** | 8 APIs | 12 APIs |
| **Complexity** | High | Low |
| **Maintenance** | Difficult | Easy |
| **Follows Smallworld Pattern** | ❌ No | ✅ Yes |
| **Status** | Deprecated | ✅ Active |

---

## ADVANTAGES OF V2.0 APPROACH

### 1. **Follows Official Smallworld Pattern**
   - Uses same approach as `interop_demo`
   - Annotation-based registration
   - Automatic type conversion

### 2. **Simpler Magik Code**
   - No exemplar definitions needed
   - No manual type conversion
   - Just call procedures directly

### 3. **Cleaner Java Code**
   - Clear separation: annotated procs vs internal implementation
   - Standard Java patterns
   - Easier to test

### 4. **Better Performance**
   - Interop framework handles conversion efficiently
   - No manual JSON parsing overhead
   - Direct method invocation

### 5. **Easier Maintenance**
   - Less code to maintain
   - Standard patterns
   - Clear structure

### 6. **Better Error Handling**
   - Return structured Maps
   - Interop converts to property_list automatically
   - Consistent error format

---

## IMPLEMENTATION PHASES

### Phase 1: Core Infrastructure (APPROVE BEFORE EXECUTION)
**Goal:** Set up module structure and configuration

**Tasks:**
1. Create module directory structure under `pni_custom/modules/rwwi_astri_integration/`
2. Create `module.def` with `requires_java rwwi.astri.integration`
3. Create `load_list.txt` files
4. Create `astri_config.properties` in resources directory
5. Update `pni_custom/module.def` to include new module

**Verification:**
- Directory structure exists
- Module definition files are valid
- Configuration file has correct values

---

### Phase 2: Java Implementation (APPROVE BEFORE EXECUTION)
**Goal:** Implement annotated Java classes

**Tasks:**
1. Create `rwwi_astri_integration_java/pom.xml` with correct manifest
2. Implement `AstriConfig.java` (configuration singleton)
3. Implement `AstriWorkOrderProcs.java` with `@MagikProc` annotations
4. Implement `AstriPriceListProcs.java` with `@MagikProc` annotations
5. Implement `AstriKmzDownloadProcs.java` with `@MagikProc` annotations
6. Implement internal clients:
   - `internal/WorkOrderClient.java`
   - `internal/PriceListClient.java`
   - `internal/KmzDownloadClient.java`

**Verification:**
- Java code compiles without errors
- JAR builds successfully
- JAR manifest has correct entries
- JAR contains all classes

---

### Phase 3: Magik Test Procedures (APPROVE BEFORE EXECUTION)
**Goal:** Create simple test procedures

**Tasks:**
1. Create `test_astri_procs.magik` with test procedures
2. Test procedures call annotated procs directly
3. Format output clearly
4. Handle errors gracefully

**Verification:**
- Magik code compiles without errors
- Test procedures are accessible

---

### Phase 4: Build and Integration (APPROVE BEFORE EXECUTION)
**Goal:** Build JAR and integrate with Smallworld

**Tasks:**
1. Run Maven build: `mvn clean package`
2. Verify JAR created: `pni_custom.rwwi.astri.integration.1.jar`
3. Load module in Smallworld
4. Run test procedures
5. Verify API calls work

**Verification:**
- Module loads without errors
- Annotated procs are available as globals
- API calls return data
- Error handling works

---

## SUCCESS CRITERIA

Phase 1 (Module Structure):
- ✅ Module directory exists under pni_custom
- ✅ module.def has correct requires_java
- ✅ Configuration file is readable
- ✅ pni_custom/module.def updated

Phase 2 (Java Implementation):
- ✅ All 7 Java classes compile
- ✅ JAR builds with correct manifest
- ✅ JAR naming follows convention: `pni_custom.rwwi.astri.integration.1.jar`
- ✅ Bundle-SymbolicName matches: `rwwi.astri.integration`
- ✅ Bundle-Activator is: `JavaToMagikActivator`

Phase 3 (Magik):
- ✅ test_astri_procs.magik compiles
- ✅ Test procedures are simple and clean
- ✅ No manual type conversion code

Phase 4 (Integration):
- ✅ Module loads: `sw_module_manager.load_module(:rwwi_astri_integration)`
- ✅ Global procs exist: `astri_get_work_orders`, `astri_get_price_list`, etc.
- ✅ API calls return property_list results
- ✅ Work Order API works
- ✅ Price List API works
- ✅ KMZ Download API works
- ✅ Filters work correctly
- ✅ Error handling works

---

## TESTING PLAN

### Unit Testing (During Development)

1. **Test Java classes independently**
   - AstriConfig loads properties
   - HTTP clients connect to API
   - Response parsing works

2. **Test Magik procedures**
   - Procedures are registered
   - Type conversion works
   - Return values are correct

### Integration Testing (After Build)

1. **Load module test**
   ```magik
   sw_module_manager.load_module(:rwwi_astri_integration)
   ```

2. **Basic API test**
   ```magik
   result << astri_get_work_orders(10, 0)
   write(result)
   ```

3. **Filtered query test**
   ```magik
   filters << property_list.new_with(:category_name, "cluster_boq")
   result << astri_get_work_orders(20, 0, filters)
   ```

4. **Price list test**
   ```magik
   result << astri_get_price_list()
   ```

5. **KMZ download test**
   ```magik
   result << astri_download_cluster_kmz("f2366e49-602c-4066-bc6d-95978cc8e456")
   ```

6. **Run full test suite**
   ```magik
   test_all_astri_apis()
   ```

---

## KNOWN LIMITATIONS

Same as v1.0, but with clearer documentation:

1. **Synchronous API calls** - May block Magik session during requests
2. **No connection pooling** - Each call creates new HTTP client
3. **Basic error handling** - Simple error messages
4. **No caching** - Every call hits API
5. **No rate limiting** - Could overwhelm API
6. **Plain text credentials** - Stored in properties file
7. **HTTP only** - No HTTPS support (API limitation)
8. **No file size checks** - Large KMZ files may cause issues

**Note:** These are acceptable for Phase 1. Future phases will address them.

---

## FUTURE ENHANCEMENTS (Phase 2+)

1. **Async API calls** using Vert.x promises
2. **Connection pooling** for better performance
3. **Response caching** with TTL
4. **Rate limiting** protection
5. **Encrypted credentials** using Smallworld's credential store
6. **Progress callbacks** for large downloads
7. **Batch operations** for multiple work orders
8. **WebSocket support** for real-time updates

---

## ROLLBACK PLAN

If v2.0 fails:

1. **Keep old implementation** (rwi_astri_integration) available
2. **Switch back** by updating pni_custom/module.def
3. **Remove new module** from requires list
4. **No data loss** - configuration compatible

---

## APPROVAL CHECKLIST

Before implementation begins, confirm:

- [ ] Architecture approved (annotation-based interop)
- [ ] Module location approved (under pni_custom, not separate product)
- [ ] Module naming approved (rwwi_astri_integration)
- [ ] JAR naming approved (pni_custom.rwwi.astri.integration.1.jar)
- [ ] Magik API approved (global procedures, not exemplars)
- [ ] Java structure approved (annotated procs + internal clients)
- [ ] Configuration approach approved (properties in JAR classpath)
- [ ] Testing plan approved
- [ ] Phase 1 tasks approved for execution

---

## QUESTIONS FOR APPROVAL

1. **Module naming:** Accept `rwwi_astri_integration` (double 'w')? Or prefer different name?

2. **Magik API style:** Accept global procedures (`astri_get_work_orders()`)? Or prefer exemplar-based?

3. **Error handling:** Return property_list with `:success` and `:error` keys? Or different pattern?

4. **Configuration:** Accept properties file in JAR? Or prefer external file?

5. **Should we keep old implementation** (rwi_astri_integration) temporarily?

---

## REFERENCES

- Smallworld Interop Demo: `C:\Smallworld\core\interop.demo\`
- Interop Documentation: (refer to Smallworld 5.x docs)
- Example JAR: `magik.interop.demo.jar`
- Example Module: `interop_demo_module`

---

**END OF PLAN**

**Status:** AWAITING APPROVAL - DO NOT EXECUTE

**Next Action:** Review plan, answer approval questions, then approve Phase 1 for execution.

---

---

## CHANGE LOG

### 2025-10-27
- **Directory Rename:** `java_src_v2` renamed to `rwwi_astri_integration_java` for better clarity
- Added 4 new APIs: Work Order Update, Vendor List, BOQ DRM, OLT Rollout
- Added comprehensive test procedures for all APIs
- Updated documentation with complete API reference

---

## IMPLEMENTATION STATUS (2025-10-27)

### ✅ Phase 1: Core Infrastructure - COMPLETED
- Module directory structure created
- Configuration files created
- Module definition files validated

### ✅ Phase 2: Java Implementation - COMPLETED
All 15 Java classes implemented and compiled successfully:
- 8 @MagikProc annotated classes
- 7 Internal HTTP client classes
- 1 Configuration class

### ✅ Phase 3: Magik Test Procedures - COMPLETED
Test file created with 16 test procedures covering all 12 APIs:
- `test_astri_work_orders()` - List work orders
- `test_astri_work_order_single()` - Single work order
- `test_astri_price_list()` - Price list
- `test_astri_kmz_download_cluster()` - Cluster KMZ
- `test_astri_kmz_download_subfeeder()` - Subfeeder KMZ
- `test_astri_kmz_download_feeder()` - Feeder KMZ
- `test_astri_kmz_download_olt_site()` - OLT Site KMZ
- `test_astri_kml_parser()` - KML parser
- `test_astri_vendor_list()` - Vendor list
- `test_astri_vendor_list_with_filters()` - Vendor list with filters
- `test_astri_add_boq_drm()` - Add BOQ DRM
- `test_astri_update_work_order()` - Update work order
- `test_astri_olt_list()` - OLT list
- `test_astri_olt_list_with_filters()` - OLT list with filters
- `test_all_astri_apis()` - Run all tests

### ✅ Phase 4: Build and Integration - COMPLETED
- JAR built successfully: `pni_custom.rwwi.astri.integration.1.jar`
- Module loadable in Smallworld
- All 12 global procedures available
- API calls functional

---

## RECENTLY ADDED APIS (2025-10-27)

### 1. Work Order Update API
**Implementation:** `AstriWorkOrderUpdateProcs.java` + `WorkOrderUpdateClient.java`
- Uses PUT method (different from other APIs)
- JSON request body with 3 required fields
- Returns JSON response

### 2. Vendor List API
**Implementation:** `AstriVendorProcs.java` + `VendorClient.java`
- POST method with pagination and optional filters
- Query parameters: name, subcont_vendor_name, label, sap_vendor_code
- Returns JSON response

### 3. BOQ DRM Cluster API
**Implementation:** `AstriBoqProcs.java` + `BoqClient.java`
- POST method with 13 parameters (mix of strings and integers)
- JSON request body with all parameters
- Manual JSON body building for performance
- Returns JSON response

### 4. OLT Rollout List API
**Implementation:** `AstriOltProcs.java` + `OltClient.java`
- POST method with pagination and optional filters
- Query parameters: device_code, name, label
- Returns JSON response

All new APIs follow the established pattern:
- @MagikProc annotation for Magik exposure
- Internal Client class for HTTP operations
- Java 11+ HttpClient for REST calls
- Basic Authentication
- JSON request/response format
- Error handling with empty string or JSON error response

---

## API BASE URL CONFIGURATION

All APIs use the base URL configured in `astri_config.properties`:

```
astri.api.base.url=http://172.17.75.22/astri-api-v2/v4
astri.dm.base.url=http://172.17.75.22/astri-dm/v4
```

### API Endpoints Summary

| API Type | Base URL | Endpoints |
|----------|----------|-----------|
| Work Orders | API v4 | `/work-order/list/all/{limit}/{offset}`, `/work-order/view/{uuid}`, `/work-order/update` |
| Price List | API v4 | `/price/list/all` |
| Vendor | API v4 | `/vendor/list/all/{limit}/{offset}` |
| BOQ DRM | API v4 | `/osp/cluster/boq/add` |
| OLT Rollout | API v4 | `/olt/rollout/list/all/{limit}/{offset}` |
| Document Management | DM v4 | `/document-management/{type}/download/{uuid}` |

---

---

**Document:** RWI ASTRI Integration Implementation Plan
**Created:** 2025-10-23
**Updated:** 2025-10-27
**Author:** Claude Code
**Status:** ✅ IMPLEMENTED AND ACTIVE
**Location:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\RWI_ASTRI_INTEGRATION_PLAN.md`
