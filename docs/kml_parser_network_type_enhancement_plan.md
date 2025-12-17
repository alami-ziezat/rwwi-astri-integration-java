# KML Parser Enhancement Plan: Network Type Detection

## Overview

Enhance `astri_kml_parser.magik` to automatically detect and classify network object types based on KML style icons and geometry patterns. Add a new `:network_type` key to each placemark property_list.

---

## Current Implementation Analysis

### File Location
`pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_kml_parser.magik`

### Current parse_placemark() Output
```magik
property_list.new_with(
    :name       - Placemark name
    :desc       - Description text
    :coord      - Coordinates string
    :type       - Geometry type ("point", "line", "area")
    :id         - Placemark id attribute
    :parent     - Parent folder path
    :ring_name  - Ring name (FDT/Closure splice name)
    :extended   - Extended data property_list
)
```

### Current KML Structure
```xml
<Document>
    <!-- Style definitions -->
    <Style id="olt">
        <IconStyle>
            <Icon>
                <href>http://maps.google.com/mapfiles/kml/shapes/ranger_station.png</href>
            </Icon>
        </IconStyle>
    </Style>
    <StyleMap id="olt_map">
        <Pair>
            <key>normal</key>
            <styleUrl>#olt</styleUrl>
        </Pair>
    </StyleMap>

    <!-- Placemarks -->
    <Placemark>
        <name>OLT ABC</name>
        <styleUrl>#olt_map</styleUrl>
        <Point>...</Point>
    </Placemark>
</Document>
```

---

## Requirements

### 1. Network Type Mapping

Map icon hrefs to network types:

| Network Type | Icon Href | Geometry |
|--------------|-----------|----------|
| `olt` | `ranger_station.png` | Point |
| `fdt` | `cross-hairs.png` | Point |
| `pole` | `placemark_circle.png` | Point |
| `joint_closure` | `forbidden.png` | Point |
| `slack_cable` | `target.png` | Point |
| `homepass` | `homegardenbusiness.png` | Point |
| `handhole` | `square.png` | Point |
| `handhole` | `grn-blank.png` | Point (pedestal) |
| `fat` | `triangle.png` | Point |
| `cable` | (any icon) | LineString |
| `sling_wire` | (any icon) | LineString + name contains "sw" |

### 2. Detection Logic Priority

1. **LineString geometry with "sw" in name** → `sling_wire`
2. **LineString geometry** → `cable`
3. **Point/Polygon geometry** → Look up icon href in mapping

### 3. Style Cache Implementation

To avoid repeated KML style lookups, implement a cache:

```magik
# Cache structure (property_list):
# Key: styleUrl (e.g., "#olt_map")
# Value: network_type (e.g., "olt")

_local style_cache << property_list.new()
```

**Cache Logic:**
1. When parsing placemark, extract `styleUrl`
2. Check if `styleUrl` exists in cache
3. If cached: Use cached `network_type`
4. If not cached:
   - Look up style in KML document
   - Extract icon href
   - Map href to network_type
   - Store in cache
   - Return network_type

---

## Implementation Strategy

### Phase 1: Add Style Parsing Infrastructure

**File:** `astri_kml_parser.magik`

**Add new slots to exemplar (line 14-21):**
```magik
def_slotted_exemplar(:astri_kml_parser,
    {
        {:kml_content, _unset},
        {:splice_names, _unset},
        {:splice_name_mapping, _unset},
        {:splice_index_mapping, _unset},
        {:current_splice_folder_name, _unset},
        {:style_cache, _unset},           # NEW: Cache styleUrl → network_type
        {:kml_document, _unset}            # NEW: Store parsed KML document for style lookups
    })
$
```

**Update parse() method (line 55-82):**
```magik
_method astri_kml_parser.parse()
    # ... existing code ...

    # Initialize style cache
    .style_cache << property_list.new()

    # ... existing code ...
_endmethod
```

### Phase 2: Store KML Document Reference

