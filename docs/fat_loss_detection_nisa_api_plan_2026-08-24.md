# FAT Loss Detection & Segment Problem Detail — NISA API Java Callers

**Date:** 2026-08-24
**Author:** Claude Code
**Status:** DRAFT - Awaiting Approval

---

## 1. Overview

Add two new NISA API callers to the existing `rwwi_nisa_integration_java` Java module (same jar as
`nisa_get_massproblem_active_cluster` / `nisa_search_massproblem_by_area`):

1. **FAT Loss Detection** — `GET /fatlossticketing/fat-loss-detection` (no parameters)
2. **Segment Problem Detail** — `GET /fatlossticketing/segment-problem-detail?area=&hostname=&fat=` (all three query params required)

Both live under a new `fatlossticketing` namespace, distinct from the existing `massproblem` namespace,
but reuse everything already built for NISA: `NisaConfig` (base URL/credentials/timeouts) and
`NisaAuthClient` (JWT bearer token via `POST /authentication/gettoken`). No auth changes needed.

**Base path:** `https://apinisa.oss.myrepublic.co.id/api/transaction` — same as the existing
massproblem client, which is `NisaConfig.getApiBaseUrl()` (`.../api`) + `/transaction/...` appended
per-call. The two new endpoints follow the identical convention:
`getApiBaseUrl() + "/transaction/fatlossticketing/fat-loss-detection"` and
`getApiBaseUrl() + "/transaction/fatlossticketing/segment-problem-detail"`.

**Key difference from the existing massproblem client:** `NisaMassProblemClient` sends its filter as a
JSON body on a GET request (`.method("GET", BodyPublishers.ofString(body))`), because that's what that
endpoint expects. The new segment-problem-detail endpoint's spec instead shows a real query string
(`?area=JAKARTA&hostname=OLT-JKT-01&fat=FAT001`), so the new client builds a proper URL-encoded query
string instead of a body-on-GET. fat-loss-detection takes no parameters at all — a plain `GET` with only
the `Authorization: Bearer` header.

---

## 2. New Files

All under the existing `rwwi_nisa_integration_java/src/main/java/com/rwi/myrepublic/nisa/` tree —
no new Maven module, no `pom.xml` changes (same `Export-Package: com.rwi.myrepublic.nisa`, same output
jar `pni_custom.rwwi.nisa.integration.1.jar`).

### 2.1 `internal/NisaFatLossClient.java` (new)

Modeled on `NisaMassProblemClient.java`'s structure (own `HttpClient` + `NisaAuthClient` instance,
same logging style, same `IOException` on non-200).

```java
public class NisaFatLossClient {
    private final NisaConfig config;
    private final HttpClient httpClient;
    private final NisaAuthClient authClient;

    public NisaFatLossClient() {
        this.config = NisaConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
            .build();
        this.authClient = new NisaAuthClient(config, httpClient);
    }

    /** GET /transaction/fatlossticketing/fat-loss-detection — no parameters. */
    public String detectFatLoss() throws IOException, InterruptedException {
        String token = authClient.getToken();
        String url = config.getApiBaseUrl() + "/transaction/fatlossticketing/fat-loss-detection";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("NISA fat-loss-detection API returned HTTP " + response.statusCode()
                + " | Body: " + response.body());
        }
        return response.body();
    }

    /**
     * GET /transaction/fatlossticketing/segment-problem-detail?area=&hostname=&fat=
     * All three params are required by the API (it returns a validation error otherwise);
     * this client still sends whatever it's given and lets the API's own "message" field
     * ("area is required" etc.) surface back to Magik as-is rather than duplicating
     * that validation here.
     */
    public String getSegmentProblemDetail(String area, String hostname, String fat)
            throws IOException, InterruptedException {
        String token = authClient.getToken();
        String url = config.getApiBaseUrl() + "/transaction/fatlossticketing/segment-problem-detail"
            + "?area=" + urlEncode(area)
            + "&hostname=" + urlEncode(hostname)
            + "&fat=" + urlEncode(fat);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("NISA segment-problem-detail API returned HTTP " + response.statusCode()
                + " | Body: " + response.body());
        }
        return response.body();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
```

Notes:
- Both responses are returned **raw** (as-is) to Magik, same convention as `nisa_get_massproblem_active_cluster`
  — Magik parses with `json_parser`, not Java.
- `segment-problem-detail`'s two documented failure shapes (`{"success":false,"message":"area is required"}`
  on validation and `{"success":false,"msg":"failed get segment problem detail","data":[]}` on server error)
  are just passed through as the raw body on HTTP 200 — no special-casing needed in Java, since both are
  valid JSON the Magik-side parser can inspect via `[:success]`.
