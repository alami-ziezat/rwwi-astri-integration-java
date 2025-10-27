package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.OltClient;

/**
 * ASTRI OLT (Optical Line Terminal) Rollout API procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriOltProcs {

    /**
     * Get OLT rollout list from ASTRI API.
     *
     * Creates global Magik procedure: astri_get_olt_list(limit, offset,
     *                                   _optional device_code, _optional name,
     *                                   _optional label)
     *
     * @param proc The Magik proc object
     * @param limit Maximum number of results (Magik integer)
     * @param offset Offset for pagination (Magik integer)
     * @param deviceCode Optional device code filter (Magik string)
     * @param name Optional name filter (Magik string)
     * @param label Optional label filter (Magik string)
     * @return String - JSON response or empty string on error
     */
    @MagikProc(@Name("astri_get_olt_list"))
    public static Object getOltList(Object proc, Object limit, Object offset,
                                    @Optional Object deviceCode,
                                    @Optional Object name,
                                    @Optional Object label) {
        System.out.println("=== DEBUG: astri_get_olt_list called ===");

        OltClient client = null;
        try {
            int limitInt = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);

            String deviceCodeStr = deviceCode != null ? MagikInteropUtils.fromMagikString(deviceCode) : null;
            String nameStr = name != null ? MagikInteropUtils.fromMagikString(name) : null;
            String labelStr = label != null ? MagikInteropUtils.fromMagikString(label) : null;

            System.out.println("Parameters: limit=" + limitInt + ", offset=" + offsetInt);
            System.out.println("Filters: device_code=" + deviceCodeStr + ", name=" + nameStr +
                             ", label=" + labelStr);

            client = new OltClient();
            String jsonResponse = client.getOltList(limitInt, offsetInt, deviceCodeStr, nameStr, labelStr);

            System.out.println("=== DEBUG: OLT list retrieved successfully ===");
            return jsonResponse;

        } catch (Exception e) {
            System.err.println("=== DEBUG: ERROR in getOltList ===");
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
            System.out.println("=== DEBUG: astri_get_olt_list completed ===");
        }
    }
}
