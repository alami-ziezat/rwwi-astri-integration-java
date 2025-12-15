# Implementation Plan: Object-Specific KML Styling Enhancement

## Overview

Enhance the aerial KML export by implementing **37 object-specific styles** with color-coded visualization based on core counts, pole specifications, and equipment types. This expands from the current 15 generic styles to support detailed network infrastructure visualization.

---

## Current Implementation Analysis

### KML Template System
- **Location:** `pni_custom\rwwi_astri_integration_java\magik\rwi_export_to_kml\resources\base\data\network_styles.kml`
- **Current styles:** 15 generic styles (boundaries, equipment, poles, cables, home pass)
- **Approach:** Template-based with placeholders (PROJECT_NAME_PLACEHOLDER, CONTENT_PLACEHOLDER)
- **Note:** Renamed from `aerial_styles.kml` to `network_styles.kml` to reflect support for both aerial and underground infrastructure

### Style Assignment Logic
**File:** `rwi_aerial_kmz_object_writer.magik`

**Current patterns:**
- **Poles:** Folder name pattern matching (e.g., "NEW POLE 7-4" → `#new_pole_7_4_map`)
- **Equipment:** Based on `sheath_splice_object_type` field (FAT/FDT/CLOSURE detection)
- **Home Pass:** Folder name pattern (HP COVER/HP UNCOVER)
- **Cables/Wires:** Fixed styles (`#cable_map`, `#sling_wire_map`)

### Object Detection Mechanisms
- **Poles:** `construction_status` + `asset_owner` + `type` field
- **Equipment:** `sheath_splice_object_type` contains "FAT", "FDT", "Closure", "Joint"
- **Core count:** Utility pattern exists in `astri_migrator_utilities.magik` using wildcard matching

---

## Implementation Scope

### Objects to Style (37 types)

**Point Features (28 types):**
1. OLT (1 style)
2. FDT (5 core count variations: 288C, 144C, 96C, 72C, 48C)
3. POLE (5 variations: 9M 4INCH, 7M 4INCH, 7M 3INCH, 7M 2.5INCH, EXISTING)
4. JOINT CLOSURE (7 core count variations: 24C, 36C, 48C, 72C, 96C, 144C, 288C)
5. SLACK CABLE (1 style)
6. HOMEPASS (1 style - already exists)
7. FAT (5 core count variations: 48C, 32C, 24C, 16C, 8C)
8. HANDHOLE (4 dimension variations: 80x80x130, 40x40x60, 20x20x60, PEDESTAL)

**Line Features (9 types):**
1. SLING WIRE (1 style - already exists)
2. CABLE ADSS (7 core count variations: 24C, 36C, 48C, 72C, 96C, 144C, 288C)
3. SUB DUCT (2 spec variations: 40/34, 32/28)

### Files to Modify

| File | Changes | Est. Lines |
|------|---------|------------|
| `network_styles.kml` | Add 37 object-specific styles (×2-3 definitions each) | +2000 |
| `rwi_aerial_kmz_object_writer.magik` | Modify 4 methods, add 10 new utility methods | +400 |

---

## User Confirmations Received ✓

**CONFIRMED ANSWERS:**

### ✓ Core Count Storage
**Confirmed:** Core count is embedded in `sheath_splice_object_type` field (e.g., "FDT 288C", "FAT 48C", "Joint Closure 24C")
- **Implementation:** Use wildcard pattern matching to extract core count (576, 288, 144, 96, 72, 48, 36, 32, 24, 16, 8)

### ✓ Pole Specification Mapping
**Confirmed:** Pole `type` field contains values like "Pole 7-2.5", "Pole 7-4", etc.

**Pole Styling Logic:**
- **PROPOSED poles:** Extract spec from `type` field → Use specific style
  - "Pole 7-2.5" → `pole_7m_2_5inch_map` (FFAA00FF - magenta)
  - "Pole 7-3" → `pole_7m_3inch_map` (FF00FFFF - cyan)
  - "Pole 7-4" → `pole_7m_4inch_map` (FF00FF00 - green)
  - "Pole 9-4" → `pole_9m_4inch_map` (FFFF0000 - red)
- **Non-PROPOSED poles (IN SERVICE, etc.):** Use single style
  - Any spec → `pole_existing_map` (FF550000 - dark red)

**Simplified:** No differentiation by owner (EMR/PARTNER/TELKOM) for existing poles - all use same dark red style

