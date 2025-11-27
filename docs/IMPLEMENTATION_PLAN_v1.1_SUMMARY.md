# Aerial KMZ Export Implementation Plan v1.1 - Summary of Changes

## Document Update
- **Previous Version:** 1.0 (Static structure)
- **Current Version:** 1.1 (Dynamic structure)
- **Date:** 2025-11-26

---

## Critical Changes Made

### 1. Dynamic FDT-Based Cluster Structure ⭐ NEW

**Problem:** Real-world export areas may contain multiple FDTs (Fiber Distribution Terminals).

**Solution:**
- **If 1 FDT detected:** Simplified structure with FDT at top level
  ```
  CLUSTER CODE
  ├── BOUNDARY CLUSTER
  ├── FDT
  └── LINE A/B/C/D (4 lines)
  ```

- **If 2+ FDTs detected:** Separate FDT folder for each FDT (NO "CLUSTER" prefix)
  ```
  CLUSTER CODE
  ├── BOUNDARY CLUSTER
  ├── FDT_[FDT_001_NAME]
  │   ├── FDT
  │   └── LINE A/B/C/D
  ├── FDT_[FDT_002_NAME]
  │   ├── FDT
  │   └── LINE A/B/C/D
  └── FDT_[FDT_N_NAME]
      └── ...
  ```

**Implementation Impact:**
- Added parameter `p_fdts` to `build_cluster_folders()` method
- Added FDT count checking logic
- FDT folders named as "FDT_[FDT_NAME]" (removed CLUSTER prefix)
- Objects assigned using ring_name field (primary), proximity as fallback
- FDT detection from TWO object types: mit_terminal_enclosure OR sheath_splice (type=FDT)

---

### 2. Mixed Network Level Detection and Export ⭐ NEW

**Problem:** Export areas may contain FEEDER, SUBFEEDER, and CLUSTER networks simultaneously.

**Solution:**
- Scan area for all network levels present
- Generate **single KMZ file** with dynamic top-level folders
- Only create folders for detected network levels

**Export Structure Examples:**

**Scenario: Pure Cluster (3 FDTs)**
```
PROJECT_NAME.KMZ
└── CLUSTER CODE
    ├── BOUNDARY CLUSTER
    ├── FDT_FDT_001
    ├── FDT_FDT_002
    └── FDT_FDT_003
```

**Scenario: Mixed Feeder + Cluster (2 FDTs)**
```
PROJECT_NAME.KMZ
├── FEEDER CODE (15 folders)
└── CLUSTER CODE
    ├── BOUNDARY CLUSTER
    ├── FDT_FDT_001
    └── FDT_FDT_002
```

**Scenario: All Levels (1 FDT)**
```
PROJECT_NAME.KMZ
├── FEEDER CODE (15 folders)
├── SUBFEEDER CODE (14 folders)
└── CLUSTER CODE
    ├── BOUNDARY CLUSTER
    ├── FDT
    └── LINE A/B/C/D
```

**Implementation Impact:**
- New method: `detect_network_levels_in_area(p_area)`
- New method: `export_mixed_network(p_area, p_output_file)`
- Conditional folder generation based on detected levels
- Single export invocation handles all scenarios

---

### 3. File Output Strategy Change

**Old Approach (v1.0):**
- 3 separate KMZ files:
  - `PROJECT_NAME FEEDER CODE.KMZ`
  - `PROJECT_NAME SUBFEEDER CODE.KMZ`
  - `PROJECT_NAME CLUSTER CODE.KMZ`
- User must export each level separately
- Results in 3 files per project

**New Approach (v1.1):**
- **1 dynamic KMZ file:**
  - `PROJECT_NAME.KMZ`
- Contains all detected network levels
- Single export operation
- Single file for entire project area

**Benefits:**
- Simpler for users (one file to manage)
- More accurate representation of real network
- Easier to share and distribute
- Better alignment with actual project structure

---

### 4. Enhanced Object Assignment Logic

**New Requirements:**

1. **FDT Assignment for Cluster Objects**
   - When multiple FDTs exist, determine which FDT each cluster object belongs to
   - Use proximity calculation (nearest FDT)
   - Fallback to name pattern matching

2. **LINE Assignment with FDT Context**
   - LINE assignment now considers parent FDT
   - HP points assigned to LINE within correct cluster
   - FAT assigned to LINE serving specific FDT

**New Methods:**
```magik
assign_fdt_to_cluster_object(p_object, p_fdts)
    ## Returns nearest FDT for given object

assign_line_with_fdt_context(p_object, p_fdt)
    ## Returns LINE (A/B/C/D) for object within FDT's cluster
```

