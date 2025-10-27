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
 * Internal HTTP client for ASTRI Work Order Update API.
 * NOT exposed to Magik - used only by AstriWorkOrderUpdateProcs.
 * Uses Java 11+ HttpClient.
 */
public class WorkOrderUpdateClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public WorkOrderUpdateClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Update work order.
     */
    public String updateWorkOrder(String number, String latestStatusName, String detail)
            throws IOException, InterruptedException {
        String baseUrl = config.getApiBaseUrl();
        String path = "/work-order/update";
        String url = baseUrl + path;

        // Build JSON request body
        String jsonBody = buildJsonBody(number, latestStatusName, detail);

        System.out.println("PUT URL: " + url);
        System.out.println("Request body: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String buildJsonBody(String number, String latestStatusName, String detail) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"number\":\"").append(escapeJson(number)).append("\",");
        json.append("\"latest_status_name\":\"").append(escapeJson(latestStatusName)).append("\",");
        json.append("\"detail\":\"").append(escapeJson(detail)).append("\"");
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
