# BoQ Refactoring Plan - create_pl_boq to Class-Based Architecture

## Executive Summary

This document outlines a comprehensive plan to refactor the `create_pl_boq` global procedure (608 lines, lines 699-1306) into a modern object-oriented class-based architecture. The refactoring will create a new file `rwwi_astri_boq_generator.magik` containing the `rwwi_astri_boq_generator` exemplar with proper encapsulation, reusability, testability, and maintainability.

**Current State**: Large procedural code with 30+ hard-coded material definitions, repetitive property_list creation, no error handling, and global procedures.

**Target State**: Clean class-based design with data-driven catalog, reusable methods, comprehensive error handling, and full backward compatibility.

---

## Problem Analysis

### Current Code Issues

1. **Code Duplication** (Lines 731-1296)
   - 30+ nearly identical `property_list.new_with()` blocks
   - Same 6-parameter structure repeated: `:type`, `:code`, `:object`, `:name`, `:material`, `:service`
   - Hard-coded material codes, descriptions throughout

2. **Hard-Coded Data**
   - Material codes: "200001033", "200001047", etc.
   - Descriptions: "Instalasi strand wire/sling messenger 6 mm", etc.
   - Spec IDs: "Join Closure In-line 24 Core", "Pole 7 meter 3 inch", etc.
   - No central configuration

3. **Poor Separation of Concerns**
   - Single 608-line procedure doing everything
   - Counting logic mixed with output formatting
   - No abstraction layers

4. **No Error Handling**
   - No `_try _with errCond` blocks
   - Silent failures possible
   - No validation

5. **Global Variable Pollution**
   - Helper procedures use global variables without `_local`
   - Variables: `s`, `change_set`, `id`, `nama`, `owner`, etc.

6. **Repetitive Helper Procedures**
   - 6 helper procedures (`get_count_*`) follow identical pattern
   - Each recreates design retrieval and change_set logic
   - No code reuse

7. **Magic Numbers/Strings**
   - Closure IDs: "clr1", "clr2", ..., "clr13"
   - No symbolic constants
   - Difficult to maintain

8. **Limited Extensibility**
   - Adding new material types requires code changes in multiple places
   - No plugin architecture

---

## Proposed Architecture

### New Class: `rwwi_astri_boq_generator`

**File**: `rwwi_astri_integration_java/magik/rwwi_astri_workorder/source/rwwi_astri_boq_generator.magik`

**Purpose**: Encapsulate all BoQ generation logic with proper OO design

### Class Structure

```magik
#% text_encoding = iso8859_1
_package user
$

_pragma(classify_level=basic, topic={boq, generator})
def_slotted_exemplar(:rwwi_astri_boq_generator,
    {
        {:scheme, _unset},           # Current design scheme
        {:change_set, _unset},       # MIT scheme record change set
        {:output_type, "json"}       # Output type (json, xml, etc.)
    },
    :object)
$
```

### Slots

| Slot | Type | Purpose |
|------|------|---------|
| `scheme` | `mit_scheme` | Current design scheme from Design Manager |
| `change_set` | `mit_scheme_record_change_set` | Track design changes/objects |
| `output_type` | `string` | Output format ("json", "xml", etc.) |

---

## Material Catalog Design

### Catalog Structure

