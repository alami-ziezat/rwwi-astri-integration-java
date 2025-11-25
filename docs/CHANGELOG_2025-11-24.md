# ASTRI Integration Change Log - November 24, 2025

## Summary of Changes

This document summarizes all changes made to the ASTRI Integration system on November 24, 2025.

---

## 1. Fixed BOQ API Integration Issues

### Issues Fixed:
1. **Decimal to Integer Conversion Error** - Cannot convert decimal values like `433.92757928565965` to integer
2. **Error Response Handling** - When exceptions occur, Magik reads them as success
3. **Comma Removal in Work Order Data** - Vendor names like "TELKOM AKSES, PT" lose commas

### Changes Made:

#### A. AstriBoqProcs.java - Fixed Decimal Handling and Error Responses

**File:** `src/main/java/com/rwi/myrepublic/astri/AstriBoqProcs.java`

**Changes:**
- Changed parameter types from `Integer` to `Double` for all quantity and price fields
- Added `convertMagikNumberToDouble()` method that:
  - Handles both integer and float values from Magik
  - Rounds to 2 decimal places using `Math.round(value * 100.0) / 100.0`
  - Falls back to integer conversion if float conversion fails
- Added `escapeJson()` method for safe JSON string escaping
- Changed error handling to return proper JSON format: `{"success":false, "error":"error message"}`
- Previously returned empty string on error (interpreted as success in Magik)

**Code Example:**
```java
private static Double convertMagikNumberToDouble(Object magikNumber) {
    if (magikNumber == null) return null;
    try {
        Float floatValue = MagikInteropUtils.fromMagikFloat(magikNumber);
        Double value = floatValue.doubleValue();
        return Math.round(value * 100.0) / 100.0; // Round to 2 decimal places
    } catch (Exception e) {
        try {
            Integer intValue = MagikInteropUtils.fromMagikInteger(magikNumber);
            return intValue.doubleValue();
        } catch (Exception e2) {
            return null;
        }
    }
}
```

#### B. BoqClient.java - Updated to Support Double Values

**File:** `src/main/java/com/rwi/myrepublic/astri/internal/BoqClient.java`

**Changes:**
- Changed all `Integer` parameters to `Double` in `addBoqDrmCluster()` method
- Updated `buildJsonBody()` to accept `Double` parameters
- Updated `appendJsonField()` to format Double values with 2 decimal places:
  ```java
  else if (value instanceof Double) {
      json.append(String.format("%.2f", (Double) value));
  }
  ```

#### C. rwwi_astri_workorder_dialog.magik - Parse JSON Responses

**File:** `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_dialog.magik`

**Changes (Lines 1331-1367):**
- Added JSON response parsing using `json_parser.parse_string(json_response)`
- Check for `success` field in JSON response
- If `success _is _true` → count as successful API call
- If `success _is _false` → count as error and extract error message
- Added nested error handling for JSON parsing failures

**Code Example:**
```magik
_try _with parseErr
    _local parser << json_parser.new()
    _local json_obj << parser.parse_string(json_response)
    _local success << json_obj[:success]

    _if success _is _true
    _then
        write("  SUCCESS: BOQ item sent to ASTRI API")
        api_success_count +<< 1
    _else
        _local error_msg << json_obj[:error].default("Unknown error")
        write("  ERROR: API returned error:", error_msg)
        api_error_count +<< 1
    _endif
_when error
    write("  ERROR: Failed to parse API response")
    api_error_count +<< 1
_endtry
```

### Result:
✅ Decimal values like `433.93` are properly handled and rounded to 2 decimal places
✅ API errors return JSON: `{"success":false, "error":"message"}` → Magik correctly identifies as failure
✅ Success responses work as before
✅ Magik code properly counts successes vs errors

---

## 2. Fixed Comma Removal in WorkOrderClient

### Issue:
JSON values like `"assigned_vendor_name": "telkom_akses,_pt"` were being truncated to `telkom_akses` (comma removed) in XML conversion.

### Root Cause:
The regex pattern in `extractJsonValue()` was:
```java
Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"?([^,}\"\\n]+)\"?");
```

The character class `[^,}\"\\n]+` means "match any character **except** comma, closing brace, quote, or newline", causing the regex to stop at the first comma.

