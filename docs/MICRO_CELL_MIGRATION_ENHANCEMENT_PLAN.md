# Micro Cell Migration Enhancement Plan

## Date: 2025-11-06
## Status: 📋 PLANNED

## Overview
This document outlines the enhancement plan to upgrade **micro_cell (zone)** migration functionality in the ASTRI design migrator. The enhancement will add distinction between Macro and Micro cells, LINE pattern validation, boundary pattern validation, splitter detection, and complete attribute mapping.

## Reference Implementation
Based on `cluster_micro_cell_migration_astri()` from:
**File:** `C:\Users\user\Downloads\cluster_astri (1).magik:1553-1706`

## Background

### What is a Micro Cell vs Macro Cell?

#### Macro Cell
- **Definition:** Large coverage area representing the entire project boundary
- **Characteristic:** Area placemark **WITHOUT parent folders** (folders = null/empty)
- **Type:** "Macro Cell"
- **Use Case:** Represents the top-level coverage area for the entire cluster

#### Micro Cell
- **Definition:** Small coverage area representing splitter distribution zones
- **Characteristic:** Area placemark **WITH parent folders** containing LINE and boundary patterns
- **Type:** "Micro Cell"
- **Use Case:** Represents individual FAT/FDT coverage zones with associated splitters

### Current State vs Required State

#### Current Implementation (Lines 1825-1864)
```magik
# Basic implementation - treats everything as micro cell
_if parent = "" _orif parent _is _unset
_then
    _return  # Skips areas without folders
_endif

# Creates basic zone with minimal attributes
```

**Limitations:**
- ❌ No Macro Cell support (boundary areas are skipped)
- ❌ No LINE pattern validation
- ❌ No boundary pattern validation
- ❌ No splitter detection
- ❌ Missing key attributes

#### Required Implementation
```magik
# Distinguish Macro vs Micro Cell
_if folders is empty → Create Macro Cell
_elif LINE pattern + boundary pattern → Create Micro Cell
_else → Skip

# Find splitter within cell area
splitter_id << _self.splitter_inside_cell(area)

# Create with complete attributes
```

## Folder Pattern Matching

### Macro Cell Identification
**Pattern:** folders = null or empty string
```magik
_if folders _is _unset _orif folders = ""
_then
    # This is a MACRO CELL (boundary area)
    type << "Macro Cell"
_endif
```

### Micro Cell Identification
**Pattern:** folders must contain BOTH:
1. **LINE pattern** (fsize or fparent level)
2. **Boundary pattern** (fsize or fparent level)

#### LINE Patterns (Required)
- `LINE A`
- `LINE B`
- `LINE C`
- `LINE D`
- `LINE E`

#### Boundary Patterns (Required)
- `FAT COVERAGE`
- `BOUNDARY`
- `BOUNDARY FAT`
- `SH`

**Example Valid Micro Cell Folders:**
```
"Project|LINE A|FAT COVERAGE"     → Micro Cell ✓
"Cluster|LINE B|BOUNDARY"         → Micro Cell ✓
"LINE C|BOUNDARY FAT"             → Micro Cell ✓
"PROJECT|LINE D|SH"               → Micro Cell ✓

"Project|FDT|Coverage"            → SKIP ✗ (no LINE pattern)
"LINE A|Coverage"                 → SKIP ✗ (no boundary pattern)
```

## Migration Logic Flow

### Step 0: Parse Folder Hierarchy
```magik
_local folders << pm[:parent].default("")
_local fol << folders.split_by("|")
_local fsize << fol.size
_local fparent << _if fsize > 1 _then >> fsize - 1 _else >> fsize _endif
```

### Step 1: Macro Cell Path (No Folders)
```magik
_if folders _is _unset _orif folders = ""
_then
    write("  Processing Macro Cell: ", pm[:name])

    # Parse area geometry
    _local area << _self.parse_area_geometry(pm[:coord])

    # Create Macro Cell
    _local prop_values << property_list.new_with(
        :identification, pm[:name],
        :name, pm[:name],
        :type, "Macro Cell",
        :status, "Active",
        :location, area,
        :segment, .segment_id,
        :fttx_network_type, "Cluster",
        :pop, .pop_name,
        :olt, .pop_name,
        :deployment_technology, "System Default",
        :note, folders,
        :uuid, .uuid,
        :project, .prj_id,
        :construction_status, "Proposed"
    )

    # Insert and return
    _return
_endif
```

