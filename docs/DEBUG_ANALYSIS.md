# Debug Analysis for Migration Errors

## Error Observed

```
ERROR creating object for placemark 1 (NN-90): This is the exemplar condition
ERROR creating object for placemark 2 (14): This is the exemplar condition
...
All 1144 placemarks failed with same error
```

## "This is the exemplar condition" Error

This is a generic Smallworld error that typically occurs when:

1. **Calling method on exemplar instead of instance**
   - Example: `my_class.some_method()` instead of `my_instance.some_method()`

2. **Missing or incorrect slot initialization**
   - Slots are `_unset` when they should have values

3. **Incorrect method definition**
   - Missing `$` delimiter
   - Incorrect syntax in method body

4. **Field name mismatch in database**
   - Querying for field that doesn't exist in collection
   - Example: Using `:uuid` when collection has `:kmz_uuid`

## Debug Output Added

### 1. Main Loop Debug (First 3 Placemarks)
```magik
write("DEBUG [", i, "]: Processing placemark")
write("  Name: ", pm[:name])
write("  Type: ", pm[:type])
write("  Parent: ", pm[:parent])
write("  Coord: ", pm[:coord].default("").subseq(1, 100.min(pm[:coord].size)))
write("  Calling create_or_update_point...")
```

### 2. create_or_update_point Debug
```magik
write("    DEBUG: Entering create_or_update_point")
write("    DEBUG: pm[:coord] = ", pm[:coord])
write("    DEBUG: coord_parts.size = ", coord_parts.size)
write("    DEBUG: lon=", lon, ", lat=", lat)
write("    DEBUG: Creating WGS84 coordinate...")
write("    DEBUG: WGS84 coord created: ", wgs84_coord)
write("    DEBUG: Transform is: ", .transform)
write("    DEBUG: Local coord: ", local_coord)
write("    DEBUG: Checking for existing record...")
write("    DEBUG: existing_rec = ", existing_rec)
```

### 3. find_existing_record Debug
```magik
write("      DEBUG find_existing_record: collection=", collection)
write("      DEBUG find_existing_record: uuid=", .uuid)
write("      DEBUG find_existing_record: name=", name)
write("      DEBUG find_existing_record: parent=", parent)
write("      DEBUG find_existing_record: predicate created")
write("      DEBUG find_existing_record: query executed, found ", existing_recs.size, " records")
```

### 4. Enhanced Error Handler
```magik
write("ERROR creating object for placemark ", i, " (", pm[:name], "): ")
write("  Type: ", pm[:type])
write("  Error name: ", condition.name)
write("  Error message: ", condition.report_contents_string)
write("  Error traceback: ")
_for line _over condition.report_contents_on(internal_text_output_stream.new()).split_by(%newline).fast_elements()
_loop
	write("    ", line)
_endloop
```

## What to Look For

### 1. Where Does It Fail?

Run the migration again and look for the **last DEBUG message** before the error:

**If last message is:**
- `"DEBUG: Entering create_or_update_point"` → Error is in method call itself
- `"DEBUG: coord_parts.size = X"` → Error is in coordinate parsing
- `"DEBUG: Creating WGS84 coordinate..."` → Error is in coordinate.new()
- `"DEBUG: Transform is: ..."` → Error is in transform.convert()
- `"DEBUG: Checking for existing record..."` → Error is in find_existing_record()
- `"DEBUG find_existing_record: collection=..."` → Error is in predicate.new() or collection.select()

### 2. Common Issues and Solutions

#### Issue: Error at "predicate created"
**Cause:** Collection doesn't have field `:uuid`, `:name`, or `:folders`

**Solution:** Check actual field names in collection:
```magik
# In Smallworld session:
_for fd _over gis_program_manager.databases[:gis].collections[:rw_point].physical_fields.fast_elements()
_loop
	write(fd.name)
_endloop
```

**Expected fields:**
- `:uuid` or `:kmz_uuid` or similar
- `:name` or `:object_name` or similar
- `:folders` or `:parent_folder` or similar

If field names are different, update the code to use correct names.

#### Issue: Error at "Transform is: ..."
**Cause:** Transform object is _unset or invalid

**Solution:** Check transform initialization in init() method:
```magik
write("Database world: ", .database.world)
write("Database CS: ", .database.world.coordinate_system)
write("Ace view: ", .ace_view)
write("WGS84 CS: ", cs_wgs84)
write("Transform: ", .transform)
```

#### Issue: Error at "WGS84 coord created"
**Cause:** coordinate.new() failed with lon/lat values

**Solution:** Check coordinate values:
```magik
write("lon type: ", lon.class_name)
write("lat type: ", lat.class_name)
write("lon value: ", lon)
write("lat value: ", lat)
```

## Likely Root Cause

Based on the error occurring for **all** placemarks, the most likely causes are:

### 1. Field Name Mismatch (Most Likely)
Collections don't have fields named `:uuid`, `:name`, or `:folders`.

**To verify:**
```magik
# Check rw_point collection fields
_local coll << gis_program_manager.databases[:gis].collections[:rw_point]
write("Collection: ", coll)
write("Fields:")
_for fd _over coll.physical_fields.fast_elements()
_loop
	write("  ", fd.name, " (", fd.class_name, ")")
_endloop
```

**If fields are different, need to update:**
- `find_existing_record()` predicate field names
- `create_or_update_point/line/area()` property_list field names

### 2. Collections Don't Exist
Collections `:rw_point`, `:rw_line`, `:rw_area` don't exist in database.

**To verify:**
```magik
_local db << gis_program_manager.databases[:gis]
write("Database: ", db)
write("rw_point: ", db.collections[:rw_point])
write("rw_line: ", db.collections[:rw_line])
write("rw_area: ", db.collections[:rw_area])
```

**If collections don't exist, need to:**
- Use correct collection names
- Or create these collections

### 3. Transform Not Initialized
Transform object is _unset because coordinate system setup failed.

**To verify:** Check init() method creates transform successfully

## Next Steps

1. **Run migration again** with debug output enabled
2. **Capture full console output** for first 3 placemarks
3. **Identify exact point of failure** from last DEBUG message
4. **Check collection field names** if error is in predicate
5. **Update code** based on actual field names

## Temporary Workaround

To test without duplicate detection, you can temporarily disable find_existing_record:

```magik
# In create_or_update_point, change:
_local existing_rec << _self.find_existing_record(.point_col, pm[:name], pm[:parent])

# To:
_local existing_rec << _unset  # Force create instead of update
```

This will skip the duplicate check and always create new records.

## Collection Field Names - Common Variations

Different Smallworld implementations use different field names:

| Feature | Possible Field Names |
|---------|---------------------|
| UUID | `:uuid`, `:kmz_uuid`, `:doc_id`, `:external_id` |
| Name | `:name`, `:object_name`, `:label`, `:description` |
| Parent/Folder | `:folders`, `:parent`, `:category`, `:group` |
| Location (point) | `:location`, `:position`, `:geometry` |
| Route (line) | `:route`, `:geometry`, `:centreline` |
| Area (polygon) | `:area`, `:geometry`, `:boundary` |

**Need to check actual field names in your database schema.**
