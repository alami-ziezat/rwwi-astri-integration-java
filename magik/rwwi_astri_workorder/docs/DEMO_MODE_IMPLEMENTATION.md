# ASTRI Work Order Manager - Demo Mode Implementation

## Overview

Demo mode implementation that injects dummy work order data with a valid KMZ UUID for testing and demonstration purposes. The dummy data appears as the **first record** in the work order list, followed by actual API results.

## Implementation Date
2025-10-30

## Purpose

Since the actual ASTRI API currently does not return `kmz_uuid` values, this demo mode allows testing of:
- KMZ download functionality (Scenario 1 & 2)
- Migrate to Design feature
- Migrate as Temporary feature
- UI table display with KMZ UUID column
- Selection and action button enabling

## Demo Data Specification

### Dummy Work Order Fields

| Field | Value | Description |
|-------|-------|-------------|
| `uuid` | `demo-wo-00001` | Work order UUID |
| `wo_number` | `WO/JKT/2025/DEMO/00001` | Work order number |
| **`kmz_uuid`** | **`2989eecc-3fd0-402c-bd81-99e02caa7ef5`** | **KMZ document UUID** |
| `cluster_code` | `JKT00001` | Cluster code (Jakarta region) |
| `cluster_name` | `PRE ABD_KEBON PALA RW 09_873 HP` | Cluster name |
| `topology` | `AE` | Topology type (Aerial) |
| `category` | `Cluster BOQ` | Work order category |
| `status` | `in_progress` | Current status |
| `vendor` | `Demo Vendor Ltd` | Assigned vendor |
| `created_at` | Current timestamp | Creation date |

### Key Demo Value
The KMZ UUID `2989eecc-3fd0-402c-bd81-99e02caa7ef5` is used for:
- Testing `astri_download_cluster_kmz()` API calls
- Validating KML content retrieval (Scenario 1)
- Validating file download (Scenario 2)
- Testing migration buttons

## Implementation Details

### Location
**File:** `C:\Smallworld\pni_custom\modules\rwwi_astri_workorder\source\rwwi_astri_workorder_engine.magik`

### Method 1: `create_dummy_workorder_data()` (Lines 113-159)

```magik
_pragma(classify_level=debug, topic={astri_integration})
_private _method rwwi_astri_workorder_engine.create_dummy_workorder_data()
	## Create dummy work order data for demo/testing purposes
	##
	## Returns:
	##   property_list - Dummy work order with KMZ UUID

	_try
		_local dummy << property_list.new_with(
			# Primary identifiers
			:uuid, "demo-wo-00001",
			:wo_number, "WO/JKT/2025/DEMO/00001",

			# KMZ UUID for download testing
			:kmz_uuid, "2989eecc-3fd0-402c-bd81-99e02caa7ef5",

			# Cluster information
			:cluster_code, "JKT00001",
			:cluster_name, "PRE ABD_KEBON PALA RW 09_873 HP",
			:topology, "AE",

			# Work order metadata
			:category, "Cluster BOQ",
			:status, "in_progress",
			:vendor, "Demo Vendor Ltd",

			# Timestamps
			:created_at, date_time.now().write_string)

		write("Created dummy work order data:")
		write("  UUID: ", dummy[:uuid])
		write("  WO Number: ", dummy[:wo_number])
		write("  KMZ UUID: ", dummy[:kmz_uuid])
		write("  Cluster: ", dummy[:cluster_code], " - ", dummy[:cluster_name])

		_return dummy

	_when error
		write("WARNING: Failed to create dummy work order data:", condition.report_contents_string)
		_return _unset
	_endtry
_endmethod
```

### Method 2: Modified `get_workorders_from_api()` (Lines 36-109)

**Key Addition (Lines 84-94):**
```magik
# DEMO MODE: Prepend dummy data with KMZ UUID
dummy_data << _self.create_dummy_workorder_data()
_if dummy_data _isnt _unset
_then
	write("=== DEMO MODE: Adding dummy work order as first record ===")
	# Create new rope with dummy data first, then API results
	combined_rope << rope.new()
	combined_rope.add(dummy_data)
	combined_rope.add_all_last(workorders)
	workorders << combined_rope
_endif
```

## Behavior

### When Opening the Dialog
1. API is called to get real work orders
2. Dummy work order is created
3. Dummy work order is **prepended** (added as first item)
4. Combined rope is returned with:
   - **Index 0:** Dummy work order (with KMZ UUID)
   - **Index 1+:** Real API work orders

### When Applying Filters
1. API is called with filter parameters
2. Dummy work order is **still prepended** regardless of filters
3. User always sees dummy data as first record for testing