### ✓ HANDHOLE Object
**Confirmed:**
- Object exists in database as `:uub` class
- Currently NOT exported
- **User requirement:** Include handhole styles in template for future implementation
- **Action:** Create 4 handhole styles but do NOT implement writer method yet

### ✓ SUB DUCT Object
**Decision:** Include sub duct styles (2 variations) in template for future use

### ✓ Color Format
**Confirmed:** Colors are already in KML AABBGGRR format - use as-is

---

## Implementation Strategy

### Phase 1: Style Definitions (network_styles.kml)

**Structure:**
```xml
<!-- SECTION 1: OLT STYLES -->
<Style id="olt"> ... </Style>
<StyleMap id="olt_map"> ... </StyleMap>

<!-- SECTION 2: FDT STYLES (5 core count variations) -->
<Style id="fdt_288c"> ... </Style>
<StyleMap id="fdt_288c_map"> ... </StyleMap>
<!-- ... 4 more FDT variations ... -->
<Style id="fdt_default"> ... </Style>
<StyleMap id="fdt_map"> ... </StyleMap>  <!-- Fallback -->

<!-- SECTION 3: POLE STYLES (5 specifications) -->
<Style id="pole_9m_4inch"> ... </Style>
<StyleMap id="pole_9m_4inch_map"> ... </StyleMap>
<!-- ... 4 more pole variations ... -->

<!-- Continue for all 37 object types -->
```

**Naming Convention:**
- Pattern: `{object_type}_{variant}` (e.g., `fdt_288c`, `pole_9m_4inch`)
- StyleMaps: Add `_map` suffix (e.g., `fdt_288c_map`)
- Fallbacks: Use `_map` only (e.g., `fdt_map` for unknown core count)

### Phase 2: Core Count Detection (rwi_aerial_kmz_object_writer.magik)

**Add utility method:**
```magik
_method rwi_aerial_kmz_object_writer.extract_equipment_core_count(p_equipment)
    ## Extract core count from equipment object
    ## Uses wildcard matching pattern (576, 288, 144, 96, 72, 48, 36, 32, 24, 16, 8)

    # Implementation depends on Q1 answer
    # Returns: String ("288", "144", etc.) or _unset
_endmethod
```

**Add style mapping methods:**
```magik
_method get_fdt_style(p_core_count)
    ## Map FDT core count to style URL
    ## Returns: "#fdt_288c_map", "#fdt_144c_map", etc.
    ## Fallback: "#fdt_map" for unknown
_endmethod

_method get_fat_style(p_core_count)
    ## Map FAT core count to style URL (48C, 32C, 24C, 16C, 8C)
_endmethod

_method get_closure_style(p_core_count)
    ## Map Joint Closure core count to style URL (24C-288C)
_endmethod

_method get_cable_style(p_cable_spec)
    ## Map cable spec to ADSS style URL
    ## Detects ADSS type and core count from spec_id
_endmethod
```

### Phase 3: Writer Method Updates

**Modify `write_sheath_splice()` (lines 289-364):**
```magik
# Before: style_url << "#fdt_map"
# After:
core_count << _self.extract_equipment_core_count(p_splice_equipment)
style_url << _self.get_fdt_style(core_count)  # Returns specific or fallback
```

**Modify `get_pole_style_from_folder()` (lines 161-221):**
```magik
# Update pattern matching to map:
# "7-2.5" → "#pole_7m_2_5inch_map"
# "7-3"   → "#pole_7m_3inch_map"
# "7-4"   → "#pole_7m_4inch_map"
# "9-4"   → "#pole_9m_4inch_map"
# EXISTING → "#pole_existing_map"
```

**Modify `write_cable()` (lines 490-553):**
```magik
# Add: cable_spec << p_cable.perform(:spec_id)
# Add: style_url << _self.get_cable_style(cable_spec)
# Detects ADSS and maps core count (24C-288C)
```

**Modify `write_figure_eight()` (line 401):**
```magik
# Change: "#slack_hanger_map" → "#slack_cable_map"
```

### Phase 4: New Object Types (If Applicable)

**Add `write_handhole()` method** (pending Q3 confirmation)
**Add `write_sub_duct()` method** (pending Q3 confirmation)

---

## Style Color and Icon Reference

