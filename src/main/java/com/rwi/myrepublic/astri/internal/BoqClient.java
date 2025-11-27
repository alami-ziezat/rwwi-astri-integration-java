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
 * Internal HTTP client for ASTRI BOQ DRM API.
 * NOT exposed to Magik - used only by AstriBoqProcs.
 * Uses Java 11+ HttpClient.
 */
public class BoqClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public BoqClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Add BOQ DRM Cluster.
     * Accepts nullable Double values to support Magik _unset values and decimal quantities.
     */
    public String addBoqDrmCluster(String clusterCode, String vendorName, String subcontVendorName,
                                   String equipmentName, String description, Double quantityMaterial,
                                   Double quantityService, String remarks, String phase, String area,
                                   String areaPlantCode, Double overridePriceMaterial,
                                   Double overridePriceService) throws IOException, InterruptedException {
        String baseUrl = config.getApiBaseUrl();
        String path = "/osp/cluster/boq/add";
        String url = baseUrl + path;

        // Build JSON request body
        String jsonBody = buildJsonBody(
            clusterCode, vendorName, subcontVendorName, equipmentName, description,
            quantityMaterial, quantityService, remarks, phase, area, areaPlantCode,
            overridePriceMaterial, overridePriceService
        );

        System.out.println("POST URL: " + url);
        System.out.println("Request body: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var jsonResponse = response.body();
        System.out.println("Response body: " + jsonResponse);
        return jsonResponse;
    }

    private String buildJsonBody(String clusterCode, String vendorName, String subcontVendorName,
                                  String equipmentName, String description, Double quantityMaterial,
                                  Double quantityService, String remarks, String phase, String area,
                                  String areaPlantCode, Double overridePriceMaterial,
                                  Double overridePriceService) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        appendJsonField(json, "cluster_code", clusterCode, true);
        appendJsonField(json, "vendor_name", vendorName, true);
        appendJsonField(json, "subcont_vendor_name", subcontVendorName, true);
        appendJsonField(json, "equipment_name", equipmentName, true);
        appendJsonField(json, "description", description, true);
        appendJsonField(json, "quantity_material", quantityMaterial, true);
        appendJsonField(json, "quantity_service", quantityService, true);
        appendJsonField(json, "remarks", remarks, true);
        appendJsonField(json, "phase", phase, true);
        appendJsonField(json, "area", area, true);
        appendJsonField(json, "area_plant_code", areaPlantCode, true);
        appendJsonField(json, "override_price_material", overridePriceMaterial, true);
        appendJsonField(json, "override_price_service", overridePriceService, false);
        json.append("}");
        return json.toString();
    }

    /**
     * Append a JSON field to the StringBuilder.
     * If value is null, appends "null" (without quotes).
     *
     * @param json The StringBuilder to append to
     * @param fieldName The name of the field
     * @param value The value (String, Integer, or Double, can be null)
     * @param addComma Whether to add a comma after the field
     */
    private void appendJsonField(StringBuilder json, String fieldName, Object value, boolean addComma) {
        json.append("\"").append(fieldName).append("\":");

        if (value == null) {
            json.append("null");
        } else if (value instanceof String) {
            json.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Integer) {
            json.append(value);
        } else if (value instanceof Double) {
            // Format Double with 2 decimal places
            json.append(String.format("%.2f", (Double) value));
        } else {
            // Fallback for other types
            json.append("\"").append(escapeJson(value.toString())).append("\"");
        }

        if (addComma) {
            json.append(",");
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Close and cleanup resources.
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
