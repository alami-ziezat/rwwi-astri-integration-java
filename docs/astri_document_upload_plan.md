# ASTRI Document Upload API Caller — Implementation Plan

## 1. Overview

After the operator exports a Smallworld KML (which produces a `.kmz` file on disk) or generates a BOQ Excel file, the system shall automatically (or on demand) upload the file to the ASTRI Document Management API.

**Trigger points:**
| Action | File produced | Upload target |
|---|---|---|
| "Export Smallworld KML" button | `%USERPROFILE%\sw_kml\<WO_NUMBER>.kmz` | ASTRI document upload API (KMZ type) |
| "Generate BOQ Excel" button | Path selected/saved by operator | ASTRI document upload API (BOQ type — future) |

This document covers the KMZ upload path in full detail. BOQ Excel upload follows the same pattern.

---

## 2. ASTRI Upload API Specification

```
Route  : POST /v4/osp/cluster/document/add/multipart
Auth   : Basic Auth  (same credentials as existing API — AstriConfig)
Content: multipart/form-data
```

### Required fields
| Field | Type | Notes |
|---|---|---|
| `type_name` | string | Document type code (see table below) |
| `filename` | string | File name including extension, e.g. `KDR000292_APD.kmz` |
| `file` | binary | File bytes |

### Conditional fields
| Field | Required when | Example |
|---|---|---|
| `cluster_code` | infra_type = cluster or subfeeder | `KDR000292` |
| `osp_route_name` | infra_type = feeder | `karawaci_utara_segment_6` |
| `olt_name` | infra_type = olt | `olt_bsd_04` |
| `olt_site_name` | infra_type = olt_site | `olt_bsd_04` |

### `type_name` mapping for KMZ uploads
| Name (API value) | Label | Condition |
|---|---|---|
| `as_plan_drawing_kmz` | APD KMZ Cluster | infra_type=cluster, construction=Proposed |
| `as_plan_drawing_kmz_main_feeder` | APD KMZ Main Feeder | infra_type=feeder, construction=Proposed |
| `as_plan_drawing_subfeeder_kmz` | APD Subfeeder KMZ | infra_type=subfeeder, construction=Proposed |
| `as_built_drawing_kmz` | ABD Cluster KMZ | infra_type=cluster, construction=In Service |
| `as_built_drawing_kmz_main_feeder` | ABD Main Feeder KMZ | infra_type=feeder, construction=In Service |
| `network_design` | Network Design | fallback / manual override |
| `pre_abd_kmz` | Pre-ABD KMZ | infra_type=cluster, construction=Pre-ABD |
| `pre_abd_kmz_feeder` | Pre-ABD KMZ Feeder | infra_type=feeder, construction=Pre-ABD |
| `boundary_kmz` | Boundary KMZ | boundary-only export |
| `high_level_design` | High Level Design | HLD export |

The `type_name` is resolved automatically from `work_order.infrastructure_type` + `work_order.construction_status`, but the UI will expose a dropdown so the operator can override.

---

## 3. Architecture

Follows the existing Java+Magik interop pattern used by `KmzDownloadClient` / `AstriKmzDownloadProcs`.

```
Magik UI  (rwwi_astri_workorder_dialog_upload.magik)
    │  calls global Magik proc
    ▼
AstriDocumentUploadProcs.java   (@MagikProc annotations → global Magik procs)
    │  delegates to internal client
    ▼
DocumentUploadClient.java       (HTTP multipart, KML→KMZ conversion, XML response)
    │  reads config from
    ▼
AstriConfig.java                (existing singleton — base URL, credentials)
```

---

## 4. Java Components

### 4.1 `DocumentUploadClient.java`
**Package:** `com.rwi.myrepublic.astri.internal`  
**Path:** `src/main/java/com/rwi/myrepublic/astri/internal/DocumentUploadClient.java`

Responsibilities:
1. **KML → KMZ conversion** — if the input file ends with `.kml`, wrap it in a ZIP with the `.kmz` extension before upload. If it is already `.kmz`, validate the ZIP header (`PK\x03\x04`) and use as-is.
2. **Multipart POST** — build a `multipart/form-data` request using Java 11 `HttpClient` + manual boundary construction (no third-party libraries, consistent with existing clients).
3. **Return XML response** — parse HTTP response and return a structured XML string back to Magik.

