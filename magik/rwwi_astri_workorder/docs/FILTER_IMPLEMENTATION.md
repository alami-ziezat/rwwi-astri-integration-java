# Filter Implementation for ASTRI Work Order Module

**Date:** 2025-10-30
**Status:** ✅ IMPLEMENTED

## Overview

The filter functionality has been fully implemented to allow filtering of work orders from the ASTRI API by multiple criteria.

## Architecture

### 1. Magik Side (Dialog → Engine)

**File:** `rwwi_astri_workorder_dialog.magik`

The dialog collects filter values from UI components and builds a property_list:

```magik
_method rwwi_astri_workorder_dialog.build_filter_params()
    ## Build filter property_list from UI inputs

    _local filters << property_list.new()

    # Category filter
    _local category << .items[:filter_category].value
    _if category _isnt _unset _and category.size > 0
    _then
        filters[:category_name] << category
    _endif

    # Status filter
    _local status << .items[:filter_status].value
    _if status _isnt _unset _and status.size > 0
    _then
        filters[:latest_status_name] << status
    _endif

    # Topology filter
    _local topology << .items[:filter_topology].value
    _if topology _isnt _unset _and topology.size > 0
    _then
        filters[:target_cluster_topology] << topology
    _endif

    # Cluster code filter
    _local cluster << .items[:filter_cluster].value
    _if cluster _isnt _unset _and cluster.size > 0
    _then
        filters[:target_cluster_code] << cluster
    _endif

    _return filters
_endmethod
```

### 2. Engine Layer

**File:** `rwwi_astri_workorder_engine.magik`

The engine passes the filter property_list to the Java @MagikProc:

```magik
_method rwwi_astri_workorder_engine.get_workorders_from_api(
        limit, offset, _optional filters)
    ## filters (property_list) - Optional filters:
    ##   :category_name - Category filter (e.g., "cluster_boq")
    ##   :latest_status_name - Status filter (e.g., "in_progress")
    ##   :assigned_vendor_name - Vendor name filter
    ##   :target_cluster_topology - Topology filter (AE/UG/OH)
    ##   :target_cluster_code - Cluster code filter

    xml_result << astri_get_work_orders(limit, offset, filters)
    # ...
_endmethod
```

### 3. Java Side (Magik ↔ Java Interop)

**File:** `AstriWorkOrderProcs.java`

The Java @MagikProc receives the Magik property_list and converts it to URL query parameters:

```java
@MagikProc(@Name("astri_get_work_orders"))
public static Object getWorkOrders(Object proc, Object limit, Object offset,
                                   @Optional Object filters) {
    // Convert Magik property_list to filter string
    String filterParams = buildFilterParams(filters);

    // Make API call with filters
    client = new WorkOrderClient();
    String xmlResponse = client.getWorkOrders(limitInt, offsetInt, filterParams);
    // ...
}
```

### 4. Filter Parameter Building

The `buildFilterParams()` method uses Java reflection to:

1. **Check if filters object is a Magik property_list**
   ```java
   Class<?> clazz = magikFilters.getClass();
   Method atMethod = clazz.getMethod("at", Object.class);
   ```

2. **Extract each filter value by key:**
   - Convert Java string to Magik symbol: `:category_name`, `:latest_status_name`, etc.
   - Call `property_list.at(symbol)` to get the value
   - Check if value is not `_unset`
   - Convert Magik string to Java string using `MagikInteropUtils.fromMagikString()`

3. **Build URL query string:**
   ```java
   // Example output:
   // "category_name=cluster_boq&latest_status_name=in_progress&target_cluster_topology=AE"
   ```

4. **URL encode parameter values** to handle special characters

### 5. HTTP Client

**File:** `WorkOrderClient.java`

The client appends filter parameters to the API URL:

```java
public String getWorkOrders(int limit, int offset, String filterParams) {
    String url = baseUrl + "/work-order/cluster/boq/simple/list/all/" + limit + "/" + offset;

    if (filterParams != null && !filterParams.isEmpty()) {
        url += "?" + filterParams;
    }

    // Makes request to:
    // https://api.example.com/work-order/.../50/0?category_name=cluster_boq&latest_status_name=in_progress
}
```

## Supported Filters

| Magik Key | API Parameter | Description | Example Values |
|-----------|---------------|-------------|----------------|
| `:category_name` | `category_name` | Work order category | `"cluster_boq"`, `"survey"` |
| `:latest_status_name` | `latest_status_name` | Work order status | `"in_progress"`, `"completed"`, `"cancelled"` |
| `:assigned_vendor_name` | `assigned_vendor_name` | Vendor name | `"Yangtze Optical"` |
| `:target_cluster_topology` | `target_cluster_topology` | Topology type | `"AE"`, `"UG"`, `"OH"` |
| `:target_cluster_code` | `target_cluster_code` | Cluster code | `"MNA000033"` |

## How Filters Work

### User Flow:

