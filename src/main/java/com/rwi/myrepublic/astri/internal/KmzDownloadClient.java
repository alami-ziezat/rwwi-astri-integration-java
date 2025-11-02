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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Internal HTTP client for ASTRI KMZ Document Download API.
 * NOT exposed to Magik - used only by AstriKmzDownloadProcs.
 * Uses Java 11+ HttpClient.
 */
public class KmzDownloadClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public KmzDownloadClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Download cluster document KMZ.
     */
    public String downloadClusterDocument(String uuid, String outputDir) throws IOException, InterruptedException {
        return downloadDocument("cluster", uuid, outputDir);
    }

    /**
     * Download subfeeder document KMZ.
     */
    public String downloadSubfeederDocument(String uuid, String outputDir) throws IOException, InterruptedException {
        return downloadDocument("subfeeder", uuid, outputDir);
    }

    /**
     * Download feeder document KMZ.
     */
    public String downloadFeederDocument(String uuid, String outputDir) throws IOException, InterruptedException {
        return downloadDocument("feeder", uuid, outputDir);
    }

    /**
     * Download OLT site document KMZ.
     */
    public String downloadOltSiteDocument(String uuid, String outputDir) throws IOException, InterruptedException {
        return downloadDocument("olt-site", uuid, outputDir);
    }

    /**
     * Generic document download method.
     *
     * Behavior:
     * - If outputDir is null/empty: Returns XML with KML content (for SW object migration)
     * - If outputDir is provided: Downloads files and returns XML with file paths
     *
     * @param docType Document type (cluster, subfeeder, feeder, olt-site)
     * @param uuid Document UUID
     * @param outputDir Output directory (null/empty = return KML content, provided = save files)
     * @return XML string with KML content or file paths
     */
    private String downloadDocument(String docType, String uuid, String outputDir) throws IOException, InterruptedException {
        String baseUrl = config.getDmBaseUrl();

        // Build correct endpoint path based on document type
        String path;
        switch (docType) {
            case "cluster":
                path = "/osp/cluster/document/cluster/download/" + uuid;
                break;
            case "subfeeder":
                path = "/osp/cluster/document/subfeeder/download/" + uuid;
                break;
            case "feeder":
                path = "/osp/cluster/document/feeder/download/" + uuid;
                break;
            case "olt-site":
                path = "/osp/cluster/document/olt/site/download/" + uuid;
                break;
            default:
                throw new IllegalArgumentException("Unknown document type: " + docType);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", authHeader)
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] kmzData = response.body();

        // Extract KML content from KMZ (ZIP file)
        String kmlContent = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(kmzData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".kml")) {
                    // Read KML content
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    kmlContent = baos.toString("UTF-8");
                    break;
                }
                zis.closeEntry();
            }
        }

        // SCENARIO 1: No output directory provided - Return XML with KML content
        if (outputDir == null || outputDir.trim().isEmpty()) {
            System.out.println("=== Returning KML content in XML ===");
            System.out.println("docType: " + docType);
            System.out.println("uuid: " + uuid);
            System.out.println("kmlContent length: " + kmlContent.length() + " characters");

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<response>\n");
            xml.append("  <success>true</success>\n");
            xml.append("  <document_type>").append(escapeXml(docType)).append("</document_type>\n");
            xml.append("  <uuid>").append(escapeXml(uuid)).append("</uuid>\n");
            xml.append("  <kml_content><![CDATA[").append(kmlContent).append("]]></kml_content>\n");
            xml.append("</response>");

            System.out.println("=== XML built successfully, length: " + xml.length() + " ===");
            return xml.toString();
        }

        // SCENARIO 2: Output directory provided - Save files and return file paths
        System.out.println("=== Saving files to disk ===");

        Path dirPath = Paths.get(outputDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Save KMZ file
        String kmzFileName = docType + "_" + uuid + ".kmz";
        Path kmzFilePath = dirPath.resolve(kmzFileName);
        Files.write(kmzFilePath, kmzData);

        // Save KML file
        String kmlFileName = docType + "_" + uuid + ".kml";
        Path kmlFilePath = dirPath.resolve(kmlFileName);
        Files.write(kmlFilePath, kmlContent.getBytes("UTF-8"));

        // Return XML with file paths
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<response>\n");
        xml.append("  <success>true</success>\n");
        xml.append("  <document_type>").append(escapeXml(docType)).append("</document_type>\n");
        xml.append("  <uuid>").append(escapeXml(uuid)).append("</uuid>\n");
        xml.append("  <kmz_file_path>").append(escapeXml(kmzFilePath.toString())).append("</kmz_file_path>\n");
        xml.append("  <kml_file_path>").append(escapeXml(kmlFilePath.toString())).append("</kml_file_path>\n");
        xml.append("</response>");

        System.out.println("=== XML built successfully, length: " + xml.length() + " ===");
        return xml.toString();
    }

    /**
     * Escape special characters for XML.
     */
    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    /**
     * Close and cleanup resources.
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