| Object Type | Variant | Color (AABBGGRR) | Icon |
|-------------|---------|------------------|------|
| **OLT** | - | FFFFFFFF | ranger_station.png |
| **FDT** | 288C | FFAA0000 | cross-hairs.png |
| **FDT** | 144C | FFFFFF00 | cross-hairs.png |
| **FDT** | 96C | FF00FFFF | cross-hairs.png |
| **FDT** | 72C | FF0000FF | cross-hairs.png |
| **FDT** | 48C | FFAA00FF | cross-hairs.png |
| **POLE** | 9M 4INCH | FFFF0000 | placemark_circle.png |
| **POLE** | 7M 4INCH | FF00FF00 | placemark_circle.png |
| **POLE** | 7M 3INCH | FF00FFFF | placemark_circle.png |
| **POLE** | 7M 2.5INCH | FFAA00FF | placemark_circle.png |
| **POLE** | EXISTING | FF550000 | placemark_circle.png |
| **JOINT CLOSURE** | 24C | FF00FF00 | forbidden.png |
| **JOINT CLOSURE** | 36C | FFFF00FF | forbidden.png |
| **JOINT CLOSURE** | 48C | FFAA00FF | forbidden.png |
| **JOINT CLOSURE** | 72C | FF550000 | forbidden.png |
| **JOINT CLOSURE** | 96C | FFFF0000 | forbidden.png |
| **JOINT CLOSURE** | 144C | FFFFFF00 | forbidden.png |
| **JOINT CLOSURE** | 288C | FFFFAA00 | forbidden.png |
| **SLACK CABLE** | - | FFFF0000 | target.png |
| **HOMEPASS** | - | FF00FF00 | homegardenbusiness.png |
| **FAT** | 48C | FF0000FF | triangle.png |
| **FAT** | 32C | FFFF00FF | triangle.png |
| **FAT** | 24C | FFFF0000 | triangle.png |
| **FAT** | 16C | FFFFFF00 | triangle.png |
| **FAT** | 8C | FF00FF00 | triangle.png |
| **HANDHOLE** | 80x80x130 | FFFFAA00 | square.png |
| **HANDHOLE** | 40x40x60 | FF0000FF | square.png |
| **HANDHOLE** | 20x20x60 | FF00FF00 | square.png |
| **HANDHOLE** | PEDESTAL | FF00FF00 | grn-blank.png |
| **CABLE ADSS** | 24C | FF00FF00 | (line) |
| **CABLE ADSS** | 36C | FFFF00FF | (line) |
| **CABLE ADSS** | 48C | FFAA00FF | (line) |
| **CABLE ADSS** | 72C | FF550000 | (line) |
| **CABLE ADSS** | 96C | FFFF0000 | (line) |
| **CABLE ADSS** | 144C | FFFFFF00 | (line) |
| **CABLE ADSS** | 288C | FFFFAA00 | (line) |
| **SUB DUCT** | 40/34 | FFAA0000 | (line) |
| **SUB DUCT** | 32/28 | FFAA007F | (line) |

---

## Backwards Compatibility

**Guarantee:** Existing exports will continue to work

**Strategy:**
- Keep all existing `*_map` style IDs as fallback defaults
- New core-count-specific styles are additive, not replacement
- If core count detection fails, code falls back to existing generic styles

**Example:**
```
Before: FDT always uses #fdt_map
After:  FDT 288C uses #fdt_288c_map
        FDT (unknown) uses #fdt_map (same as before)
```

---

## Edge Case Handling

### Core Count Not Found
- Use fallback generic style (`#fdt_map`, `#fat_map`, `#closure_map`)
- Continue export without error
- Optional: Log warning message

### Unknown Pole Specification
- Default to `#pole_7m_4inch_map` for PROPOSED poles
- Default to `#pole_existing_map` for non-PROPOSED poles

### Cable Without ADSS Spec
- Use generic `#cable_map` style
- Silent fallback, no error

---

## Critical Files

1. **network_styles.kml** - `pni_custom\rwwi_astri_integration_java\magik\rwi_export_to_kml\resources\base\data\network_styles.kml`
   - Add ~70 new style definitions
   - **Renamed from `aerial_styles.kml`** to better reflect both aerial and underground network coverage

2. **rwi_aerial_kmz_object_writer.magik** - `pni_custom\rwwi_astri_integration_java\magik\rwi_export_to_kml\source\rwi_aerial_kmz_object_writer.magik`
   - Modify 4 existing methods
   - Add 10 new utility methods