---

### 5. Object Type Detection Corrections ⭐ UPDATED

**Corrections to Object Mapping:**

1. **FDT Detection (Dual Sources)**
   - **OLD**: Only `mit_terminal_enclosure`
   - **NEW**: `mit_terminal_enclosure` OR `sheath_splice` with `type="FDT"`
   - Query both object types and combine results

2. **Joint Closure Detection (NEW)**
   - **Object**: `sheath_splice` with `type="Joint Closure"` or `type="Closure"`
   - **Placement**: FEEDER > JOINT CLOSURE or SUBFEEDER > JOINT CLOSURE
   - **Network Level**: Feeder and Subfeeder only (NOT in Cluster)
   - Previously not exported in original plan

3. **Boundary Naming (Enhanced)**
   - **OLD**: Generic name "PROJECT BOUNDARY"
   - **NEW**: Use design/scheme name from Design Manager
   - **Example**: "BOUNDARY [DESIGN_NAME]" for BOUNDARY CLUSTER folder
   - Applies to all network levels: FEEDER, SUBFEEDER, CLUSTER

4. **FAT Zone Boundary (NEW)**
   - **Object**: `ftth!zone`
   - **Placement**: CLUSTER > LINE [A/B/C/D] > BOUNDARY FAT
   - One BOUNDARY FAT polygon per LINE
   - Previously not exported in original plan

5. **LINE Assignment Logic (NEW)**
   - **Standard Objects**: Use `line_type` field (cables, poles, FAT, HP points, sling wire, slack hanger)
   - **Sling Wire**: `mit_sling_wire` uses `line_type` field (CLUSTER only)
   - **Slack Hanger**: `mit_figure_eight` uses `line_type` field (all levels)
   - **Special Objects**: Parse `comments` field for zones and demand points
   - **ftth!zone**: Extract from comments (e.g., "Line A" → LINE A)
   - **ftth!demand_point**: Extract from comments (case-insensitive matching)
   - **Fallback**: Default to LINE A if no line information found

**Code Impact:**
```magik
# FDT Detection Example
all_fdts_terminal << .gc[:mit_terminal_enclosure].select(
    predicate.within(:location, p_area))

all_fdts_splice << .gc[:sheath_splice].select(
    predicate.within(:location, p_area) _and
    predicate.eq(:type, "FDT"))

all_fdts << all_fdts_terminal.union(all_fdts_splice)

# Joint Closure Detection Example (FEEDER/SUBFEEDER only, NOT Cluster)
closures << .gc[:sheath_splice].select(
    predicate.within(:location, p_area) _and
    (predicate.eq(:type, "Joint Closure") _or
     predicate.eq(:type, "Closure")))
# Note: Place in FEEDER > JOINT CLOSURE or SUBFEEDER > JOINT CLOSURE folders

# FAT Zone Boundary Detection Example (CLUSTER only)
zones << ftth!design_unit.ftth_zones.select(
    predicate.within(:location, p_area))
# Note: Place in CLUSTER > LINE [A/B/C/D] > BOUNDARY FAT folders
# Assign zone to appropriate LINE based on comments field

# LINE Assignment Example
_method assign_line_to_object(p_object)
    # Special handling for zones and demand_points (use comments field)
    _if p_object.class_name _is :ftth!zone _orif
        p_object.class_name _is :ftth!demand_point
    _then
        comments << p_object.comments.default("")
        # Parse "Line A", "LINE B", etc. from comments
        _for line_letter _over {"A", "B", "C", "D"}.fast_elements()
        _loop
            _if comments.index_of_regex("(?i)line[\\s_]+(" + line_letter + ")") _isnt _unset
            _then _return line_letter
            _endif
        _endloop
        _return "A"  # Default
    _endif

    # Standard objects: use line_type field
    # Includes: cables, poles, FAT, HP points, mit_sling_wire, mit_figure_eight
    line_type << p_object.perform_safely(:line_type)
    _if line_type _isnt _unset _and {"A","B","C","D"}.includes?(line_type.uppercase)
    _then _return line_type.uppercase
    _endif

    _return "A"  # Default fallback
_endmethod
```

---

## Implementation Impact Summary

### New Code Components

| Component | Status | Impact |
|-----------|--------|--------|
| `detect_network_levels_in_area()` | NEW | Scans area, identifies present network levels |
| `export_mixed_network()` | NEW | Main export orchestrator for dynamic structure |
| `build_cluster_folders(p_fdts)` | MODIFIED | Now accepts FDT collection parameter |
| `build_line_folders(p_line_id, p_fdt)` | MODIFIED | Now accepts FDT context parameter |
| `assign_fdt_to_cluster_object()` | NEW | Proximity-based FDT assignment |
| `assign_line_with_fdt_context()` | NEW | LINE assignment considering parent FDT |

