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
 * Internal HTTP client for ASTRI Donation Work Order API.
 * NOT exposed to Magik - used only by AstriDonationWorkOrderProcs.
 * Uses Java 11+ HttpClient with HTTP POST and JSON body.
 *
 * Endpoint: POST /work-order/cluster/propose-donation/list/all/{limit}/{offset}
 *
 * Supported filter keys in JSON body:
 *   assigned_vendor_name  - e.g. "--not internal"
 *   target_cluster_code   - specific cluster code (optional, omit for full list fetch)
 *   latest_status_name    - e.g. "--not cancelled"
 *   permit_status         - e.g. "DONATION APPROVED"
 *   target_cluster_name   - cluster name filter (optional)
 */
public class DonationWorkOrderClient {

    private static final String DONATION_PATH = "/work-order/cluster/propose-donation/list/all";

    private final HttpClient client;
    private final AstriConfig config;
    private final String authHeader;

    public DonationWorkOrderClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Fetch donation work orders with pagination and a pre-built JSON body.
     * Returns JSON response directly — no XML conversion.
     *
     * @param limit    Maximum records to return (must be > 0)
     * @param offset   Pagination offset (must be >= 0)
     * @param jsonBody JSON string with filter fields (built by AstriDonationWorkOrderProcs)
     * @return JSON string directly from API response, for Magik json_parser.parse()
     *         Always success:true. Empty data[] means not found; non-empty means results.
     */
    public String getDonationWorkOrders(int limit, int offset, String jsonBody)
            throws IOException, InterruptedException {

        String baseUrl = config.getApiBaseUrl();
        String url = baseUrl + DONATION_PATH + "/" + limit + "/" + offset;

        System.out.println("  [DonationWorkOrderClient] URL: " + url);
        System.out.println("  [DonationWorkOrderClient] Body: " + jsonBody);

        String body = (jsonBody != null && !jsonBody.isEmpty()) ? jsonBody : "{}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        System.out.println("  [DonationWorkOrderClient] Sending HTTP POST request...");

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();

        System.out.println("  [DonationWorkOrderClient] Response status: " + response.statusCode());
        System.out.println("  [DonationWorkOrderClient] Response length: "
            + (jsonResponse != null ? jsonResponse.length() : 0));

        // Return JSON as-is — Magik parses with json_parser.parse()
        return jsonResponse;
    }

    public void close() {
        // HttpClient does not need explicit closing in Java 11+
    }
}
