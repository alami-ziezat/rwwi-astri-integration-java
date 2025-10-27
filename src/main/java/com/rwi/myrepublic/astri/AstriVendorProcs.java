package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.VendorClient;

/**
 * ASTRI Vendor API procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriVendorProcs {

    /**
     * Get vendor list from ASTRI API.
     *
     * Creates global Magik procedure: astri_get_vendor_list(limit, offset,
     *                                   _optional name, _optional subcont_vendor_name,
     *                                   _optional label, _optional sap_vendor_code)
     *
     * @param proc The Magik proc object
     * @param limit Maximum number of results (Magik integer)
     * @param offset Offset for pagination (Magik integer)
     * @param name Optional vendor name filter (Magik string)
     * @param subcontVendorName Optional subcontractor vendor name filter (Magik string)
     * @param label Optional label filter (Magik string)
     * @param sapVendorCode Optional SAP vendor code filter (Magik string)
     * @return String - JSON response or empty string on error
     */
    @MagikProc(@Name("astri_get_vendor_list"))
    public static Object getVendorList(Object proc, Object limit, Object offset,
                                       @Optional Object name,
                                       @Optional Object subcontVendorName,
                                       @Optional Object label,
                                       @Optional Object sapVendorCode) {
        System.out.println("=== DEBUG: astri_get_vendor_list called ===");

        VendorClient client = null;
        try {
            int limitInt = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);

            String nameStr = name != null ? MagikInteropUtils.fromMagikString(name) : null;
            String subcontStr = subcontVendorName != null ? MagikInteropUtils.fromMagikString(subcontVendorName) : null;
            String labelStr = label != null ? MagikInteropUtils.fromMagikString(label) : null;
            String sapCodeStr = sapVendorCode != null ? MagikInteropUtils.fromMagikString(sapVendorCode) : null;

            System.out.println("Parameters: limit=" + limitInt + ", offset=" + offsetInt);
            System.out.println("Filters: name=" + nameStr + ", subcont=" + subcontStr +
                             ", label=" + labelStr + ", sap_code=" + sapCodeStr);

            client = new VendorClient();
            String jsonResponse = client.getVendorList(limitInt, offsetInt, nameStr, subcontStr, labelStr, sapCodeStr);

            System.out.println("=== DEBUG: Vendor list retrieved successfully ===");
            return jsonResponse;

        } catch (Exception e) {
            System.err.println("=== DEBUG: ERROR in getVendorList ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return "";
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("Error closing client: " + e.getMessage());
                }
            }
            System.out.println("=== DEBUG: astri_get_vendor_list completed ===");
        }
    }
}