- A non-200 HTTP status still throws `IOException` (same as the existing client), which the Procs layer
  below turns into `{"success":false,"error":"..."}` — consistent with how HTTP-level failures are already
  surfaced for massproblem.

### 2.2 `NisaFatLossProcs.java` (new)

Modeled on `NisaMassProblemProcs.java` — same try/catch → JSON-error-string fallback pattern.

```java
public class NisaFatLossProcs {

    /** Creates global Magik procedure: nisa_fat_loss_detection() */
    @MagikProc(@Name("nisa_fat_loss_detection"))
    public static Object detectFatLoss(Object proc) {
        try {
            NisaFatLossClient client = new NisaFatLossClient();
            String jsonResponse = client.detectFatLoss();
            return MagikInteropUtils.toMagikString(jsonResponse);
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    /** Creates global Magik procedure: nisa_segment_problem_detail(area, hostname, fat) */
    @MagikProc(@Name("nisa_segment_problem_detail"))
    public static Object getSegmentProblemDetail(Object proc, Object area, Object hostname, Object fat) {
        try {
            String areaStr     = MagikInteropUtils.fromMagikString(area);
            String hostnameStr = MagikInteropUtils.fromMagikString(hostname);
            String fatStr      = MagikInteropUtils.fromMagikString(fat);

            NisaFatLossClient client = new NisaFatLossClient();
            String jsonResponse = client.getSegmentProblemDetail(areaStr, hostnameStr, fatStr);
            return MagikInteropUtils.toMagikString(jsonResponse);
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private static Object errorJson(Exception e) {
        String errorJson = "{\"success\":false,\"error\":" + jsonEscape(e.getMessage()) + "}";
        try {
            return MagikInteropUtils.toMagikString(errorJson);
        } catch (Exception e2) {
            return errorJson;
        }
    }

    private static String jsonEscape(String value) { /* same helper as NisaMassProblemProcs */ }
}
```

`area`/`hostname`/`fat` are required by the *API*, not enforced again in Java — a blank value just
travels through and the API's own `"area is required"`-style message comes back for Magik to show.
This matches the plan note above (§2.1) about not duplicating the API's own validation.

---

## 3. Magik Layer

**File:** `magik/rwwi_nisa_integration/source/test_nisa_procs.magik` (append to existing file — same
module the other NISA test/parse helpers already live in, no new module needed)

Add two thin parse helpers (same shape as `nisa_parse_massproblem_response`) plus test globals:

- `nisa_parse_fat_loss_detection_response(json_string)` → `property_list` with `:success`, `:msg`,
  `:is_fat_loss_detected`, `:active_ticket_count`, `:active_tickets` (rope of property_lists with
  `:ticket_number`, `:tlop_status`, `:status_name`, `:tlop_created_date`, `:affected_customer`).
- `nisa_parse_segment_problem_detail_response(json_string)` → `property_list` with `:success`, `:msg`,
  `:total`, `:data` (rope of property_lists with `:ticket_number`, `:tlop_status`, `:status_name`,
  `:tlop_created_date`, `:tlop_area_name`, `:tlop_cluster_name`, `:fdt_code`, `:fat_code`, `:hostname`).
- `test_nisa_fat_loss_detection()` — calls `nisa_fat_loss_detection()` directly, prints response.
- `test_nisa_segment_problem_detail(area, hostname, fat)` — calls `nisa_segment_problem_detail(...)`
  directly, prints response.

No UI wiring (toolbar buttons, dialog fields) is included in this plan — the ask was for the API
callers themselves, following the same "caller first, wire into a dialog later" pattern already used
for `nisa_search_massproblem_by_area`. Flagging this so it's an explicit scope decision, not an
oversight.

---

## 4. Build & Verify

1. `mvn -q -o package` in `pni_custom/rwwi_astri_integration_java/rwwi_nisa_integration_java/` —
   confirm the two new classes land in `pni_custom.rwwi.nisa.integration.1.jar` (`jar tf ... | grep -i FatLoss`).
2. Restart `gis.exe` to pick up the rebuilt bundle and the appended Magik test procs.
3. At the Magik prompt:
   - `nisa_fat_loss_detection()` → confirm a JSON string comes back and `nisa_parse_fat_loss_detection_response(...)`
     extracts `:is_fat_loss_detected` / `:active_tickets` correctly.
   - `nisa_segment_problem_detail("JAKARTA", "OLT-JKT-01", "FAT001")` → confirm a JSON string comes back
     and `nisa_parse_segment_problem_detail_response(...)` extracts `:data` correctly.
   - `nisa_segment_problem_detail("", "", "")` → confirm the API's `"area is required"` validation
     message surfaces through unmodified (proves the no-duplicate-validation decision in §2.1/§2.2 is safe).

---

*Plan created: 2026-08-24. Ready for review and approval.*