3. **astri_migrator_utilities.magik** (reference only) - `pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_migrator_utilities.magik`
   - Contains `match_core()` pattern to reference (lines 708-752)

---

## Implementation Sequence

**All clarifications confirmed - ready to proceed!**

### Step 1: Create Style Definitions in network_styles.kml
**File:** `pni_custom\rwwi_astri_integration_java\magik\rwi_export_to_kml\resources\base\data\network_styles.kml`

Add 37 object-specific styles following the existing pattern:
- Each object type needs: base style, highlight style, and StyleMap
- Insert after EQUIPMENT STYLES section (after line 200)
- Follow exact format from current file

**Styles to add:**
- OLT (1): White, ranger_station.png
- FDT variations (5): 288C, 144C, 96C, 72C, 48C - cross-hairs.png
- POLE variations (5): Update existing poles to match user color codes
- JOINT CLOSURE variations (7): 24C-288C - forbidden.png
- FAT variations (5): 48C, 32C, 24C, 16C, 8C - triangle.png
- SLACK CABLE (1): Red, target.png
- HANDHOLE variations (4): 80x80x130, 40x40x60, 20x20x60, PEDESTAL - square.png/grn-blank.png
- CABLE ADSS variations (7): 24C-288C - LineStyle only
- SUB DUCT variations (2): 40/34, 32/28 - LineStyle only

**Estimated additions:** ~2000 lines

### Step 2: Add Core Count Extraction Utility
**File:** `pni_custom\rwwi_astri_integration_java\magik\rwi_export_to_kml\source\rwi_aerial_kmz_object_writer.magik`

Add new method:
```magik
_method rwi_aerial_kmz_object_writer.extract_equipment_core_count(p_equipment)
```
- Extract core count from `sheath_splice_object_type` field
- Use wildcard matching: check for 576, 288, 144, 96, 72, 48, 36, 32, 24, 16, 8
- Return string ("288", "48", etc.) or _unset

### Step 3: Add Style Mapping Methods
Add 4 new methods to map core counts to style URLs:
- `get_fdt_style(p_core_count)` - Maps to fdt_288c_map, fdt_144c_map, etc.
- `get_fat_style(p_core_count)` - Maps to fat_48c_map, fat_32c_map, etc.
- `get_closure_style(p_core_count)` - Maps to closure_24c_map through closure_288c_map
- `get_cable_style(p_cable_spec)` - Detects ADSS and maps to cable_adss_24c_map, etc.

Each returns fallback style if core count unknown.

### Step 4: Update Existing Writer Methods
**Modify 3 methods:**

1. **write_sheath_splice()** (lines 289-364):
   - Add core count extraction call
   - Replace fixed style URLs with dynamic style mapping

2. **get_pole_style_from_folder()** (lines 161-221):
   - **Simplified logic:** Check if folder contains "PROPOSED" or "NEW POLE"
     - If PROPOSED: Extract spec from folder name ("7-2.5", "7-3", "7-4", "9-4") → Map to specific style
       - "7-2.5" → "#pole_7m_2_5inch_map"
       - "7-3" → "#pole_7m_3inch_map"
       - "7-4" → "#pole_7m_4inch_map"
       - "9-4" → "#pole_9m_4inch_map"
     - If NOT PROPOSED: Return "#pole_existing_map" (ignore owner, ignore spec)
   - **Remove:** All EMR/PARTNER/TELKOM differentiation logic

3. **write_cable()** (lines 490-553):
   - Add cable spec extraction
   - Add ADSS detection and core count-based styling

### Step 5: Testing
- Export test KMZ with variety of equipment types
- Verify in Google Earth:
  - Colors match user reference table
  - Icons display correctly
  - Fallback works for unknown core counts

**Note:** HANDHOLE and SUB DUCT writer methods will NOT be implemented (only styles created for future use)

**Estimated effort:** 2-3 days for implementation + testing

---

## Risks

**High Risk:**
- Core count field not accessible → Cannot implement core count-based styling
- HANDHOLE/SUB DUCT objects don't exist → Remove 6 object types from scope

**Medium Risk:**
- Pole spec mapping incorrect → Poles styled incorrectly
- Icon files not available → Icons display as default pushpin

**Low Risk:**
- Performance impact → Minimal (<5% slowdown expected)

---

## Implementation Status

**Status:** Plan completed and approved - ready for implementation

**Last Updated:** 2025-12-14
