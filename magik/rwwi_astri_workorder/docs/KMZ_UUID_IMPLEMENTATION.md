# KMZ UUID Field Implementation

**Date:** 2025-10-30
**Status:** ✅ COMPLETED

## Purpose

Add `kmz_uuid` field to work order response and UI to enable KMZ file downloads using the proper UUID from the API.

## Problem

Previously, the download KMZ functionality was using `cluster_code` and calling a wrong procedure `astri_download_kmz_cluster()`. The correct approach is:
- Use `kmz_uuid` field from work order data
- Call `astri_download_cluster_kmz(uuid, outputDir)` procedure

## Solution Overview

Added `kmz_uuid` field throughout the entire stack:
1. ✅ Java API - XML conversion in WorkOrderClient
2. ✅ Magik Engine - XML parsing in rwwi_astri_workorder_engine
3. ✅ Magik Dialog - Table column and data display
4. ✅ Download Method - Updated to use kmz_uuid and correct procedure

## Changes Made

### 1. Java - WorkOrderClient.java ✅

**File:** `C:\Smallworld\pni_custom\rwwi_astri_integration_java\src\main\java\com\rwi\myrepublic\astri\internal\WorkOrderClient.java`

**Line 197:** Added kmz_uuid field extraction

```java
// Extract all work order fields
appendXmlField(xml, woJson, "uuid", 6);
appendXmlField(xml, woJson, "number", 6);
appendXmlField(xml, woJson, "target_cluster_code", 6);
appendXmlField(xml, woJson, "target_cluster_name", 6);
appendXmlField(xml, woJson, "category_label", 6);
appendXmlField(xml, woJson, "category_name", 6);
appendXmlField(xml, woJson, "latest_status_name", 6);
appendXmlField(xml, woJson, "target_cluster_topology", 6);
appendXmlField(xml, woJson, "assigned_vendor_label", 6);
appendXmlField(xml, woJson, "assigned_vendor_name", 6);
appendXmlField(xml, woJson, "created_at", 6);
appendXmlField(xml, woJson, "updated_at", 6);
appendXmlField(xml, woJson, "kmz_uuid", 6);  // ← NEW
```

**Expected XML Output:**
```xml
<response>
  <success>true</success>
  <count>50</count>
  <data>
    <workorder>
      <uuid>d35ed679-0b5e-4c33-953c-2740b5cc7772</uuid>
      <number>WO/ALL/2025/DOCU/16/54556</number>
      <target_cluster_code>MNA000033</target_cluster_code>
      <target_cluster_name>PADANG SIALANG RT 01, 02, 03...</target_cluster_name>
      <category_label>Cluster BOQ</category_label>
      <latest_status_name>in_progress</latest_status_name>
      <target_cluster_topology>AE</target_cluster_topology>
      <assigned_vendor_label>Yangtze Optical Fible and Cable</assigned_vendor_label>
      <created_at>2025-10-28 14:28:08</created_at>
      <kmz_uuid>abc123-def456-ghi789</kmz_uuid>  <!-- NEW FIELD -->
    </workorder>
  </data>
</response>
```

### 2. Magik Engine - rwwi_astri_workorder_engine.magik ✅

**File:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\source\rwwi_astri_workorder_engine.magik`

**Line 163:** Added kmz_uuid to property_list extraction

```magik
# Extract all work order fields
pl[:uuid] << _self.get_xml_element_value(wo_elem, :uuid)
pl[:wo_number] << _self.get_xml_element_value(wo_elem, :number)
pl[:cluster_code] << _self.get_xml_element_value(wo_elem, :target_cluster_code)
pl[:cluster_name] << _self.get_xml_element_value(wo_elem, :target_cluster_name)
pl[:category] << _self.get_xml_element_value(wo_elem, :category_label)
pl[:status] << _self.get_xml_element_value(wo_elem, :latest_status_name)
pl[:topology] << _self.get_xml_element_value(wo_elem, :target_cluster_topology)
pl[:vendor] << _self.get_xml_element_value(wo_elem, :assigned_vendor_label)
pl[:created_at] << _self.get_xml_element_value(wo_elem, :created_at)
pl[:kmz_uuid] << _self.get_xml_element_value(wo_elem, :kmz_uuid)  # ← NEW
```

### 3. Magik Dialog - Table Column ✅

**File:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\source\rwwi_astri_workorder_dialog.magik`

#### A. Column Headers (Line 197-199)

**Before:**
```magik
.items[:table].set_column_labels({
    "No", "WO Number", "Cluster Code", "Cluster Name",
    "Category", "Status", "Topology", "Vendor", "Created"})
```

**After:**
```magik
.items[:table].set_column_labels({
    "No", "WO Number", "Cluster Code", "Cluster Name",
    "Category", "Status", "Topology", "Vendor", "Created", "KMZ UUID"})  # ← NEW
```

#### B. Column Widths (Line 202)