**Option 1: Constant Hash Table** (Recommended)
```magik
_pragma(classify_level=basic, topic={boq})
rwwi_astri_boq_generator.define_shared_constant(
    :MATERIAL_CATALOG,
    equality_hash_table.new_with(
        # Sling Wire
        :sling_wire, property_list.new_with(
            :code, "200001033",
            :object, "Sling Wire",
            :name, "Instalasi strand wire/sling messenger 6 mm",
            :material_slot, :service
        ),

        # FAT Types
        :fat_pole_16, property_list.new_with(
            :code, "200001047",
            :object, "FAT",
            :name, "Pole mounted outdoor type (16 ports type)",
            :material_slot, :material
        ),
        :fat_pedestal_16, property_list.new_with(
            :code, "100000824",
            :object, "FAT",
            :name, "Pedestal mounted type (16 ports type)",
            :material_slot, :material
        ),

        # FDT Types (6 types)
        :fdt_48, property_list.new_with(
            :code, "200001039",
            :object, "FDT",
            :name, "48 cores capacity pole mounted FDT",
            :material_slot, :material
        ),
        :fdt_72, property_list.new_with(
            :code, "200001040",
            :object, "FDT",
            :name, "72 cores capacity pole mounted FDT",
            :material_slot, :material
        ),
        :fdt_96, property_list.new_with(
            :code, "200001041",
            :object, "FDT",
            :name, "96 cores capacity pole mounted FDT",
            :material_slot, :material
        ),
        :fdt_144, property_list.new_with(
            :code, "200001042",
            :object, "FDT",
            :name, "144 cores capacity ground mounted FDT",
            :material_slot, :material
        ),
        :fdt_288, property_list.new_with(
            :code, "200001043",
            :object, "FDT",
            :name, "288 cores capacity ground mounted FDT",
            :material_slot, :material
        ),
        :fdt_576, property_list.new_with(
            :code, "200001044",
            :object, "FDT",
            :name, "576 cores capacity ground mounted FDT",
            :material_slot, :material
        ),

        # Poles (5 types)
        :pole_7m_3inch, property_list.new_with(
            :code, "200001055",
            :object, "Pole",
            :name, "Pengadaan Tiang 7 meter 3 inch",
            :material_slot, :material
        ),
        :pole_7m_4inch, property_list.new_with(
            :code, "200001183",
            :object, "Pole",
            :name, "Pengadaan Tiang 7 meter 4\", STEL L-003 1996",
            :material_slot, :material
        ),
        :pole_7m_5inch, property_list.new_with(
            :code, "200000187",
            :object, "Pole",
            :name, "Pengadaan Tiang 7 meter 5\", STEL L-003 1996",
            :material_slot, :material
        ),
        :pole_9m_4inch, property_list.new_with(
            :code, "200001181",
            :object, "Pole",
            :name, "Pengadaan Tiang Tunggal 9 meter 4\", STEL L-003 1996",
            :material_slot, :material
        ),
        :pole_9m_5inch, property_list.new_with(
            :code, "200000169",
            :object, "Pole",
            :name, "Pengadaan Tiang Tunggal 9 meter 5\", STEL L-003 1996",
            :material_slot, :material
        ),

        # Cables (7 types)
        :cable_24, property_list.new_with(
            :code, "200000100",
            :object, "Cable",
            :name, "FO core type SM G.652.D-ADSS 24 cores",
            :material_slot, :material
        ),
        :cable_36, property_list.new_with(
            :code, "200000975",
            :object, "Cable",
            :name, "FO core type SM G.652.D-ADSS 36 cores",
            :material_slot, :material
        ),
        :cable_48, property_list.new_with(
            :code, "200001038",
            :object, "Cable",
            :name, "FO core type SM G.652.D-ADSS 48 cores",
            :material_slot, :material
        ),
        :cable_96, property_list.new_with(
            :code, "200001630",
            :object, "Cable",
            :name, "FO core type SM G.652.D-ADSS 96 cores",
            :material_slot, :material
        ),
        :cable_144, property_list.new_with(
            :code, "200001030",
            :object, "Cable",
            :name, "FO core type SM G.652.D-ADSS 144 cores",
            :material_slot, :material
        ),
        :cable_288, property_list.new_with(
            :code, "200001015",
            :object, "Cable",
            :name, "FO core type SM G.652.D-ADSS 288 cores",
            :material_slot, :material
        ),

        # Closures - Inline (6 types)
        :closure_inline_24, property_list.new_with(
            :code, "200000182",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type In-line 24 Core",
            :material_slot, :material,
            :spec_id, "Join Closure In-line 24 Core"
        ),
        :closure_inline_36, property_list.new_with(
            :code, "200001048",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type In-line 36 Core",
            :material_slot, :material,
            :spec_id, "Join Closure In-line 36 Core"
        ),
        :closure_inline_48, property_list.new_with(
            :code, "200000159",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type In-line 48 Core",
            :material_slot, :material,
            :spec_id, "Join Closure In-line 48 Core"
        ),
        :closure_inline_96, property_list.new_with(
            :code, "200000179",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type In-line 96 Core",
            :material_slot, :material,
            :spec_id, "Join Closure In-line 96 Core"
        ),
        :closure_inline_144, property_list.new_with(
            :code, "200000186",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type In-line 144 Core",
            :material_slot, :material,
            :spec_id, "Join Closure In-line 144 Core"
        ),
        :closure_inline_288, property_list.new_with(
            :code, "200000155",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type In-line 288 Core",
            :material_slot, :material,
            :spec_id, "Join Closure In-line 288 Core"
        ),

        # Closures - Dome (7 types)
        :closure_dome_24, property_list.new_with(
            :code, "200000180",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type Dome 24 Core",
            :material_slot, :material,
            :spec_id, "Join Closure Dome 24 Core"
        ),
        :closure_dome_36, property_list.new_with(
            :code, "200001049",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type Dome 36 Core",
            :material_slot, :material,
            :spec_id, "Join Closure Dome 36 Core"
        ),
        :closure_dome_48, property_list.new_with(
            :code, "200000164",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type Dome 48 Core",
            :material_slot, :material,
            :spec_id, "Join Closure Dome 48 Core"
        ),
        :closure_dome_96, property_list.new_with(
            :code, "200000176",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type Dome 96 Core",
            :material_slot, :material,
            :spec_id, "Join Closure Dome 96 Core"
        ),
        :closure_dome_144, property_list.new_with(
            :code, "200000158",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type Dome 144 Core",
            :material_slot, :material,
            :spec_id, "Join Closure Dome 144 Core"
        ),
        :closure_dome_288, property_list.new_with(
            :code, "200000156",
            :object, "Closure",
            :name, "Fiber Optic Joint Closure Type Dome 288 Core",
            :material_slot, :material,
            :spec_id, "Join Closure Dome 288 Core"
        )
    ),
    :public
)
$
```

