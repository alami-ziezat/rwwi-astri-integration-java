# Smallworld Project & Design Creation Guide

## Overview

Guide for creating Smallworld projects and designs (schemes) programmatically, specifically for ASTRI KML migration to design workflow.

## Analysis Date
2025-10-31

## Smallworld Design Management Architecture

### Core Components

**1. swg_dsn_admin_engine**
- Central engine for design management operations
- Creates and manages projects (jobs) and schemes (designs)
- Handles design alternatives and versioning

**2. swg_dsn_project**
- Represents a project (job)
- Contains metadata (name, area, datasets, attributes)
- Parent container for schemes

**3. swg_dsn_scheme**
- Represents a design/scheme within a project
- Contains actual design data in separate design alternatives
- Links to specific datasets

**4. Design Alternative**
- Actual working environment for design data
- Each scheme can have multiple alternatives
- Design objects created in alternative, not base database

### Key Concepts

**Project (Job):**
- High-level container
- Defines geographical area
- Associates with datasets
- Has custom attributes (job_type, etc.)

**Scheme (Design):**
- Design within a project
- Can have multiple alternatives
- Each alternative is a separate "version" of design
- Objects created in design alternative are isolated from base data

**Design Alternative:**
- Working environment for design changes
- Changes isolated from base database
- Can be posted/merged to base when finalized
- Supports versioning and conflict resolution

## Creating Project and Design

### Method 1: Using swg_dsn_admin_engine (Recommended)

**Based on:** `C:\Smallworld\rwi_custom_product\modules\rwi_provisioning_integration\source\rwi_provisioning_swg_dsn_design_manager.magik`

#### Step 1: Setup Environment

```magik
_dynamic !current_grs!
_dynamic !current_world!
_dynamic !swg_dsn_initiator!

# Get GRS (Graphics Rendering System) from database
_local grs << gis_program_manager.databases[:gis].grs

# Set dynamic globals
!current_grs! << grs
!current_world! << swg_dsn_admin_engine.ensure_design_admin_world(grs.world)
```

#### Step 2: Define Project Parameters

```magik
# Project name (job name)
_local project_name << "ASTRI_KML_" + date_time.now().write_string

# Datasets to include in project
_local dsnames << set.new_with(:gis)  # Usually just :gis

# Project area (geographical bounds)
# Option A: Use current view bounds
_local world_bounds << grs.world_bounds
_local area_sr << sector_rope.check_type(world_bounds.outline)

# Option B: Use specific area (if you have polygon)
# _local area_sr << your_polygon_sector_rope

# Project attributes (custom fields in swg_dsn_project)
_local prj_attrs << property_list.new_with(
	:job_type, "FTTH",           # Or other job type
	:description, "ASTRI KML Migration",
	:status, "Active"
	# Add other custom attributes as needed
)
```

#### Step 3: Create Project

```magik
_try
	# Create project
	_local prj << swg_dsn_admin_engine.create_project(
		project_name,   # Name
		dsnames,        # Dataset names (set)
		area_sr,        # Project area (sector_rope)
		prj_attrs       # Additional attributes
	)

	write("Project created: ", prj.name, " (ID: ", prj.id, ")")

_when error
	write("ERROR creating project: ", condition.report_contents_string)
_endtry
```

#### Step 4: Create Scheme (Design)

```magik
_try
	# Scheme name
	_local scheme_name << "Design_" + date_time.now().write_string

	# Scheme attributes
	_local sch_attrs << property_list.new_with(
		:description, "KML Migration Design",
		:designer, gis_program_manager.login_name
		# Add other custom attributes
	)

	# Determine datasets for scheme
	# Usually inherit from project
	_local scheme_dsnames << set.new()
	_for ds_info _over prj.dataset_infos.fast_elements()
	_loop
		scheme_dsnames.add(ds_info.dataset_name.as_symbol())
	_endloop

	# Create scheme
	_local sch << swg_dsn_admin_engine.create_scheme(
		prj,              # Parent project
		_unset,           # Parent scheme (for child schemes, usually _unset)
		scheme_name,      # Name
		scheme_dsnames,   # Dataset names
		prj.design_partition,  # Design partition from project
		sch_attrs         # Additional attributes
	)

	write("Scheme created: ", sch.name, " (ID: ", sch.id, ")")

_when error
	write("ERROR creating scheme: ", condition.report_contents_string)

	# Cleanup: Delete project if scheme creation failed
	_if prj _isnt _unset _andif prj.is_valid?
	_then
		swg_dsn_admin_engine.perform_short_transaction(
			_unset, :|do_delete_records()|, {{prj}})
	_endif
_endtry
```

## Activating and Working in Design

### Activate Design

