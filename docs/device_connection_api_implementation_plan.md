# Device Connection API Implementation Plan

**Document Version:** 1.1 (UPDATED)
**Created:** 2026-01-02
**Last Updated:** 2026-01-02
**Status:** PENDING APPROVAL
**Author:** Claude Code Assistant

---

## Table of Contents

1. [Overview](#overview)
2. [Requirements Analysis](#requirements-analysis)
3. [API Endpoint Specification](#api-endpoint-specification)
4. [Implementation Approach](#implementation-approach)
5. [File Structure](#file-structure)
6. [Detailed Implementation Steps](#detailed-implementation-steps)
7. [Error Handling Strategy](#error-handling-strategy)
8. [Testing Strategy](#testing-strategy)
9. [Integration Points](#integration-points)
10. [Implementation Checklist](#implementation-checklist)
11. [Appendices](#appendices)

---

## 1. Overview

### 1.1 Purpose
Implement a new Java API caller to retrieve device connection information (available/taken cores) from ASTRI's Device Connection API endpoint. The API returns detailed information about fiber optic core allocations in feeder and subfeeder infrastructure.

### 1.2 Business Context
This API enables Smallworld GIS to:
- Query available fiber cores in feeder/subfeeder cables
- Track core usage and allocations (AVAILABLE, TAKEN, RESERVED)
- Support network planning and capacity management
- Integrate device connection data with design workflows

### 1.3 Technical Scope
- **New Java Procedure:** `astri_get_device_connections()`
- **New Internal Client:** `DeviceConnectionClient`
- **HTTP Method:** POST (differs from existing GET-based work order APIs)
- **Response Format:** JSON string (directly returned to Magik, no XML conversion)
- **Authentication:** Reuse existing Basic Auth mechanism
- **JSON Parsing:** Magik-side JSON parsing (similar to BoQ API approach)

---

## 2. Requirements Analysis

### 2.1 Functional Requirements

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| FR-001 | POST request to `/v4/device/connection/list/all/{limit}/{offset}` | HIGH |
| FR-002 | Support pagination via `limit` and `offset` path parameters | HIGH |
| FR-003 | Accept EITHER `transport_feeder_code` OR `transport_subfeeder_code` in request body (only send one, omit the other) | HIGH |
| FR-004 | Determine which code to send based on `infra_type` parameter | HIGH |
| FR-005 | Return JSON response directly as Magik string (no XML conversion) | HIGH |
| FR-006 | Reuse existing authentication (Basic Auth from `AstriConfig`) | HIGH |
| FR-007 | Reuse existing API base URL and timeout configuration | HIGH |
| FR-008 | Return detailed device connection data including core status | HIGH |
| FR-009 | Handle large response payloads (potentially 100+ connections) | MEDIUM |
| FR-010 | Provide comprehensive error messages | MEDIUM |

**Change Log (v1.1):**
- **REMOVED FR-005 (XML conversion)** - Now returns JSON string directly like `AstriBoqProcs`
- **UPDATED FR-003** - Only send one field in request body, don't include the other as null

### 2.2 Non-Functional Requirements

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| NFR-001 | Response time < 5 seconds for typical queries | HIGH |
| NFR-002 | Handle HTTP 4xx/5xx errors gracefully | HIGH |
| NFR-003 | Log requests/responses for debugging | HIGH |
| NFR-004 | Follow existing code patterns (consistency with `AstriBoqProcs`) | HIGH |
| NFR-005 | Thread-safe client implementation | MEDIUM |
| NFR-006 | Minimal memory footprint for large responses | MEDIUM |

### 2.3 Constraints

1. **Technology Stack:** Java 11+, existing Magik interop framework
2. **Authentication:** Must use existing Basic Auth from `astri_config.properties`
3. **API Base URL:** Same domain as work order API (`http://172.17.75.22/astri-api-v2/v4`)
4. **Response Format:** Return JSON string directly (Magik will parse JSON on its side)
5. **Infrastructure Types:** Must support `feeder` and `subfeeder` (not `cluster`)

---

## 3. API Endpoint Specification

### 3.1 Endpoint Details

**URL Pattern:**
```
POST http://172.17.75.22/astri-api-v2/v4/device/connection/list/all/{limit}/{offset}
```

**Path Parameters:**
- `{limit}` (integer) - Number of records to return (e.g., 50, 100)
- `{offset}` (integer) - Starting offset for pagination (e.g., 0, 50, 100)

**Request Headers:**
```http
Authorization: Basic <base64_encoded_credentials>
Content-Type: application/json
```

**Request Body (Feeder):**
```json
{
  "transport_feeder_code": "FPLB0073"
}
```

**Request Body (Subfeeder):**
```json
{
  "transport_subfeeder_code": "PLB005917"
}
```

**⚠️ IMPORTANT:** Only send ONE field based on `infra_type`:
- `infra_type = "feeder"` → Send `{"transport_feeder_code": "..."}` only
- `infra_type = "subfeeder"` → Send `{"transport_subfeeder_code": "..."}` only
- **Do NOT send both fields**
- **Do NOT set unused field to null**

### 3.2 Response Structure

**Success Response (HTTP 200):**
```json
{
  "success": true,
  "count": 48,
  "count_all": 48,
  "data": [
    {
      "uuid": "6fec3954-1301-44db-affe-409c2b9f9d1e",
      "source_device_name": null,
      "source_device_hardware_type": null,
      "source_device_port_name": null,
      "source_device_port_type": null,
      "source_device_remarks": null,
      "source_device_capacity": null,
      "source_device_capacity_measurement": null,
      "source_bng_device_code": null,
      "source_bng_name": null,
      "source_bng_label": null,
      "source_bng_hostname": null,
      "source_olt_device_code": null,
      "source_olt_name": null,
      "source_olt_label": null,
      "source_olt_hostname": null,
      "source_feeder_code": "FPLB0073",
      "source_feeder_name": "palembang-main_feeder_olt_pangkalan_balai_segment_2_to_suak_tapeh_fo_144c",
      "source_feeder_label": "PALEMBANG-MAIN_FEEDER_OLT_PANGKALAN_BALAI_SEGMENT_2_TO_SUAK_TAPEH_FO_144C",
      "source_subfeeder_code": null,
      "source_subfeeder_name": null,
      "source_cluster_code": null,
      "source_cluster_name": null,
      "source_tube_number": 1,
      "source_core_number": 5,
      "transport_name": null,
      "transport_hardware_type": null,
      "transport_capacity": null,
      "transport_capacity_measurement": null,
      "transport_feeder_code": null,
      "transport_feeder_name": null,
      "transport_feeder_label": null,
      "transport_feeder_tube_count": null,
      "transport_feeder_core_count": null,
      "transport_subfeeder_code": "PLB005917",
      "transport_subfeeder_name": "SETERIO RW 01 SAMPAI 03 PALEMBANG",
      "transport_subfeeder_tube_count": 4,
      "transport_subfeeder_core_count": 48,
      "transport_tube_number": 1,
      "transport_core_number": 5,
      "transport_status": "TAKEN",
      "transport_remarks": null,
      "closure_code": null,
      "destination_device_name": null,
      "destination_device_hardware_type": null,
      "destination_device_port_name": null,
      "destination_device_port_type": null,
      "destination_device_remarks": null,
      "destination_device_capacity": null,
      "destination_device_capacity_measurement": null,
      "destination_bng_device_code": null,
      "destination_bng_name": null,
      "destination_bng_label": null,
      "destination_bng_hostname": null,
      "destination_olt_device_code": null,
      "destination_olt_name": null,
      "destination_olt_label": null,
      "destination_olt_hostname": null,
      "destination_feeder_code": null,
      "destination_feeder_name": null,
      "destination_feeder_label": null,
      "destination_tube_number": 1,
      "destination_core_number": 5,
      "destination_subfeeder_code": null,
      "destination_subfeeder_name": null,
      "destination_cluster_code": "PLB005917",
      "destination_cluster_name": "SETERIO RW 01 SAMPAI 03 PALEMBANG",
      "work_order_number": null,
      "requested_by_username": null,
      "requested_by_fullname": null,
      "verified_by_username": "muhammad.falan",
      "verified_by_fullname": "Muhammad Alfi Falan",
      "verified_at": "2025-11-17 11:14:04",
      "created_at": "2025-11-17 11:14:31",
      "updated_at": null
    }
  ]
}
```

**Error Response (HTTP 4xx/5xx):**
```json
{
  "success": false,
  "error": "Invalid feeder code",
  "message": "Feeder code 'INVALID' not found in database"
}
```

### 3.3 Key Response Fields (71 Total)

#### Pagination Fields (3)
- `success` (boolean) - API call success status
- `count` (integer) - Number of records in current response
- `count_all` (integer) - Total number of matching records

#### Device Connection Fields (71)

**Source Device (7 fields):**
- `source_device_name`
- `source_device_hardware_type`
- `source_device_port_name`
- `source_device_port_type`
- `source_device_remarks`
- `source_device_capacity`
- `source_device_capacity_measurement`

**Source BNG (4 fields):**
- `source_bng_device_code`
- `source_bng_name`
- `source_bng_label`
- `source_bng_hostname`

**Source OLT (4 fields):**
- `source_olt_device_code`
- `source_olt_name`
- `source_olt_label`
- `source_olt_hostname`

**Source Network (9 fields):**
- `source_feeder_code`, `source_feeder_name`, `source_feeder_label`
- `source_subfeeder_code`, `source_subfeeder_name`
- `source_cluster_code`, `source_cluster_name`
- `source_tube_number`, `source_core_number`

**Transport Device (4 fields):**
- `transport_name`
- `transport_hardware_type`
- `transport_capacity`
- `transport_capacity_measurement`

**Transport Network (13 fields):**
- `transport_feeder_code`, `transport_feeder_name`, `transport_feeder_label`, `transport_feeder_tube_count`, `transport_feeder_core_count`
- `transport_subfeeder_code`, `transport_subfeeder_name`, `transport_subfeeder_tube_count`, `transport_subfeeder_core_count`
- `transport_tube_number`, `transport_core_number`
- `transport_status` (**KEY FIELD:** "AVAILABLE", "TAKEN", "RESERVED")
- `transport_remarks`

**Closure (1 field):**
- `closure_code`

**Destination Device (7 fields):**
- `destination_device_name`
- `destination_device_hardware_type`
- `destination_device_port_name`
- `destination_device_port_type`
- `destination_device_remarks`
- `destination_device_capacity`
- `destination_device_capacity_measurement`

**Destination BNG (4 fields):**
- `destination_bng_device_code`
- `destination_bng_name`
- `destination_bng_label`
- `destination_bng_hostname`

**Destination OLT (4 fields):**
- `destination_olt_device_code`
- `destination_olt_name`
- `destination_olt_label`
- `destination_olt_hostname`

**Destination Network (8 fields):**
- `destination_feeder_code`, `destination_feeder_name`, `destination_feeder_label`
- `destination_tube_number`, `destination_core_number`
- `destination_subfeeder_code`, `destination_subfeeder_name`
- `destination_cluster_code`, `destination_cluster_name`

**Metadata (9 fields):**
- `work_order_number`
- `requested_by_username`, `requested_by_fullname`
- `verified_by_username`, `verified_by_fullname`
- `verified_at`, `created_at`, `updated_at`

---

## 4. Implementation Approach

### 4.1 Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│ Magik Layer (Smallworld GIS)                                 │
│                                                               │
│  astri_get_device_connections(infra_type, infra_code,       │
│                                 limit, offset)               │
│  → Returns: JSON string                                      │
│  → Magik parses JSON (no XML)                                │
│                        ↓                                      │
└────────────────────────┼────────────────────────────────────-┘
                         │
                         │ (Magik Interop)
                         ↓
┌──────────────────────────────────────────────────────────────┐
│ Java Layer (pni_custom/rwwi_astri_integration_java)         │
│                                                               │
│  AstriDeviceConnectionProcs.java                             │
│    - @MagikProc annotation                                   │
│    - Parameter conversion (Magik → Java)                     │
│    - Request body construction (ONE field only)              │
│    - Response conversion (JSON String → Magik String)        │
│                        ↓                                      │
│  DeviceConnectionClient.java (internal)                      │
│    - HTTP POST request with JSON body                        │
│    - Basic Authentication                                    │
│    - Return JSON response as-is (no XML conversion)          │
│    - Error handling                                          │
│                        ↓                                      │
│  AstriConfig.java                                            │
│    - API base URL                                            │
│    - Credentials                                             │
│    - Timeouts                                                │
└────────────────────────┼────────────────────────────────────-┘
                         │
                         │ (HTTP POST)
                         ↓
┌──────────────────────────────────────────────────────────────┐
│ ASTRI API Server                                             │
│  http://172.17.75.22/astri-api-v2/v4                        │
│  /device/connection/list/all/{limit}/{offset}               │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 Design Decisions

| Decision | Rationale |
|----------|-----------|
| **New `AstriDeviceConnectionProcs` class** | Separate concerns - device connections vs work orders/BoQ |
| **POST method** | API requires POST with JSON body (not GET with query params) |
| **Single-field request body** | Send ONLY feeder OR subfeeder code, not both (API requirement) |
| **Return JSON directly** | Follow `AstriBoqProcs` pattern - Magik handles JSON parsing |
| **No XML conversion** | Simpler, more maintainable, matches BoQ API approach |
| **Reuse `AstriConfig`** | Consistent configuration, no duplication |
| **71-field response** | Preserve all API fields for future extensibility |

### 4.3 Code Organization

**New Files:**
1. `src/main/java/com/rwi/myrepublic/astri/AstriDeviceConnectionProcs.java`
   - Magik-exposed procedure
   - Parameter validation
   - Request orchestration
   - Returns JSON string directly

2. `src/main/java/com/rwi/myrepublic/astri/internal/DeviceConnectionClient.java`
   - HTTP POST client
   - Minimal JSON handling (no conversion)
   - Response passthrough

**Modified Files:**
- None (new functionality is additive)

---

## 5. File Structure

### 5.1 Java Source Files

```
pni_custom/rwwi_astri_integration_java/
└── src/main/java/com/rwi/myrepublic/astri/
    ├── AstriDeviceConnectionProcs.java          [NEW]
    │   └── Magik-exposed procedure for device connections
    │
    ├── internal/
    │   └── DeviceConnectionClient.java          [NEW]
    │       └── Internal HTTP client for device connection API
    │
    ├── AstriConfig.java                         [EXISTING - NO CHANGES]
    │   └── Shared configuration (base URL, auth, timeouts)
    │
    ├── AstriBoqProcs.java                       [EXISTING - REFERENCE]
    │   └── Reference for JSON string return pattern
    │
    └── AstriWorkOrderProcs.java                 [EXISTING - NO CHANGES]
        └── Existing work order procedures
```

### 5.2 Documentation Files

```
pni_custom/rwwi_astri_integration_java/docs/
└── device_connection_api_implementation_plan.md  [THIS FILE]
```

---

## 6. Detailed Implementation Steps

### 6.1 Phase 1: Create AstriDeviceConnectionProcs.java

**File:** `src/main/java/com/rwi/myrepublic/astri/AstriDeviceConnectionProcs.java`

**Signature:**
```java
@MagikProc(@Name("astri_get_device_connections"))
public static Object getDeviceConnections(
    Object proc,
    Object infrastructureType,
    Object infrastructureCode,
    Object limit,
    Object offset
)
```

**Parameters:**
- `infrastructureType` (Magik string) - "feeder" or "subfeeder"
- `infrastructureCode` (Magik string) - Feeder or subfeeder code (e.g., "FPLB0073", "PLB005917")
- `limit` (Magik integer) - Number of records to fetch (e.g., 50, 100)
- `offset` (Magik integer) - Starting offset for pagination (e.g., 0, 50)

**Returns:**
- Magik string - JSON response (as-is from API)

**Implementation:**

```java
package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.DeviceConnectionClient;

/**
 * ASTRI Device Connection API procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriDeviceConnectionProcs {

    /**
     * Get device connections (available/taken cores) from ASTRI API.
     *
     * Creates global Magik procedure:
     *   astri_get_device_connections(infrastructure_type, infrastructure_code, limit, offset)
     *
     * @param proc The Magik proc object (always first parameter for @MagikProc)
     * @param infrastructureType Infrastructure type: "feeder" or "subfeeder" (Magik string)
     * @param infrastructureCode Feeder or subfeeder code (Magik string)
     * @param limit Number of records to fetch (Magik integer)
     * @param offset Starting offset (Magik integer)
     * @return String - JSON response from API (no XML conversion)
     *         JSON structure:
     *         {
     *           "success": true/false,
     *           "count": N,
     *           "count_all": M,
     *           "data": [
     *             { ... 71 connection fields ... }
     *           ]
     *         }
     */
    @MagikProc(@Name("astri_get_device_connections"))
    public static Object getDeviceConnections(
        Object proc,
        Object infrastructureType,
        Object infrastructureCode,
        Object limit,
        Object offset
    ) {
        DeviceConnectionClient client = null;
        try {
            System.out.println("====== ASTRI GET DEVICE CONNECTIONS - START ======");

            // Convert Magik string to Java String for infrastructure type
            String infraType = MagikInteropUtils.fromMagikString(infrastructureType);
            System.out.println("Infrastructure Type: " + infraType);

            // Validate infrastructure type
            if (!infraType.equals("feeder") && !infraType.equals("subfeeder")) {
                throw new IllegalArgumentException(
                    "Invalid infrastructure_type: '" + infraType + "'. " +
                    "Must be 'feeder' or 'subfeeder'"
                );
            }

            // Convert and validate infrastructure code
            String infraCode = MagikInteropUtils.fromMagikString(infrastructureCode);
            System.out.println("Infrastructure Code: " + infraCode);

            if (infraCode == null || infraCode.isEmpty()) {
                throw new IllegalArgumentException("Infrastructure code cannot be empty");
            }

            // Convert Magik integers to Java int
            int limitInt = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);

            System.out.println("Limit: " + limitInt + ", Offset: " + offsetInt);

            // Build JSON request body based on infra_type
            // IMPORTANT: Only send ONE field (feeder OR subfeeder), not both
            String requestBody;
            if (infraType.equals("feeder")) {
                requestBody = String.format(
                    "{\"transport_feeder_code\":\"%s\"}",
                    escapeJson(infraCode)
                );
            } else {  // subfeeder
                requestBody = String.format(
                    "{\"transport_subfeeder_code\":\"%s\"}",
                    escapeJson(infraCode)
                );
            }

            System.out.println("Request Body: " + requestBody);

            // Create client and make API call
            client = new DeviceConnectionClient();
            String jsonResponse = client.getDeviceConnections(
                infraType,
                requestBody,
                limitInt,
                offsetInt
            );

            System.out.println("API call successful, response length: " +
                (jsonResponse != null ? jsonResponse.length() : 0));

            // Convert Java String to Magik string (no XML conversion)
            Object magikString = MagikInteropUtils.toMagikString(jsonResponse);
            System.out.println("====== ASTRI GET DEVICE CONNECTIONS - END ======");

            // Return JSON string directly - Magik will parse it
            return magikString;

        } catch (Exception e) {
            System.err.println("ERROR in getDeviceConnections: " + e.getMessage());
            e.printStackTrace();

            // Return error as JSON string
            String errorJson = "{" +
                "\"success\":false," +
                "\"error\":\"" + escapeJson(e.getMessage()) + "\"" +
                "}";

            try {
                return MagikInteropUtils.toMagikString(errorJson);
            } catch (Exception e2) {
                System.err.println("Failed to convert error JSON to Magik string: " +
                    e2.getMessage());
                return errorJson; // Fallback to Java string
            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Escape special characters for JSON string.
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
```

### 6.2 Phase 2: Create DeviceConnectionClient.java

**File:** `src/main/java/com/rwi/myrepublic/astri/internal/DeviceConnectionClient.java`

**Implementation:**

```java
package com.rwi.myrepublic.astri.internal;

import com.rwi.myrepublic.astri.AstriConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Internal HTTP client for ASTRI Device Connection API.
 * NOT exposed to Magik - used only by AstriDeviceConnectionProcs.
 * Uses Java 11+ HttpClient.
 */
public class DeviceConnectionClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public DeviceConnectionClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " +
            Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Get device connections from API with pagination.
     * Returns JSON string directly (no XML conversion).
     *
     * @param infrastructureType Infrastructure type: "feeder" or "subfeeder"
     * @param requestBody JSON request body (contains ONE field: transport_feeder_code OR transport_subfeeder_code)
     * @param limit Number of records to fetch
     * @param offset Starting offset
     * @return JSON string directly from API response
     */
    public String getDeviceConnections(
        String infrastructureType,
        String requestBody,
        int limit,
        int offset
    ) throws IOException, InterruptedException {

        String baseUrl = config.getApiBaseUrl();
        System.out.println("  [DeviceConnectionClient] Base URL: " + baseUrl);
        System.out.println("  [DeviceConnectionClient] Infrastructure Type: " +
            infrastructureType);

        // Build endpoint
        // /v4/device/connection/list/all/{limit}/{offset}
        String path = "/device/connection/list/all/" + limit + "/" + offset;
        String url = baseUrl + path;

        System.out.println("  [DeviceConnectionClient] URL: " + url);
        System.out.println("  [DeviceConnectionClient] Request Body: " + requestBody);

        // Build POST request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        System.out.println("  [DeviceConnectionClient] Sending HTTP POST request...");

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        String jsonResponse = response.body();

        System.out.println("  [DeviceConnectionClient] Response status: " +
            response.statusCode());
        System.out.println("  [DeviceConnectionClient] Response body length: " +
            (jsonResponse != null ? jsonResponse.length() : 0));

        // Return JSON response as-is (no conversion)
        System.out.println("  [DeviceConnectionClient] Returning JSON response directly");
        return jsonResponse;
    }

    /**
     * Close the HTTP client (cleanup).
     */
    public void close() {
        // HttpClient doesn't require explicit close in Java 11+
        // This method exists for API consistency
    }
}
```

### 6.3 Key Implementation Notes

1. **Request Body Construction:**
   - Only ONE field in JSON body (not both)
   - Feeder: `{"transport_feeder_code":"FPLB0073"}`
   - Subfeeder: `{"transport_subfeeder_code":"PLB005917"}`

2. **No JSON Parsing:**
   - Return API response as-is
   - Magik side will parse JSON
   - Simpler Java code, less dependencies

3. **Error Handling:**
   - Return error as JSON: `{"success":false,"error":"..."}`
   - Consistent with API response structure

4. **Logging:**
   - Same logging pattern as existing procs
   - System.out for normal flow
   - System.err for errors

---

## 7. Error Handling Strategy

### 7.1 Error Categories

| Error Type | HTTP Status | Handling Strategy |
|------------|-------------|-------------------|
| **Invalid Parameters** | N/A (Java) | Validate before API call, throw `IllegalArgumentException` |
| **Authentication Failure** | 401 | Return error JSON with auth failure message |
| **Invalid Infrastructure Code** | 400, 404 | Return error JSON with "Code not found" message |
| **Network Timeout** | N/A | Return error JSON with timeout message |
| **Server Error** | 500, 503 | Return error JSON with server error message |
| **JSON Parse Error** | N/A (Magik) | Magik handles JSON parsing errors |

### 7.2 Error Response Format

**Standard Error JSON:**
```json
{
  "success": false,
  "error": "Error message here"
}
```

**Example Error Scenarios:**

1. **Invalid Infrastructure Type:**
   ```json
   {
     "success": false,
     "error": "Invalid infrastructure_type: 'cluster'. Must be 'feeder' or 'subfeeder'"
   }
   ```

2. **Empty Infrastructure Code:**
   ```json
   {
     "success": false,
     "error": "Infrastructure code cannot be empty"
   }
   ```

3. **HTTP 404 - Code Not Found:**
   ```json
   {
     "success": false,
     "error": "Feeder code 'FDR999' not found in database"
   }
   ```

4. **Network Timeout:**
   ```json
   {
     "success": false,
     "error": "Request timeout: No response from server after 30000ms"
   }
   ```

### 7.3 Logging Strategy

**Log Format:**
```
====== ASTRI GET DEVICE CONNECTIONS - START ======
Infrastructure Type: feeder
Infrastructure Code: FPLB0073
Limit: 50, Offset: 0
Request Body: {"transport_feeder_code":"FPLB0073"}
URL: http://172.17.75.22/astri-api-v2/v4/device/connection/list/all/50/0
Sending HTTP POST request...
Response status: 200
Response body length: 15743
Returning JSON response directly
====== ASTRI GET DEVICE CONNECTIONS - END ======
```

---

## 8. Testing Strategy

### 8.1 Unit Testing (Java)

**Test Cases:**

1. **Parameter Validation Tests**
   - Valid feeder infrastructure type ✓
   - Valid subfeeder infrastructure type ✓
   - Invalid infrastructure type (should throw exception) ✓
   - Empty infrastructure code (should throw exception) ✓
   - Negative limit/offset (should handle gracefully) ✓

2. **Request Body Construction Tests**
   - Feeder request body has ONLY `transport_feeder_code` ✓
   - Subfeeder request body has ONLY `transport_subfeeder_code` ✓
   - JSON escaping for special characters ✓

3. **Response Handling Tests**
   - Empty data array ✓
   - Single connection object ✓
   - Multiple connection objects (50+) ✓
   - Large response (500KB+) ✓

4. **Error Handling Tests**
   - HTTP 401 (authentication failure) ✓
   - HTTP 404 (code not found) ✓
   - HTTP 500 (server error) ✓
   - Network timeout ✓

### 8.2 Integration Testing (Magik)

**Test Script Location:** `pni_custom/rwwi_astri_integration_java/magik/rwwi_mancore_plan/source/test_astri_device_connection_api.magik`

**Complete Test Procedure File** (see Section 8.3 for full implementation)

**Test Overview:**

The Magik test suite validates:
1. ✅ Basic feeder query functionality
2. ✅ Basic subfeeder query functionality
3. ✅ Pagination (limit/offset handling)
4. ✅ Error handling (invalid codes, invalid types)
5. ✅ Large response handling (100+ connections)
6. ✅ JSON response structure validation
7. ✅ Core status parsing (AVAILABLE/TAKEN/RESERVED)
8. ✅ Performance measurement

### 8.3 Complete Magik Test Implementation

**File:** `pni_custom/rwwi_astri_integration_java/magik/rwwi_mancore_plan/source/test_astri_device_connection_api.magik`

**Complete Test Suite Code:**

```magik
#% text_encoding = iso8859_1
_package user
$

## ===========================================================================
## ASTRI Device Connection API - Comprehensive Test Suite
## ===========================================================================
## Purpose: Test astri_get_device_connections() Magik procedure
## Location: pni_custom/rwwi_astri_integration_java/magik/rwwi_mancore_plan/source/
##
## Test Coverage:
##   - Basic feeder/subfeeder queries
##   - Pagination
##   - Error handling
##   - JSON response validation
##   - Performance testing
##   - Core status filtering
## ===========================================================================

## ---------------------------------------------------------------------------
## TEST 1: Basic Feeder Query
## ---------------------------------------------------------------------------
_global test_device_connection_feeder << _proc()
	## Test basic feeder connection query
	## Expected: JSON response with success=true and data array

	write("========================================")
	write("TEST 1: Basic Feeder Query")
	write("========================================")

	_try
		_local json_result << astri_get_device_connections(
			"feeder",        # infra_type
			"FPLB0073",      # infra_code (example feeder)
			50,              # limit
			0                # offset
		)

		write("✓ API call succeeded")
		write("Response type: ", json_result.class_name)
		write("Response length: ", json_result.size, " characters")

		# Check for success indicator
		_if json_result.index_of_seq(%"success":true%) _isnt _unset
		_then
			write("✓ Response contains success:true")
		_else
			write("✗ FAILED: Response does not contain success:true")
			write("Response preview: ", json_result.subseq(1, 200.min(json_result.size)))
		_endif

		# Check for data array
		_if json_result.index_of_seq(%"data":[%) _isnt _unset
		_then
			write("✓ Response contains data array")
		_else
			write("✗ FAILED: Response missing data array")
		_endif

		# Display first 1000 chars for inspection
		write("")
		write("Response preview (first 1000 chars):")
		write(json_result.subseq(1, 1000.min(json_result.size)))

		write("")
		write("✓ TEST 1 COMPLETED")

	_when error
		write("✗ TEST 1 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 2: Basic Subfeeder Query
## ---------------------------------------------------------------------------
_global test_device_connection_subfeeder << _proc()
	## Test basic subfeeder connection query
	## Expected: JSON response with success=true and data array

	write("========================================")
	write("TEST 2: Basic Subfeeder Query")
	write("========================================")

	_try
		_local json_result << astri_get_device_connections(
			"subfeeder",     # infra_type
			"PLB005917",     # infra_code (example subfeeder)
			50,              # limit
			0                # offset
		)

		write("✓ API call succeeded")
		write("Response type: ", json_result.class_name)
		write("Response length: ", json_result.size, " characters")

		# Check for success indicator
		_if json_result.index_of_seq(%"success":true%) _isnt _unset
		_then
			write("✓ Response contains success:true")
		_else
			write("✗ FAILED: Response does not contain success:true")
		_endif

		# Check for subfeeder_code in response
		_if json_result.index_of_seq(%"transport_subfeeder_code":"PLB005917"%) _isnt _unset
		_then
			write("✓ Response contains expected subfeeder code")
		_else
			write("⚠ WARNING: Expected subfeeder code not found in response")
		_endif

		write("")
		write("✓ TEST 2 COMPLETED")

	_when error
		write("✗ TEST 2 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 3: Pagination Test
## ---------------------------------------------------------------------------
_global test_device_connection_pagination << _proc()
	## Test pagination with limit/offset parameters
	## Expected: Different responses for different offsets

	write("========================================")
	write("TEST 3: Pagination Test")
	write("========================================")

	_try
		# First page (offset 0)
		_local page1 << astri_get_device_connections("feeder", "FPLB0073", 10, 0)
		write("Page 1 (offset 0, limit 10):")
		write("  Response length: ", page1.size, " characters")

		# Second page (offset 10)
		_local page2 << astri_get_device_connections("feeder", "FPLB0073", 10, 10)
		write("Page 2 (offset 10, limit 10):")
		write("  Response length: ", page2.size, " characters")

		# Third page (offset 20)
		_local page3 << astri_get_device_connections("feeder", "FPLB0073", 10, 20)
		write("Page 3 (offset 20, limit 10):")
		write("  Response length: ", page3.size, " characters")

		# Verify pages are different
		_if page1 <> page2
		_then
			write("✓ Page 1 and Page 2 are different (pagination working)")
		_else
			write("✗ FAILED: Page 1 and Page 2 are identical")
		_endif

		write("")
		write("✓ TEST 3 COMPLETED")

	_when error
		write("✗ TEST 3 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 4: Error Handling - Invalid Code
## ---------------------------------------------------------------------------
_global test_device_connection_invalid_code << _proc()
	## Test error handling with invalid feeder code
	## Expected: JSON response with success=false and error message

	write("========================================")
	write("TEST 4: Error Handling - Invalid Code")
	write("========================================")

	_try
		_local json_result << astri_get_device_connections(
			"feeder",
			"INVALID_CODE_999",  # Invalid code
			10,
			0
		)

		write("Response received:")
		write("Response length: ", json_result.size, " characters")

		# Check for error indicator
		_if json_result.index_of_seq(%"success":false%) _isnt _unset
		_then
			write("✓ Response contains success:false (expected for invalid code)")
		_elif json_result.index_of_seq(%"success":true%) _isnt _unset
		_then
			write("⚠ WARNING: Response contains success:true (API may have returned empty data)")
		_else
			write("✗ FAILED: Response missing success field")
		_endif

		write("")
		write("Full response:")
		write(json_result)

		write("")
		write("✓ TEST 4 COMPLETED")

	_when error
		write("✗ TEST 4 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 5: Error Handling - Invalid Infrastructure Type
## ---------------------------------------------------------------------------
_global test_device_connection_invalid_type << _proc()
	## Test error handling with invalid infrastructure type
	## Expected: Error JSON response

	write("========================================")
	write("TEST 5: Error Handling - Invalid Type")
	write("========================================")

	_try
		_local json_result << astri_get_device_connections(
			"cluster",       # Invalid type (should be feeder or subfeeder)
			"PLB005917",
			10,
			0
		)

		write("Response received (should be error):")
		write(json_result)

		# Check for error message
		_if json_result.index_of_seq(%"success":false%) _isnt _unset _orif
		    json_result.index_of_seq(%"error"%) _isnt _unset
		_then
			write("✓ API correctly rejected invalid infrastructure type")
		_else
			write("✗ FAILED: API did not return error for invalid type")
		_endif

		write("")
		write("✓ TEST 5 COMPLETED")

	_when error
		write("✗ TEST 5 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 6: Large Response Handling
## ---------------------------------------------------------------------------
_global test_device_connection_large_response << _proc()
	## Test handling of large response (high limit)
	## Expected: Response with many connection objects

	write("========================================")
	write("TEST 6: Large Response Handling")
	write("========================================")

	_try
		_local start_time << system.elapsed_milliseconds()

		_local json_result << astri_get_device_connections(
			"feeder",
			"FPLB0073",
			100,             # Large limit
			0
		)

		_local end_time << system.elapsed_milliseconds()
		_local elapsed << end_time - start_time

		write("✓ API call succeeded")
		write("Response length: ", json_result.size, " characters")
		write("Response time: ", elapsed, " ms")

		# Check for data array
		_if json_result.index_of_seq(%"data":[%) _isnt _unset
		_then
			write("✓ Response contains data array")
		_endif

		# Performance check
		_if elapsed < 5000
		_then
			write("✓ Response time is acceptable (<5 seconds)")
		_else
			write("⚠ WARNING: Response time exceeded 5 seconds (", elapsed, " ms)")
		_endif

		write("")
		write("✓ TEST 6 COMPLETED")

	_when error
		write("✗ TEST 6 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 7: JSON Structure Validation
## ---------------------------------------------------------------------------
_global test_device_connection_json_structure << _proc()
	## Test JSON response structure validation
	## Expected: Response contains all expected fields

	write("========================================")
	write("TEST 7: JSON Structure Validation")
	write("========================================")

	_try
		_local json_result << astri_get_device_connections(
			"feeder",
			"FPLB0073",
			5,
			0
		)

		write("Checking for required fields in JSON response...")

		# Check top-level fields
		_local required_fields << {"success", "count", "count_all", "data"}
		_for field _over required_fields.fast_elements()
		_loop
			_if json_result.index_of_seq(write_string(%", field, %")) _isnt _unset
			_then
				write("  ✓ Field '", field, "' present")
			_else
				write("  ✗ Field '", field, "' MISSING")
			_endif
		_endloop

		# Check data object fields (sample of key fields)
		_local data_fields << {
			"uuid",
			"transport_status",
			"transport_tube_number",
			"transport_core_number",
			"transport_feeder_code",
			"transport_subfeeder_code",
			"source_feeder_code",
			"destination_cluster_code"
		}

		write("")
		write("Checking for key data fields in response...")
		_for field _over data_fields.fast_elements()
		_loop
			_if json_result.index_of_seq(write_string(%", field, %")) _isnt _unset
			_then
				write("  ✓ Field '", field, "' present")
			_else
				write("  ⚠ Field '", field, "' not found (may be null)")
			_endif
		_endloop

		write("")
		write("✓ TEST 7 COMPLETED")

	_when error
		write("✗ TEST 7 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 8: Core Status Check (Simple String Parsing)
## ---------------------------------------------------------------------------
_global test_device_connection_status_check << _proc()
	## Test extracting transport_status from JSON response
	## Uses simple string search (not full JSON parsing)
	## Expected: Find AVAILABLE, TAKEN, or RESERVED statuses

	write("========================================")
	write("TEST 8: Core Status Check")
	write("========================================")

	_try
		_local json_result << astri_get_device_connections(
			"subfeeder",
			"PLB005917",
			50,
			0
		)

		write("Searching for connection statuses...")

		# Count status occurrences using simple string search
		_local available_count << 0
		_local taken_count << 0
		_local reserved_count << 0

		_local search_pos << 1
		_loop
			# Search for transport_status field
			_local status_pos << json_result.index_of_seq(%"transport_status"%, search_pos)
			_if status_pos _is _unset _then _leave _endif

			# Extract status value (simple approach: look for next quote after colon)
			_local value_start << json_result.index_of_seq(%:%, status_pos)
			_if value_start _isnt _unset
			_then
				_local snippet << json_result.subseq(value_start,
					(value_start + 50).min(json_result.size))

				_if snippet.index_of_seq(%"AVAILABLE"%) _isnt _unset
				_then
					available_count +<< 1
				_elif snippet.index_of_seq(%"TAKEN"%) _isnt _unset
				_then
					taken_count +<< 1
				_elif snippet.index_of_seq(%"RESERVED"%) _isnt _unset
				_then
					reserved_count +<< 1
				_endif
			_endif

			search_pos << status_pos + 20
		_endloop

		write("")
		write("Status Summary:")
		write("  AVAILABLE cores: ", available_count)
		write("  TAKEN cores:     ", taken_count)
		write("  RESERVED cores:  ", reserved_count)
		write("  TOTAL:           ", available_count + taken_count + reserved_count)

		_if available_count + taken_count + reserved_count > 0
		_then
			write("✓ Successfully extracted connection statuses")
		_else
			write("⚠ WARNING: No connection statuses found in response")
		_endif

		write("")
		write("✓ TEST 8 COMPLETED")

	_when error
		write("✗ TEST 8 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## TEST 9: Performance Benchmark
## ---------------------------------------------------------------------------
_global test_device_connection_performance << _proc()
	## Test API performance with multiple requests
	## Expected: All requests complete within acceptable time

	write("========================================")
	write("TEST 9: Performance Benchmark")
	write("========================================")

	_try
		_local test_cases << {
			{"feeder", "FPLB0073", 10},
			{"feeder", "FPLB0073", 50},
			{"subfeeder", "PLB005917", 10},
			{"subfeeder", "PLB005917", 50}
		}

		write("Running ", test_cases.size, " performance tests...")
		write("")

		_local total_time << 0
		_local test_num << 0

		_for test_case _over test_cases.fast_elements()
		_loop
			test_num +<< 1
			_local infra_type << test_case[1]
			_local infra_code << test_case[2]
			_local limit << test_case[3]

			_local start_time << system.elapsed_milliseconds()

			_local json_result << astri_get_device_connections(
				infra_type,
				infra_code,
				limit,
				0
			)

			_local end_time << system.elapsed_milliseconds()
			_local elapsed << end_time - start_time
			total_time +<< elapsed

			write("Test ", test_num, ": ", infra_type, " / ", infra_code, " / limit=", limit)
			write("  Time: ", elapsed, " ms")
			write("  Response size: ", json_result.size, " chars")

			_if elapsed > 5000
			_then
				write("  ⚠ WARNING: Slow response (>5 seconds)")
			_else
				write("  ✓ Acceptable performance")
			_endif
			write("")
		_endloop

		_local avg_time << total_time _div test_cases.size
		write("Performance Summary:")
		write("  Total time: ", total_time, " ms")
		write("  Average time: ", avg_time, " ms")
		write("  Tests run: ", test_cases.size)

		_if avg_time < 5000
		_then
			write("✓ Overall performance is acceptable")
		_else
			write("⚠ WARNING: Average performance below target")
		_endif

		write("")
		write("✓ TEST 9 COMPLETED")

	_when error
		write("✗ TEST 9 FAILED WITH ERROR:")
		write("Error: ", condition.report_contents_string)
	_endtry

	write("")
_endproc
$

## ---------------------------------------------------------------------------
## MASTER TEST RUNNER
## ---------------------------------------------------------------------------
_global run_all_device_connection_tests << _proc()
	## Run all device connection API tests
	## This is the main test entry point

	write("")
	write("===========================================================================")
	write("ASTRI DEVICE CONNECTION API - COMPREHENSIVE TEST SUITE")
	write("===========================================================================")
	write("Start time: ", date_time.now().write_string)
	write("")

	_local overall_start << system.elapsed_milliseconds()

	# Run all tests
	test_device_connection_feeder()
	test_device_connection_subfeeder()
	test_device_connection_pagination()
	test_device_connection_invalid_code()
	test_device_connection_invalid_type()
	test_device_connection_large_response()
	test_device_connection_json_structure()
	test_device_connection_status_check()
	test_device_connection_performance()

	_local overall_end << system.elapsed_milliseconds()
	_local total_elapsed << overall_end - overall_start

	write("===========================================================================")
	write("ALL TESTS COMPLETED")
	write("===========================================================================")
	write("End time: ", date_time.now().write_string)
	write("Total test duration: ", total_elapsed, " ms (",
		(total_elapsed / 1000.0).rounded, " seconds)")
	write("")
	write("To run individual tests, use:")
	write("  test_device_connection_feeder()")
	write("  test_device_connection_subfeeder()")
	write("  test_device_connection_pagination()")
	write("  test_device_connection_invalid_code()")
	write("  test_device_connection_invalid_type()")
	write("  test_device_connection_large_response()")
	write("  test_device_connection_json_structure()")
	write("  test_device_connection_status_check()")
	write("  test_device_connection_performance()")
	write("")
	write("===========================================================================")

_endproc
$

## ===========================================================================
## USAGE INSTRUCTIONS
## ===========================================================================
##
## To run all tests:
##   MagikSF> run_all_device_connection_tests()
##
## To run individual tests:
##   MagikSF> test_device_connection_feeder()
##   MagikSF> test_device_connection_subfeeder()
##   MagikSF> test_device_connection_pagination()
##   MagikSF> test_device_connection_invalid_code()
##   MagikSF> test_device_connection_invalid_type()
##   MagikSF> test_device_connection_large_response()
##   MagikSF> test_device_connection_json_structure()
##   MagikSF> test_device_connection_status_check()
##   MagikSF> test_device_connection_performance()
##
## Prerequisites:
##   1. Java implementation compiled and JAR deployed
##   2. Smallworld session restarted to load new procedure
##   3. ASTRI API server accessible (http://172.17.75.22/astri-api-v2/v4)
##   4. Valid credentials configured in astri_config.properties
##
## Expected Results:
##   - All tests should pass with ✓ markers
##   - Performance tests should complete in <5 seconds each
##   - JSON responses should contain expected fields
##   - Error tests should receive proper error JSON responses
##
## ===========================================================================
```

**Test Execution Instructions:**

1. **File Location:**
   - Save above code to: `pni_custom/rwwi_astri_integration_java/magik/rwwi_mancore_plan/source/test_astri_device_connection_api.magik`

2. **Load in Magik Session:**
   ```magik
   # Compile the test file
   MagikSF> load_file("pni_custom/rwwi_astri_integration_java/magik/rwwi_mancore_plan/source/test_astri_device_connection_api.magik")
   ```

3. **Run All Tests:**
   ```magik
   MagikSF> run_all_device_connection_tests()
   ```

4. **Run Individual Tests:**
   ```magik
   MagikSF> test_device_connection_feeder()
   MagikSF> test_device_connection_subfeeder()
   MagikSF> test_device_connection_pagination()
   ```

**Test Coverage:**

| Test # | Test Name | Purpose | Expected Result |
|--------|-----------|---------|-----------------|
| 1 | Feeder Query | Basic feeder API call | JSON with success:true and data array |
| 2 | Subfeeder Query | Basic subfeeder API call | JSON with success:true and data array |
| 3 | Pagination | Test limit/offset | Different responses for different offsets |
| 4 | Invalid Code | Error handling | JSON with success:false |
| 5 | Invalid Type | Parameter validation | Error JSON response |
| 6 | Large Response | High-volume data | Response <5 seconds, large data array |
| 7 | JSON Structure | Field validation | All required fields present |
| 8 | Status Check | Parse transport_status | Count AVAILABLE/TAKEN/RESERVED cores |
| 9 | Performance | Multiple requests | Average response time <5 seconds |

### 8.3 Manual Testing Checklist

- [ ] Compile Java code without errors
- [ ] Restart Smallworld session to load new Magik procedure
- [ ] Verify `astri_get_device_connections` procedure exists
- [ ] Test with valid feeder code (FPLB0073)
- [ ] Test with valid subfeeder code (PLB005917)
- [ ] Test pagination (offset 0, 50, 100)
- [ ] Test with invalid infrastructure type
- [ ] Test with empty infrastructure code
- [ ] Test with non-existent feeder/subfeeder code
- [ ] Verify JSON response structure
- [ ] Verify all 71 fields are present in JSON
- [ ] Test with large response (100+ connections)
- [ ] Verify authentication headers are correct
- [ ] Verify timeout handling (disconnect network)
- [ ] Check log output for debugging

---

## 9. Integration Points

### 9.1 Magik Integration

**Global Procedure Created:**
```
astri_get_device_connections(infra_type, infra_code, limit, offset)
```

**Usage Example in Magik:**
```magik
# Get feeder connections
_local json_result << astri_get_device_connections(
    "feeder",        # Infrastructure type
    "FPLB0073",      # Feeder code
    50,              # Limit
    0                # Offset
)

# JSON result is a string like:
# {"success":true,"count":48,"count_all":48,"data":[...]}

write("JSON Response:")
write(json_result)

# TODO: Parse JSON in Magik
# Magik JSON parsing options:
#   1. Use simple_xml to parse JSON as XML (hacky but works)
#   2. Implement custom JSON parser in Magik
#   3. Use Java-side JSON parsing and return property_list
#   4. Use external JSON library for Magik

# Example: Check for success
_if json_result.index_of_seq(%"success":true%) _isnt _unset
_then
    write("API call successful")
_else
    write("API call failed")
_endif

# Example: Count connections (parse "count" field)
# Manual string parsing (simple approach for now)
```

**Note:** JSON parsing in Magik is left as a separate task. This implementation returns the raw JSON string, similar to `AstriBoqProcs`.

### 9.2 Potential Use Cases

1. **Core Availability Checker**
   - Query available cores before design creation
   - Display available/taken core count to user
   - Filter by `transport_status = "AVAILABLE"`

2. **Core Allocation Validator**
   - Verify selected core is available
   - Prevent double allocation
   - Check `transport_status` before allocation

3. **Network Capacity Report**
   - Generate reports showing core utilization
   - Identify underutilized feeder/subfeeder cables
   - Calculate % utilization from count_all

4. **Design Workflow Integration**
   - Auto-suggest available cores during cable routing
   - Validate core assignments in BoQ generation
   - Real-time core availability updates

---

## 10. Implementation Checklist

### 10.1 Development Phase

- [ ] **Step 1:** Create `AstriDeviceConnectionProcs.java`
  - [ ] Add class with package declaration
  - [ ] Import required dependencies
  - [ ] Implement `@MagikProc` annotated method
  - [ ] Add parameter validation
  - [ ] Add request body construction logic (ONE field only)
  - [ ] Add error handling (return JSON)
  - [ ] Add logging statements
  - [ ] Add JavaDoc documentation

- [ ] **Step 2:** Create `DeviceConnectionClient.java`
  - [ ] Add class in `internal` package
  - [ ] Implement constructor with `AstriConfig`
  - [ ] Implement `getDeviceConnections()` method
  - [ ] Implement HTTP POST request
  - [ ] Add authentication header
  - [ ] Return JSON response directly (no conversion)
  - [ ] Add error handling
  - [ ] Add logging statements
  - [ ] Add JavaDoc documentation

- [ ] **Step 3:** No JSON Library Needed
  - [ ] ✓ Return JSON as-is (no parsing required)
  - [ ] ✓ No Maven dependencies to add

### 10.2 Testing Phase

- [ ] **Step 4:** Compile Java Code
  - [ ] Run Maven build
  - [ ] Fix compilation errors
  - [ ] Verify JAR is generated
  - [ ] Copy JAR to Smallworld libs directory

- [ ] **Step 5:** Test in Magik
  - [ ] Restart Smallworld session
  - [ ] Verify `astri_get_device_connections` procedure exists
  - [ ] Test with valid feeder code
  - [ ] Test with valid subfeeder code
  - [ ] Test pagination
  - [ ] Test error scenarios

- [ ] **Step 6:** Create Magik Test Scripts
  - [ ] Write test procedures in `test_device_connection_procs.magik`
  - [ ] Test all use cases
  - [ ] Document test results

### 10.3 Documentation Phase

- [ ] **Step 7:** Update Documentation
  - [ ] Add JavaDoc to all public methods
  - [ ] Update this implementation plan with actual results
  - [ ] Create usage examples for Magik developers
  - [ ] Add troubleshooting section

### 10.4 Deployment Phase

- [ ] **Step 8:** Code Review
  - [ ] Review for security issues
  - [ ] Review for performance issues
  - [ ] Review for code quality
  - [ ] Get approval from lead developer

- [ ] **Step 9:** Deployment
  - [ ] Commit Java source files
  - [ ] Commit test scripts
  - [ ] Commit documentation
  - [ ] Tag release version
  - [ ] Deploy to production environment

---

## 11. Appendices

### Appendix A: Sample JSON Response

**Success Response (1 connection):**
```json
{
  "success": true,
  "count": 1,
  "count_all": 48,
  "data": [
    {
      "uuid": "6fec3954-1301-44db-affe-409c2b9f9d1e",
      "source_device_name": null,
      "source_device_hardware_type": null,
      "source_device_port_name": null,
      "source_device_port_type": null,
      "source_device_remarks": null,
      "source_device_capacity": null,
      "source_device_capacity_measurement": null,
      "source_bng_device_code": null,
      "source_bng_name": null,
      "source_bng_label": null,
      "source_bng_hostname": null,
      "source_olt_device_code": null,
      "source_olt_name": null,
      "source_olt_label": null,
      "source_olt_hostname": null,
      "source_feeder_code": "FPLB0073",
      "source_feeder_name": "palembang-main_feeder_olt_pangkalan_balai_segment_2_to_suak_tapeh_fo_144c",
      "source_feeder_label": "PALEMBANG-MAIN_FEEDER_OLT_PANGKALAN_BALAI_SEGMENT_2_TO_SUAK_TAPEH_FO_144C",
      "source_subfeeder_code": null,
      "source_subfeeder_name": null,
      "source_cluster_code": null,
      "source_cluster_name": null,
      "source_tube_number": 1,
      "source_core_number": 5,
      "transport_name": null,
      "transport_hardware_type": null,
      "transport_capacity": null,
      "transport_capacity_measurement": null,
      "transport_feeder_code": null,
      "transport_feeder_name": null,
      "transport_feeder_label": null,
      "transport_feeder_tube_count": null,
      "transport_feeder_core_count": null,
      "transport_subfeeder_code": "PLB005917",
      "transport_subfeeder_name": "SETERIO RW 01 SAMPAI 03 PALEMBANG",
      "transport_subfeeder_tube_count": 4,
      "transport_subfeeder_core_count": 48,
      "transport_tube_number": 1,
      "transport_core_number": 5,
      "transport_status": "TAKEN",
      "transport_remarks": null,
      "closure_code": null,
      "destination_device_name": null,
      "destination_device_hardware_type": null,
      "destination_device_port_name": null,
      "destination_device_port_type": null,
      "destination_device_remarks": null,
      "destination_device_capacity": null,
      "destination_device_capacity_measurement": null,
      "destination_bng_device_code": null,
      "destination_bng_name": null,
      "destination_bng_label": null,
      "destination_bng_hostname": null,
      "destination_olt_device_code": null,
      "destination_olt_name": null,
      "destination_olt_label": null,
      "destination_olt_hostname": null,
      "destination_feeder_code": null,
      "destination_feeder_name": null,
      "destination_feeder_label": null,
      "destination_tube_number": 1,
      "destination_core_number": 5,
      "destination_subfeeder_code": null,
      "destination_subfeeder_name": null,
      "destination_cluster_code": "PLB005917",
      "destination_cluster_name": "SETERIO RW 01 SAMPAI 03 PALEMBANG",
      "work_order_number": null,
      "requested_by_username": null,
      "requested_by_fullname": null,
      "verified_by_username": "muhammad.falan",
      "verified_by_fullname": "Muhammad Alfi Falan",
      "verified_at": "2025-11-17 11:14:04",
      "created_at": "2025-11-17 11:14:31",
      "updated_at": null
    }
  ]
}
```

### Appendix B: Complete Field List (71 Fields)

**Connection Object Fields:**

1. uuid
2. source_device_name
3. source_device_hardware_type
4. source_device_port_name
5. source_device_port_type
6. source_device_remarks
7. source_device_capacity
8. source_device_capacity_measurement
9. source_bng_device_code
10. source_bng_name
11. source_bng_label
12. source_bng_hostname
13. source_olt_device_code
14. source_olt_name
15. source_olt_label
16. source_olt_hostname
17. source_feeder_code
18. source_feeder_name
19. source_feeder_label
20. source_subfeeder_code
21. source_subfeeder_name
22. source_cluster_code
23. source_cluster_name
24. source_tube_number
25. source_core_number
26. transport_name
27. transport_hardware_type
28. transport_capacity
29. transport_capacity_measurement
30. transport_feeder_code
31. transport_feeder_name
32. transport_feeder_label
33. transport_feeder_tube_count
34. transport_feeder_core_count
35. transport_subfeeder_code
36. transport_subfeeder_name
37. transport_subfeeder_tube_count
38. transport_subfeeder_core_count
39. transport_tube_number
40. transport_core_number
41. transport_status (**KEY FIELD**)
42. transport_remarks
43. closure_code
44. destination_device_name
45. destination_device_hardware_type
46. destination_device_port_name
47. destination_device_port_type
48. destination_device_remarks
49. destination_device_capacity
50. destination_device_capacity_measurement
51. destination_bng_device_code
52. destination_bng_name
53. destination_bng_label
54. destination_bng_hostname
55. destination_olt_device_code
56. destination_olt_name
57. destination_olt_label
58. destination_olt_hostname
59. destination_feeder_code
60. destination_feeder_name
61. destination_feeder_label
62. destination_tube_number
63. destination_core_number
64. destination_subfeeder_code
65. destination_subfeeder_name
66. destination_cluster_code
67. destination_cluster_name
68. work_order_number
69. requested_by_username
70. requested_by_fullname
71. verified_by_username
72. verified_by_fullname
73. verified_at
74. created_at
75. updated_at

*(Note: List updated based on actual API response - 71 total fields)*

### Appendix C: Comparison with BoQ API

| Aspect | BoQ API | Device Connection API |
|--------|---------|----------------------|
| **HTTP Method** | POST | POST |
| **Request Body** | Full BoQ item JSON | Single field (feeder OR subfeeder code) |
| **Response Format** | JSON string | JSON string (same) |
| **Magik Procedure** | `astri_add_boq_drm()` | `astri_get_device_connections()` |
| **Return Type** | JSON string (Magik) | JSON string (Magik) |
| **Conversion** | None (JSON as-is) | None (JSON as-is) |
| **Authentication** | Basic Auth | Basic Auth (same) |
| **Error Handling** | JSON error response | JSON error response (same pattern) |
| **JSON Parsing** | Magik-side | Magik-side (same approach) |

**Key Similarity:** Both return JSON strings directly to Magik without XML conversion.

### Appendix D: Troubleshooting Guide

**Common Issues:**

1. **"Global astri_get_device_connections does not exist"**
   - Cause: JAR not loaded or Magik session not restarted
   - Solution: Copy JAR to libs, restart Smallworld

2. **"Invalid infrastructure_type: 'cluster'"**
   - Cause: Using cluster instead of feeder/subfeeder
   - Solution: Only use "feeder" or "subfeeder"

3. **HTTP 401 Unauthorized**
   - Cause: Incorrect credentials in `astri_config.properties`
   - Solution: Verify username/password

4. **HTTP 404 Not Found**
   - Cause: Invalid feeder/subfeeder code
   - Solution: Verify code exists in ASTRI database

5. **Request Timeout**
   - Cause: Network issues or slow API
   - Solution: Check network, increase timeout in config

6. **Unexpected JSON structure**
   - Cause: API response format changed
   - Solution: Check actual response, update Magik parsing logic

### Appendix E: Change Log

**Version 1.1 (2026-01-02):**
- ✅ **REMOVED FR-005** - No XML conversion, return JSON directly
- ✅ **UPDATED FR-003** - Only send ONE field in request body (not both with null)
- ✅ **UPDATED field list** - 71 fields based on actual API response
- ✅ **UPDATED architecture** - Follow `AstriBoqProcs` pattern (JSON string return)
- ✅ **UPDATED implementation** - Simplified (no JSON parsing library needed)
- ✅ **UPDATED examples** - Magik JSON handling (not XML)
- ✅ **UPDATED comparisons** - Compare with BoQ API instead of Work Order API

**Version 1.0 (2026-01-02):**
- Initial plan with XML conversion approach

---

## Document Approval

**Plan Status:** ⏳ PENDING APPROVAL (v1.1 UPDATED)

**Changes from v1.0:**
- JSON response instead of XML
- Simplified implementation
- Single-field request body
- Updated field list (71 fields)

**Approval Checklist:**
- [ ] Technical approach reviewed (JSON return)
- [ ] API endpoint specification confirmed
- [ ] Single-field request body approved
- [ ] 71-field response structure validated
- [ ] Implementation steps approved
- [ ] Testing strategy approved
- [ ] Ready for implementation

**Approved By:** ________________
**Date:** ________________

**Notes:**
_Please review this updated plan (v1.1) and provide feedback before implementation begins._

---

**END OF DOCUMENT**
