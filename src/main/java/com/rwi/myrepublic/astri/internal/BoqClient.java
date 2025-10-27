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
     */
    public String addBoqDrmCluster(String clusterCode, String vendorName, String subcontVendorName,
                                   String equipmentName, String description, int quantityMaterial,
                                   int quantityService, String remarks, String phase, String area,
                                   String areaPlantCode, int overridePriceMaterial,
                                   int overridePriceService) throws IOException, InterruptedException {
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
        return response.body();
    }

    private String buildJsonBody(String clusterCode, String vendorName, String subcontVendorName,
                                  String equipmentName, String description, int quantityMaterial,
                                  int quantityService, String remarks, String phase, String area,
                                  String areaPlantCode, int overridePriceMaterial,
                                  int overridePriceService) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"cluster_code\":\"").append(escapeJson(clusterCode)).append("\",");
        json.append("\"vendor_name\":\"").append(escapeJson(vendorName)).append("\",");
        json.append("\"subcont_vendor_name\":\"").append(escapeJson(subcontVendorName)).append("\",");
        json.append("\"equipment_name\":\"").append(escapeJson(equipmentName)).append("\",");
        json.append("\"description\":\"").append(escapeJson(description)).append("\",");
        json.append("\"quantity_material\":").append(quantityMaterial).append(",");
        json.append("\"quantity_service\":").append(quantityService).append(",");
        json.append("\"remarks\":\"").append(escapeJson(remarks)).append("\",");
        json.append("\"phase\":\"").append(escapeJson(phase)).append("\",");
        json.append("\"area\":\"").append(escapeJson(area)).append("\",");
        json.append("\"area_plant_code\":\"").append(escapeJson(areaPlantCode)).append("\",");
        json.append("\"override_price_material\":").append(overridePriceMaterial).append(",");
        json.append("\"override_price_service\":").append(overridePriceService);
        json.append("}");
        return json.toString();
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
