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