### Step 2: LINE Pattern Validation
```magik
_local has_line << _false

_if fol[fsize] = "LINE A" _orif fol[fparent] = "LINE A" _orif
   fol[fsize] = "LINE B" _orif fol[fparent] = "LINE B" _orif
   fol[fsize] = "LINE C" _orif fol[fparent] = "LINE C" _orif
   fol[fsize] = "LINE D" _orif fol[fparent] = "LINE D" _orif
   fol[fsize] = "LINE E" _orif fol[fparent] = "LINE E"
_then
    has_line << _true
_else
    write("  ⚠ Skipping - no LINE pattern: ", folders)
    _return
_endif
```

### Step 3: Boundary Pattern Validation
```magik
_local has_boundary << _false

_if fol[fsize] = "FAT COVERAGE" _orif fol[fparent] = "FAT COVERAGE" _orif
   fol[fsize] = "BOUNDARY" _orif fol[fparent] = "BOUNDARY" _orif
   fol[fsize] = "BOUNDARY FAT" _orif fol[fparent] = "BOUNDARY FAT" _orif
   fol[fsize] = "SH" _orif fol[fparent] = "SH"
_then
    has_boundary << _true
_else
    write("  ⚠ Skipping - no boundary pattern: ", folders)
    _return
_endif
```

### Step 4: Parse Area Geometry
```magik
_local area << _self.parse_area_geometry(pm[:coord])
```

### Step 5: Find Splitter Inside Cell
```magik
_local s_id << _self.splitter_inside_cell(area)

_if s_id _is _unset
_then
    nm << fol[fsize]  # Use folder name as fallback
_else
    nm << s_id        # Use splitter name
_endif

write("    → Found splitter: ", s_id)
```

### Step 6: Truncate Folders
```magik
_local ff << _unset

_if folders.size > 100
_then
    ff << folders.slice(1, 120)  # Truncate to 120 chars
    write("    → Folders truncated to 120 chars")
_else
    ff << folders
_endif
```

### Step 7: Create Micro Cell
```magik
_local prop_values << property_list.new_with(
    :identification, pm[:name],
    :name, nm,  # Use splitter name or folder name
    :type, "Micro Cell",
    :status, "Active",
    :location, area,
    :folders, ff,
    :segment, .segment_id,
    :fttx_network_type, "Cluster",
    :pop, .pop_name,
    :olt, .pop_name,
    :splitter_id, s_id,
    :deployment_technology, "System Default",
    :note, folders,
    :uuid, .uuid,
    :project, .prj_id,
    :construction_status, "Proposed"
)

_local rec_trans << record_transaction.new_insert(.cell_col, prop_values)
_local result << rec_trans.run()

.stats[:micro_cells] +<< 1
write("    ✓ Micro cell created: ", pm[:name], " (Type: Micro Cell, Splitter: ", s_id, ")")
```

## Required New Method

### splitter_inside_cell(area)
**Purpose:** Find optical splitter within micro cell area boundary

**Logic:**
```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_design_migrator.splitter_inside_cell(area)
    ## Find optical splitter inside cell area
    ## Parameters:
    ##   area - pseudo_area geometry
    ## Returns:
    ##   splitter name (string) or _unset if not found

    _try
        # Create spatial predicate for intersection
        _local pred << predicate.interacts(:location, {area})

        # Query optical_splitter collection
        _local splitters << .os_col.select(pred)

        _if splitters.size > 0
        _then
            # Return first splitter name found
            _local sp << splitters.an_element()
            _return sp.name
        _else
            # No splitter found in cell
            _return _unset
        _endif

    _when error
        # Return _unset on error
        _return _unset
    _endtry
_endmethod
$
```

## Required Method Updates

### 1. is_micro_cell?(pm) - MAJOR UPDATE

**Current Logic:**
```magik
# Only checks for "micro cell" keyword in folders
>> pm[:type] = "area" _andif
   (parent.matches?("micro cell") _orif ...)
```

**New Logic:**
```magik
# Accept ALL area-based placemarks (both Macro and Micro)
# Validation happens in create_micro_cell()

>> pm[:type] = "area"
```

**Rationale:**
- Macro cells have NO folders (boundary areas)
- Micro cells have LINE + boundary patterns
- Let create_micro_cell() handle the distinction and validation

