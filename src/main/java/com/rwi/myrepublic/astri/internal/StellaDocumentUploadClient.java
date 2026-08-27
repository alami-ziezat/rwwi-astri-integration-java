package com.rwi.myrepublic.astri.internal;

import com.rwi.myrepublic.astri.AstriConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;

/**
 * Internal HTTP client for the ASTRI "... Database Stella" document upload APIs
 * (Cluster / FAT / Homepass). NOT exposed to Magik - used only by
 * AstriStellaDocumentUploadProcs.
 *
 * API (currently, all doc types): POST /v4/osp/cluster/document/homepass-database/stella/upload
 * Base: AstriConfig.getStellaBaseUrl() (dedicated to this endpoint, independent of
 * the shared astri-api-v2 base used by other callers such as BoqClient)
 * Content-Type: application/json   Body: {cluster_code, file_name, file_base64}
 *
 * Cluster/FAT are provisionally routed to the same endpoint as Homepass pending
 * confirmation from the API developer of separate routes - see resolveRoute().
 *
 * ASTRI returns HTTP 200 even for logical failures (e.g. an exception raised
 * while processing the request) - the response body's own "success" field is
 * the real indicator, so it's inspected in addition to the HTTP status code.
 */
public class StellaDocumentUploadClient {

    private final HttpClient client;
    private final AstriConfig config;
    private final String authHeader;

    public StellaDocumentUploadClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Upload a Cluster/FAT/Homepass Excel file to the ASTRI Stella document API.
     *
     * @param filePath    Absolute path to the local .xlsx file
     * @param clusterCode Cluster code for the current design job
     * @param docType     "cluster", "fat" or "homepass" (case-insensitive) - routing only,
     *                    not sent to ASTRI (the spec has no type field)
     * @param fileName    File name to send - falls back to filePath's own file name if blank
     * @return XML string:
     *   success: {@code <response><success>true</success><message>...</message></response>}
     *   failure: {@code <response><success>false</success><error>...</error><http_status>N</http_status></response>}
     */
    public String uploadDocument(String filePath, String clusterCode, String docType, String fileName)
            throws IOException, InterruptedException {

        System.out.println("=== StellaDocumentUploadClient.uploadDocument ===");
        System.out.println("filePath:    " + filePath);
        System.out.println("clusterCode: " + clusterCode);
        System.out.println("docType:     " + docType);
        System.out.println("fileName:    " + fileName);

        Path inputPath = Paths.get(filePath);
        if (!Files.exists(inputPath)) {
            return buildErrorXml("File not found: " + filePath, 0);
        }

        byte[] fileBytes = Files.readAllBytes(inputPath);
        String actualFileName = isPresent(fileName) ? fileName : inputPath.getFileName().toString();
        String base64 = Base64.getEncoder().encodeToString(fileBytes);

        System.out.println("File size: " + fileBytes.length + " bytes, actual name: " + actualFileName);

        String jsonBody = "{"
            + "\"cluster_code\":\"" + escapeJson(clusterCode) + "\","
            + "\"file_name\":\"" + escapeJson(actualFileName) + "\","
            + "\"file_base64\":\"" + base64 + "\""
            + "}";

        String url = config.getStellaBaseUrl() + resolveRoute(docType);
        System.out.println("POST URL: " + url);
        // Log the request body with file_base64 redacted to a length marker -
        // logging the full base64 blob would flood the console for real files.
        System.out.println("Request body: {\"cluster_code\":\"" + escapeJson(clusterCode)
            + "\",\"file_name\":\"" + escapeJson(actualFileName)
            + "\",\"file_base64\":\"<" + base64.length() + " chars>\"}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String body = response.body();

        System.out.println("HTTP Status: " + statusCode);
        System.out.println("Response body: " + body);

        if (statusCode >= 200 && statusCode < 300 && isApiSuccess(body)) {
            return buildSuccessXml(body, statusCode);
        } else {
            return buildErrorXml("HTTP " + statusCode + ": " + body, statusCode);
        }
    }

    /**
     * ASTRI's own response body carries a top-level "success" boolean that can
     * be false even on HTTP 200 (e.g. an exception raised server-side while
     * processing an otherwise well-formed request) - treat that as failure too.
     * No JSON library in this project, so this is a deliberately crude check
     * against the documented response shape ({"success":true|false,...}).
     */
    private boolean isApiSuccess(String jsonBody) {
        if (jsonBody == null) return false;
        String normalized = jsonBody.replaceAll("\\s+", "");
        return normalized.contains("\"success\":true");
    }

    // -------------------------------------------------------------------------
    // Route resolution
    // -------------------------------------------------------------------------

    /**
     * Resolve the upload route for a document type.
     *
     * All three types (cluster/fat/homepass) currently share the Homepass Stella
     * route - the only one specified so far. Provisional, per confirmation from
     * the requester pending sign-off from the API developer. Once Cluster/FAT get
     * their own routes, branch on docType here only - callers don't need to change.
     */
    private String resolveRoute(String docType) {
        return "/osp/cluster/document/homepass-database/stella/upload";
    }

    // -------------------------------------------------------------------------
    // XML response builders (same envelope as DocumentUploadClient)
    // -------------------------------------------------------------------------

    private String buildSuccessXml(String rawBody, int httpStatus) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<response>\n");
        xml.append("  <success>true</success>\n");
        xml.append("  <http_status>").append(httpStatus).append("</http_status>\n");
        xml.append("  <message><![CDATA[").append(rawBody).append("]]></message>\n");
        xml.append("</response>");
        return xml.toString();
    }

    private String buildErrorXml(String errorMsg, int httpStatus) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<response>\n");
        xml.append("  <success>false</success>\n");
        xml.append("  <http_status>").append(httpStatus).append("</http_status>\n");
        xml.append("  <error>").append(escapeXml(errorMsg)).append("</error>\n");
        xml.append("</response>");
        return xml.toString();
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public void close() {
        // HttpClient does not need explicit closing in Java 11+
    }
}
