package com.rwi.myrepublic.stella;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.stella.internal.StellaDocumentUploadClient;

/**
 * Stella document upload procedures exposed to Magik - Cluster/FAT/Homepass
 * Excel uploads for the Stela Integration dialog.
 * Uses @MagikProc annotation to automatically create a global Magik procedure.
 *
 * Exposes:
 *   stella_upload_document(file_path, cluster_code, doc_type, _optional file_name)
 *
 * Returns an XML string parsed by Magik via simple_xml.read_element_string().
 */
public class StellaDocumentUploadProcs {

    /**
     * Upload a Cluster/FAT/Homepass Excel file to the Stella document API.
     *
     * @param proc        Magik proc object (required by framework)
     * @param filePath    Absolute path to the local .xlsx file (Magik string)
     * @param clusterCode Cluster code for the current design job (Magik string)
     * @param docType     "cluster", "fat" or "homepass" (Magik string) - routing only
     * @param fileName    File name to send (Magik string, optional - defaults to the
     *                    local file's own name)
     * @return XML string response
     */
    @MagikProc(@Name("stella_upload_document"))
    public static Object uploadStellaDocument(Object proc,
                                               Object filePath,
                                               Object clusterCode,
                                               Object docType,
                                               @Optional Object fileName) {
        System.out.println("=== DEBUG: stella_upload_document called ===");

        StellaDocumentUploadClient client = null;
        try {
            String filePathStr    = MagikInteropUtils.fromMagikString(filePath);
            String clusterCodeStr = MagikInteropUtils.fromMagikString(clusterCode);
            String docTypeStr     = MagikInteropUtils.fromMagikString(docType);
            String fileNameStr    = toJavaStringOrNull(fileName);

            System.out.println("filePath:    " + filePathStr);
            System.out.println("clusterCode: " + clusterCodeStr);
            System.out.println("docType:     " + docTypeStr);
            System.out.println("fileName:    " + fileNameStr);

            client = new StellaDocumentUploadClient();
            String xmlResponse = client.uploadDocument(filePathStr, clusterCodeStr, docTypeStr, fileNameStr);

            System.out.println("=== DEBUG: stella_upload_document successful ===");
            return MagikInteropUtils.toMagikString(xmlResponse);

        } catch (Exception e) {
            System.err.println("=== ERROR in stella_upload_document ===");
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
            System.out.println("=== DEBUG: stella_upload_document completed ===");
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
