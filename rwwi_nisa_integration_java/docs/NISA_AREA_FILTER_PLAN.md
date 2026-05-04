# NISA Dialog — Area Filter Improvement Plan

## 1. Goal

Add a **Search Mode** selector to the NISA dialog toolbar that switches between:

- **Cluster Code** (existing) — searches PostgreSQL by `cluster_code_astri` / `cluster_id_stella` prefix, then user manually triggers NISA outage checks.
- **Area** (new) — sends area name to a new NISA endpoint, receives mass problems + affected cluster IDs, fetches full cluster details from PostgreSQL, and **auto-populates the outage cache** from the API response.

Both modes produce identical `result_rows` and `outage_cache` structures, so all downstream components (table, map rendering, tooltips, "Check All" button) are **unchanged and not broken**.

---

## 2. Files to Change

| File | Type of Change |
|------|----------------|
| `src/main/java/com/rwi/myrepublic/nisa/internal/NisaMassProblemClient.java` | Add `searchMassProblemByArea(area)` method |
| `src/main/java/com/rwi/myrepublic/nisa/NisaMassProblemProcs.java` | Add `@MagikProc` for `nisa_search_massproblem_by_area` |
| `magik/rwwi_nisa_integration/source/rwwi_nisa_dialog.magik` | Add mode slot, mode UI, new search/parse/cache methods |

**No changes to:** `rwwi_nisa_plugin.magik`, `test_nisa_procs.magik`, `module.def`, `load_list.txt`, `pom.xml`

---

## 3. Java Layer Changes

### 3.1 `NisaMassProblemClient.java` — new method

```java
/**
 * Search active mass problems by area name.
 *
 * POST /transaction/massproblem/active/cluster/search
 * Authorization: Bearer <muse_token>
 * Content-Type: application/json
 * Body: {"cluster":"<area>"}
 *
 * Response is the same mass-problem structure as getMassProblemActiveCluster,
 * but each site also includes "clusterid" (the stella ID).
 *
 * @param area  Area name (e.g. "Tangerang")
 * @return Raw JSON response body
 */
public String searchMassProblemByArea(String area) throws IOException, InterruptedException {
    System.out.println("  [NisaMassProblemClient] Fetching auth token (area search)...");
    String token = authClient.getToken();

    String url  = config.getApiBaseUrl() + "/transaction/massproblem/active/cluster/search";
    String body = "{\"cluster\":\"" + escapeJson(area) + "\"}";

    System.out.println("  [NisaMassProblemClient] POST " + url);
    System.out.println("  [NisaMassProblemClient] Body: " + body);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .timeout(Duration.ofMillis(config.getRequestTimeout()))
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    int    statusCode    = response.statusCode();
    String responseBody  = response.body();

    System.out.println("  [NisaMassProblemClient] Response status: " + statusCode);
    System.out.println("  [NisaMassProblemClient] Response length: "
        + (responseBody != null ? responseBody.length() : 0));

    if (statusCode != 200) {
        throw new IOException("NISA area search API returned HTTP " + statusCode
            + " | Body: " + responseBody);
    }
    return responseBody;
}
```

> **Key difference from `getMassProblemActiveCluster`:** uses `.POST(...)` (not `.method("GET", ...)`), and hits a different endpoint path (`/cluster/search`).

### 3.2 `NisaMassProblemProcs.java` — new `@MagikProc`