```magik
# Get design manager
_local app << smallworld_product.application(:pni)  # Or your application
_local ddm << app.plugin(:dm_design_browser).design_manager

# Activate the scheme
ddm.activate_design(sch)

write("Design activated: ", sch.full_design_name)
```

### Get Current Design Context

```magik
# Get current project
_local current_project << swg_dsn_admin_engine.get_current_project()

_if current_project _is _unset
_then
	write("No project is active")
_else
	write("Current project: ", current_project.name, " (ID: ", current_project.id, ")")
_endif

# Get current scheme (design)
_local current_scheme << swg_dsn_admin_engine.active_scheme

_if current_scheme _is _unset
_then
	write("No scheme is active")
_else
	write("Current scheme: ", current_scheme.name, " (ID: ", current_scheme.id, ")")
_endif

# Get current build phase (if applicable)
_local current_phase << swg_dsn_admin_engine.active_build_phase

_if current_phase _isnt _unset
_then
	write("Current phase: ", current_phase.name)
_endif
```

### Working in Design Alternative

When a design is activated, **all database operations automatically go to the design alternative**, not the base database!

```magik
# After activating design:
ddm.activate_design(sch)

# Now you can create objects normally - they go to design alternative
_local database << gis_program_manager.databases[:gis]
_local pole_col << database.collections[:pole]

# This creates pole in DESIGN ALTERNATIVE, not base database
_local prop_values << property_list.new_with(
	:location, some_location,
	:telco_pole_tag, "POLE-001",
	:type, "Pole 7-4"
)

_local rec_trans << record_transaction.new_insert(pole_col, prop_values)
_local result << rec_trans.run()

# Result is in design alternative!
write("Pole created in design: ", result.telco_pole_tag)
```

### Deactivate Design

```magik
# Deactivate current design (return to base view)
ddm.activate_design(_unset)

write("Design deactivated, back to base view")
```

## Complete Example: Create Project, Design, and Add Objects

```magik
_method astri_design_creator.create_and_populate_design(project_name, design_name, placemarks)
	## Create new project and design, then populate with objects

	_dynamic !current_grs!
	_dynamic !current_world!

	_local database << gis_program_manager.databases[:gis]
	_local grs << database.grs
	_local app << smallworld_product.application(:pni)
	_local ddm << app.plugin(:dm_design_browser).design_manager

	_local prj << _unset
	_local sch << _unset

	_protect
		# Setup environment
		!current_grs! << grs
		!current_world! << swg_dsn_admin_engine.ensure_design_admin_world(grs.world)

		# Create project
		write("Creating project: ", project_name)

		_local dsnames << set.new_with(:gis)
		_local world_bounds << grs.world_bounds
		_local area_sr << sector_rope.check_type(world_bounds.outline)
		_local prj_attrs << property_list.new_with(
			:job_type, "FTTH",
			:description, "ASTRI KML Migration"
		)

		prj << swg_dsn_admin_engine.create_project(
			project_name, dsnames, area_sr, prj_attrs)

		write("Project created: ", prj.name, " (ID: ", prj.id, ")")

		# Create scheme
		write("Creating design: ", design_name)

		_local scheme_dsnames << set.new()
		_for ds_info _over prj.dataset_infos.fast_elements()
		_loop
			scheme_dsnames.add(ds_info.dataset_name.as_symbol())
		_endloop

		_local sch_attrs << property_list.new_with(
			:description, "KML Migration Design",
			:designer, gis_program_manager.login_name
		)

		sch << swg_dsn_admin_engine.create_scheme(
			prj, _unset, design_name, scheme_dsnames,
			prj.design_partition, sch_attrs)

		write("Scheme created: ", sch.name, " (ID: ", sch.id, ")")

		# Activate design
		write("Activating design...")
		ddm.activate_design(sch)
		write("Design activated")

		# Now create objects from placemarks
		write("Creating objects in design...")

		_local migrator << astri_design_migrator.new(database, "migration_uuid", "POP1", prj.id, _unset)
		_local stats << migrator.migrate_placemarks(placemarks)

		write("Migration complete!")
		write("  Poles: ", stats[:poles])
		write("  Aerial routes: ", stats[:aerial_routes])
		write("  Total: ", stats[:poles] + stats[:aerial_routes])

		# Return project and scheme
		>> (prj, sch, stats)

	_protection
		# Cleanup on error
		_if sch _is _unset _andif prj _isnt _unset _andif prj.is_valid?
		_then
			write("Cleaning up failed project...")
			swg_dsn_admin_engine.perform_short_transaction(
				_unset, :|do_delete_records()|, {{prj}})
		_endif

		# Note: Don't deactivate design here - leave it active for review
	_endprotect
_endmethod
```

## Integration with migrate_to_design()

### Updated Work Order Dialog Method