### In the UI Table
The dummy work order appears as:
```
┌──────────────────────┬────────────┬────────────────────────────────┬──────────┬──────────┐
│ WO Number            │ Cluster    │ Cluster Name                   │ Topology │ Status   │
├──────────────────────┼────────────┼────────────────────────────────┼──────────┼──────────┤
│ WO/JKT/2025/DEMO/00001│ JKT00001  │ PRE ABD_KEBON PALA RW 09_873 HP│ AE       │ in_prog..│
│ <Real API data>      │            │                                │          │          │
│ ...                  │            │                                │          │          │
└──────────────────────┴────────────┴────────────────────────────────┴──────────┴──────────┘
```

## Testing Scenarios

### Test 1: Download KMZ (Scenario 2)
1. Select dummy work order (first row)
2. Click "Download KMZ" button
3. Expected: Files saved to `%TEMP%` directory:
   - `cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kmz`
   - `cluster_2989eecc-3fd0-402c-bd81-99e02caa7ef5.kml`

### Test 2: Migrate to Design (Scenario 1)
1. Select dummy work order (first row)
2. Click "Migrate to Design" button
3. Expected: User info dialog shows KML content preview

### Test 3: Migrate as Temporary (Scenario 1)
1. Select dummy work order (first row)
2. Click "Migrate as Temporary" button
3. Expected: User info dialog shows KML content preview

### Test 4: Mixed Data Display
1. Open dialog without filters
2. Expected: Dummy data shows first, followed by real API data
3. Verify table correctly displays all rows

### Test 5: Filter with Dummy Data
1. Apply any filter (e.g., status = "completed")
2. Expected: Dummy data still appears as first row, followed by filtered API results

## Debug Output

When demo mode is active, console output shows:

```
=== DEMO MODE: Adding dummy work order as first record ===
Created dummy work order data:
  UUID: demo-wo-00001
  WO Number: WO/JKT/2025/DEMO/00001
  KMZ UUID: 2989eecc-3fd0-402c-bd81-99e02caa7ef5
  Cluster: JKT00001 - PRE ABD_KEBON PALA RW 09_873 HP
```

## Disabling Demo Mode

To disable demo mode in the future, comment out lines 84-94 in `rwwi_astri_workorder_engine.magik`:

```magik
# DEMO MODE DISABLED
# dummy_data << _self.create_dummy_workorder_data()
# _if dummy_data _isnt _unset
# _then
# 	write("=== DEMO MODE: Adding dummy work order as first record ===")
# 	combined_rope << rope.new()
# 	combined_rope.add(dummy_data)
# 	combined_rope.add_all_last(workorders)
# 	workorders << combined_rope
# _endif
```

Or modify to check a configuration flag:

```magik
# DEMO MODE: Check configuration
_if system.getenv("ASTRI_DEMO_MODE").default("true") = "true"
_then
	dummy_data << _self.create_dummy_workorder_data()
	_if dummy_data _isnt _unset
	_then
		write("=== DEMO MODE: Adding dummy work order as first record ===")
		combined_rope << rope.new()
		combined_rope.add(dummy_data)
		combined_rope.add_all_last(workorders)
		workorders << combined_rope
	_endif
_endif
```

## API Compatibility

When the real ASTRI API starts returning `kmz_uuid` values:
1. Demo mode can remain active (dummy + real data)
2. Demo mode can be disabled (real data only)
3. No code changes needed in other layers
4. Migration buttons will work with both dummy and real KMZ UUIDs

## Files Modified

1. `rwwi_astri_workorder_engine.magik`
   - Added `create_dummy_workorder_data()` method (lines 113-159)
   - Modified `get_workorders_from_api()` to prepend dummy data (lines 84-94)

## Related Documentation

- `KMZ_UUID_IMPLEMENTATION.md` - KMZ UUID field addition
- `KMZ_DUAL_SCENARIO_IMPLEMENTATION.md` - Dual scenario download logic
- `FILTER_IMPLEMENTATION.md` - Filter parameter building

## Notes

- Dummy data is **always prepended**, regardless of filters
- Cluster code follows Jakarta pattern: `JKT` + 5-digit number
- KMZ UUID is a valid UUID v4 format
- Demo mode does not affect database operations
- No changes needed in Java layer

## Future Enhancements

1. **Configuration Toggle:**
   - Add environment variable check: `ASTRI_DEMO_MODE`
   - Add UI toggle button for demo mode

2. **Multiple Demo Records:**
   - Create array of dummy work orders
   - Different topologies (AE, UG, OH)
   - Different statuses (in_progress, completed, pending)

3. **Random Demo Data:**
   - Generate random cluster codes
   - Random timestamps
   - Random vendor assignments

4. **Demo Data Source:**
   - Load dummy data from external config file
   - Support multiple KMZ UUIDs for testing
   - Allow user-defined demo data