### 2. create_micro_cell(pm) - COMPLETE REWRITE

**Current:** ~40 lines, basic implementation
**New:** ~150 lines, advanced implementation with:
- Macro/Micro cell distinction
- LINE pattern validation
- Boundary pattern validation
- Splitter detection
- Folder truncation
- Complete attributes

## Attribute Mapping

### Macro Cell Attributes
| Field | Value | Note |
|-------|-------|------|
| identification | pm[:name] | Cell identifier |
| name | pm[:name] | Display name |
| type | "Macro Cell" | Fixed value |
| status | "Active" | Fixed value |
| location | area geometry | Parsed from coordinates |
| folders | "" or _unset | No folders for Macro |
| segment | .segment_id | From migrator |
| fttx_network_type | "Cluster" | Fixed value |
| pop | .pop_name | From migrator |
| olt | .pop_name | From migrator |
| splitter_id | _unset | No splitter for Macro |
| deployment_technology | "System Default" | Fixed value |
| note | folders | Original folder value |
| uuid | .uuid | From migrator |
| project | .prj_id | From migrator |
| construction_status | "Proposed" | Fixed value |

### Micro Cell Attributes
| Field | Value | Note |
|-------|-------|------|
| identification | pm[:name] | Cell identifier |
| name | splitter_id or fol[fsize] | Use splitter name if found |
| type | "Micro Cell" | Fixed value |
| status | "Active" | Fixed value |
| location | area geometry | Parsed from coordinates |
| folders | truncated folders | Max 120 chars if > 100 |
| segment | .segment_id | From migrator |
| fttx_network_type | "Cluster" | Fixed value |
| pop | .pop_name | From migrator |
| olt | .pop_name | From migrator |
| splitter_id | detected or _unset | From splitter_inside_cell() |
| deployment_technology | "System Default" | Fixed value |
| note | original folders | Full folder path |
| uuid | .uuid | From migrator |
| project | .prj_id | From migrator |
| construction_status | "Proposed" | Fixed value |

## Implementation Estimate

### Method Changes

#### 1. is_micro_cell?(pm) - UPDATE (~5 lines changed)
**Location:** Lines 574-583
**Change:** Simplify to accept all area types

#### 2. splitter_inside_cell(area) - NEW (~30 lines)
**Location:** After scan_ar_on_design() (around line 781)

#### 3. create_micro_cell(pm) - REWRITE (~150 lines)
**Location:** Lines 1825-1864 (replace ~40 lines with ~150 lines)

### Total Changes
- **Methods updated:** 1 (is_micro_cell)
- **Methods added:** 1 (splitter_inside_cell)
- **Methods rewritten:** 1 (create_micro_cell)
- **Total new lines:** ~110 lines net increase

## Testing Requirements

### Test Scenarios

#### 1. Macro Cell Creation
- [ ] Area with folders = _unset → creates Macro Cell
- [ ] Area with folders = "" → creates Macro Cell
- [ ] Macro Cell type = "Macro Cell"
- [ ] Macro Cell has no splitter_id

#### 2. LINE Pattern Validation
- [ ] Folder "Project|LINE A|BOUNDARY" → has_line = true
- [ ] Folder "Cluster|LINE B|FAT COVERAGE" → has_line = true
- [ ] Folder "LINE C|SH" → has_line = true
- [ ] Folder "Project|BOUNDARY" → has_line = false (skipped)

#### 3. Boundary Pattern Validation
- [ ] Folder "LINE A|FAT COVERAGE" → has_boundary = true
- [ ] Folder "LINE B|BOUNDARY" → has_boundary = true
- [ ] Folder "LINE C|BOUNDARY FAT" → has_boundary = true
- [ ] Folder "LINE D|SH" → has_boundary = true
- [ ] Folder "LINE A|Coverage" → has_boundary = false (skipped)

#### 4. Splitter Detection
- [ ] Micro cell with splitter inside → splitter_id populated
- [ ] Micro cell without splitter → splitter_id = _unset
- [ ] Uses splitter name for cell name if found

#### 5. Folder Truncation
- [ ] Folders < 100 chars → no truncation
- [ ] Folders > 100 chars → truncated to 120 chars
- [ ] Original folders saved in note field