Key method signatures:
```java
/**
 * Upload a KMZ (or KML, auto-converted) file to ASTRI document API.
 *
 * @param filePath     Absolute path to the .kmz or .kml file
 * @param typeName     ASTRI document type_name value
 * @param clusterCode  Cluster code (pass null for feeder/OLT)
 * @param ospRouteName OSP route name (pass null for cluster/subfeeder)
 * @param oltName      OLT name (pass null if not applicable)
 * @param oltSiteName  OLT site name (pass null if not applicable)
 * @return XML string:
 *   <response><success>true</success><document_id>...</document_id><message>...</message></response>
 *   or on failure:
 *   <response><success>false</success><error>...</error></response>
 */
public String uploadDocument(String filePath, String typeName,
                             String clusterCode, String ospRouteName,
                             String oltName, String oltSiteName)
    throws IOException, InterruptedException;

/** Convert .kml file to .kmz (ZIP). Returns path to the .kmz file. */
private Path ensureKmz(Path inputPath) throws IOException;

/** Build multipart body bytes from fields map + file bytes. */
private byte[] buildMultipartBody(Map<String, String> fields,
                                  String fileFieldName, String fileName,
                                  byte[] fileBytes, String boundary);
```

**KML → KMZ conversion logic:**
```java
// If input is .kml, create a .kmz (ZIP) alongside it
Path ensureKmz(Path inputPath) throws IOException {
    String name = inputPath.getFileName().toString();
    if (name.toLowerCase().endsWith(".kml")) {
        Path kmzPath = inputPath.resolveSibling(
            name.substring(0, name.length() - 4) + ".kmz");
        try (ZipOutputStream zos = new ZipOutputStream(
                 Files.newOutputStream(kmzPath))) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(Files.readAllBytes(inputPath));
            zos.closeEntry();
        }
        return kmzPath;
    }
    return inputPath;  // already .kmz
}
```

**API endpoint:**
```
POST {config.getApiBaseUrl()}/osp/cluster/document/add/multipart
```
Note: `getApiBaseUrl()` already returns `http://172.17.75.22/astri-api-v2/v4`, so the path becomes `/v4/osp/cluster/document/add/multipart` — matches the spec exactly.

---

### 4.2 `AstriDocumentUploadProcs.java`
**Package:** `com.rwi.myrepublic.astri`  
**Path:** `src/main/java/com/rwi/myrepublic/astri/AstriDocumentUploadProcs.java`

Exposes two global Magik procedures via `@MagikProc`:

```java
/**
 * Global Magik proc: astri_upload_kmz(file_path, type_name,
 *                        _optional cluster_code, osp_route_name, olt_name, olt_site_name)
 * Returns XML string.
 */
@MagikProc(@Name("astri_upload_kmz"))
public static Object uploadKmz(Object proc,
                                Object filePath,
                                Object typeName,
                                @Optional Object clusterCode,
                                @Optional Object ospRouteName,
                                @Optional Object oltName,
                                @Optional Object oltSiteName);

/**
 * Global Magik proc: astri_upload_document(file_path, type_name,
 *                        _optional cluster_code, osp_route_name, olt_name, olt_site_name)
 * Generic version — same impl, different name for BOQ/other docs.
 */
@MagikProc(@Name("astri_upload_document"))
public static Object uploadDocument(Object proc,
                                    Object filePath,
                                    Object typeName,
                                    @Optional Object clusterCode,
                                    @Optional Object ospRouteName,
                                    @Optional Object oltName,
                                    @Optional Object oltSiteName);
```

Both delegate to `DocumentUploadClient`. Return value is an XML string parsed by Magik.

---

## 5. Magik Components

### 5.1 `rwwi_astri_workorder_dialog_upload.magik` (new file)
**Path:** `magik/rwwi_astri_workorder/source/rwwi_astri_workorder_dialog_upload.magik`

Contains two methods on `rwwi_astri_workorder_dialog`:

#### `upload_kmz_to_astri(file_path)`
Called automatically after KMZ export succeeds, or manually via the Upload button.

Logic:
1. Determine `type_name` from selected work order:
   ```magik
   infra_type  << wo[:infrastructure_type].default("cluster")
   const_stat  << wo[:construction_status].default("Proposed")
   type_name   << _self.resolve_upload_type_name(infra_type, const_stat)
   ```
2. Resolve conditional fields (`cluster_code`, `osp_route_name`, `olt_name`)
3. Allow operator to override `type_name` via dropdown in the upload confirmation dialog
4. Call `astri_upload_kmz(file_path, type_name, cluster_code, ...)`
5. Parse XML response and display result in the log panel

#### `resolve_upload_type_name(infra_type, const_status)`
Returns the correct `type_name` string:
```magik
_method rwwi_astri_workorder_dialog.resolve_upload_type_name(infra_type, const_status)
    _local it << infra_type.default("cluster").lowercase
    _local cs << const_status.default("Proposed")
    _if cs = "In Service"
    _then
        _if it = "cluster"   _then >> "as_built_drawing_kmz"            _endif
        _if it = "feeder"    _then >> "as_built_drawing_kmz_main_feeder" _endif
        _if it = "subfeeder" _then >> "as_built_drawing_kmz"            _endif
    _else  # Proposed / default
        _if it = "cluster"   _then >> "as_plan_drawing_kmz"             _endif
        _if it = "feeder"    _then >> "as_plan_drawing_kmz_main_feeder" _endif
        _if it = "subfeeder" _then >> "as_plan_drawing_subfeeder_kmz"   _endif
    _endif
    >> "network_design"  # fallback
_endmethod
```