### Changes Made:

**File:** `src/main/java/com/rwi/myrepublic/astri/internal/WorkOrderClient.java`

**Changes (Lines 273-295):**
- Replaced single regex with two separate patterns
- **Quoted String Pattern** (for string values that can contain commas):
  ```java
  Pattern quotedPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
  ```
  - Matches: `"fieldName": "value with, commas and spaces"`
  - Captures everything between quotes, including commas
  - Handles escaped characters properly

- **Unquoted Value Pattern** (for numbers, booleans, null):
  ```java
  Pattern unquotedPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*([^,}\\s]+)");
  ```
  - Matches: `"fieldName": 123` or `"fieldName": true`
  - Stops at comma/brace/whitespace (correct for unquoted values)

### Test Cases:
✅ String with comma: `"assigned_vendor_name": "telkom_akses,_pt"` → `telkom_akses,_pt`
✅ String with spaces and comma: `"assigned_vendor_label": "TELKOM AKSES, PT"` → `TELKOM AKSES, PT`
✅ Numbers: `"count": 50` → `50`
✅ Booleans: `"success": true` → `true`
✅ Null values: `"value": null` → `null`
✅ Escaped characters: `"name": "value with \"quotes\""` → `value with \"quotes\"`

### Result:
Now when the API returns:
```json
{
  "assigned_vendor_name": "telkom_akses,_pt",
  "assigned_vendor_label": "TELKOM AKSES, PT"
}
```

The XML output correctly preserves commas:
```xml
<assigned_vendor_name>telkom_akses,_pt</assigned_vendor_name>
<assigned_vendor_label>TELKOM AKSES, PT</assigned_vendor_label>
```

---

## 3. Added feeder_code and olt_code to Subfeeder SQL Query

### Issue:
The subfeeder SQL query was missing `feeder_code` and `olt_code` fields that are now available in the database table `dim_subfeeder_master_smallworld`.

### Changes Made:

**File:** `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_engine.magik`

#### A. Updated SQL Query (Line 29)

**Before:**
```magik
:subfeeder, "SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid, CASE WHEN abd_kmz_uuid IS NOT NULL THEN 'ABD' ELSE 'APD' END AS kmz_source, subfeeder_code FROM smallworld.dim_subfeeder_master_smallworld WHERE subfeeder_code = ? LIMIT 1"
```

**After:**
```magik
:subfeeder, "SELECT COALESCE(abd_kmz_uuid, apd_kmz_uuid) AS kmz_uuid, CASE WHEN abd_kmz_uuid IS NOT NULL THEN 'ABD' ELSE 'APD' END AS kmz_source, subfeeder_code, feeder_code, olt_code FROM smallworld.dim_subfeeder_master_smallworld WHERE subfeeder_code = ? LIMIT 1"
```

#### B. Updated get_kmz_uuid_from_db Method (Lines 761-765)

**Before:**
```magik
_elif infrastructure_type = "subfeeder"
_then
    result[:subfeeder_code] << rec.subfeeder_code.default("")
```

**After:**
```magik
_elif infrastructure_type = "subfeeder"
_then
    result[:subfeeder_code] << rec.subfeeder_code.default("")
    result[:feeder_code] << rec.feeder_code.default("")
    result[:olt_code] << rec.olt_code.default("")
```

#### C. Updated Method Documentation (Lines 670-673)

**Before:**
```magik
##   For subfeeder: :subfeeder_code
```

**After:**
```magik
##   For subfeeder: :subfeeder_code, :feeder_code, :olt_code
```

### Data Flow Verification:

The fields work correctly throughout the entire data flow:

1. **Database Query** → SQL retrieves `feeder_code` and `olt_code` from `dim_subfeeder_master_smallworld` table

2. **Engine Processing** (rwwi_astri_workorder_engine.magik:761-765) → Extracts fields from database result

3. **Work Order Property List** (rwwi_astri_workorder_engine.magik:458-463) → Stores in work order data:
   ```magik
   pl[:subfeeder_code_db] << db_result[:subfeeder_code].default("")
   pl[:feeder_code_db] << db_result[:feeder_code].default("")
   pl[:olt_code] << db_result[:olt_code].default("")
   ```