```magik
_method rwwi_astri_workorder_dialog.migrate_to_design()
	## Migrate KML data to design Smallworld objects
	## Creates new project and design for isolated design work

	_if .selected_wo _is _unset
	_then
		_self.user_info("Please select a work order first")
		_return
	_endif

	_local wo << .selected_wo
	_local kmz_uuid << wo[:kmz_uuid]

	_if kmz_uuid _is _unset _orif kmz_uuid = ""
	_then
		_self.user_error("No KMZ file associated with this work order")
		_return
	_endif

	_try
		# Download and parse KML
		write("Downloading KML file for UUID: ", kmz_uuid)
		_local result << rwwi_java_interface.download_kml_from_kmz(kmz_uuid)

		_if result _is _unset
		_then
			_self.user_error("Failed to download KML file")
			_return
		_endif

		_local xml_doc << simple_xml.read_element_string(result)
		_local success_elem << xml_doc.element_matching_name(:success)
		_local kml_file_path_elem << xml_doc.element_matching_name(:kml_file_path)

		_if success_elem _isnt _unset _andif
		    success_elem.xml_result = "true" _andif
		    kml_file_path_elem _isnt _unset
		_then
			_local kml_file_path << kml_file_path_elem.xml_result
			write("KML file downloaded to:", kml_file_path)

			# Parse KML
			write("Parsing KML file...")
			_local parser << astri_kml_parser.new(kml_file_path)
			_local placemarks << parser.parse()
			write("KML parsing complete. Found", placemarks.size, "placemarks")

			# Generate project and design names
			_local timestamp << date_time.now().write_string
			_local project_name << "ASTRI_" + kmz_uuid.subseq(1, 8.min(kmz_uuid.size))
			_local design_name << "KML_Migration_" + timestamp

			# Create project and design, populate with objects
			write("Creating project and design...")

			_local creator << astri_design_creator.new()
			_local (prj, sch, stats) << creator.create_and_populate_design(
				project_name, design_name, placemarks)

			# Display results
			_local msg << write_string(
				"=== Design Migration Complete! ===", %newline,
				"Project: ", prj.name, " (ID: ", prj.id, ")", %newline,
				"Design: ", sch.name, " (ID: ", sch.id, ")", %newline,
				"=" * 50, %newline,
				"Aerial Routes:   ", stats[:aerial_routes], %newline,
				"Poles:           ", stats[:poles], %newline,
				"Sheaths:         ", stats[:sheaths], %newline,
				"Sling Wires:     ", stats[:sling_wires], %newline,
				"Demand Points:   ", stats[:demand_points], %newline,
				"Errors:          ", stats[:errors], %newline,
				"Skipped:         ", stats[:skipped], %newline,
				"=" * 50, %newline,
				"Total objects:   ", stats[:aerial_routes] + stats[:poles] + stats[:sheaths] + stats[:sling_wires] + stats[:demand_points], %newline,
				"=" * 50, %newline,
				%newline,
				"Design is now active. All objects are in design alternative.", %newline,
				"Review objects and post design when ready.")

			_self.user_info(msg)

		_else
			_local error_elem << xml_doc.element_matching_name(:error)
			_local error_msg << _if error_elem _isnt _unset
					    _then >> error_elem.xml_result
					    _else >> "Unknown error"
					    _endif
			_self.user_error(write_string("Failed to download KML file:", %newline, error_msg))
		_endif

	_when error
		_self.user_error(write_string("Error migrating to design:", %newline,
			condition.report_contents_string))
	_endtry
_endmethod
```

## Key Advantages of Using Design Approach

### 1. Isolation
- **Design objects separate from base data**
- No impact on production database
- Can experiment without risk
- Easy rollback (just delete design)

### 2. Versioning
- **Each design is a version**
- Can create multiple designs from same KML
- Compare different approaches
- History of design changes

### 3. Review Workflow
- **Objects created in design for review**
- Stakeholders review in design alternative
- Make changes without affecting base
- Post to base only when approved

### 4. Conflict Management
- **Smallworld handles conflicts automatically**
- If base data changes while designing, SW detects conflicts
- Conflict resolution tools available
- Ensures data consistency

### 5. No Database Mode Switching
- **No need to switch readonly/writable**
- Design alternatives always writable
- Simpler code
- Safer operations

### 6. Multi-user Support
- **Multiple users can work on different designs**
- Each design is isolated
- No locking issues
- Parallel workflows supported

## Design Lifecycle

### 1. Create Phase
```
User selects KML → Create Project → Create Design → Activate Design
```

### 2. Populate Phase
```
Parse KML → Create objects in design → Review statistics
```

### 3. Review Phase
```
Review objects in design → Make adjustments → Validate
```

### 4. Finalize Phase (Future)
```
Approve design → Post to base → Archive design
```

