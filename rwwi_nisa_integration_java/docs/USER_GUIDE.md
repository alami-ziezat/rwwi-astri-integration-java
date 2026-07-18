# NISA Integration — User Guide

**Module:** `rwwi_nisa_integration` / Java bundle `rwwi.nisa.integration`
**Location:** `pni_custom/rwwi_astri_integration_java/rwwi_nisa_integration_java/`
**Audience:** Smallworld PNI operators and developers

---

## 1. What Is This?

The **NISA Integration** connects Smallworld GIS to the **NISA REST API**
(`https://apinisa.oss.myrepublic.co.id/api`) — MyRepublic's Network
Infrastructure Service API — so that operators can check **active mass
problems (network outages)** per FTTH cluster and visualise the impacted
clusters directly on the GIS map.

It provides:

| Component | What it gives you |
|-----------|-------------------|
| **Java OSGi bundle** (`src/main/java`) | Two global Magik procedures that call the NISA API with automatic JWT authentication: `nisa_get_massproblem_active_cluster()` and `nisa_search_massproblem_by_area()` |
| **Magik module** (`magik/rwwi_nisa_integration`) | The **"Mass Problem Monitoring (NISA)"** dialog, a map-rendering plugin (colour-coded tooltips, outage zone highlighting, blink animation), plus convenience/test procedures |

### High-level data flow

```
Operator (SWIFT dialog)
      │  search by Area or Cluster Code
      ▼
rwwi_nisa_dialog ──────────► PostgreSQL (ASTRI DB)
      │                       smallworld.dim_cluster_stella_master_smallworld
      │                       (resolves cluster codes ⇄ Stella IDs, lat/lon, OLT/FDT info)
      ▼
rwwi_nisa_plugin (map rendering, tooltips, blink)
      │
      ▼
Global Magik procs (Java interop, @MagikProc)
      │  1. POST /authentication/gettoken  → JWT (muse_token)
      │  2. GET  /transaction/massproblem/active/cluster        (by Stella ID)
      │     POST /transaction/massproblem/active/cluster/search (by area name)
      ▼
NISA REST API
```

---

## 2. Prerequisites

Before using the integration, make sure:

1. **Smallworld 5.x session** with the `pni_custom` product loaded.
   `rwwi_nisa_integration` is already listed in
   `pni_custom/modules/pni_custom/module.def`, so it loads with the product.
2. **Java bundle built and in place:**
   `pni_custom/rwwi_astri_integration_java/libs/pni_custom.rwwi.nisa.integration.1.jar`
   (see §6 if you need to rebuild it).
3. **Network access** to the NISA API endpoint
   (`https://apinisa.oss.myrepublic.co.id`) — VPN may be required.
4. **PostgreSQL ASTRI DB reachable** — the dialog resolves clusters via
   `user:rwwi_external_ds_manager.open_connection_for("[POSTGRESQL_ASTRI_DB]")`.
5. **NISA credentials configured** in
   `magik/rwwi_nisa_integration/resources/nisa_config.properties`
   (packaged into the JAR at build time — see §7).

---

## 3. Opening the Dialog

The plugin registers the action **`activate_nisa_monitor`** with caption
**"NISA Mass Problem Monitor..."**. If it is wired into your application
menu/toolbar, just click it.