```java
/**
 * Search active mass problems by area name.
 *
 * Creates global Magik procedure: nisa_search_massproblem_by_area(area_name)
 *
 * @param proc      Magik proc object (required first parameter)
 * @param areaName  Area name string (e.g. "Tangerang")
 * @return Raw JSON response string, or error JSON on failure
 */
@MagikProc(@Name("nisa_search_massproblem_by_area"))
public static Object searchMassProblemByArea(Object proc, Object areaName) {
    try {
        System.out.println("====== NISA SEARCH MASSPROBLEM BY AREA - START ======");
        String area = MagikInteropUtils.fromMagikString(areaName);
        System.out.println("  Area: " + area);

        if (area == null || area.trim().isEmpty()) {
            throw new IllegalArgumentException("area_name must not be empty");
        }

        NisaMassProblemClient client = new NisaMassProblemClient();
        String jsonResponse = client.searchMassProblemByArea(area.trim());

        System.out.println("  Response length: " + (jsonResponse != null ? jsonResponse.length() : 0));
        System.out.println("====== NISA SEARCH MASSPROBLEM BY AREA - END ======");

        return MagikInteropUtils.toMagikString(jsonResponse);

    } catch (Exception e) {
        System.err.println("ERROR in nisa_search_massproblem_by_area: " + e.getMessage());
        e.printStackTrace();
        String errorJson = "{\"success\":false,\"error\":" + jsonEscape(e.getMessage()) + "}";
        try { return MagikInteropUtils.toMagikString(errorJson); }
        catch (Exception e2) { return errorJson; }
    }
}
```

### 3.3 Build step

After Java changes: `mvn package` — the Maven Jar Plugin already copies the output JAR to `../libs/pni_custom.rwwi.nisa.integration.1`. No `pom.xml` changes needed.

---

## 4. Magik Layer Changes — `rwwi_nisa_dialog.magik`

### 4.1 New slot

Add to the `def_slotted_exemplar` block (after `:selected_cluster`):

```magik
{:search_mode, _unset, :writable}   # :cluster_code (default) or :area
```

In `init()`, add:

```magik
.search_mode << :cluster_code
```

### 4.2 `build_toolbar()` — UI additions

Insert these items **before** the existing `sw_label_item.new(tb, "  Cluster Code: ")` line:

```magik
# Search mode selector
sw_label_item.new(tb, "  Search by: ")

.items[:mode_selector] << sw_choice_item.new(tb,
    :model,          _self,
    :display_length, 14,
    :editable?,      _false)
.items[:mode_selector].set_items({"Cluster Code", "Area"})
.items[:mode_selector].selection_index << 1
.items[:mode_selector].change_selector << :mode_changed|()|
```

Then **replace** the existing static `sw_label_item.new(tb, "  Cluster Code: ")` with:

```magik
.items[:search_label] << sw_label_item.new(tb, "  Cluster Code: ")
```

This makes the label dynamic so `mode_changed()` can update it.

### 4.3 New method `mode_changed()`

```magik
_pragma(classify_level=basic, topic={nisa_integration})
_method rwwi_nisa_dialog.mode_changed()
    ## Handle mode selector change — update label and clear current results.

    _local idx << .items[:mode_selector].selection_index
    _if idx = 1
    _then
        .search_mode << :cluster_code
        .items[:search_label].value << "  Cluster Code: "
    _else
        .search_mode << :area
        .items[:search_label].value << "  Area: "
    _endif
    _self.reset()
_endmethod
$
```

### 4.4 Modified `search_clusters()` — dispatcher only

Replace the existing body with:

```magik
_pragma(classify_level=basic, topic={nisa_integration})
_method rwwi_nisa_dialog.search_clusters()
    ## Dispatch to cluster-code or area search based on current mode.

    _local term << .items[:search_field].value.default("").trim_spaces()
    _if term.size < 3
    _then
        _self.log_separator()
        _self.show_alert("Please enter at least 3 characters to search.")
        _return
    _endif

    _if .search_mode _is :area
    _then
        _self.search_by_area(term)
    _else
        _self.search_by_cluster_code(term)
    _endif
_endmethod
$
```

### 4.5 New private method `search_by_cluster_code(term)`

Extract the **existing** `search_clusters()` body verbatim into:

```magik
_private _method rwwi_nisa_dialog.search_by_cluster_code(term)
    ## [Existing implementation from search_clusters() — no logic changes]
    ## Logs, connects to DB, runs ILIKE query, populates result_rows.
    _self.log_separator()
    _self.log_info("Searching clusters: '" + term + "'")
    # ... (all existing code unchanged) ...
_endmethod
$
```