**Catalog Benefits**:
- Single source of truth for all material data
- Easy to add/modify materials without code changes
- Can be externalized to configuration file in future
- Maintains spec_id mapping for closure counting

---

## Method Design

### 1. Constructor and Initialization

#### `new()`
```magik
_method rwwi_astri_boq_generator.new()
    ## Create new BoQ generator instance
    >> _clone.init()
_endmethod
$
```

#### `init()`
```magik
_private _method rwwi_astri_boq_generator.init()
    ## Initialize generator
    .scheme << _unset
    .change_set << _unset
    .output_type << "json"
    >> _self
_endmethod
$
```

#### `new_for_current_design(output_type)`
```magik
_method rwwi_astri_boq_generator.new_for_current_design(_optional output_type)
    ## Factory method - creates generator for current active design
    ## Parameters:
    ##   output_type (optional) - "json", "xml", etc. Defaults to "json"
    ## Returns:
    ##   New generator instance or _unset if no design active

    _try _with errCond
        _local scheme << swg_dsn_admin_engine.get_current_job()
        _if scheme _is _unset
        _then
            write("LOG: ERROR - No design currently active")
            _return _unset
        _endif

        _local generator << _self.new()
        generator.set_scheme(scheme)
        _if output_type _isnt _unset
        _then
            generator.output_type << output_type
        _endif

        >> generator
    _when error
        write("LOG: ERROR creating BoQ generator:", errCond.report_contents_string)
        _return _unset
    _endtry
_endmethod
$
```

### 2. Configuration Methods

#### `set_scheme(scheme)`
```magik
_method rwwi_astri_boq_generator.set_scheme(scheme)
    ## Set the design scheme and create change set
    ## Parameters:
    ##   scheme - MIT scheme object

    _try _with errCond
        _if scheme _is _unset
        _then
            write("LOG: ERROR - Cannot set _unset scheme")
            condition.raise(:user_error, :string, "Scheme cannot be _unset")
        _endif

        .scheme << scheme
        .change_set << mit_scheme_record_change_set.new(scheme)

        write("LOG: BoQ generator configured for design:", scheme.name)
    _when error
        write("LOG: ERROR setting scheme:", errCond.report_contents_string)
        condition.raise(:user_error, :string, errCond)
    _endtry
_endmethod
$
```

### 3. Main Generation Method

#### `generate()`
```magik
_method rwwi_astri_boq_generator.generate()
    ## Generate complete BoQ for current design
    ## Returns: rope of property_lists with BoQ items

    _try _with errCond
        _if .scheme _is _unset _orif .change_set _is _unset
        _then
            write("LOG: ERROR - Generator not initialized properly")
            condition.raise(:user_error, :string,
                "BoQ generator not initialized. Call set_scheme() first.")
        _endif

        _local result << rope.new()

        write("LOG: Generating BoQ for design:", .scheme.name)

        # Generate BoQ items by category
        _self.add_sling_wire_items(result)
        _self.add_fat_items(result)
        _self.add_fdt_items(result)
        _self.add_pole_items(result)
        _self.add_cable_items(result)
        _self.add_closure_items(result)

        write("LOG: BoQ generation complete. Total items:", result.size)

        >> result
    _when error
        write("LOG: ERROR generating BoQ:", errCond.report_contents_string)
        condition.raise(:user_error, :string, errCond)
    _endtry
_endmethod
$
```

### 4. Category-Specific Generation Methods

#### `add_sling_wire_items(result)`
```magik
_private _method rwwi_astri_boq_generator.add_sling_wire_items(result)
    ## Add sling wire items to result
    ## Parameters:
    ##   result - rope to add items to

    _try _with errCond
        _local length << _self.count_sling_wire_length()
        _if length > 0
        _then
            _local item << _self.create_boq_item(:sling_wire, _unset, length)
            result.add(item)
        _endif
    _when error
        write("LOG: ERROR adding sling wire items:", errCond.report_contents_string)
    _endtry
_endmethod
$
```