**Update extract_placemarks_from_file() method (line 99-147):**

After parsing the KML document (line 119), store it for later style lookups:

```magik
_for element _over sxml.read_elements_from(kml_file_path)
_loop
    # ... existing code ...

    _if elem_type.matches?(C_DOCUMENT)
    _then
        # Store document reference for style lookups
        .kml_document << element

        # Process Document and all its children recursively
        _self.process_element(element, "", result_rope)
        _leave
    _endif
_endloop
```

### Phase 3: Create Network Type Detection Method

**Add new method after parse_placemark() (after line 387):**

```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.detect_network_type(placemark_element, pl)
    ## Detect network type based on styleUrl and icon href
    ##
    ## Parameters:
    ##   placemark_element - simple_xml placemark element
    ##   pl - property_list containing placemark data (with :type, :name)
    ##
    ## Returns: string - network_type ("olt", "fdt", "fat", etc.) or _unset

    # PRIORITY 1: LineString with "sw" in name
    _if pl[:type] = "line" _andif
        pl[:name].lowercase.index_of_seq("sw") _isnt _unset
    _then
        _return "sling_wire"
    _endif

    # PRIORITY 2: LineString (any other line)
    _if pl[:type] = "line"
    _then
        _return "cable"
    _endif

    # PRIORITY 3: Point geometry - look up icon from styleUrl
    _if pl[:type] = "point" _orif pl[:type] = "area"
    _then
        # Extract styleUrl from placemark
        _local style_url << _self.extract_style_url(placemark_element)

        _if style_url _is _unset
        _then
            _return _unset
        _endif

        # Check cache first
        _if .style_cache.includes_key?(style_url)
        _then
            _return .style_cache[style_url]
        _endif

        # Not cached - look up in KML document
        _local icon_href << _self.lookup_icon_href_from_style(style_url)

        _if icon_href _is _unset
        _then
            _return _unset
        _endif

        # Map icon href to network type
        _local network_type << _self.map_icon_to_network_type(icon_href)

        # Cache the result
        _if network_type _isnt _unset
        _then
            .style_cache[style_url] << network_type
        _endif

        _return network_type
    _endif

    _return _unset
_endmethod
$
```

### Phase 4: Create Helper Methods

**Add after detect_network_type():**