### 4.6 New private method `search_by_area(area_name)`

```magik
_private _method rwwi_nisa_dialog.search_by_area(area_name)
    ## Area-mode search flow:
    ##   1. Call NISA area search API -> list of mass problems with sites
    ##   2. Extract unique clusterid values from all sites
    ##   3. Query PostgreSQL for those clusters (by cluster_id_stella)
    ##   4. Build result_rows (same structure as cluster_code mode)
    ##   5. Pre-populate outage_cache from API response

    _self.log_separator()
    _self.log_info("Searching by area: '" + area_name + "'")

    ## -- Step 1: Call NISA area search API --
    _local json_result   << nisa_search_massproblem_by_area(area_name)
    _local area_response << _self.parse_area_search_response(json_result)

    _if _not area_response[:success]
    _then
        _self.log_error("NISA area search failed: " + area_response[:msg])
        _return
    _endif

    _local mp_list << area_response[:data]
    _self.log_info(write_string("NISA returned ", mp_list.size, " mass problems"))

    ## -- Step 2: Extract unique clusterids from all sites --
    _local clusterid_set << equality_hash_table.new()
    _for mp_pl _over mp_list.fast_elements()
    _loop
        _for site_pl _over mp_pl[:sites].fast_elements()
        _loop
            _local cid << site_pl[:clusterid]
            _if cid _isnt _unset _andif cid <> ""
            _then
                clusterid_set[cid] << _true
            _endif
        _endloop
    _endloop

    _if clusterid_set.empty?
    _then
        _self.log_warning("No cluster IDs found in NISA area response.")
        _return
    _endif

    _self.log_info(write_string("Found ", clusterid_set.size, " unique cluster IDs from sites"))

    ## -- Step 3: Query DB for full cluster details --
    _local db_conn << _unset
    _protect
        _try _with errCon

            _local is_connect?
            (is_connect?, db_conn) << user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")
            _if _not is_connect?
            _then
                _self.log_error("Failed to connect to POSTGRESQL_ASTRI_DB")
                _return
            _endif

            ## Build safe IN clause — values come from NISA API (validated, not user input)
            _local safe_parts << rope.new()
            _for cid, _dummy _over clusterid_set.fast_keys_and_elements()
            _loop
                _if _self.int!safe_cluster_id?(cid)
                _then
                    safe_parts.add_last("'" + cid + "'")
                _endif
            _endloop

            _if safe_parts.empty?
            _then
                _self.log_warning("No valid cluster IDs after safety check.")
                _return
            _endif

            ## Join: 'ID1', 'ID2', ...
            _local in_clause << ""
            _for i _over 1.upto(safe_parts.size)
            _loop
                _if i > 1 _then in_clause << in_clause + ", " _endif
                in_clause << in_clause + safe_parts[i]
            _endloop

            _local sql << write_string(
                "SELECT cluster_id_stella, cluster_code_astri, olt_code, fdt_code, ",
                "cluster_latitude, cluster_longitude, olt_hostname, homepass_total ",
                "FROM smallworld.dim_cluster_stella_master_smallworld ",
                "WHERE cluster_id_stella IN (", in_clause, ") ",
                "ORDER BY cluster_code_astri")

            _local recs << db_conn.sql_select(sql)
            .result_rows.empty()
            _loop
                _local rec << recs.get()
                _if rec _is _unset _then _leave _endif
                _local cc << rec.cluster_code_astri.default("").write_string
                _local pl << property_list.new_with(
                    :cluster_id_stella, rec.cluster_id_stella.default("").write_string,
                    :cluster_code,      cc,
                    :olt_code,          rec.olt_code.default("").write_string,
                    :fdt_code,          rec.fdt_code.default("").write_string,
                    :lat,               rec.cluster_latitude.default("").write_string,
                    :lon,               rec.cluster_longitude.default("").write_string,
                    :olt_hostname,      rec.olt_hostname.default("").write_string,
                    :homepass_total,    rec.homepass_total.default("").write_string,
                    :zone_rwo,          _unset)

                ## Zone lookup — same pattern as cluster_code mode
                _try
                    _local zone_col << _self.database.collections[:ftth!zone]
                    _if zone_col _isnt _unset
                    _then
                        pl[:zone_rwo] << zone_col.select(
                            predicate.eq(:type, "Macro Cell") _and
                            predicate.eq(:cluster_code, cc)).an_element()
                    _endif
                _when error
                _endtry

                .result_rows.add_last(pl)
            _endloop
            recs.close()

            _try
                db_conn.commit()
            _when error
                db_conn.rollback()
            _endtry

        _when error
            _self.log_error("Area search DB error: " + errCon.report_contents_string)
        _endtry

    _protection
        _if db_conn _isnt _unset
        _then
            _try
                db_conn.rollback()
                db_conn.close()
            _when error
            _endtry
        _endif
    _endprotect

    ## -- Step 4: Pre-populate outage_cache from NISA response --
    _self.int!prepopulate_outage_cache(mp_list)

    _local n << .result_rows.size
    .items[:count_label].value << write_string("  ", n, " clusters found  ")
    _self.log_success(write_string("Found ", n, " clusters via area '", area_name, "'"))
    _self.changed(:cluster_list, :renew)
    _self.manage_actions()
_endmethod
$
```

