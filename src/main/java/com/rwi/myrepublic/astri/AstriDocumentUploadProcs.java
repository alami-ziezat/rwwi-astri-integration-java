package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.DocumentUploadClient;

/**
 * ASTRI Document Upload procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 *
 * Exposes two global procs:
 *   astri_upload_kmz(file_path, type_name, _optional cluster_code, osp_route_name, olt_name, olt_site_name)
 *   astri_upload_document(file_path, type_name, _optional cluster_code, osp_route_name, olt_name, olt_site_name)
 *
 * Both return an XML string parsed by Magik via simple_xml.read_element_string().
 */
public class AstriDocumentUploadProcs {

    /**
     * Upload a KMZ file (or KML — auto-converted) to the ASTRI document API.
     *
     * Creates global Magik procedure:
     *   astri_upload_kmz(file_path, type_name,
     *                    _optional cluster_code, osp_route_name, olt_name, olt_site_name)
     *
     * @param proc          Magik proc object (required by framework)
     * @param filePath      Absolute path to the .kmz or .kml file (Magik string)
     * @param typeName      ASTRI document type_name value (Magik string)
     * @param clusterCode   Cluster/subfeeder code — pass _unset for feeder/OLT (Magik string)
     * @param ospRouteName  OSP route name — pass _unset for cluster/subfeeder (Magik string)
     * @param oltName       OLT name (Magik string, optional)
     * @param oltSiteName   OLT site name (Magik string, optional)
     * @return XML string response
     */
    @MagikProc(@Name("astri_upload_kmz"))
    public static Object uploadKmz(Object proc,
                                    Object filePath,
                                    Object typeName,
                                    @Optional Object clusterCode,
                                    @Optional Object ospRouteName,
                                    @Optional Object oltName,
                                    @Optional Object oltSiteName) {
        System.out.println("=== DEBUG: astri_upload_kmz called ===");
        System.out.println("filePath:  " + filePath);
        System.out.println("typeName:  " + typeName);

        DocumentUploadClient client = null;
        try {
            String filePathStr    = MagikInteropUtils.fromMagikString(filePath);
            String typeNameStr    = MagikInteropUtils.fromMagikString(typeName);
            String clusterStr     = toJavaStringOrNull(clusterCode);
            String ospRouteStr    = toJavaStringOrNull(ospRouteName);
            String oltNameStr     = toJavaStringOrNull(oltName);
            String oltSiteNameStr = toJavaStringOrNull(oltSiteName);

            System.out.println("filePathStr:    " + filePathStr);
            System.out.println("typeNameStr:    " + typeNameStr);
            System.out.println("clusterStr:     " + clusterStr);
            System.out.println("ospRouteStr:    " + ospRouteStr);
            System.out.println("oltNameStr:     " + oltNameStr);
            System.out.println("oltSiteNameStr: " + oltSiteNameStr);

            client = new DocumentUploadClient();
            String xmlResponse = client.uploadDocument(
                filePathStr, typeNameStr,
                clusterStr, ospRouteStr, oltNameStr, oltSiteNameStr);

            System.out.println("=== DEBUG: astri_upload_kmz successful ===");
            return MagikInteropUtils.toMagikString(xmlResponse);

        } catch (Exception e) {
            System.err.println("=== ERROR in astri_upload_kmz ===");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            String errXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<response><success>false</success><error>"
                + escapeXml(e.getMessage()) + "</error></response>";
            return MagikInteropUtils.toMagikString(errXml);

        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception e) { /* ignore */ }
            }
            System.out.println("=== DEBUG: astri_upload_kmz completed ===");
        }
    }

    /**
     * Generic document upload (same implementation as astri_upload_kmz).
     * Use this for BOQ Excel or other document types where no KML conversion is needed.
     *
     * Creates global Magik procedure:
     *   astri_upload_document(file_path, type_name,
     *                         _optional cluster_code, osp_route_name, olt_name, olt_site_name)
     */
    @MagikProc(@Name("astri_upload_document"))
    public static Object uploadDocument(Object proc,
                                         Object filePath,
                                         Object typeName,
                                         @Optional Object clusterCode,
                                         @Optional Object ospRouteName,
                                         @Optional Object oltName,
                                         @Optional Object oltSiteName) {
        System.out.println("=== DEBUG: astri_upload_document called ===");
        System.out.println("filePath: " + filePath);
        System.out.println("typeName: " + typeName);

        DocumentUploadClient client = null;
        try {
            String filePathStr    = MagikInteropUtils.fromMagikString(filePath);
            String typeNameStr    = MagikInteropUtils.fromMagikString(typeName);
            String clusterStr     = toJavaStringOrNull(clusterCode);
            String ospRouteStr    = toJavaStringOrNull(ospRouteName);
            String oltNameStr     = toJavaStringOrNull(oltName);
            String oltSiteNameStr = toJavaStringOrNull(oltSiteName);

            client = new DocumentUploadClient();
            String xmlResponse = client.uploadDocument(
                filePathStr, typeNameStr,
                clusterStr, ospRouteStr, oltNameStr, oltSiteNameStr);

            System.out.println("=== DEBUG: astri_upload_document successful ===");
            return MagikInteropUtils.toMagikString(xmlResponse);

        } catch (Exception e) {
            System.err.println("=== ERROR in astri_upload_document ===");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            String errXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<response><success>false</success><error>"
                + escapeXml(e.getMessage()) + "</error></response>";
            return MagikInteropUtils.toMagikString(errXml);

        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception e) { /* ignore */ }
            }
            System.out.println("=== DEBUG: astri_upload_document completed ===");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Convert a Magik object to a Java String, returning null for Magik _unset / null.
     */
    private static String toJavaStringOrNull(Object magikObj) {
        if (magikObj == null) return null;
        try {
            String s = MagikInteropUtils.fromMagikString(magikObj);
            return (s == null || s.trim().isEmpty()) ? null : s;
        } catch (Exception e) {
            return null;
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
