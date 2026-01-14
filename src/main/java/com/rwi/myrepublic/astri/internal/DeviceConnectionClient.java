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
     * Add device connection to ASTRI API.
     * Returns JSON string directly (no XML conversion).
     *
     * @param requestBody JSON request body containing all 35 fields
     * @return JSON string directly from API response
     *         Response structure: { "success": true/false, "data": [{id: internalId}] }
     */
    public String addDeviceConnection(String requestBody)
        throws IOException, InterruptedException {

        String baseUrl = config.getApiBaseUrl();
        System.out.println("  [DeviceConnectionClient] Base URL: " + baseUrl);
        System.out.println("  [DeviceConnectionClient] Adding device connection");

        // Build endpoint - using same base path as get
        // /v4/device/connection/add
        String path = "/device/connection/add";
        String url = baseUrl + path;

        System.out.println("  [DeviceConnectionClient] URL: " + url);
        System.out.println("  [DeviceConnectionClient] Request Body length: " +
            (requestBody != null ? requestBody.length() : 0));

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