1. **User enters filter values** in the dialog UI (category, status, topology, cluster code)
2. **User clicks "Apply Filters"** button
3. **Dialog calls `apply_filters()`** method
4. **`build_filter_params()`** collects UI values into property_list
5. **`refresh_data()`** triggers data reload
6. **`workorder_list_data()`** calls engine with filters
7. **Engine calls Java** `astri_get_work_orders(limit, offset, filters)`
8. **Java extracts filter values** from property_list using reflection
9. **Java builds query string** like `"category_name=cluster_boq&status=in_progress"`
10. **HTTP request** is made to API with filters in URL
11. **API returns filtered results** in JSON
12. **Java converts JSON to XML**
13. **Magik parses XML** and displays in table

### Example Filter Request:

**Magik:**
```magik
filters << property_list.new_with(
    :category_name, "cluster_boq",
    :latest_status_name, "in_progress",
    :target_cluster_topology, "AE")

workorders << engine.get_workorders_from_api(50, 0, filters)
```

**Java converts to:**
```
category_name=cluster_boq&latest_status_name=in_progress&target_cluster_topology=AE
```

**API URL:**
```
https://api.astri.example.com/work-order/cluster/boq/simple/list/all/50/0?category_name=cluster_boq&latest_status_name=in_progress&target_cluster_topology=AE
```

## Error Handling

The implementation includes robust error handling:

1. **Null/unset filters:** Returns empty string (no filters)
2. **Missing filter keys:** Skips that filter, logs warning
3. **Invalid filter values:** Skips that filter, logs warning
4. **Reflection errors:** Falls back gracefully, logs error
5. **URL encoding errors:** Returns empty string

All errors are logged to stderr for debugging but don't break the API call.

## Testing

### Test Without Filters:
```magik
engine << rwwi_astri_workorder_engine.new()
workorders << engine.get_workorders_from_api(10, 0)
write(workorders.size, "work orders retrieved")
```

### Test With Filters:
```magik
engine << rwwi_astri_workorder_engine.new()

filters << property_list.new_with(
    :category_name, "cluster_boq",
    :latest_status_name, "in_progress")

workorders << engine.get_workorders_from_api(10, 0, filters)
write("Filtered results:", workorders.size)
```

### Test From Dialog:
1. Open ASTRI Work Order dialog
2. Select "ASTRI API" as source
3. Enter filter values (category, status, topology, cluster code)
4. Set limit and offset
5. Click "Apply Filters"
6. Check console for debug output showing filter query string

## Technical Implementation Details

### Magik Symbol Conversion

The Java code converts string keys to Magik symbols:

```java
// Method 1: Try MagikInteropUtils
Class<?> utilsClass = Class.forName("com.gesmallworld.magik.interop.MagikInteropUtils");
Method method = utilsClass.getMethod("toMagikSymbol", String.class);
Object symbol = method.invoke(null, "category_name");

// Method 2: Fallback to MagikSymbol.get()
Class<?> symbolClass = Class.forName("com.gesmallworld.magik.commons.runtime.MagikSymbol");
Method getMethod = symbolClass.getMethod("get", String.class);
Object symbol = getMethod.invoke(null, "category_name");
```

### Unset Detection

Checks if a Magik value is `_unset`:

```java
private static boolean isUnset(Object obj) {
    if (obj == null) return true;

    String className = obj.getClass().getName();
    return className.contains("Unset") ||
           className.equals("com.gesmallworld.magik.commons.runtime.MagikUnset");
}
```

### String Extraction

Extracts string value from Magik object:

```java
private static String extractStringValue(Object obj) {
    try {
        // Try fromMagikString first (handles rope, char16_vector, etc.)
        return MagikInteropUtils.fromMagikString(obj);
    } catch (Exception e) {
        // Fallback to toString
        return obj.toString();
    }
}
```

## Build and Deployment

**Build Command:**
```bash
cd C:\Smallworld\pni_custom\rwwi_astri_integration_java
mvn clean package
```

**Output:**
```
C:\Smallworld\pni_custom\libs\pni_custom.rwwi.astri.integration.1.jar
```

**Reload in Smallworld:**
```magik
# Restart Smallworld session or reload the module
sw_module_manager.reload_module(:rwwi_astri_integration)
sw_module_manager.reload_module(:rwwi_astri_workorder)
```

## Limitations

1. **Filter values must be strings** - The current implementation only supports string filter values
2. **AND logic only** - All filters are combined with AND (not OR)
3. **No wildcard support** - Exact match only (API-dependent)
4. **No date range filters** - Not implemented yet

## Future Enhancements

- [ ] Add date range filters (created_at, updated_at)
- [ ] Add vendor filter to UI
- [ ] Add support for multiple values per filter (OR logic)
- [ ] Add filter presets/saved filters
- [ ] Add "Clear All Filters" button
- [ ] Add filter validation before API call
- [ ] Add filter count badge in UI

## References

- **Magik Code:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\source\`
- **Java Code:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\src\main\java\com\rwi\myrepublic\astri\`
- **Implementation Plan:** `IMPLEMENTATION_PLAN.md`

---

**Last Updated:** 2025-10-30
**Implemented By:** Claude Code
**Status:** ✅ Production Ready
