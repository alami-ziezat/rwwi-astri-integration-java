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
     * @param docType Document type (cluster, subfeeder, feeder, olt-site)
     * @param uuid Document UUID
     * @param outputDir Output directory (null = use config default)
     * @return JSON string with download results
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

        // Determine output directory
        String dir = outputDir != null ? outputDir : config.getDownloadDir();
        Path dirPath = Paths.get(dir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Save KMZ file
        String kmzFileName = docType + "_" + uuid + ".kmz";
        Path kmzFilePath = dirPath.resolve(kmzFileName);
        Files.write(kmzFilePath, kmzData);

        // Extract KML from KMZ (ZIP file)
        String kmlContent = "";
        String kmlFileName = docType + "_" + uuid + ".kml";
        Path kmlFilePath = dirPath.resolve(kmlFileName);

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

                    // Save KML file
                    Files.write(kmlFilePath, kmlContent.getBytes("UTF-8"));
                    break;
                }
                zis.closeEntry();
            }
        }

        // Return just the KML file path as a simple string
        System.out.println("=== DEBUG: Returning KML file path ===");
        System.out.println("KML file path: " + kmlFilePath);

        return kmlFilePath.toString();

        /* OLD CODE: XML response (commented out - caused parsing issues in Magik)
        // Build XML response (easier to parse in Magik with simple_xml)
        System.out.println("=== DEBUG: Building XML response ===");
        System.out.println("docType: " + docType);
        System.out.println("uuid: " + uuid);
        System.out.println("kmzFilePath: " + kmzFilePath);
        System.out.println("kmlFilePath: " + kmlFilePath);
        System.out.println("kmlContent length: " + kmlContent.length() + " characters");

        StringBuilder xml = new StringBuilder();

        // Debug: Build XML step by step to isolate issue
        System.out.println("Adding XML header...");
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<response>");
        xml.append("<success>true</success>");

        // Debug: Check if docType causes issues
        System.out.println("Adding document_type...");
        xml.append("<document_type>").append(escapeXml(docType)).append("</document_type>");

        // Debug: Check if uuid causes issues
        System.out.println("Adding uuid...");
        xml.append("<uuid>").append(escapeXml(uuid)).append("</uuid>");

        // Debug: Check if file paths cause issues
        System.out.println("Adding file paths...");
        xml.append("<kmz_file_path>").append(escapeXml(kmzFilePath.toString())).append("</kmz_file_path>");
        xml.append("<kml_file_path>").append(escapeXml(kmlFilePath.toString())).append("</kml_file_path>");

        // Debug: Temporarily commented out kml_content to test if it's the source of error
        // The kml_content contains the full KML file which may have Unicode characters
        // that cause issues in Magik (e.g., zero-width spaces U+200B)
        System.out.println("Skipping kml_content (commented out for debugging)...");
        //xml.append("<kml_content><![CDATA[").append(kmlContent).append("]]></kml_content>");

        // Debug: Add placeholder to confirm XML structure is valid
        xml.append("<kml_content_status>temporarily_disabled_for_debugging</kml_content_status>");

        xml.append("</response>");

        System.out.println("=== DEBUG: XML built successfully, length: " + xml.length() + " ===");
        System.out.println("XML Preview (first 500 chars): " + xml.substring(0, Math.min(500, xml.length())));

        return xml.toString();
        END OLD CODE */
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
