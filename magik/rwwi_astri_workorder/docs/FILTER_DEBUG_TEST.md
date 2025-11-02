# Filter Debug Test Guide

## What Changed

### Fixed Issues:
1. ✅ **Method name** - Now uses `at0()` instead of `at()`
2. ✅ **Map interface** - Tries to treat property_list as Java Map first
3. ✅ **Multiple approaches** - Tries 3 different ways to extract values:
   - Map.get(String key)
   - Map.get(Symbol key)
   - at0(Symbol key) via reflection

## Test Command

```magik
# Create engine
engine << rwwi_astri_workorder_engine.new()

# Create filters
filters << property_list.new_with(
    :latest_status_name, "in_progress")

# Call API with filters
workorders << engine.get_workorders_from_api(10, 0, filters)

# Check results
write("Retrieved:", workorders.size, "work orders")
```

## Expected Debug Output

You should now see:

```
====== ASTRI GET WORK ORDERS - START ======
Limit: 10
Offset: 0
Filters object: magik.sw.property_list
  [buildFilterParams] START
  [buildFilterParams] Object class: magik.sw.property_list
  [buildFilterParams] Object is a Map instance!        <-- NEW!
  OR
  [buildFilterParams] Found at0() method              <-- NEW!

  [buildFilterParams] Processing filter: latest_status_name
    Using Map.get() approach                          <-- NEW!
    Map.get(String): null
    Map.get(Symbol): in_progress                      <-- VALUE FOUND!
    Retrieved value: in_progress
    Value class: magik.sw.char16_vector
    Is unset?: false
    Extracted string: 'in_progress'
    Added to params: latest_status_name=in_progress   <-- SUCCESS!

  [buildFilterParams] Final result: 'latest_status_name=in_progress'

  [WorkOrderClient] Final URL: http://.../10/0?latest_status_name=in_progress
```

## What to Look For

### ✅ SUCCESS Indicators:
- `Object is a Map instance!` OR `Found at0() method`
- `Map.get(Symbol): <value>` shows your filter value
- `Added to params: <key>=<value>`
- Final URL contains `?<key>=<value>`

### ❌ FAILURE Indicators:
- `No suitable method found`
- `Retrieved value: null` for ALL attempts
- `Built filter params: ''` (empty)
- Final URL has no `?` query string

## Multi-Filter Test

```magik
# Test multiple filters
filters << property_list.new_with(
    :latest_status_name, "in_progress",
    :category_name, "cluster_boq",
    :target_cluster_topology, "AE")

workorders << engine.get_workorders_from_api(10, 0, filters)
```

Expected URL:
```
http://.../10/0?latest_status_name=in_progress&category_name=cluster_boq&target_cluster_topology=AE
```

## Troubleshooting

### Still shows empty filters?

Check debug output for:
1. Which approach was used (Map or at0)?
2. What value was retrieved for each key?
3. Was the value marked as unset?

### If Map.get(String) and Map.get(Symbol) both return null:

The property_list might store keys differently. Check if there are other methods in the "Available methods" list that might work.

### If at0() is not found:

Look at the "Available methods" output and see what method IS available for accessing values by key.

## Reload Steps

```magik
# In Magik console
sw_module_manager.reload_module(:rwwi_astri_integration)
sw_module_manager.reload_module(:rwwi_astri_workorder)
```

Or restart Smallworld session for a clean start.

---

**Test Date:** 2025-10-30
**Key Fix:** Use `at0()` method and Map interface for property_list access