```magik
_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.extract_style_url(placemark_element)
    ## Extract styleUrl from placemark element
    ##
    ## Parameters:
    ##   placemark_element - simple_xml placemark element
    ##
    ## Returns: string - styleUrl (e.g., "#olt_map") or _unset

    _constant C_STYLEURL << "styleUrl"

    _for child _over placemark_element.xml_elements.fast_elements()
    _loop
        _if child _isnt _unset _andif
            child.type.write_string.matches?(C_STYLEURL)
        _then
            _local url_elem << child.xml_elements.an_element()
            _if url_elem _isnt _unset
            _then
                _return url_elem.write_string
            _endif
        _endif
    _endloop

    _return _unset
_endmethod
$

_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.lookup_icon_href_from_style(style_url)
    ## Look up icon href from styleUrl in KML document
    ## Handles both direct Style and StyleMap references
    ##
    ## Parameters:
    ##   style_url - styleUrl string (e.g., "#olt_map" or "#olt")
    ##
    ## Returns: string - icon href filename or _unset

    _if .kml_document _is _unset _orif style_url _is _unset
    _then
        _return _unset
    _endif

    # Remove '#' prefix
    _local style_id << style_url
    _if style_id.index_of(1) = %#
    _then
        style_id << style_id.slice(2, style_id.size)
    _endif

    _constant C_STYLE << "Style"
    _constant C_STYLEMAP << "StyleMap"
    _constant C_PAIR << "Pair"
    _constant C_KEY << "key"
    _constant C_STYLEURL << "styleUrl"
    _constant C_ICONSTYLE << "IconStyle"
    _constant C_ICON << "Icon"
    _constant C_HREF << "href"

    # Search document for matching Style or StyleMap
    _for element _over .kml_document.xml_elements.fast_elements()
    _loop
        _if element _is _unset
        _then
            _continue
        _endif

        elem_type << element.type.write_string

        # Check if this is a Style element with matching id
        _if elem_type.matches?(C_STYLE)
        _then
            _local elem_id << element.attributes[:id]
            _if elem_id _isnt _unset _andif elem_id = style_id
            _then
                # Found matching Style - extract icon href
                _return _self.extract_icon_href_from_style(element)
            _endif

        # Check if this is a StyleMap element with matching id
        _elif elem_type.matches?(C_STYLEMAP)
        _then
            _local elem_id << element.attributes[:id]
            _if elem_id _isnt _unset _andif elem_id = style_id
            _then
                # Found matching StyleMap - resolve to normal Style
                _local normal_style_url << _self.resolve_stylemap_to_style(element)
                _if normal_style_url _isnt _unset
                _then
                    # Recursively look up the referenced Style
                    _return _self.lookup_icon_href_from_style(normal_style_url)
                _endif
            _endif
        _endif
    _endloop

    _return _unset
_endmethod
$

_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.resolve_stylemap_to_style(stylemap_element)
    ## Resolve StyleMap to its normal Style reference
    ##
    ## Parameters:
    ##   stylemap_element - simple_xml StyleMap element
    ##
    ## Returns: string - styleUrl for normal style (e.g., "#olt") or _unset

    _constant C_PAIR << "Pair"
    _constant C_KEY << "key"
    _constant C_STYLEURL << "styleUrl"

    # Find Pair with key="normal"
    _for pair _over stylemap_element.xml_elements.fast_elements()
    _loop
        _if pair _isnt _unset _andif
            pair.type.write_string.matches?(C_PAIR)
        _then
            _local key_value << _unset
            _local style_url << _unset

            # Extract key and styleUrl from Pair
            _for child _over pair.xml_elements.fast_elements()
            _loop
                _if child _is _unset
                _then
                    _continue
                _endif

                elem_type << child.type.write_string

                _if elem_type.matches?(C_KEY)
                _then
                    _local key_elem << child.xml_elements.an_element()
                    _if key_elem _isnt _unset
                    _then
                        key_value << key_elem.write_string
                    _endif

                _elif elem_type.matches?(C_STYLEURL)
                _then
                    _local url_elem << child.xml_elements.an_element()
                    _if url_elem _isnt _unset
                    _then
                        style_url << url_elem.write_string
                    _endif
                _endif
            _endloop

            # Check if this is the "normal" Pair
            _if key_value = "normal" _andif style_url _isnt _unset
            _then
                _return style_url
            _endif
        _endif
    _endloop

    _return _unset
_endmethod
$

_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.extract_icon_href_from_style(style_element)
    ## Extract icon href from Style element
    ##
    ## Parameters:
    ##   style_element - simple_xml Style element
    ##
    ## Returns: string - icon filename (e.g., "ranger_station.png") or _unset

    _constant C_ICONSTYLE << "IconStyle"
    _constant C_ICON << "Icon"
    _constant C_HREF << "href"

    # Search for IconStyle > Icon > href
    _for child _over style_element.xml_elements.fast_elements()
    _loop
        _if child _isnt _unset _andif
            child.type.write_string.matches?(C_ICONSTYLE)
        _then
            # Found IconStyle - look for Icon
            _for icon_child _over child.xml_elements.fast_elements()
            _loop
                _if icon_child _isnt _unset _andif
                    icon_child.type.write_string.matches?(C_ICON)
                _then
                    # Found Icon - look for href
                    _for href_child _over icon_child.xml_elements.fast_elements()
                    _loop
                        _if href_child _isnt _unset _andif
                            href_child.type.write_string.matches?(C_HREF)
                        _then
                            # Found href - extract value
                            _local href_elem << href_child.xml_elements.an_element()
                            _if href_elem _isnt _unset
                            _then
                                _local full_href << href_elem.write_string
                                # Extract filename from URL
                                _return _self.extract_filename_from_url(full_href)
                            _endif
                        _endif
                    _endloop
                _endif
            _endloop
        _endif
    _endloop

    _return _unset
_endmethod
$

_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.extract_filename_from_url(url)
    ## Extract filename from URL
    ## Example: "http://maps.google.com/mapfiles/kml/shapes/ranger_station.png" → "ranger_station.png"
    ##
    ## Parameters:
    ##   url - Full URL string
    ##
    ## Returns: string - filename or url if no slash found

    _if url _is _unset
    _then
        _return _unset
    _endif

    # Find last slash
    _local last_slash_index << url.rindex_of(%/)
    _if last_slash_index _isnt _unset
    _then
        _return url.slice(last_slash_index + 1, url.size)
    _endif

    # No slash found - return whole url
    _return url
_endmethod
$

_pragma(classify_level=basic, topic={astri_integration})
_private _method astri_kml_parser.map_icon_to_network_type(icon_filename)
    ## Map icon filename to network type
    ##
    ## Parameters:
    ##   icon_filename - Icon filename (e.g., "ranger_station.png")
    ##
    ## Returns: string - network_type or _unset

    _if icon_filename _is _unset
    _then
        _return _unset
    _endif

    _local filename_lc << icon_filename.lowercase

    # Map icon filenames to network types
    _if filename_lc.index_of_seq("ranger_station") _isnt _unset
    _then
        _return "olt"

    _elif filename_lc.index_of_seq("cross-hairs") _isnt _unset _orif
          filename_lc.index_of_seq("crosshairs") _isnt _unset
    _then
        _return "fdt"

    _elif filename_lc.index_of_seq("placemark_circle") _isnt _unset
    _then
        _return "pole"

    _elif filename_lc.index_of_seq("forbidden") _isnt _unset
    _then
        _return "joint_closure"

    _elif filename_lc.index_of_seq("target") _isnt _unset
    _then
        _return "slack_cable"

    _elif filename_lc.index_of_seq("homegardenbusiness") _isnt _unset
    _then
        _return "homepass"

    _elif filename_lc.index_of_seq("square") _isnt _unset
    _then
        _return "handhole"

    _elif filename_lc.index_of_seq("grn-blank") _isnt _unset
    _then
        _return "handhole"  # Pedestal handhole

    _elif filename_lc.index_of_seq("triangle") _isnt _unset
    _then
        _return "fat"

    _else
        # Unknown icon
        _return _unset
    _endif
_endmethod
$
```

