package com.rwi.myrepublic.astri.internal;

import com.rwi.myrepublic.astri.AstriConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Internal HTTP client for ASTRI OLT Rollout API.
 * NOT exposed to Magik - used only by AstriOltProcs.
 * Uses Java 11+ HttpClient.
 */
public class OltClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public OltClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Get OLT rollout list with optional filters.
     *
     * @param limit Maximum number of results
     * @param offset Offset for pagination
     * @param deviceCode Optional device code filter
     * @param name Optional name filter
     * @param label Optional label filter
     * @return JSON string from API response or empty string on error
     */
    public String getOltList(int limit, int offset, String deviceCode, String name, String label)
            throws IOException, InterruptedException {
        String baseUrl = config.getApiBaseUrl();
        String path = "/olt/rollout/list/all/" + limit + "/" + offset;

        // Build query parameters for filters
        StringBuilder queryParams = new StringBuilder();
        if (deviceCode != null && !deviceCode.isEmpty()) {
            appendParam(queryParams, "device_code", deviceCode);
        }
        if (name != null && !name.isEmpty()) {
            appendParam(queryParams, "name", name);
        }
        if (label != null && !label.isEmpty()) {
            appendParam(queryParams, "label", label);
        }

        String url = baseUrl + path;
        if (queryParams.length() > 0) {
            url += "?" + queryParams.toString();
        }

        System.out.println("POST URL: " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private void appendParam(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) {
            sb.append("&");
        }
        sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
          .append("=")
          .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /**
     * Close and cleanup resources.
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