To open it manually from the Magik prompt (the plugin must be part of the
running application's plugin set):

```magik
# From a session where the plugin is registered in the application:
smallworld_product.applications.an_element().plugin(:rwwi_nisa_plugin).activate_nisa_monitor()
```

The dialog **"Mass Problem Monitoring (NISA)"** opens with:

```
┌──────────────────────────────────────────────────────────────────────┐
│ Search by: [Area ▾] [search text……] [🔍][✖][▶][➤][💡] N found | [▶][■] │
├──────────────────────────────────────────────────────────────────────┤
│  # │ MP No │ Cluster Code │ Stella ID │ OLT │ FDT │ Hostname │ HP │ Outage Status │
│  … result table (sortable, filterable, multi-select) …               │
├──────────────────────────────────────────────────────────────────────┤
│  Log window (progress / errors)                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Toolbar buttons

| Button (icon) | Function |
|---|---|
| **Search by** dropdown | `Area` (default) or `Cluster Code` |
| Search field | Enter at least **3 characters** |
| 🔍 **Search** | Run the search |
| ✖ **Reset** | Clear results, caches, log, and map highlights |
| ▶ **Check All Outages** | Call NISA API for every listed cluster (*Cluster Code mode only*) |
| ➤ **Go To Selected** | Pan/zoom the map to the selected cluster (exactly 1 row selected) |
| 💡 **Show on Map** (toggle) | Draw outage tooltips + red zone areas for **selected** outage clusters |
| ▶ **Start** / ■ **Stop** (after the `|` separator) | Start/stop **blink animation** for all outage clusters in the current map viewport |

---

## 4. Typical Workflows

### 4.1 Search by Area (recommended, default)

Use this to answer *"what outages are active in area X right now?"*

1. Leave **Search by** on **Area**.
2. Type an area name, e.g. `Tangerang`, and click **Search**.
3. What happens automatically:
   - NISA API is queried for all **active mass problems** in that area.
   - The unique cluster (Stella) IDs from the affected sites are looked up
     in PostgreSQL to get cluster code, OLT/FDT codes, hostname, homepass
     and coordinates.
   - One table row is created **per affected site** (MP No + cluster + port info).
   - The **Outage Status** column is pre-populated — no "Check All" needed:
     - 🔴 **Outage (N MP)** — cluster has N active mass problems
     - 🟢 **Active** — no active mass problem
     - 🟡 **Unchecked** — not yet checked
4. Select rows of interest, then use **Go To Selected**, **Show on Map**,
   or **Start** blink (see §5).

### 4.2 Search by Cluster Code

Use this to check specific clusters.

1. Switch **Search by** to **Cluster Code** (this resets current results).
2. Enter a cluster code prefix, e.g. `DPK0118` — matches
   `cluster_code_astri` or `cluster_id_stella` (ILIKE `term%`, max 25 rows).
3. Click **Search** — rows appear with status 🟡 *Unchecked*.
4. Click **▶ Check All Outages** to query the NISA API for every row.
   The log shows per-cluster progress:
   ```
   [1/5] DPK0118.WCK.057 -> OK
   [2/5] DPK0118.WCK.058 -> OUTAGE (1 MP)
   ```
5. Status icons update; continue with the map features below.

---

## 5. Map Features

All map features work on the currently open map view.

### 5.1 Go To Selected
Select **exactly one** row and click **➤**. The map pans to the cluster's
location (WGS84 lat/lon from the DB, converted to the local coordinate system).

### 5.2 Show on Map (toggle)
Select one or more rows that have an **active outage** (red icon) and toggle
**💡 Show on Map**:

- Each outage cluster gets a **tooltip bubble** on the map showing:
  - *ASTRI information*: cluster code, OLT code, FDT code, homepass
  - *Mass Problem details*: MP No, event, area, category, start time, ETA
  - *Site information*: OLT name, cluster name, and affected Frame/Slot/Port list
- If a matching **FTTH zone** exists (`ftth!zone`, Macro Cell by cluster code,
  else Micro Cell containing the outage point), the zone area is filled **red
  (50% wash)** on the map.

Toggle off (or click **Reset**) to remove the highlights. The rendering
follows the map — tooltips redraw as you pan/zoom, and only clusters in the
current view are drawn.

### 5.3 Blink Animation
Independent of *Show on Map*:

- **▶ Start** — every outage cluster in the current viewport starts a
  blinking marker animation (useful in NOC/wallboard displays).
- **■ Stop** — stops the animation and refreshes the display.

Note: only clusters **visible in the current viewport** are animated. Pan to
the area of interest first, or use **Go To Selected**.

### 5.4 Status icon legend

| Icon | Meaning |
|------|---------|
| 🟡 Unchecked | NISA API not called yet for this cluster |
| 🟢 Active | API returned success with **no** active mass problem |
| 🔴 Outage (N MP) | N active mass problem(s) affect this cluster |
| 🔴 Error | API call failed (see log window for the reason) |

---

## 6. Developer Guide

### 6.1 Building the Java bundle

Requires **Java 17** and **Maven**. The compile classpath references the
Smallworld interop JARs in `C:/Smallworld/core/libs/` (see `pom.xml`).

```powershell
cd C:\Smallworld\pni_custom\rwwi_astri_integration_java\rwwi_nisa_integration_java
mvn clean package
```

Output: `../libs/pni_custom.rwwi.nisa.integration.1.jar` — an OSGi bundle
with `JavaToMagikActivator` that auto-registers the `@MagikProc` procedures
when the bundle starts. `nisa_config.properties` is copied into the JAR
during `process-resources`, so **rebuild after changing configuration**.

The Magik module declares the dependency in `module.def`:

```
requires_java
    rwwi.nisa.integration
end
```

so the bundle is started automatically when the `rwwi_nisa_integration`
module loads (module also requires `base` and `animator_demo` for the blink
animation classes).

### 6.2 Global Magik procedures (API reference)

Provided by the **Java bundle** (`NisaMassProblemProcs`):

```magik
# Query active mass problems by Stella cluster ID. Returns raw JSON string.
json_result << nisa_get_massproblem_active_cluster("JKT.0123.JGO06.007")

# Search active mass problems by area name. Returns raw JSON string.
json_result << nisa_search_massproblem_by_area("Tangerang")
```

Both handle authentication internally (fresh JWT per call) and on failure
return `{"success":false,"error":"..."}` instead of raising, so always check
the parsed `:success` flag.

Provided by **`test_nisa_procs.magik`** (Magik convenience layer):

```magik
# Resolve ASTRI cluster code -> Stella ID via PostgreSQL, then call the API.
nisa_massproblem_by_cluster_code("DPK0118.WCK.057")

# Parse a raw JSON response into property_lists
# (:success, :msg, :data -> rope of MPs, each with :sites)
result << nisa_parse_massproblem_response(json_result)

# Convenience: query + parse in one call
result << nisa_massproblem_parsed("DPK0118.WCK.057")

# Quick console tests (no DB lookup)
test_nisa_massproblem("JKT.0123.JGO06.007")
test_nisa_massproblem_default()
```

Parsed response structure:

```
property_list
├── :success   boolean
├── :msg       string
└── :data      rope of property_lists (one per mass problem)
    ├── :mp_no, :event, :area, :category
    ├── :start_date, :start_time, :estimation
    └── :sites  rope of property_lists
        └── :olt_name, :cluster_name, :frame, :slot, :port
            (+ :clusterid in area-search responses — the Stella ID)
```

### 6.3 Important note — dummy test data is active

`rwwi_nisa_dialog.check_one_cluster()` currently calls
**`parse_massproblem_response_test()`**, which **injects a dummy mass
problem (`MP-DUMMY-0001`)** whenever the real API returns no data. This is
intentional for development so the outage/map path can be exercised.

**Before production use**, change the call in `check_one_cluster()` from
`parse_massproblem_response_test(...)` to `parse_massproblem_response(...)`,
otherwise *every* cluster will appear to have an outage in Cluster Code mode.

### 6.4 Architecture summary

| File | Role |
|------|------|
| `src/.../NisaMassProblemProcs.java` | `@MagikProc` entry points exposed to Magik |
| `src/.../internal/NisaAuthClient.java` | JWT auth: `POST /authentication/gettoken` → `muse_token` |
| `src/.../internal/NisaMassProblemClient.java` | Mass-problem endpoints, Bearer auth, cookie handling |
| `src/.../NisaConfig.java` | Singleton reading `nisa_config.properties` from the JAR |
| `magik/.../source/rwwi_nisa_dialog.magik` | SWIFT dialog: search, table, outage checks, logging |
| `magik/.../source/rwwi_nisa_plugin.magik` | Map plugin: tooltip rendering, zone highlight, blink, databus (`:goto_request`, `:geometry_to_draw/undraw`) |
| `magik/.../source/test_nisa_procs.magik` | Global convenience/test procedures |
| `magik/.../source/nisa_timer.magik` | `timed_event` background scheduler (interval timer utility) |

Design/implementation plans live alongside this guide in `docs/`:
`NISA_CLUSTER_OUTAGE_DIALOG_PLAN.md`, `NISA_AREA_FILTER_PLAN.md`,
`NISA_BLINK_ANIMATION_PLAN.md`.

---

## 7. Configuration

`magik/rwwi_nisa_integration/resources/nisa_config.properties`
(baked into the JAR — **rebuild with Maven after editing**):

| Key | Default | Description |
|-----|---------|-------------|
| `nisa.api.base.url` | `https://apinisa.oss.myrepublic.co.id/api` | NISA API base URL |
| `nisa.username` | `fms.team` | User for `/authentication/gettoken` |
| `nisa.password` | *(set in file)* | Password for token endpoint |
| `nisa.timeout.request` | `30000` | HTTP request timeout (ms) |
| `nisa.timeout.connection` | `10000` | HTTP connect timeout (ms) |

The PostgreSQL connection is **not** configured here — it uses the shared
`rwwi_external_ds_manager` data source named `[POSTGRESQL_ASTRI_DB]`.

---

## 8. Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `nisa_get_massproblem_active_cluster` is undefined | Java bundle not loaded. Check the JAR exists in `../libs/` and `requires_java rwwi.nisa.integration` resolved at module load. Rebuild with `mvn clean package`. |
| `{"success":false,"error":"NISA authentication failed..."}` | Wrong credentials in `nisa_config.properties`, or no network/VPN access to the API. Remember: config changes require a rebuild. |
| "Failed to connect to POSTGRESQL_ASTRI_DB" in the log | ASTRI PostgreSQL is unreachable or the `[POSTGRESQL_ASTRI_DB]` data source is not configured in `rwwi_external_ds_manager`. |
| Every cluster shows an outage (MP-DUMMY-0001) | The test parser is active — see §6.3. |
| "No clusters selected. Select rows in the table first." | *Show on Map* works on **selected** rows only — select the red rows first. |
| Blink: "no outage clusters in current viewport" | Only clusters visible on the map are animated — pan/zoom to the area (e.g. via **Go To Selected**) and press **Start** again. |
| Map tooltip missing for a red row | The cluster may have no coordinates in the DB (`lat`/`lon` empty) — check the log. |
| Zone not highlighted red | No matching `ftth!zone` record (Macro Cell with that cluster code, or Micro Cell containing the point). Tooltip still renders. |
| Search does nothing | Minimum **3 characters** required in the search field. |

Console/Java debug output (auth steps, HTTP status codes, response sizes) is
printed to the GIS session console — check it when diagnosing API problems.
