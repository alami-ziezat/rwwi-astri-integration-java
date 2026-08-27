package com.rwi.myrepublic.nisa.internal;

import com.rwi.myrepublic.nisa.NisaConfig;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Internal HTTP client for the NISA FAT Loss Ticketing APIs.
 *
 * Authentication flow (per call), same as NisaMassProblemClient:
 *   1. Call NisaAuthClient.getToken() -> receives JWT (muse_token)
 *   2. GET .../fatlossticketing/... with Authorization: Bearer <token>
 *
 * Unlike NisaMassProblemClient (which sends its filter as a JSON body on a GET
 * request), the segment-problem-detail endpoint's spec uses a real query string,
 * so this client builds one instead.
 *
 * NOT exposed to Magik - used only by NisaFatLossProcs.
 */
public class NisaFatLossClient {

    private final NisaConfig config;
    private final HttpClient httpClient;
    private final NisaAuthClient authClient;

    public NisaFatLossClient() {
        this.config = NisaConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
            .build();
        this.authClient = new NisaAuthClient(config, httpClient);
    }

    /**
     * Detect whether there is currently an active FAT Loss.
     *
     * GET /transaction/fatlossticketing/fat-loss-detection
     * Authorization: Bearer <muse_token>
     * No parameters.
     *
     * @return Raw JSON response body from the NISA API
     */
    public String detectFatLoss() throws IOException, InterruptedException {
        String token = authClient.getToken();
        String url = config.getApiBaseUrl() + "/transaction/fatlossticketing/fat-loss-detection";

        System.out.println("  [NisaFatLossClient] GET " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String responseBody = response.body();

        System.out.println("  [NisaFatLossClient] Response status: " + statusCode);
        System.out.println("  [NisaFatLossClient] Response length: "
            + (responseBody != null ? responseBody.length() : 0));

        if (statusCode != 200) {
            throw new IOException("NISA fat-loss-detection API returned HTTP " + statusCode
                + " | Body: " + responseBody);
        }

        return responseBody;
    }

    /**
     * Get problem detail for a segment area/hostname/FAT combination.
     *
     * GET /transaction/fatlossticketing/segment-problem-detail?area=&hostname=&fat=
     * Authorization: Bearer <muse_token>
     *
     * All three params are required by the API - it returns its own validation
     * error (e.g. {"success":false,"message":"area is required"}) when one is
     * missing, so that isn't duplicated here; whatever is passed is sent as-is.
     *
     * @param area     Area name (e.g. "JAKARTA")
     * @param hostname OLT hostname (e.g. "OLT-JKT-01")
     * @param fat      FAT code (e.g. "FAT001")
     * @return Raw JSON response body from the NISA API
     */
    public String getSegmentProblemDetail(String area, String hostname, String fat)
            throws IOException, InterruptedException {
        String token = authClient.getToken();
        String url = config.getApiBaseUrl() + "/transaction/fatlossticketing/segment-problem-detail"
            + "?area=" + urlEncode(area)
            + "&hostname=" + urlEncode(hostname)
            + "&fat=" + urlEncode(fat);

        System.out.println("  [NisaFatLossClient] GET " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String responseBody = response.body();

        System.out.println("  [NisaFatLossClient] Response status: " + statusCode);
        System.out.println("  [NisaFatLossClient] Response length: "
            + (responseBody != null ? responseBody.length() : 0));

        if (statusCode != 200) {
            throw new IOException("NISA segment-problem-detail API returned HTTP " + statusCode
                + " | Body: " + responseBody);
        }

        return responseBody;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
