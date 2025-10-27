package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.KmzDownloadClient;

/**
 * ASTRI KMZ Document Download procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriKmzDownloadProcs {

    /**
     * Download cluster KMZ document from ASTRI DM API.
     *
     * Creates global Magik procedure: astri_download_cluster_kmz(uuid, _optional output_dir)
     *
     * @param proc The Magik proc object
     * @param uuid Document UUID (Magik string)
     * @param outputDir Optional output directory (Magik string), defaults to config setting
     * @return String - XML response with download results:
     *         <response><success>true</success><kmz_file_path>...</kmz_file_path>
     *         <kml_file_path>...</kml_file_path><kml_content><![CDATA[...]]></kml_content></response>
     */
    @MagikProc(@Name("astri_download_cluster_kmz"))
    public static Object downloadClusterKmz(Object proc, Object uuid,
                                            @Optional Object outputDir) {
        System.out.println("=== DEBUG: astri_download_cluster_kmz called ===");
        System.out.println("Magik uuid object: " + uuid);
        System.out.println("Magik outputDir object: " + outputDir);

        KmzDownloadClient client = null;
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                           MagikInteropUtils.fromMagikString(outputDir) : null;

            System.out.println("Converted uuidStr: " + uuidStr);
            System.out.println("Converted dirStr: " + dirStr);
            System.out.println("Creating KmzDownloadClient...");

            client = new KmzDownloadClient();

            System.out.println("Calling downloadClusterDocument...");
            String xmlResponse = client.downloadClusterDocument(uuidStr, dirStr);

            System.out.println("=== DEBUG: Download successful, returning XML response ===");
            System.out.println("Response length: " + xmlResponse.length() + " characters");

            return xmlResponse;

        } catch (Exception e) {
            System.err.println("=== DEBUG: ERROR in downloadClusterKmz ===");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Return empty string on error (simple approach)
            return "";

            /* OLD CODE: XML error response (commented out)
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response><success>false</success><error>" +
                   escapeXml(e.getMessage()) + "</error></response>";
            END OLD CODE */
        } finally {
            if (client != null) {
                try {
                    System.out.println("Closing client...");
                    client.close();
                } catch (Exception e) {
                    System.err.println("Error closing client: " + e.getMessage());
                }
            }
            System.out.println("=== DEBUG: astri_download_cluster_kmz completed ===");
        }
    }

    /**
     * Download subfeeder KMZ document from ASTRI DM API.
     *
     * Creates global Magik procedure: astri_download_subfeeder_kmz(uuid, _optional output_dir)
     */
    @MagikProc(@Name("astri_download_subfeeder_kmz"))
    public static Object downloadSubfeederKmz(Object proc, Object uuid,
                                              @Optional Object outputDir) {
        KmzDownloadClient client = null;
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                           MagikInteropUtils.fromMagikString(outputDir) : null;

            client = new KmzDownloadClient();
            String xmlResponse = client.downloadSubfeederDocument(uuidStr, dirStr);

            return xmlResponse;

        } catch (Exception e) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response><success>false</success><error>" +
                   escapeXml(e.getMessage()) + "</error></response>";
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Download feeder KMZ document from ASTRI DM API.
     *
     * Creates global Magik procedure: astri_download_feeder_kmz(uuid, _optional output_dir)
     */
    @MagikProc(@Name("astri_download_feeder_kmz"))
    public static Object downloadFeederKmz(Object proc, Object uuid,
                                           @Optional Object outputDir) {
        KmzDownloadClient client = null;
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                           MagikInteropUtils.fromMagikString(outputDir) : null;

            client = new KmzDownloadClient();
            String xmlResponse = client.downloadFeederDocument(uuidStr, dirStr);

            return xmlResponse;

        } catch (Exception e) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response><success>false</success><error>" +
                   escapeXml(e.getMessage()) + "</error></response>";
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Download OLT site KMZ document from ASTRI DM API.
     *
     * Creates global Magik procedure: astri_download_olt_site_kmz(uuid, _optional output_dir)
     */
    @MagikProc(@Name("astri_download_olt_site_kmz"))
    public static Object downloadOltSiteKmz(Object proc, Object uuid,
                                            @Optional Object outputDir) {
        KmzDownloadClient client = null;
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            String dirStr = outputDir != null ?
                           MagikInteropUtils.fromMagikString(outputDir) : null;

            client = new KmzDownloadClient();
            String xmlResponse = client.downloadOltSiteDocument(uuidStr, dirStr);

            return xmlResponse;

        } catch (Exception e) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response><success>false</success><error>" +
                   escapeXml(e.getMessage()) + "</error></response>";
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Escape special characters for XML.
     */
    private static String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
}