#### `add_fat_items(result)`
```magik
_private _method rwwi_astri_boq_generator.add_fat_items(result)
    ## Add FAT items to result

    _try _with errCond
        _local (pole_count, pedestal_count) << _self.count_fat_types()

        _if pole_count > 0
        _then
            result.add(_self.create_boq_item(:fat_pole_16, pole_count, _unset))
        _endif

        _if pedestal_count > 0
        _then
            result.add(_self.create_boq_item(:fat_pedestal_16, pedestal_count, _unset))
        _endif
    _when error
        write("LOG: ERROR adding FAT items:", errCond.report_contents_string)
    _endtry
_endmethod
$
```

#### `add_fdt_items(result)`
```magik
_private _method rwwi_astri_boq_generator.add_fdt_items(result)
    ## Add FDT items to result

    _try _with errCond
        _local (f48, f72, f96, f144, f288, f576) << _self.count_fdt_types()

        _local fdt_types << {:fdt_48, :fdt_72, :fdt_96, :fdt_144, :fdt_288, :fdt_576}
        _local counts << {f48, f72, f96, f144, f288, f576}

        _for i _over 1.upto(fdt_types.size)
        _loop
            _if counts[i] > 0
            _then
                result.add(_self.create_boq_item(fdt_types[i], counts[i], _unset))
            _endif
        _endloop
    _when error
        write("LOG: ERROR adding FDT items:", errCond.report_contents_string)
    _endtry
_endmethod
$
```

#### `add_pole_items(result)`
```magik
_private _method rwwi_astri_boq_generator.add_pole_items(result)
    ## Add pole items to result

    _try _with errCond
        _local (p7_3, p7_4, p7_5, p9_4, p9_5) << _self.count_pole_types()

        _local pole_types << {:pole_7m_3inch, :pole_7m_4inch, :pole_7m_5inch,
                              :pole_9m_4inch, :pole_9m_5inch}
        _local counts << {p7_3, p7_4, p7_5, p9_4, p9_5}

        _for i _over 1.upto(pole_types.size)
        _loop
            _if counts[i] > 0
            _then
                result.add(_self.create_boq_item(pole_types[i], counts[i], _unset))
            _endif
        _endloop
    _when error
        write("LOG: ERROR adding pole items:", errCond.report_contents_string)
    _endtry
_endmethod
$
```

#### `add_cable_items(result)`
```magik
_private _method rwwi_astri_boq_generator.add_cable_items(result)
    ## Add cable items to result

    _try _with errCond
        _local (c24, c36, c48, c96, c144, c288) << _self.count_cable_types()

        _local cable_types << {:cable_24, :cable_36, :cable_48,
                               :cable_96, :cable_144, :cable_288}
        _local lengths << {c24, c36, c48, c96, c144, c288}

        _for i _over 1.upto(cable_types.size)
        _loop
            _if lengths[i] > 0
            _then
                result.add(_self.create_boq_item(cable_types[i], lengths[i], _unset))
            _endif
        _endloop
    _when error
        write("LOG: ERROR adding cable items:", errCond.report_contents_string)
    _endtry
_endmethod
$
```

#### `add_closure_items(result)`
```magik
_private _method rwwi_astri_boq_generator.add_closure_items(result)
    ## Add closure items to result

    _try _with errCond
        _local closure_counts << _self.count_closure_types()

        # Closure counts returned as hash: spec_id -> count
        _for catalog_key, catalog_entry _over _self.MATERIAL_CATALOG.fast_keys_and_elements()
        _loop
            _if catalog_entry[:object] = "Closure"
            _then
                _local spec_id << catalog_entry[:spec_id]
                _local count << closure_counts[spec_id]

                _if count _isnt _unset _andif count > 0
                _then
                    result.add(_self.create_boq_item(catalog_key, count, _unset))
                _endif
            _endif
        _endloop
    _when error
        write("LOG: ERROR adding closure items:", errCond.report_contents_string)
    _endtry
_endmethod
$
```

### 5. Item Creation Helper

#### `create_boq_item(catalog_key, material_qty, service_qty)`
```magik
_private _method rwwi_astri_boq_generator.create_boq_item(catalog_key, material_qty, service_qty)
    ## Create a BoQ item property_list from catalog
    ## Parameters:
    ##   catalog_key - Symbol key in MATERIAL_CATALOG
    ##   material_qty - Quantity for :material slot (or _unset)
    ##   service_qty - Quantity for :service slot (or _unset)
    ## Returns:
    ##   property_list with BoQ item data

    _try _with errCond
        _local catalog_entry << _self.MATERIAL_CATALOG[catalog_key]
        _if catalog_entry _is _unset
        _then
            write("LOG: ERROR - Unknown catalog key:", catalog_key)
            condition.raise(:user_error, :string,
                write_string("Unknown material catalog key: ", catalog_key))
        _endif

        # Determine which slot gets which quantity
        _local mat_value << _unset
        _local svc_value << _unset

        _local slot_type << catalog_entry[:material_slot]
        _if slot_type = :material
        _then
            mat_value << material_qty
            svc_value << service_qty
        _elif slot_type = :service
        _then
            mat_value << service_qty
            svc_value << material_qty
        _endif

        # Create property list
        _local item << property_list.new_with(
            :type, .output_type,
            :code, catalog_entry[:code],
            :object, catalog_entry[:object],
            :name, catalog_entry[:name],
            :material, mat_value,
            :service, svc_value
        )

        >> item
    _when error
        write("LOG: ERROR creating BoQ item:", errCond.report_contents_string)
        condition.raise(:user_error, :string, errCond)
    _endtry
_endmethod
$
```