### 5. Cleanup Phase
```
Delete design (if rejected) → Or keep for reference
```

## Common Patterns

### Get Current Design Info

```magik
_method get_current_design_info()
	_local current_project << swg_dsn_admin_engine.get_current_project()
	_local current_scheme << swg_dsn_admin_engine.active_scheme

	_if current_project _is _unset
	_then
		>> "No active project"
	_endif

	_if current_scheme _is _unset
	_then
		>> "Project: " + current_project.name + ", No active scheme"
	_endif

	>> "Project: " + current_project.name + ", Design: " + current_scheme.name
_endmethod
```

### Check if Working in Design

```magik
_method is_design_active?()
	>> swg_dsn_admin_engine.active_scheme _isnt _unset
_endmethod
```

### Auto-generate Project Name

```magik
_method generate_project_name(kmz_uuid)
	_local timestamp << date_time.now().format_string("%Y%m%d_%H%M%S")
	_local short_uuid << kmz_uuid.subseq(1, 8.min(kmz_uuid.size))

	>> "ASTRI_" + short_uuid + "_" + timestamp
_endmethod
```

## Error Handling

### Project Creation Failures

```magik
_try
	prj << swg_dsn_admin_engine.create_project(...)
_when error
	write("Failed to create project: ", condition.report_contents_string)

	# Check specific errors
	_if condition.name _is :user_error
	_then
		write("User error: Check parameters")
	_elif condition.name _is :duplicate_id
	_then
		write("Project with this name already exists")
	_endif
_endtry
```

### Scheme Creation Failures

```magik
_try
	sch << swg_dsn_admin_engine.create_scheme(...)
_when error
	write("Failed to create scheme: ", condition.report_contents_string)

	# Cleanup: Delete project if scheme creation failed
	_if prj _isnt _unset _andif prj.is_valid?
	_then
		swg_dsn_admin_engine.perform_short_transaction(
			_unset, :|do_delete_records()|, {{prj}})
		write("Project cleaned up")
	_endif
_endtry
```

## Files to Modify

### New File: astri_design_creator.magik

```magik
# C:\Smallworld\pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_design_creator.magik

_package sw
$

def_slotted_exemplar(:astri_design_creator, {})
$

_method astri_design_creator.new()
	>> _clone
_endmethod
$

_method astri_design_creator.create_and_populate_design(project_name, design_name, placemarks)
	# Implementation as shown above
_endmethod
$
```

### Update: load_list.txt

```
astri_kml_parser
astri_kml_migrator
astri_design_migrator
astri_design_creator    # NEW
test_astri_procs
```

### Update: rwwi_astri_workorder_dialog.magik

Update `migrate_to_design()` method as shown above.

## Testing Strategy

### Test 1: Create Empty Design

```magik
# Test creating project and design without objects
_local creator << astri_design_creator.new()
_local (prj, sch, stats) << creator.create_and_populate_design(
	"TEST_PROJECT", "TEST_DESIGN", rope.new())

# Verify project and scheme created
write("Project: ", prj.name)
write("Scheme: ", sch.name)
```

### Test 2: Create Design with Objects

```magik
# Test with sample placemarks
_local placemarks << _self.get_sample_placemarks()
_local (prj, sch, stats) << creator.create_and_populate_design(
	"TEST_PROJECT_2", "TEST_DESIGN_2", placemarks)

# Verify objects created in design
write("Objects created: ", stats[:poles])
```

### Test 3: Verify Design Isolation

```magik
# Before migration: Count poles in base
_local base_pole_count << gis_program_manager.databases[:gis].collections[:pole].size

# Create design and add poles
_local (prj, sch, stats) << creator.create_and_populate_design(...)

# After migration: Count poles in base (should be same!)
_local base_pole_count_after << gis_program_manager.databases[:gis].collections[:pole].size

# Verify base unchanged
_if base_pole_count = base_pole_count_after
_then
	write("SUCCESS: Base data unchanged, objects in design only")
_else
	write("FAIL: Base data was modified!")
_endif
```

## Summary

Using Smallworld's project and design (scheme) approach provides:

✅ **Isolation:** Design objects separate from base data
✅ **Safety:** No risk to production database
✅ **Versioning:** Each migration creates versioned design
✅ **Review:** Objects can be reviewed before finalizing
✅ **Conflict Management:** SW handles data conflicts automatically
✅ **No Mode Switching:** No need to toggle readonly/writable
✅ **Multi-user:** Multiple designs can coexist
✅ **Rollback:** Easy to delete entire design if needed

This approach is **better than switching database mode** because:
- More aligned with Smallworld best practices
- Safer (can't accidentally modify base data)
- Supports design review workflow
- Enables versioning and comparison
- Professional GIS design workflow

**Ready for implementation!**