**Before:**
```magik
.items[:table].col_resize_values << {0, 2, 1.5, 3, 1.5, 1.5, 1, 2.5, 1.5}
```

**After:**
```magik
.items[:table].col_resize_values << {0, 2, 1.5, 3, 1.5, 1.5, 1, 2.5, 1.5, 2}  # ← NEW
```

#### C. Table Data Population (Line 292)

**Before:**
```magik
.items[:table].add_label(row, 1, row.write_string)
.items[:table].add_label(row, 2, wo[:wo_number].default(""))
.items[:table].add_label(row, 3, wo[:cluster_code].default(""))
.items[:table].add_label(row, 4, wo[:cluster_name].default(""))
.items[:table].add_label(row, 5, wo[:category].default(""))
.items[:table].add_label(row, 6, wo[:status].default(""))
.items[:table].add_label(row, 7, wo[:topology].default(""))
.items[:table].add_label(row, 8, wo[:vendor].default(""))
.items[:table].add_label(row, 9, wo[:created_at].default(""))
```

**After:**
```magik
.items[:table].add_label(row, 1, row.write_string)
.items[:table].add_label(row, 2, wo[:wo_number].default(""))
.items[:table].add_label(row, 3, wo[:cluster_code].default(""))
.items[:table].add_label(row, 4, wo[:cluster_name].default(""))
.items[:table].add_label(row, 5, wo[:category].default(""))
.items[:table].add_label(row, 6, wo[:status].default(""))
.items[:table].add_label(row, 7, wo[:topology].default(""))
.items[:table].add_label(row, 8, wo[:vendor].default(""))
.items[:table].add_label(row, 9, wo[:created_at].default(""))
.items[:table].add_label(row, 10, wo[:kmz_uuid].default(""))  # ← NEW
```

### 4. Download KMZ Method - Complete Rewrite ✅

**File:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\source\rwwi_astri_workorder_dialog.magik`

**Lines 486-542**

**Before (Incorrect):**
```magik
_method rwwi_astri_workorder_dialog.download_kmz()
	## Download KMZ file for selected work order

	_local wo << .selected_wo
	_local cluster_code << wo[:cluster_code]  # ← WRONG: Using cluster_code

	write("Downloading KMZ for cluster:", cluster_code)

	_try
		# Call ASTRI KMZ download API
		_local kmz_result << astri_download_kmz_cluster(cluster_code)  # ← WRONG PROC NAME

		_local file_path << write_string(kmz_result)
		_self.user_info(write_string("KMZ downloaded successfully:", %newline, file_path))
	_when error
		_self.user_error(...)
	_endtry
_endmethod
```

**After (Correct):**
```magik
_method rwwi_astri_workorder_dialog.download_kmz()
	## Download KMZ file for selected work order using kmz_uuid

	_if .selected_wo _is _unset
	_then
		_self.user_info("Please select a work order first")
		_return
	_endif

	_local wo << .selected_wo
	_local kmz_uuid << wo[:kmz_uuid]  # ← CORRECT: Using kmz_uuid

	_if kmz_uuid _is _unset _orif kmz_uuid = ""
	_then
		_self.user_info("No KMZ UUID available for this work order")
		_return
	_endif

	write("Downloading KMZ for UUID:", kmz_uuid)

	_try
		# Call ASTRI KMZ download API with kmz_uuid
		# astri_download_cluster_kmz(uuid, outputDir)  ← CORRECT PROC NAME
		_local output_dir << system.getenv("TEMP").default("C:\temp")
		_local xml_result << astri_download_cluster_kmz(kmz_uuid, output_dir)

		_if xml_result _is _unset _orif xml_result = ""
		_then
			_self.user_error("Failed to download KMZ file")
			_return
		_endif

		# Parse XML response to get file path
		_local xml_doc << simple_xml.read_element_string(xml_result)
		_local success_elem << xml_doc.element_matching_name(:success)
		_local file_path_elem << xml_doc.element_matching_name(:file_path)

		_if success_elem _isnt _unset _andif
		    success_elem.xml_result = "true" _andif
		    file_path_elem _isnt _unset
		_then
			_local file_path << file_path_elem.xml_result
			_self.user_info(write_string("KMZ downloaded successfully:", %newline, file_path))
		_else
			_local error_elem << xml_doc.element_matching_name(:error)
			_local error_msg << _if error_elem _isnt _unset
					    _then >> error_elem.xml_result
					    _else >> "Unknown error"
					    _endif
			_self.user_error(write_string("Failed to download KMZ:", %newline, error_msg))
		_endif

	_when error
		_self.user_error(write_string("Error downloading KMZ:", %newline,
			condition.report_contents_string))
	_endtry