### 6. Object Counting Methods

#### `count_sling_wire_length()`
```magik
_private _method rwwi_astri_boq_generator.count_sling_wire_length()
    ## Count total sling wire length from design
    ## Returns: float - total length

    _try _with errCond
        _local total_length << 0.0

        _for record _over .change_set.fast_elements()
        _loop
            _local col_name << record.current_record.source_collection.name
            _if col_name _is :sling_wire
            _then
                _local geom << record.current_record.geometry
                _if geom _isnt _unset
                _then
                    total_length +<< geom.length
                _endif
            _endif
        _endloop

        >> total_length
    _when error
        write("LOG: ERROR counting sling wire:", errCond.report_contents_string)
        >> 0.0
    _endtry
_endmethod
$
```

#### `count_fat_types()`
```magik
_private _method rwwi_astri_boq_generator.count_fat_types()
    ## Count FAT types from design
    ## Returns: (pole_count, pedestal_count)

    _try _with errCond
        _local pole_count << 0
        _local pedestal_count << 0

        _for record _over .change_set.fast_elements()
        _loop
            _local col_name << record.current_record.source_collection.name
            _if col_name _is :fat
            _then
                _local spec << record.current_record.spec_id
                _if spec.index_of_seq("Pole") _isnt _unset
                _then
                    pole_count +<< 1
                _elif spec.index_of_seq("Pedestal") _isnt _unset
                _then
                    pedestal_count +<< 1
                _endif
            _endif
        _endloop

        >> pole_count, pedestal_count
    _when error
        write("LOG: ERROR counting FAT types:", errCond.report_contents_string)
        >> 0, 0
    _endtry
_endmethod
$
```

#### `count_fdt_types()`
```magik
_private _method rwwi_astri_boq_generator.count_fdt_types()
    ## Count FDT types by capacity
    ## Returns: (f48, f72, f96, f144, f288, f576)

    _try _with errCond
        _local counts << equality_hash_table.new()
        counts["48"] << 0
        counts["72"] << 0
        counts["96"] << 0
        counts["144"] << 0
        counts["288"] << 0
        counts["576"] << 0

        _for record _over .change_set.fast_elements()
        _loop
            _local col_name << record.current_record.source_collection.name
            _if col_name _is :fdt
            _then
                _local spec << record.current_record.spec_id
                # Parse capacity from spec_id
                _for capacity _over counts.keys()
                _loop
                    _if spec.index_of_seq(capacity) _isnt _unset
                    _then
                        counts[capacity] +<< 1
                        _leave
                    _endif
                _endloop
            _endif
        _endloop

        >> counts["48"], counts["72"], counts["96"],
           counts["144"], counts["288"], counts["576"]
    _when error
        write("LOG: ERROR counting FDT types:", errCond.report_contents_string)
        >> 0, 0, 0, 0, 0, 0
    _endtry
_endmethod
$
```

#### `count_pole_types()`
```magik
_private _method rwwi_astri_boq_generator.count_pole_types()
    ## Count pole types by height and diameter
    ## Returns: (p7_3, p7_4, p7_5, p9_4, p9_5)

    _try _with errCond
        _local p7_3 << 0
        _local p7_4 << 0
        _local p7_5 << 0
        _local p9_4 << 0
        _local p9_5 << 0

        _for record _over .change_set.fast_elements()
        _loop
            _local col_name << record.current_record.source_collection.name
            _if col_name _is :pole
            _then
                _local spec << record.current_record.spec_id
                _if spec.index_of_seq("Pole 7 meter 3 inch") _isnt _unset
                _then
                    p7_3 +<< 1
                _elif spec.index_of_seq("Pole 7 meter 4 inch") _isnt _unset
                _then
                    p7_4 +<< 1
                _elif spec.index_of_seq("Pole 7 meter 5 inch") _isnt _unset
                _then
                    p7_5 +<< 1
                _elif spec.index_of_seq("Pole 9 meter 4 inch") _isnt _unset
                _then
                    p9_4 +<< 1
                _elif spec.index_of_seq("Pole 9 meter 5 inch") _isnt _unset
                _then
                    p9_5 +<< 1
                _endif
            _endif
        _endloop

        >> p7_3, p7_4, p7_5, p9_4, p9_5
    _when error
        write("LOG: ERROR counting pole types:", errCond.report_contents_string)
        >> 0, 0, 0, 0, 0
    _endtry
_endmethod
$
```