### 4.7 New private method `parse_area_search_response(json_string)`

Identical to the existing `parse_massproblem_response()`, with one addition: each site property_list also sets `:clusterid`.

```magik
_private _method rwwi_nisa_dialog.parse_area_search_response(json_string)
    ## Parse NISA area search response JSON.
    ## Same structure as parse_massproblem_response but sites include :clusterid
    ## (the stella ID, e.g. "JKT.0123.JGO06.007") which is used to join back to DB.

    _local result << property_list.new()
    result[:success] << _false
    result[:msg]     << ""
    result[:data]    << rope.new()

    _if json_string _is _unset _orif json_string.empty?
    _then
        result[:msg] << "No response"
        _return result
    _endif

    _try _with errCon
        _local json_obj << json_parser.parse(json_string)
        result[:success] << json_obj[:success].default(_false)
        result[:msg]     << json_obj[:msg].default("").write_string

        _local json_data << json_obj[:data]
        _if json_data _is _unset _then _return result _endif

        _local data_rope << rope.new()
        _for mp_item _over json_data.fast_elements()
        _loop
            _local mp_pl << property_list.new()
            mp_pl[:mp_no]      << mp_item[:mp_no].default("").write_string
            mp_pl[:event]      << mp_item[:event].default("").write_string
            mp_pl[:area]       << mp_item[:area].default("").write_string
            mp_pl[:category]   << mp_item[:category].default("").write_string
            mp_pl[:start_date] << mp_item[:start_date].default("").write_string
            mp_pl[:start_time] << mp_item[:start_time].default("").write_string
            mp_pl[:estimation] << mp_item[:estimation].default("").write_string

            _local json_sites << mp_item[:sites]
            _local sites_rope << rope.new()
            _if json_sites _isnt _unset
            _then
                _for site_item _over json_sites.fast_elements()
                _loop
                    _local site_pl << property_list.new()
                    site_pl[:olt_name]     << site_item[:olt_name].default("").write_string
                    site_pl[:cluster_name] << site_item[:cluster_name].default("").write_string
                    site_pl[:clusterid]    << site_item[:clusterid].default("").write_string  # NEW
                    site_pl[:frame]        << site_item[:frame].default(0).write_string
                    site_pl[:slot]         << site_item[:slot].default(0).write_string
                    site_pl[:port]         << site_item[:port].default(0).write_string
                    sites_rope.add_last(site_pl)
                _endloop
            _endif

            mp_pl[:sites] << sites_rope
            data_rope.add_last(mp_pl)
        _endloop
        result[:data] << data_rope

    _when error
        result[:msg] << "Parse error: " + errCon.report_contents_string
        _self.log_error("parse_area_search_response: " + errCon.report_contents_string)
    _endtry

    >> result
_endmethod
$
```

