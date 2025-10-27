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
 * Internal HTTP client for ASTRI Price List API.
 * NOT exposed to Magik - used only by AstriPriceListProcs.
 * Uses Java 11+ HttpClient.
 */
public class PriceListClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public PriceListClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Get price list from API with optional filters.
     *
     * @param filterParams Filter query string (e.g. "project_type=ALL&valid_date_end=2025-01-01")
     * @return JSON string from API response
     */
    public String getPriceList(String filterParams) throws IOException, InterruptedException {
        String baseUrl = config.getApiBaseUrl();
        // Correct endpoint from ASTRI API documentation
        String path = "/device/price/list/all";

        // Build URL with filters if provided
        String url = baseUrl + path;
        if (filterParams != null && !filterParams.isEmpty()) {
            url += "?" + filterParams;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Close and cleanup resources.
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
