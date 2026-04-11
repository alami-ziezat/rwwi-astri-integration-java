package com.rwi.myrepublic.astri.internal;

import com.rwi.myrepublic.astri.AstriConfig;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Internal HTTP client for ASTRI Document Upload API.
 * NOT exposed to Magik - used only by AstriDocumentUploadProcs.
 *
 * API: POST /v4/osp/cluster/document/add/multipart  (multipart/form-data)
 *
 * Handles:
 *  - KML -> KMZ conversion (wraps .kml into ZIP with .kmz extension)
 *  - Multipart/form-data body construction (no third-party libraries)
 *  - Basic Authentication (same credentials as existing clients)
 */
public class DocumentUploadClient {

    private final HttpClient client;
    private final AstriConfig config;
    private final String authHeader;

    public DocumentUploadClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Upload a KMZ (or KML, auto-converted to KMZ) file to the ASTRI document API.
     *
     * @param filePath      Absolute path to the .kmz or .kml source file
     * @param typeName      ASTRI document type_name (e.g. "as_plan_drawing_kmz")
     * @param clusterCode   Cluster/subfeeder code — pass null for feeder/OLT types
     * @param ospRouteName  OSP route name — pass null for cluster/subfeeder types
     * @param oltName       OLT name — pass null if not applicable
     * @param oltSiteName   OLT site name — pass null if not applicable
     * @return XML string:
     *   success: {@code <response><success>true</success><message>...</message></response>}
     *   failure: {@code <response><success>false</success><error>...</error><http_status>N</http_status></response>}
     */
    public String uploadDocument(String filePath, String typeName,
                                 String clusterCode, String ospRouteName,
                                 String oltName, String oltSiteName)
            throws IOException, InterruptedException {

        System.out.println("=== DocumentUploadClient.uploadDocument ===");
        System.out.println("filePath:     " + filePath);
        System.out.println("typeName:     " + typeName);
        System.out.println("clusterCode:  " + clusterCode);
        System.out.println("ospRouteName: " + ospRouteName);
        System.out.println("oltName:      " + oltName);
        System.out.println("oltSiteName:  " + oltSiteName);

        // Step 1: Ensure we have a proper KMZ file
        Path inputPath = Paths.get(filePath);
        if (!Files.exists(inputPath)) {
            return buildErrorXml("File not found: " + filePath, 0);
        }

        Path kmzPath = ensureKmz(inputPath);
        System.out.println("KMZ path: " + kmzPath);

        byte[] fileBytes = Files.readAllBytes(kmzPath);
        String actualFileName = kmzPath.getFileName().toString();
        System.out.println("File size: " + fileBytes.length + " bytes, actual name: " + actualFileName);

        // Step 2: Build multipart body (order matches curl: file, type_name, filename, code)
        String boundary = "----AstriBoundary" + UUID.randomUUID().toString().replace("-", "");

        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();

        writeFileField(bodyOut, boundary, "file", actualFileName, fileBytes);
        writeTextField(bodyOut, boundary, "type_name", typeName);
        writeTextField(bodyOut, boundary, "filename", actualFileName);

        if (isPresent(clusterCode))   writeTextField(bodyOut, boundary, "cluster_code",   clusterCode);
        if (isPresent(ospRouteName))  writeTextField(bodyOut, boundary, "osp_route_name", ospRouteName);
        if (isPresent(oltName))       writeTextField(bodyOut, boundary, "olt_name",        oltName);
        if (isPresent(oltSiteName))   writeTextField(bodyOut, boundary, "olt_site_name",   oltSiteName);

        // Final boundary closer
        String closingBoundary = "--" + boundary + "--\r\n";
        bodyOut.write(closingBoundary.getBytes("UTF-8"));

        byte[] bodyBytes = bodyOut.toByteArray();

        // Step 3: POST to ASTRI API
        String url = config.getDmBaseUrl() + "/osp/cluster/document/add/multipart";
        System.out.println("POST URL: " + url);
        System.out.println("Body size: " + bodyBytes.length + " bytes");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String body    = response.body();

        System.out.println("HTTP Status: " + statusCode);
        System.out.println("Response body: " + body);

        // Step 4: Build XML response for Magik
        if (statusCode >= 200 && statusCode < 300) {
            return buildSuccessXml(body, statusCode);
        } else {
            return buildErrorXml("HTTP " + statusCode + ": " + body, statusCode);
        }
    }

    // -------------------------------------------------------------------------
    // KML -> KMZ conversion
    // -------------------------------------------------------------------------

    /**
     * If the input file is a .kml, wrap it into a .kmz (ZIP) placed alongside it.
     * If the input is already .kmz the same path is returned unchanged.
     */
    private Path ensureKmz(Path inputPath) throws IOException {
        String name = inputPath.getFileName().toString();
        if (name.toLowerCase().endsWith(".kml")) {
            String baseName = name.substring(0, name.length() - 4);
            Path kmzPath = inputPath.resolveSibling(baseName + ".kmz");

            System.out.println("Converting KML -> KMZ: " + kmzPath);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(kmzPath))) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(Files.readAllBytes(inputPath));
                zos.closeEntry();
            }
            return kmzPath;
        }
        // Already .kmz — use as-is
        return inputPath;
    }

    // -------------------------------------------------------------------------
    // Multipart body helpers
    // -------------------------------------------------------------------------

    private void writeTextField(OutputStream out, String boundary,
                                 String fieldName, String value) throws IOException {
        String part = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n"
            + "\r\n"
            + value + "\r\n";
        out.write(part.getBytes("UTF-8"));
    }

    private void writeFileField(OutputStream out, String boundary,
                                 String fieldName, String fileName,
                                 byte[] fileBytes) throws IOException {
        String header = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + fieldName
            + "\"; filename=\"" + fileName + "\"\r\n"
            + "Content-Type: application/vnd.google-earth.kmz\r\n"
            + "\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(fileBytes);
        out.write("\r\n".getBytes("UTF-8"));
    }

    // -------------------------------------------------------------------------
    // XML response builders
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

    private boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public void close() {
        // HttpClient does not need explicit closing in Java 11+
    }
}