#### 6. Micro Cell Creation
- [ ] Valid pattern → creates Micro Cell
- [ ] Micro Cell type = "Micro Cell"
- [ ] All attributes populated correctly
- [ ] construction_status = "Proposed"

## Performance Considerations

### Spatial Queries
- **Splitter search:** 1 query per micro cell (area intersection)

### Optimization Notes
- Queries use spatial predicates (optimized)
- Area intersection is efficient
- Early returns prevent unnecessary processing

## Migration Statistics

The enhanced implementation will increment statistics:
```magik
.stats[:micro_cells] +<< 1      # On micro cell OR macro cell create
.stats[:errors] +<< 1            # On error
```

**Note:** Both Macro and Micro cells increment the same counter as they're both ftth!zone objects.

## Example Output

```
Starting design migration of 250 placemarks...
  Pass 3: Creating demand points, splices, and area-based objects...
area
  Processing Macro Cell: Cluster Boundary
    ✓ Macro cell created: Cluster Boundary (Type: Macro Cell)
area
  Processing Micro Cell: FAT-001-COVERAGE
    → Found splitter: FDT-144-A-SP-01
    ✓ Micro cell created: FAT-001-COVERAGE (Type: Micro Cell, Splitter: FDT-144-A-SP-01)
area
  Processing Micro Cell: LINE-B-BOUNDARY-02
    → No splitter found in cell area
    ✓ Micro cell created: LINE-B-BOUNDARY-02 (Type: Micro Cell, Splitter: _unset)
area
  ⚠ Skipping - no LINE pattern: Project|BOUNDARY
area
  ⚠ Skipping - no boundary pattern: LINE C|Coverage
  ...

============================================================
Design Migration Statistics
============================================================
Micro Cells:        15  (10 Micro + 5 Macro)
============================================================
```

## Key Design Decisions

### 1. Macro Cell = Boundary Area
- **Boundary areas** (folders = null/empty) → Macro Cell
- **NOT skipped** anymore
- Represents top-level coverage area

### 2. Exact Pattern Matching
- **LINE patterns:** Exact match "LINE A", "LINE B", etc. (not wildcard)
- **Boundary patterns:** Exact match "FAT COVERAGE", "BOUNDARY", etc.
- **Case-sensitive:** Must match exactly as in reference

### 3. Splitter Association
- Uses spatial intersection to find splitter in cell area
- If found: uses splitter name for cell name
- If not found: uses folder name for cell name

### 4. Folder Truncation
- **Threshold:** 100 characters
- **Truncation:** Slice to 120 characters
- **Preservation:** Original folders saved in note field

### 5. Both Patterns Required
- Micro cell requires **BOTH** LINE and boundary patterns
- Missing either pattern → skip
- Prevents creating invalid micro cells

## Related Documentation

- **Reference Implementation:** `C:\Users\user\Downloads\cluster_astri (1).magik:1553-1706`
- **Design Migration Plan:** `DESIGN_MIGRATION_PLAN.md`
- **Figure Eight Enhancement:** `FIGURE_EIGHT_MIGRATION_IMPLEMENTATION_SUMMARY.md`

## Next Steps

1. ✅ Create enhancement plan document (this file)
2. ⏳ Create implementation summary document template
3. ⏳ Update is_micro_cell?() method (simplify to accept all areas)
4. ⏳ Implement splitter_inside_cell() method
5. ⏳ Rewrite create_micro_cell() method with Macro/Micro distinction
6. ⏳ Test with sample work order containing micro cell KML data
7. ⏳ Verify Macro cell creation (boundary areas)
8. ⏳ Verify Micro cell creation (LINE + boundary patterns)
9. ⏳ Verify splitter detection
10. ⏳ Verify folder truncation
11. ⏳ Deploy to production

## Notes

- **Macro Cell = Boundary Area** - No longer skips areas without folders
- **Exact pattern matching** - Uses = equality (not wildcard matches)
- **Case-sensitive** - Pattern matching is case-sensitive per reference
- **Both patterns required** - LINE + boundary both needed for Micro Cell
- **Splitter detection** - Spatial intersection with optical_splitter collection
- **Folder truncation** - 100 char threshold → 120 char limit
- **Statistics** - Both Macro and Micro cells increment same counter
- **Error handling** - All operations wrapped in _try/_when blocks

---
**Enhancement plan created on 2025-11-06**