#### `upload_boq_to_astri(_optional file_path)`
Called manually via an "Upload BOQ" button. If `file_path` is unset, opens a file chooser. Then calls `astri_upload_document(file_path, "high_level_design", cluster_code, ...)`.

---

### 5.2 Changes to `rwwi_astri_workorder_dialog_export.magik`

In `export_smallworld_kml()`, after the success block (line ~253), add an auto-upload call:

```magik
# Auto-upload KMZ to ASTRI after successful export
_if .items[:auto_upload_kmz] _isnt _unset _andif
    .items[:auto_upload_kmz].value _is _true
_then
    _self.upload_kmz_to_astri(output_path)
_endif
```

---

### 5.3 Changes to `rwwi_astri_workorder_dialog.magik` — toolbar UI

Add to `build_toolbar2` (or a new `build_toolbar4`):

1. **Auto-upload checkbox** — `sw_boolean_item` (or `sw_text_item`) labelled "Auto Upload KMZ"
2. **Upload KMZ button** — triggers `upload_kmz_to_astri` with last exported file path (stored in `.last_kmz_path` slot)
3. **Upload BOQ button** — triggers `upload_boq_to_astri()` with file chooser
4. **Type name dropdown** — 10-item list of `type_name` values with human-readable labels; default resolved automatically from work order

New slots on `rwwi_astri_workorder_dialog`:
```magik
{:last_kmz_path, _unset, :writable},   # Path of most recently exported KMZ
{:last_boq_path, _unset, :writable},   # Path of most recently saved BOQ Excel
```

---

## 6. Integration / Call Flow

### KMZ Upload (auto)
```
export_smallworld_kml()
  → exporter.export_mixed_network()   returns output_path (.kmz)
  → .last_kmz_path << output_path
  → [if auto_upload checked]
      upload_kmz_to_astri(output_path)
        → resolve_upload_type_name(infra_type, const_status)  → type_name
        → astri_upload_kmz(path, type_name, cluster_code, ...)  [Java proc]
          → DocumentUploadClient.uploadDocument()
              → ensureKmz()   [no-op if already .kmz]
              → POST /v4/osp/cluster/document/add/multipart
              → return XML
        → parse XML → log result in dialog text panel
```

### KMZ Upload (manual)
```
[User clicks "Upload KMZ" button]
  → upload_kmz_to_astri(.last_kmz_path)   [same tail as above]
```

### BOQ Excel Upload (manual)
```
[User clicks "Upload BOQ" button]
  → upload_boq_to_astri()
      → file chooser if .last_boq_path is unset
      → astri_upload_document(path, "high_level_design", cluster_code, ...)
      → parse XML → log result
```

---

## 7. XML Response Contract

### Success
```xml
<response>
  <success>true</success>
  <document_id>12345</document_id>
  <message>Document uploaded successfully</message>
</response>
```

### Failure
```xml
<response>
  <success>false</success>
  <error>Unauthorized</error>
  <http_status>401</http_status>
</response>
```

Magik parses this with `simple_xml.read_element_string()` (existing pattern from `download_kmz()`).

---

## 8. File Structure

```
src/main/java/com/rwi/myrepublic/astri/
  internal/
    DocumentUploadClient.java        ← NEW
  AstriDocumentUploadProcs.java      ← NEW

magik/rwwi_astri_workorder/source/
  rwwi_astri_workorder_dialog_upload.magik   ← NEW
  rwwi_astri_workorder_dialog.magik          ← MODIFY (slots + toolbar buttons)
  rwwi_astri_workorder_dialog_export.magik   ← MODIFY (auto-upload hook)
```

---

## 9. Implementation Order

1. **`DocumentUploadClient.java`** — core HTTP + KMZ logic, testable standalone
2. **`AstriDocumentUploadProcs.java`** — Magik bridge, register procs
3. **`rwwi_astri_workorder_dialog_upload.magik`** — Magik methods (`upload_kmz_to_astri`, `upload_boq_to_astri`, `resolve_upload_type_name`)
4. **Toolbar UI changes** — add slots, buttons, dropdown to dialog
5. **Auto-upload hook** — add trigger in `export_smallworld_kml()` after success

---

## 10. Open Questions

- **BOQ upload `type_name`**: The spec lists only KMZ types. Confirm with ASTRI team if there is a dedicated type for BOQ Excel, or if it should use a generic document type.
- **Duplicate upload guard**: Should the system prevent re-uploading the same file (check by filename or cluster_code)? Or is overwrite acceptable?
- **Upload confirmation dialog**: Should there be a modal confirm step before upload, showing the resolved `type_name` and target cluster, so the operator can verify?
- **Error retry**: On HTTP 5xx, should the system retry automatically (e.g., up to 3 times) or just report failure?
