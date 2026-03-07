package com.rwi.myrepublic.nisa.internal;

import com.rwi.myrepublic.nisa.NisaConfig;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Internal HTTP client for the NISA Mass Problem API.
 *
 * Authentication flow (per call):
 *   1. Call NisaAuthClient.getToken() → receives JWT (muse_token)
 *   2. GET /transaction/massproblem/active/cluster
 *      Authorization: Bearer <token>, Body: {"cluster":"<clusterCode>"}
 *
 * NOT exposed to Magik - used only by NisaMassProblemProcs.
 */
public class NisaMassProblemClient {

    private final NisaConfig config;
    private final HttpClient httpClient;
    private final NisaAuthClient authClient;

    public NisaMassProblemClient() {
        this.config = NisaConfig.getInstance();
        // CookieManager allows cookies set during auth (e.g. F5 session cookie)
        // to be automatically carried to subsequent requests, matching curl --location behavior
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
            .build();
        this.authClient = new NisaAuthClient(config, httpClient);
    }

    /**
     * Query active mass problems for a given cluster code.
     *
     * GET /transaction/massproblem/active/cluster
     * Authorization: Bearer <muse_token>
     * Content-Type: application/json
     * Body: {"cluster":"<clusterCode>"}
     *
     * @param clusterCode The cluster code to query (e.g. "CLUSTER-001")
     * @return Raw JSON response body from the NISA API
     * @throws Exception on auth failure or HTTP error
     */
    public String getMassProblemActiveCluster(String clusterCode) throws IOException, InterruptedException {
        // Step 1: Obtain JWT token
        System.out.println("  [NisaMassProblemClient] Fetching auth token...");
        String token = authClient.getToken();

        // Step 2: Build GET request with cluster code as JSON body
        String url = config.getApiBaseUrl() + "/transaction/massproblem/active/cluster";
        String body = "{\"cluster\":\"" + escapeJson(clusterCode) + "\"}";

        System.out.println("  [NisaMassProblemClient] GET " + url);
        System.out.println("  [NisaMassProblemClient] Body: " + body);

        // Use .method("GET", ...) to send a body with a GET request
        // Cookies (e.g. F5 session cookie) are handled automatically by the CookieManager
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .method("GET", HttpRequest.BodyPublishers.ofString(body))
            .build();

        // Step 3: Send and return response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String responseBody = response.body();

        System.out.println("  [NisaMassProblemClient] Response status: " + statusCode);
        System.out.println("  [NisaMassProblemClient] Response length: "
            + (responseBody != null ? responseBody.length() : 0));

        if (statusCode != 200) {
            throw new IOException("NISA mass problem API returned HTTP " + statusCode
                + " | Body: " + responseBody);
        }

        return responseBody;
    }

    /** Escape special characters in JSON string values. */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