### 4.8 New private method `int!prepopulate_outage_cache(mp_list)`

```magik
_private _method rwwi_nisa_dialog.int!prepopulate_outage_cache(mp_list)
    ## Pre-populate outage_cache from area search results.
    ##
    ## For each cluster in result_rows, collects the mass problems where any
    ## site's clusterid matches the cluster's cluster_id_stella.
    ## Stores under .outage_cache[cluster_code] using the same structure
    ## that check_one_cluster() produces, so all display/map code is unaffected.

    _for cluster_pl _over .result_rows.fast_elements()
    _loop
        _local stella_id   << cluster_pl[:cluster_id_stella]
        _local cc          << cluster_pl[:cluster_code]
        _local matched_mps << rope.new()

        _for mp_pl _over mp_list.fast_elements()
        _loop
            _local added? << _false
            _for site_pl _over mp_pl[:sites].fast_elements()
            _loop
                _if _not added? _andif site_pl[:clusterid] = stella_id
                _then
                    matched_mps.add_last(mp_pl)
                    added? << _true
                _endif
            _endloop
        _endloop

        .outage_cache[cc] << property_list.new_with(
            :success, _true,
            :msg,     "area search",
            :data,    matched_mps)
    _endloop
_endmethod
$
```

### 4.9 New private method `int!safe_cluster_id?(id)`

```magik
_private _method rwwi_nisa_dialog.int!safe_cluster_id?(id)
    ## Return _true if id contains only alphanumeric, '.', '-', '_'.
    ## Prevents injection via unexpected clusterid values from the API.

    _if id _is _unset _orif id = "" _then _return _false _endif
    _for i _over 1.upto(id.size)
    _loop
        _local c << id[i]
        _if _not (c.alphanumeric? _orif c = %. _orif c = %- _orif c = %_)
        _then
            _return _false
        _endif
    _endloop
    >> _true
_endmethod
$
```

---

## 5. Data Flow Comparison

```
CLUSTER CODE MODE                      AREA MODE
──────────────────────────────────     ──────────────────────────────────────────
User types: "DPK0118"                  User types: "Tangerang"
      ↓                                      ↓
search_by_cluster_code("DPK0118")      search_by_area("Tangerang")
      ↓                                      ↓
PostgreSQL ILIKE query                 POST /cluster/search {"cluster":"Tangerang"}
      ↓                                      ↓
result_rows populated                  Parse API response → mp_list
      ↓                                      ↓
outage_cache empty (unchecked)         Extract clusterids from all sites
                                             ↓
                                       PostgreSQL IN (id1, id2, ...) query
                                             ↓
                                       result_rows populated (same structure)
                                             ↓
                                       int!prepopulate_outage_cache(mp_list)
                                             ↓
                                       outage_cache filled (pre-checked)
      ↓                                      ↓
Table shows yellow "Unchecked"         Table shows red/green icons immediately
      ↓                                      ↓
User clicks "Check All"                Not needed — data already loaded
(manual NISA call per cluster)         ("Check All" still works if needed)
```

---

## 6. Field Population — Both Modes Identical

All `result_rows` property_list fields come from the same DB columns in both modes:

| Field | DB Column | Source |
|-------|-----------|--------|
| `:cluster_id_stella` | `cluster_id_stella` | PostgreSQL |
| `:cluster_code` | `cluster_code_astri` | PostgreSQL |
| `:olt_code` | `olt_code` | PostgreSQL |
| `:fdt_code` | `fdt_code` | PostgreSQL |
| `:lat` | `cluster_latitude` | PostgreSQL |
| `:lon` | `cluster_longitude` | PostgreSQL |
| `:olt_hostname` | `olt_hostname` | PostgreSQL |
| `:homepass_total` | `homepass_total` | PostgreSQL |
| `:zone_rwo` | GIS DB `ftth!zone` (Macro Cell) | GIS database |