#### `count_cable_types()`
```magik
_private _method rwwi_astri_boq_generator.count_cable_types()
    ## Count cable types by core count and total length
    ## Returns: (c24, c36, c48, c96, c144, c288)

    _try _with errCond
        _local lengths << equality_hash_table.new()
        lengths["24"] << 0.0
        lengths["36"] << 0.0
        lengths["48"] << 0.0
        lengths["96"] << 0.0
        lengths["144"] << 0.0
        lengths["288"] << 0.0

        _for record _over .change_set.fast_elements()
        _loop
            _local col_name << record.current_record.source_collection.name
            _if col_name _is :fo_cable
            _then
                _local spec << record.current_record.spec_id
                _local geom << record.current_record.geometry
                _local length << _if geom _isnt _unset
                                 _then >> geom.length
                                 _else >> 0.0
                                 _endif

                # Match core count
                _for cores _over lengths.keys()
                _loop
                    _if spec.index_of_seq(cores + " cores") _isnt _unset
                    _then
                        lengths[cores] +<< length
                        _leave
                    _endif
                _endloop
            _endif
        _endloop

        >> lengths["24"], lengths["36"], lengths["48"],
           lengths["96"], lengths["144"], lengths["288"]
    _when error
        write("LOG: ERROR counting cable types:", errCond.report_contents_string)
        >> 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    _endtry
_endmethod
$
```

#### `count_closure_types()`
```magik
_private _method rwwi_astri_boq_generator.count_closure_types()
    ## Count closure types by spec_id
    ## Returns: equality_hash_table mapping spec_id to count

    _try _with errCond
        _local counts << equality_hash_table.new()

        _for record _over .change_set.fast_elements()
        _loop
            _local col_name << record.current_record.source_collection.name
            _if col_name _is :sheath_splice
            _then
                _local spec << record.current_record.spec_id
                _local current << counts[spec]
                _if current _is _unset
                _then
                    counts[spec] << 1
                _else
                    counts[spec] << current + 1
                _endif
            _endif
        _endloop

        >> counts
    _when error
        write("LOG: ERROR counting closure types:", errCond.report_contents_string)
        >> equality_hash_table.new()
    _endtry
_endmethod
$
```

---

## Migration Strategy

### Phase 1: Create New Class (Non-Breaking)

1. **Create new file**: `rwwi_astri_boq_generator.magik`
2. **Implement all methods** as designed above
3. **Add to load_list.txt**
4. **Test independently** without touching old code

### Phase 2: Create Wrapper (Maintain Compatibility)

Update `rwwi_astri_boq.magik` to use new class:

```magik
_global create_pl_boq <<
_proc(type)
    ## DEPRECATED: Use rwwi_astri_boq_generator.new_for_current_design() instead
    ## This procedure maintained for backward compatibility

    _try _with errCond
        # Use new class-based implementation
        _local generator << rwwi_astri_boq_generator.new_for_current_design(type)
        _if generator _is _unset
        _then
            write("No Design Selected")
            _return rope.new()
        _endif

        >> generator.generate()
    _when error
        write("LOG: ERROR in create_pl_boq:", errCond.report_contents_string)
        _return rope.new()
    _endtry
_endproc
$
```

**Benefits**:
- Zero breaking changes
- All existing code continues to work
- New code can use class directly

### Phase 3: Update Dialog (Gradual Migration)

Update `generate_boq_json()` in dialog to use new class:

```magik
_method rwwi_astri_workorder_dialog.generate_boq_json()
    _if .selected_wo _is _unset
    _then
        _self.user_info("Please select a work order first")
        _return
    _endif

    _local wo << .selected_wo
    _local (has_project, has_design) << _self.check_project_and_design_exist(wo)
    _if _not (has_project _and has_design)
    _then
        _self.user_error("Project and design must exist before generating BoQ")
        _return
    _endif

    _try _with errCond
        _local scheme << _self.activate_design_for_wo(wo)
        _if scheme _is _unset
        _then
            _self.user_error("Failed to activate design. Cannot generate BoQ.")
            _return
        _endif

        # NEW: Use class-based generator
        write("Creating BoQ generator...")
        _local generator << rwwi_astri_boq_generator.new_for_current_design("json")
        _if generator _is _unset
        _then
            _self.user_error("Failed to create BoQ generator")
            _return
        _endif

        write("Generating BoQ...")
        _local boq_items << generator.generate()

        # Display summary (existing code)
        _local msg << write_string("BoQ JSON generation completed successfully", %newline,
            "Design: ", scheme.name, %newline,
            "Total items: ", boq_items.size)

        _if boq_items.size > 0
        _then
            msg +<< write_string(%newline, %newline, "Sample items (first 5):", %newline)
            _local max_display << min(5, boq_items.size)
            _for i _over 1.upto(max_display)
            _loop
                _local item << boq_items[i]
                msg +<< write_string("  - ", item[:object], ": ", item[:name], %newline,
                    "    Code: ", item[:code],
                    ", Material: ", item[:material],
                    ", Service: ", item[:service], %newline)
            _endloop
        _endif

        _self.user_info(msg)
    _when error
        write("LOG: Error generating BoQ as JSON:", errCond.report_contents_string)
        _self.user_error(write_string("Error generating BoQ as JSON:", %newline,
            errCond.report_contents_string))
    _endtry
_endmethod
$
```