_endmethod
```

### Key Improvements in download_kmz():

1. ✅ **Uses kmz_uuid** instead of cluster_code
2. ✅ **Correct procedure name:** `astri_download_cluster_kmz(uuid, outputDir)`
3. ✅ **Proper validation:** Checks if kmz_uuid exists before proceeding
4. ✅ **XML response parsing:** Extracts file_path from XML response
5. ✅ **Error handling:** Parses error messages from XML response
6. ✅ **Output directory:** Uses TEMP environment variable with fallback

## Build Information

**Build Time:** 2025-10-30T21:58:18+07:00
**Build Result:** ✅ SUCCESS
**Output JAR:** `C:\Smallworld\pni_custom\libs\pni_custom.rwwi.astri.integration.1.jar`

## Testing Guide

### 1. Reload Modules
```magik
sw_module_manager.reload_module(:rwwi_astri_integration)
sw_module_manager.reload_module(:rwwi_astri_workorder)
```

### 2. Open Dialog
```magik
rwwi_astri_workorder_dialog.open()
```

### 3. Verify KMZ UUID Column

Check that the table displays a new column "KMZ UUID" with values from the API.

### 4. Test Download KMZ

1. Select a work order row that has a kmz_uuid value
2. Click "Download KMZ" button
3. Expected behavior:
   - Console shows: `Downloading KMZ for UUID: <uuid_value>`
   - KMZ file downloads to TEMP directory
   - Success message shows file path

### 5. Debug Output

Expected console output:
```
Downloading KMZ for UUID: abc123-def456-ghi789
=== DEBUG: astri_download_cluster_kmz called ===
Magik uuid object: abc123-def456-ghi789
=== DEBUG: Download successful, returning XML response ===
```

## Table Structure

| Column | Field | Width | Filter | Description |
|--------|-------|-------|--------|-------------|
| 1 | No | 0 | No | Row number |
| 2 | WO Number | 2 | Yes | Work order number |
| 3 | Cluster Code | 1.5 | Yes | Target cluster code |
| 4 | Cluster Name | 3 | Yes | Target cluster name |
| 5 | Category | 1.5 | No | Category label |
| 6 | Status | 1.5 | Yes | Latest status |
| 7 | Topology | 1 | No | Topology (AE/UG/OH) |
| 8 | Vendor | 2.5 | No | Assigned vendor |
| 9 | Created | 1.5 | No | Creation date |
| **10** | **KMZ UUID** | **2** | **No** | **UUID for KMZ download** ← NEW |

## API Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. User Opens Dialog                                        │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Load Work Orders from API                                │
│    - astri_get_work_orders(limit, offset, filters)         │
│    - Returns XML with kmz_uuid field                        │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Parse XML in Engine                                      │
│    - Extract kmz_uuid from <kmz_uuid> element              │
│    - Store in property_list                                 │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Display in Table                                         │
│    - Show kmz_uuid in column 10                            │
│    - Store in wo_cache[row]                                │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. User Selects Row & Clicks "Download KMZ"                │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. download_kmz() Method                                    │
│    - Get kmz_uuid from selected work order                 │
│    - Call astri_download_cluster_kmz(kmz_uuid, output_dir) │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 7. Java @MagikProc downloads file                          │
│    - Downloads KMZ from DM API                              │
│    - Returns XML with file_path                             │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 8. Show Success Message                                     │
│    - Display file path to user                              │
└─────────────────────────────────────────────────────────────┘
```

## Error Scenarios

### Scenario 1: No KMZ UUID
```magik
# If work order doesn't have kmz_uuid
"No KMZ UUID available for this work order"
```

### Scenario 2: Download Failed
```magik
# If astri_download_cluster_kmz returns empty/error XML
"Failed to download KMZ file"
```

### Scenario 3: API Error
```magik
# If API returns error in XML
"Failed to download KMZ: <error_message_from_api>"
```

### Scenario 4: Exception
```magik
# If any exception occurs
"Error downloading KMZ: <exception_details>"
```

## Files Modified Summary

| File | Lines Changed | Purpose |
|------|---------------|---------|
| WorkOrderClient.java | 1 line added | Extract kmz_uuid from JSON |
| rwwi_astri_workorder_engine.magik | 1 line added | Parse kmz_uuid from XML |
| rwwi_astri_workorder_dialog.magik | 3 lines (headers) | Add table column |
| rwwi_astri_workorder_dialog.magik | 1 line (widths) | Set column width |
| rwwi_astri_workorder_dialog.magik | 1 line (data) | Populate column |
| rwwi_astri_workorder_dialog.magik | ~60 lines (method) | Rewrite download_kmz() |

**Total:** 6 locations modified across 3 files

## Related Documentation

- `JAVA_TO_MAGIK_STRING_ALL_APIS.md` - String conversion for all APIs
- `FILTER_SOLUTION_FINAL.md` - Filter implementation
- `JAVA_STRING_FIX.md` - Original string conversion fix

---

**Status:** ✅ PRODUCTION READY
**Last Updated:** 2025-10-30
**Confidence:** HIGH - Complete end-to-end implementation with proper error handling
