package com.rwi.myrepublic.nisa.internal;

import com.rwi.myrepublic.nisa.NisaConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal HTTP client for NISA JWT authentication.
 *
 * Calls POST /authentication/gettoken with userid + password in the JSON body.
 * On success, extracts and returns the muse_token (JWT) from the response.
 *
 * NOT exposed to Magik - used only by NisaMassProblemClient.
 */
public class NisaAuthClient {

    private final NisaConfig config;
    private final HttpClient httpClient;

    public NisaAuthClient(NisaConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    /**
     * Obtain a fresh JWT token from NISA authentication endpoint.
     *
     * POST /authentication/gettoken
     * Body: {"userid":"fms.team","password":"..."}
     *
     * @return muse_token string (JWT Bearer token)
     * @throws Exception if authentication fails or token is missing
     */
    public String getToken() throws IOException, InterruptedException {
        String url = config.getApiBaseUrl() + "/authentication/gettoken";
        String body = buildAuthPayload();

        System.out.println("  [NisaAuthClient] Authenticating at: " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String responseBody = response.body();

        System.out.println("  [NisaAuthClient] Auth response status: " + statusCode);

        if (statusCode != 200) {
            throw new IOException("NISA authentication failed. HTTP status: " + statusCode
                + " | Response: " + responseBody);
        }

        // Validate success field
        String success = extractJsonValue(responseBody, "success");
        if (!"true".equalsIgnoreCase(success)) {
            String msg = extractJsonValue(responseBody, "msg");
            throw new IOException("NISA authentication returned success=false. msg: "
                + (msg != null ? msg : "(no message)"));
        }

        // Extract muse_token
        String token = extractJsonValue(responseBody, "muse_token");
        if (token == null || token.isEmpty()) {
            throw new IOException("NISA authentication succeeded but muse_token is missing in response");
        }

        System.out.println("  [NisaAuthClient] Token obtained successfully (length=" + token.length() + ")");
        return token;
    }

    /**
     * Build JSON authentication payload.
     * {"userid":"fms.team","password":"..."}
     */
    private String buildAuthPayload() {
        return "{\"userid\":\"" + escapeJson(config.getUsername())
            + "\",\"password\":\"" + escapeJson(config.getPassword()) + "\"}";
    }

    /**
     * Extract a JSON string field value using regex.
     * Handles both quoted strings and unquoted booleans/numbers.
     */
    private String extractJsonValue(String json, String fieldName) {
        // Try quoted string: "fieldName": "value"
        Pattern quoted = Pattern.compile(
            "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher m = quoted.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Try unquoted (boolean, number, null): "fieldName": value
        Pattern unquoted = Pattern.compile(
            "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*([^,}\\s\\]]+)");
        m = unquoted.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
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