### Phase 4: Deprecate Old Helper Procedures (Future)

Mark old procedures as deprecated but keep for compatibility:

```magik
_global get_count_closure <<
_proc()
    ## DEPRECATED: Use rwwi_astri_boq_generator.count_closure_types() instead
    ## Maintained for backward compatibility only

    # Keep existing implementation
    # ...
_endproc
$
```

---

## Benefits of Refactoring

### Code Quality

| Aspect | Before | After |
|--------|--------|-------|
| Lines of code | 608 lines in 1 proc | ~800 lines across 15+ methods (better organization) |
| Duplication | 30+ identical blocks | 1 catalog + 1 item creator |
| Error handling | None | Comprehensive _try/_when in all methods |
| Testability | Cannot unit test | Each method independently testable |
| Maintainability | Hard-coded values | Data-driven catalog |

### Extensibility

**Adding New Material Type**:

**Before** (3 locations to change):
1. Add helper procedure or extend existing
2. Add property_list creation block
3. Add to closure loop if closure type

**After** (1 location):
1. Add entry to MATERIAL_CATALOG

Example:
```magik
# Add new cable type
:cable_72, property_list.new_with(
    :code, "200001999",
    :object, "Cable",
    :name, "FO core type SM G.652.D-ADSS 72 cores",
    :material_slot, :material
)
```

### Reusability

**Before**: Cannot reuse counting logic outside create_pl_boq

**After**:
```magik
# Generate BoQ for specific design
gen << rwwi_astri_boq_generator.new()
gen.set_scheme(my_scheme)
boq << gen.generate()

# Just count cables
cable_counts << gen.count_cable_types()

# Get specific material info
mat_info << rwwi_astri_boq_generator.MATERIAL_CATALOG[:cable_48]
```

### Testing

**Before**: Must test entire 608-line procedure

**After**: Can unit test each method:
```magik
# Test sling wire counting
test_gen << rwwi_astri_boq_generator.new()
test_gen.set_scheme(test_scheme)
length << test_gen.count_sling_wire_length()
_self.assert_equals(expected_length, length)

# Test item creation
item << test_gen.create_boq_item(:sling_wire, _unset, 100.5)
_self.assert_equals("200001033", item[:code])
_self.assert_equals(100.5, item[:service])
```

### Performance

**Before**: Recreates design retrieval in each helper proc

**After**:
- Design/change_set created once in init
- Reused across all counting methods
- No redundant lookups

---

## Testing Plan

### Unit Tests

Create `rwwi_astri_boq_generator_test.magik`:

```magik
_pragma(classify_level=debug, topic={boq, unit_testing})
def_slotted_exemplar(:rwwi_astri_boq_generator_test,
    {},
    {:test_case})
$

_method rwwi_astri_boq_generator_test.test_catalog_structure()
    ## Test material catalog is properly defined
    _local catalog << rwwi_astri_boq_generator.MATERIAL_CATALOG

    _self.assert_not_unset(catalog)
    _self.assert_true(catalog.size > 0)

    # Test specific entries
    _local sling << catalog[:sling_wire]
    _self.assert_equals("200001033", sling[:code])
    _self.assert_equals("Sling Wire", sling[:object])
_endmethod
$

_method rwwi_astri_boq_generator_test.test_create_boq_item()
    ## Test item creation from catalog
    _local gen << rwwi_astri_boq_generator.new()
    _local item << gen.create_boq_item(:sling_wire, _unset, 150.5)

    _self.assert_equals("json", item[:type])
    _self.assert_equals("200001033", item[:code])
    _self.assert_equals("Sling Wire", item[:object])
    _self.assert_unset(item[:material])
    _self.assert_equals(150.5, item[:service])
_endmethod
$

_method rwwi_astri_boq_generator_test.test_initialization()
    ## Test generator initialization
    _local gen << rwwi_astri_boq_generator.new()

    _self.assert_not_unset(gen)
    _self.assert_unset(gen.scheme)
    _self.assert_unset(gen.change_set)
    _self.assert_equals("json", gen.output_type)
_endmethod
$
```