The `outage_cache[cluster_code][:data]` rope entries use the same property_list keys (`mp_no`, `event`, `area`, `category`, `start_date`, `start_time`, `estimation`, `sites`) in both modes.

---

## 7. Backward Compatibility — What Is NOT Changed

| Component | Status |
|-----------|--------|
| `cluster_list_data()` | Unchanged — reads result_rows + outage_cache |
| `cluster_selected()` | Unchanged |
| `check_outage_selected()` | Unchanged — still works in area mode (re-calls API) |
| `check_outage_all()` | Unchanged — still works in area mode (overrides pre-populated cache) |
| `check_one_cluster()` | Unchanged |
| `goto_selected()` | Unchanged |
| `show_map()` / `records()` | Unchanged |
| `outage_status_for()` | Unchanged |
| `outage_tooltip_for()` | Unchanged |
| `get_outage_label()` | Unchanged |
| `manage_actions()` | Unchanged |
| `reset()` | Unchanged — clears both caches for both modes |
| `rwwi_nisa_plugin.magik` | Not touched |
| `test_nisa_procs.magik` | Not touched |

---

## 8. `outage_tooltip_for()` — Area Mode Tooltip Content

Since area mode pre-populates `outage_cache` with the same structure, the existing tooltip method produces the correct output without any changes:

```
Cluster : JOGLO RW 06
OLT     : (from DB olt_code)
FDT     : (from DB fdt_code)
Homepass: (from DB homepass_total)
-- MASS PROBLEM ACTIVE --
MP No   : MP2026042440
Event   : MP
Area    : Tangerang
Category: OLT
Start   : 2026-04-14 20:02:00
ETA     : 1 x 24 jam
Sites   : JOGLO RW 06 (TNG-CDG-OLT1-FH)
```

---

## 9. Implementation Steps (Execution Order)

1. **Java** — Add `searchMassProblemByArea()` to `NisaMassProblemClient.java`
2. **Java** — Add `@MagikProc nisa_search_massproblem_by_area` to `NisaMassProblemProcs.java`
3. **Java** — Run `mvn package` to rebuild JAR (copies to `../libs/` automatically)
4. **Magik** — Add `:search_mode` slot + init to `:cluster_code`
5. **Magik** — Update `build_toolbar()`: add `sw_choice_item` + make label item dynamic
6. **Magik** — Add `mode_changed()` method
7. **Magik** — Refactor `search_clusters()` into dispatcher + `search_by_cluster_code(term)`
8. **Magik** — Add `search_by_area(area_name)` method
9. **Magik** — Add `parse_area_search_response(json_string)` method
10. **Magik** — Add `int!prepopulate_outage_cache(mp_list)` method
11. **Magik** — Add `int!safe_cluster_id?(id)` method
12. **Test** — Cluster Code mode: verify existing flow unchanged
13. **Test** — Area mode: enter "Tangerang", verify table populated + outage icons shown
14. **Test** — Switch modes mid-session: verify reset clears state correctly

---

## 10. Risk / Notes

| Risk | Mitigation |
|------|-----------|
| Area search returns 0 sites | Log warning, show 0 results, no error thrown |
| Cluster ID from API not in DB | Silently skipped — DB query only returns matching rows |
| `int!safe_cluster_id?` rejects valid ID | Only alphanumeric + `.`, `-`, `_` — should cover all real stella IDs |
| `sw_choice_item.change_selector` not available in this SW version | Fallback: use a separate button to change mode, or use `sw_image_toggle_item` pair |
| Area mode + "Check All": re-checks NISA per cluster (slow) | Acceptable — "Check All" in area mode is optional since cache is already filled |
