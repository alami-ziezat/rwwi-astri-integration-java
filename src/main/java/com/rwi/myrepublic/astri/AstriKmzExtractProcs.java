package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.interop.MagikInteropUtils;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility procedures for extracting KML content from a local KMZ file.
 * Exposed to Magik via @MagikProc annotation.
 *
 * KMZ is a ZIP archive containing one or more .kml files.
 * This class reads the archive from disk (as opposed to KmzDownloadClient
 * which receives the bytes over HTTP) and saves the first .kml entry to disk.
 */
public class AstriKmzExtractProcs {

    /**
     * Extract the KML file from a local KMZ archive and save it to outputDir.
     *
     * Creates global Magik procedure: astri_extract_kmz_to_kml(kmz_file_path, output_dir)
     *
     * @param proc        The Magik proc object (required by interop framework)
     * @param kmzFilePath Full path to the local .kmz file (Magik string)
     * @param outputDir   Directory where the extracted .kml will be saved (Magik string)
     * @return XML string:
     *         <response>
     *           <success>true</success>
     *           <kmz_file_path>...</kmz_file_path>
     *           <kml_file_path>...</kml_file_path>
     *         </response>
     *         or on failure:
     *         <response><success>false</success><error>...</error></response>
     */
    @MagikProc(@Name("astri_extract_kmz_to_kml"))
    public static Object extractKmzToKml(Object proc, Object kmzFilePath, Object outputDir) {
        System.out.println("=== DEBUG: astri_extract_kmz_to_kml called ===");

        try {
            String kmzPath = MagikInteropUtils.fromMagikString(kmzFilePath);
            String outDir  = MagikInteropUtils.fromMagikString(outputDir);

            System.out.println("KMZ file path : " + kmzPath);
            System.out.println("Output dir    : " + outDir);

            File kmzFile = new File(kmzPath);
            if (!kmzFile.exists()) {
                throw new FileNotFoundException("KMZ file not found: " + kmzPath);
            }

            // Extract first .kml entry from KMZ (ZIP archive)
            String kmlContent    = null;
            String kmlEntryName  = null;

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(kmzFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().toLowerCase().endsWith(".kml")) {
                        kmlEntryName = entry.getName();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
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

            if (kmlContent == null) {
                throw new IOException("No .kml file found inside KMZ: " + kmzPath);
            }

            System.out.println("Extracted KML entry : " + kmlEntryName);
            System.out.println("KML content length  : " + kmlContent.length() + " characters");

            // Derive output KML file name from KMZ file name (same base name, .kml extension)
            String kmzFileName = kmzFile.getName();
            String kmlFileName = kmzFileName.substring(0, kmzFileName.length() - 4) + ".kml";

            // Ensure output directory exists
            Path dirPath = Paths.get(outDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Write KML content to disk
            Path kmlFilePath = dirPath.resolve(kmlFileName);
            Files.write(kmlFilePath, kmlContent.getBytes("UTF-8"));

            System.out.println("Saved KML to : " + kmlFilePath.toString());

            // Build XML response
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<response>\n");
            xml.append("  <success>true</success>\n");
            xml.append("  <kmz_file_path>").append(escapeXml(kmzPath)).append("</kmz_file_path>\n");
            xml.append("  <kml_file_path>").append(escapeXml(kmlFilePath.toString())).append("</kml_file_path>\n");
            xml.append("</response>");

            System.out.println("=== DEBUG: astri_extract_kmz_to_kml completed successfully ===");
            return MagikInteropUtils.toMagikString(xml.toString());

        } catch (Exception e) {
            System.err.println("=== ERROR in astri_extract_kmz_to_kml: " + e.getMessage());
            e.printStackTrace();

            String errorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<response><success>false</success><error>" +
                escapeXml(e.getMessage()) + "</error></response>";
            return MagikInteropUtils.toMagikString(errorXml);
        }
    }

    private static String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
}