### Integration Tests

Test with real design data:

```magik
_method rwwi_astri_boq_generator_test.test_generate_with_test_design()
    ## Test full generation with test design
    # Assumes test design exists with known object counts

    _local gen << rwwi_astri_boq_generator.new_for_current_design()
    _self.assert_not_unset(gen)

    _local result << gen.generate()
    _self.assert_not_unset(result)
    _self.assert_true(result.size > 0)

    # Verify structure
    _for item _over result.fast_elements()
    _loop
        _self.assert_not_unset(item[:code])
        _self.assert_not_unset(item[:object])
        _self.assert_not_unset(item[:name])
    _endloop
_endmethod
$
```

---

## Implementation Checklist

### Phase 1: Class Creation
- [ ] Create `rwwi_astri_boq_generator.magik` file
- [ ] Add file header and package declaration
- [ ] Define exemplar with slots
- [ ] Define MATERIAL_CATALOG constant
- [ ] Implement constructor methods (new, init, new_for_current_design)
- [ ] Implement set_scheme method
- [ ] Implement create_boq_item helper
- [ ] Implement counting methods (6 methods)
- [ ] Implement category add methods (6 methods)
- [ ] Implement main generate method
- [ ] Add to load_list.txt
- [ ] Compile and test syntax

### Phase 2: Testing
- [ ] Create test file
- [ ] Write unit tests for catalog
- [ ] Write unit tests for item creation
- [ ] Write unit tests for counting methods
- [ ] Create test design with known object counts
- [ ] Write integration test for full generation
- [ ] Run all tests and verify results

### Phase 3: Integration
- [ ] Update create_pl_boq to use new class (wrapper)
- [ ] Test backward compatibility
- [ ] Update generate_boq_json in dialog
- [ ] Test from UI
- [ ] Verify Excel generation still works

### Phase 4: Documentation
- [ ] Update BOQ_GENERATION_GUIDE.md
- [ ] Update CHANGELOG_BOQ_FEATURE.md
- [ ] Add inline method documentation
- [ ] Create migration guide for developers
- [ ] Document catalog extension process

### Phase 5: Cleanup (Optional)
- [ ] Mark old helper procedures as deprecated
- [ ] Add deprecation warnings
- [ ] Plan removal timeline (if desired)

---

## Risk Assessment

### Low Risk
- New class file (doesn't modify existing code)
- Wrapper maintains 100% backward compatibility
- Can test extensively before switching

### Medium Risk
- Spec_id string matching in counting methods
  - **Mitigation**: Comprehensive testing with real data
  - **Fallback**: Keep old procedures as backup

### Zero Risk
- MATERIAL_CATALOG is compile-time constant
- No database changes required
- No API changes to existing code

---

## File Structure Summary

```
rwwi_astri_integration_java/magik/rwwi_astri_workorder/source/
├── rwwi_astri_boq.magik                    (existing - will update wrapper)
├── rwwi_astri_boq_generator.magik          (new - class implementation)
├── rwwi_astri_boq_generator_test.magik     (new - unit tests)
├── rwwi_astri_workorder_dialog.magik       (existing - update to use new class)
└── load_list.txt                           (update - add new files)
```

---

## Estimated Effort

| Task | Lines of Code | Estimated Time |
|------|---------------|----------------|
| Create class structure | 50 | 30 min |
| Define MATERIAL_CATALOG | 200 | 1 hour |
| Implement counting methods | 300 | 2 hours |
| Implement generation methods | 200 | 1.5 hours |
| Create wrapper | 20 | 15 min |
| Write tests | 150 | 1.5 hours |
| Integration and testing | - | 2 hours |
| Documentation | - | 1 hour |
| **Total** | **~920 lines** | **~10 hours** |

---

## Approval Checklist

Before proceeding with implementation, please confirm:

- [ ] Class structure and slot design approved
- [ ] MATERIAL_CATALOG design approved
- [ ] Method organization approved
- [ ] Migration strategy (wrapper approach) approved
- [ ] Testing plan approved
- [ ] Risk mitigation acceptable

---

## Next Steps

Upon approval, implementation will proceed in this order:

1. **Create class file** with catalog and structure
2. **Implement counting methods** (smallest, most testable)
3. **Implement item creation** and category methods
4. **Implement main generate** method
5. **Create tests** and verify
6. **Create wrapper** for backward compatibility
7. **Test integration** with existing code
8. **Update documentation**
9. **Commit changes**

---

**Document Version**: 1.0
**Created**: 2025-11-17
**Status**: Awaiting Approval
**Estimated Implementation**: 10 hours / 920 lines of code
**Risk Level**: Low
**Breaking Changes**: None (100% backward compatible)