### Phase 5: Update parse_placemark() to Add Network Type

**Modify parse_placemark() method (line 269-387):**

```magik
_method astri_kml_parser.parse_placemark(placemark_element, parent_path, _optional ring_name)
    # ... existing initialization code ...

    # Initialize property_list with NEW :network_type key
    pl << property_list.new_with(
        :name, "unnamed",
        :desc, "",
        :coord, "",
        :type, "unknown",
        :id, _unset,
        :parent, parent_path,
        :ring_name, ring_name,
        :network_type, _unset,        # NEW
        :extended, property_list.new())

    # ... existing extraction code ...

    # After all geometry and attributes are extracted, detect network type
    _local network_type << _self.detect_network_type(placemark_element, pl)
    pl[:network_type] << network_type

    _return pl
_endmethod
```

---

## Testing Strategy

### Test Case 1: Point Objects with Icons

**Input KML:**
```xml
<Placemark>
    <name>Test OLT</name>
    <styleUrl>#olt_map</styleUrl>
    <Point><coordinates>106.123,10.456</coordinates></Point>
</Placemark>
```

**Expected Output:**
```magik
property_list(:name, "Test OLT",
              :type, "point",
              :network_type, "olt")
```

### Test Case 2: LineString (Cable)

**Input KML:**
```xml
<Placemark>
    <name>Fiber Cable</name>
    <styleUrl>#cable_map</styleUrl>
    <LineString><coordinates>106.1,10.4 106.2,10.5</coordinates></LineString>
</Placemark>
```

