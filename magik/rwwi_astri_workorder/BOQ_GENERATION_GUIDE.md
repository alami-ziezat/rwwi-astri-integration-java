# Bill of Quantities (BoQ) Generation Guide

## Overview

The ASTRI Work Order Manager now includes Bill of Quantities (BoQ) generation functionality. This feature allows users to generate material and service quantity reports from migrated design data in two formats:

1. **Excel Format** - Full formatted BoQ report using Excel templates
2. **JSON Format** - Structured property list for programmatic access

## Prerequisites

Before generating a BoQ, the following conditions must be met:

1. **Project Must Exist**: A Smallworld Design Manager project must exist for the work order
2. **Design Must Exist**: The project must contain at least one design (scheme)
3. **Design Must Be Populated**: The design should contain migrated objects (poles, cables, closures, etc.)
4. **Template File**: For Excel generation, the environment variable `TEMPLATE_BOQ` must point to a valid Excel template file

## UI Changes

### Buttons Replaced

The **"Mark Construction"** button has been replaced with two new buttons:

- **Generate BoQ as Excel** - Generates BoQ in Excel format
- **Generate BoQ as JSON** - Generates BoQ as property list

### Button Enable Logic

Both BoQ buttons are **disabled by default** and only become **enabled** when:

1. A work order is selected in the table
2. The selected work order has an associated project in Smallworld
3. The project contains at least one design

This ensures users cannot accidentally attempt to generate a BoQ when no design data exists.

## Workflow

### Step 1: Migrate Design Data

Before generating a BoQ, you must first migrate KML data to a Smallworld design:

1. Select a work order with a valid KMZ UUID
2. Click **"Migrate to Design"**
3. Wait for migration to complete
4. Verify the project and design were created successfully

### Step 2: Verify Project/Design Exist

After migration:

1. Select the work order again in the table
2. Check that the BoQ buttons are now **enabled**
3. If buttons remain disabled, the project/design may not exist

### Step 3: Generate BoQ

#### Option A: Generate Excel BoQ

1. Ensure `TEMPLATE_BOQ` environment variable is set:
   ```
   set TEMPLATE_BOQ=C:\path\to\template\BoQ_Template.xlsx
   ```
2. Click **"Generate BoQ as Excel"**
3. The system will:
   - Activate the design
   - Count all design objects
   - Populate the Excel template
   - Open Excel with the completed BoQ

#### Option B: Generate JSON BoQ

1. Click **"Generate BoQ as JSON"**
2. The system will:
   - Activate the design
   - Count all design objects
   - Return a structured property list
   - Display a summary of the first 5 items

## Technical Details

### Methods Added

#### `check_project_and_design_exist(wo)`

**Purpose**: Verify if project and design exist for a work order

**Parameters**:
- `wo` - Work order property list

**Returns**:
- `(has_project?, has_design?)` - Two boolean values

**Logic**:
1. Extract work order number from WO data
2. Search Design Manager projects by name (matches WO number)
3. If project found, check for schemes (designs)
4. Return true/false for each condition

#### `activate_design_for_wo(wo)`

**Purpose**: Find and activate the design for a work order

**Parameters**:
- `wo` - Work order property list

**Returns**:
- `scheme` - The activated scheme, or `_unset` if failed

**Logic**:
1. Find project by work order number
2. Get first scheme from project
3. Call `swg_dsn_admin_engine.activate_design(scheme)`
4. Return activated scheme

#### `generate_boq_excel()`

**Purpose**: Generate BoQ as Excel file

**Process**:
1. Validate work order selected
2. Check project and design exist
3. Activate the design
4. Call global `create_boq()` procedure
5. Display success message

**Global Procedure Called**:
- `create_boq()` - Defined in `rwwi_astri_boq.magik`

#### `generate_boq_json()`

**Purpose**: Generate BoQ as JSON property list

**Process**:
1. Validate work order selected
2. Check project and design exist
3. Activate the design
4. Call global `create_pl_boq("json")` procedure
5. Display result summary with first 5 items

**Global Procedure Called**:
- `create_pl_boq(type)` - Defined in `rwwi_astri_boq.magik`

### BoQ Data Structure

The `create_pl_boq` procedure returns a rope of property lists, each containing:

```magik
property_list.new_with(
    :type,      "json",           # BoQ type
    :code,      "200001033",      # Material/service code
    :object,    "Sling Wire",     # Object category
    :name,      "Instalasi...",   # Item description
    :material,  24,               # Material quantity (or _unset)
    :service,   150.5             # Service quantity (or _unset)
)
```