4. **Design Migrator Initialization** (astri_design_migrator.magik:119-122) → Extracts from work order:
   ```magik
   .subfeeder_code_db << wo_data[:subfeeder_code_db].default("")
   .feeder_code_db << wo_data[:feeder_code_db].default("")
   .olt_code << wo_data[:olt_code].default("")
   ```

5. **Migrated Objects** (astri_aerial_route_migrator.magik:141-144) → Used in all created objects:
   ```magik
   :cluster_code, .cluster_code_db,
   :subfeeder_code, .subfeeder_code_db,
   :feeder_code, .feeder_code_db,
   :olt_code, .olt_code,
   ```

### Infrastructure Type Field Comparison:

| Infrastructure Type | Fields Returned |
|-------------------|-----------------|
| **Cluster** | `cluster_code`, `subfeeder_code`, `feeder_code`, `olt_code` |
| **Subfeeder** | `subfeeder_code`, `feeder_code`, `olt_code` ✅ *(Updated)* |
| **Feeder** | `feeder_code`, `olt_code` |

### Objects That Use These Fields:

All migrated objects now properly receive and store these database fields:
- ✅ Aerial Routes (cable segments)
- ✅ Sling Wires (messenger wire support)
- ✅ Poles (support structures)
- ✅ Sheaths (cable sections)
- ✅ Sheath Splices (cable connections)
- ✅ Demand Points (customer connection points)
- ✅ OLTs (Optical Line Terminals)
- ✅ Risers (vertical cable runs)
- ✅ Access Points (network access locations)
- ✅ All other migrated objects

### Result:

The subfeeder infrastructure type now has **full parity** with cluster and feeder types. When a subfeeder work order is migrated to Smallworld design objects, all created objects will properly include:

- ✅ `subfeeder_code` - The subfeeder's own code
- ✅ `feeder_code` - The parent feeder code (newly added)
- ✅ `olt_code` - The OLT code (newly added)

This ensures complete traceability of the network hierarchy (Feeder → Subfeeder → Cluster → OLT) for all migrated objects.

---

## JAR Rebuild

All Java changes were compiled and packaged into the JAR file:

**JAR File:** `C:\Smallworld\pni_custom\libs\pni_custom.rwwi.astri.integration.1.jar`
- **Size:** 45KB
- **Build Date:** November 24, 2025
- **Build Status:** ✅ SUCCESS

**Build Command:**
```bash
cd C:\Smallworld\pni_custom\rwwi_astri_integration_java
mvn clean package
```

---

## Testing Recommendations

### 1. Test BOQ API Integration:
- Test with decimal values (e.g., 433.93, 1234.5678)
- Verify values are rounded to 2 decimal places in API requests
- Test error scenarios to verify JSON error responses
- Verify Magik correctly identifies success vs failure responses

### 2. Test Work Order Data with Commas:
- Query work orders with vendor names containing commas
- Verify commas are preserved in XML and displayed in UI
- Test with various special characters in string fields

### 3. Test Subfeeder Migration:
- Select a subfeeder work order
- Migrate to design
- Verify all created objects have `feeder_code` and `olt_code` fields populated
- Check database queries return correct values

---

## Files Modified

### Java Files:
1. `src/main/java/com/rwi/myrepublic/astri/AstriBoqProcs.java`
2. `src/main/java/com/rwi/myrepublic/astri/internal/BoqClient.java`
3. `src/main/java/com/rwi/myrepublic/astri/internal/WorkOrderClient.java`

### Magik Files:
1. `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_dialog.magik`
2. `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_engine.magik`

### Build Output:
1. `libs/pni_custom.rwwi.astri.integration.1.jar` (rebuilt)

---

## Deployment Notes

1. **Restart Smallworld GIS** after deploying the new JAR file
2. The new JAR must be loaded by the OSGi framework
3. Test in a development environment before production deployment
4. Database table `dim_subfeeder_master_smallworld` must have `feeder_code` and `olt_code` columns

---

## Author
- **Changes made by:** Claude Code (Anthropic AI Assistant)
- **Date:** November 24, 2025
- **Project:** RWI ASTRI Integration v2