**Expected Output:**
```magik
property_list(:name, "Fiber Cable",
              :type, "line",
              :network_type, "cable")
```

### Test Case 3: Sling Wire (LineString with "sw")

**Input KML:**
```xml
<Placemark>
    <name>LINE-A-sw-123</name>
    <styleUrl>#sling_wire_map</styleUrl>
    <LineString><coordinates>106.1,10.4 106.2,10.5</coordinates></LineString>
</Placemark>
```

**Expected Output:**
```magik
property_list(:name, "LINE-A-sw-123",
              :type, "line",
              :network_type, "sling_wire")
```

### Test Case 4: Style Cache Performance

**Scenario:** 1000 placemarks using same styleUrl
**Expected Behavior:**
- First placemark: Full style lookup (slow)
- Remaining 999: Cache hit (fast)
- Only 1 KML document traversal

---

## Implementation Checklist

- [ ] **Phase 1:** Add `:style_cache` and `:kml_document` slots to exemplar
- [ ] **Phase 1:** Initialize `.style_cache` in `parse()` method
- [ ] **Phase 2:** Store `.kml_document` reference in `extract_placemarks_from_file()`
- [ ] **Phase 3:** Implement `detect_network_type()` method
- [ ] **Phase 4:** Implement `extract_style_url()` helper
- [ ] **Phase 4:** Implement `lookup_icon_href_from_style()` helper
- [ ] **Phase 4:** Implement `resolve_stylemap_to_style()` helper
- [ ] **Phase 4:** Implement `extract_icon_href_from_style()` helper
- [ ] **Phase 4:** Implement `extract_filename_from_url()` helper
- [ ] **Phase 4:** Implement `map_icon_to_network_type()` helper
- [ ] **Phase 5:** Update `parse_placemark()` to add `:network_type` key
- [ ] **Phase 5:** Call `detect_network_type()` before returning placemark property_list
- [ ] **Testing:** Test with OLT point objects
- [ ] **Testing:** Test with FDT/FAT/Closure point objects
- [ ] **Testing:** Test with LineString cables
- [ ] **Testing:** Test with sling wires (name contains "sw")
- [ ] **Testing:** Verify cache performance with repeated styleUrls
- [ ] **Testing:** Test with unknown icons (should return _unset)

---

## Benefits

1. **Automatic Classification:** No manual object type specification needed
2. **Performance:** Style cache prevents repeated KML traversals
3. **Extensibility:** Easy to add new icon → network_type mappings
4. **Backwards Compatible:** Existing code continues to work (`:network_type` is _unset)
5. **Robust:** Handles StyleMap → Style indirection automatically
6. **Geometry-Based Detection:** Sling wire detection uses both geometry and naming

---

## Future Enhancements

1. **Sub-type Detection:** Add `:network_subtype` for core counts (e.g., "fdt_288c", "fat_48c")
2. **Style Attributes:** Extract color information from styles
3. **Custom Icon Support:** Handle non-Google Maps icon URLs
4. **Performance Metrics:** Log cache hit rate for optimization

---

## File Changes Summary

**Single File Modified:**
- `pni_custom\rwwi_astri_integration_java\magik\rwwi_astri_integration\source\astri_kml_parser.magik`

**Changes:**
- Add 2 new slots to exemplar
- Modify 2 existing methods (parse, extract_placemarks_from_file, parse_placemark)
- Add 8 new private methods (detect_network_type, extract_style_url, lookup_icon_href_from_style, resolve_stylemap_to_style, extract_icon_href_from_style, extract_filename_from_url, map_icon_to_network_type)
- Estimated additions: ~350 lines

---

**End of Plan Document**