### Object Types Counted

The BoQ generation counts the following design objects:

1. **Poles** - By type (7m, 9m, various diameters)
2. **Cables** - By core count (24, 36, 48, 72, 96, 144, 288 cores)
3. **Closures** - By type and capacity (inline/dome, various cores)
4. **FDT** - By capacity (48, 72, 96, 144, 288, 576 cores)
5. **FAT** - By type (pole/pedestal mounted)
6. **Sling Wire** - By total length

## Error Handling

### Common Errors

**"Please select a work order first"**
- Cause: No work order selected in table
- Solution: Select a work order row

**"Project and design must exist before generating BoQ"**
- Cause: Project or design not found for work order
- Solution: Run "Migrate to Design" first

**"Failed to activate design. Cannot generate BoQ."**
- Cause: Design activation failed
- Solution: Check Design Manager is accessible and design is valid

**"No Design Selected" (in BoQ procedure)**
- Cause: Design not active when BoQ procedure runs
- Solution: System should auto-activate; report as bug if occurs

## Environment Setup

### Excel Template Configuration

Set the environment variable before starting Smallworld:

**Windows (Command Prompt)**:
```cmd
set TEMPLATE_BOQ=C:\template_boq\BoQ_Template.xlsx
```

**Windows (System Environment Variables)**:
1. Right-click "This PC" → Properties
2. Advanced System Settings → Environment Variables
3. Add new variable:
   - Name: `TEMPLATE_BOQ`
   - Value: `C:\path\to\template\BoQ_Template.xlsx`

### Template Requirements

The Excel template must contain:

- Sheet named: **"BoQ Roll Out ODN"**
- Predefined cell ranges for each material/service category
- See `rwwi_astri_boq.magik` lines 563-693 for expected cell locations

## Troubleshooting

### BoQ Buttons Always Disabled

**Check**:
1. Is a work order selected?
2. Does the work order have a `wo_number` field?
3. Does a project exist with name = `wo_number`?
4. Does the project have any schemes?

**Debug**:
```magik
# Check projects exist
swg_dsn_admin_engine.projects.size

# Check specific project
_for prj _over swg_dsn_admin_engine.projects.fast_elements()
_loop
    write(prj.name, " - ", prj.top_schemes.size)
_endloop
```

### Excel BoQ Fails to Generate

**Check**:
1. Is `TEMPLATE_BOQ` environment variable set?
2. Does the template file exist at that path?
3. Is Excel installed on the system?
4. Does the template have the correct sheet name?

**Debug**:
```magik
# Check environment variable
system.getenv("TEMPLATE_BOQ")

# Check file exists
system.file_exists?(system.getenv("TEMPLATE_BOQ"))
```

### JSON BoQ Returns Empty Results

**Check**:
1. Is the design activated?
2. Does the design contain objects?
3. Are objects in the correct collections?

**Debug**:
```magik
# Check current design
s << swg_dsn_admin_engine.get_current_job()
write(s.name, " - ", s.id)

# Check design objects
change_set << mit_scheme_record_change_set.new(s)
write("Total changes:", change_set.size)
```

## Code Files Modified

1. **rwwi_astri_workorder_dialog.magik**
   - Added `check_project_and_design_exist()` method
   - Added `activate_design_for_wo()` method
   - Added `generate_boq_excel()` method
   - Added `generate_boq_json()` method
   - Modified button creation to use new BoQ buttons
   - Updated button enable logic in `update_detail_panel()`

2. **rwwi_astri_boq.magik**
   - Contains global procedures (no changes made)
   - `create_boq()` - Excel generation
   - `create_pl_boq(type)` - JSON generation

## Future Enhancements

Potential improvements:

1. **Custom Template Selection** - Allow users to select template file from UI
2. **BoQ Export** - Export JSON BoQ to CSV or other formats
3. **Multi-Design BoQ** - Aggregate BoQ across multiple designs
4. **Custom Filtering** - Filter BoQ by object type or segment
5. **BoQ Comparison** - Compare BoQ between different design versions
6. **Auto-Refresh** - Detect when design changes and update button state

## Support

For issues or questions:

1. Check console output (`write()` statements) for diagnostic information
2. Verify all prerequisites are met
3. Test with a simple migrated design first
4. Review `rwwi_astri_boq.magik` for BoQ logic details

---

**Last Updated**: 2025-11-12
**Version**: 1.0
**Author**: Claude Code Assistant