### Test Cases to Add

- [ ] TC-10: Multiple FDTs cluster structure generation
- [ ] TC-11: Mixed network detection (FEEDER + CLUSTER)
- [ ] TC-12: Mixed network detection (all levels)
- [ ] TC-13: FDT assignment for cluster objects using ring_name
- [ ] TC-14: LINE assignment with multiple FDTs
- [ ] TC-15: Single vs multiple FDT folder structure
- [ ] TC-16: FDT detection from sheath_splice (type=FDT)
- [ ] TC-17: Joint Closure detection and placement (FEEDER/SUBFEEDER only)
- [ ] TC-18: Boundary naming from design/scheme name
- [ ] TC-19: ring_name-based assignment with fallback to proximity
- [ ] TC-20: ftth!zone export to BOUNDARY FAT folders per LINE
- [ ] TC-21: LINE assignment using line_type field
- [ ] TC-22: LINE assignment from comments field (zones and demand_points)

### Documentation Updates

- [x] Section 1: Requirements Analysis (FR-2.3, FR-2.4, FR-9, FR-10, FR-11 added)
- [x] Section 2: Architecture (export modes updated)
- [x] Section 3.1.0: Mixed network detection (NEW)
- [x] Section 3.1.3: Cluster folders (dynamic structure with FDT_ prefix)
- [x] Implementation code examples (updated with FDT parameters and ring_name)
- [x] Example scenarios (4 scenarios with corrected FDT folder naming)
- [x] Appendix A: Mapping table (updated FDT, Joint Closure, Boundary)
- [x] Appendix B: File naming (updated)
- [x] CRITICAL NOTES: Added object detection corrections

---

## Migration from v1.0 to v1.1

If you've already started implementing v1.0, here are the key changes needed:

### 1. Export Entry Point
**Old:**
```magik
exporter.export(:feeder)
exporter.export(:subfeeder)
exporter.export(:cluster)
```

**New:**
```magik
exporter.export_mixed_network(p_area, p_output_file)
# Automatically detects and exports all levels
```

### 2. Cluster Folder Building
**Old:**
```magik
folders << build_cluster_folders()
# Fixed 4 LINEs at top level
```

**New:**
```magik
fdts << detect_fdts_in_area(p_area)
folders << build_cluster_folders(fdts)
# Dynamic structure based on FDT count
```

### 3. GUI Actions
**Old:**
- 3 separate actions: Export FEEDER, Export SUBFEEDER, Export CLUSTER

**New:**
- 1 action: Export Network (auto-detects levels)
- Optional: Keep separate actions for filtered exports (e.g., "Export Cluster Only")

---

## Benefits of v1.1 Changes

### For Users
✅ Single file per project (simpler management)
✅ Automatic detection of network complexity
✅ Handles real-world multi-FDT scenarios
✅ No need to know network structure beforehand

### For Implementation
✅ More flexible and adaptive
✅ Handles edge cases (0 FDTs, 1 FDT, many FDTs)
✅ Better alignment with actual network topology
✅ Single export workflow reduces code complexity

### For Maintenance
✅ Configuration-driven structure (easy to modify)
✅ Clearer separation of concerns (detection vs generation)
✅ Better testability (discrete test scenarios)
✅ Reduced user error (no manual level selection)

---

## Validation Checklist

Before implementation, verify:

- [ ] All stakeholders understand dynamic structure approach
- [ ] Database consistently populates FDT objects
- [ ] FDT naming convention is standardized
- [ ] Network level attributes (sheath_network_type, fttx_network_type) are reliable
- [ ] Proximity calculation performance acceptable for large FDT counts
- [ ] Google Earth renders nested folder structure correctly
- [ ] Users can navigate multi-FDT cluster structure easily

---

## Next Steps

1. **Stakeholder Review** - Present v1.1 changes and get approval
2. **Adjust Timeline** - Minor increase in complexity (+3-5 days estimated)
3. **Begin Implementation** - Start with Phase 1 foundation
4. **Test Early** - Create multi-FDT test scenarios immediately
5. **Iterate** - Refine FDT assignment logic based on real data

---

**Document:** Implementation Plan v1.1 Summary
**Main Document:** `changelog-20251126-aerial-kmz-export-implementation-plan.md`
**Version:** 1.1
**Date:** 2025-11-26
**Status:** ✅ READY FOR IMPLEMENTATION