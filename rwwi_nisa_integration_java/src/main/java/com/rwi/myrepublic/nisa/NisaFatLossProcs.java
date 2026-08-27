package com.rwi.myrepublic.nisa;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.nisa.internal.NisaFatLossClient;

/**
 * NISA FAT Loss Ticketing API procedures exposed to Magik.
 *
 * Uses @MagikProc annotation to create global Magik procedures automatically.
 *
 * Authentication: JWT Bearer token (2-step), same as the massproblem procs:
 *   1. POST /authentication/gettoken -> receives muse_token
 *   2. GET endpoint with Authorization: Bearer <muse_token>
 */
public class NisaFatLossProcs {

    /**
     * Detect whether there is currently an active FAT Loss.
     *
     * Creates global Magik procedure: nisa_fat_loss_detection()
     *
     * @param proc The Magik proc object (always first parameter for @MagikProc)
     * @return Raw JSON response string from NISA API (Magik string).
     *         Parse in Magik using json_parser.
     *
     * Magik usage example:
     *   json_result << nisa_fat_loss_detection()
     *   json_obj << json_parser.parse(json_result)
     *   is_detected << json_obj[:data][:is_fat_loss_detected]
     */
    @MagikProc(@Name("nisa_fat_loss_detection"))
    public static Object detectFatLoss(Object proc) {
        try {
            System.out.println("====== NISA FAT LOSS DETECTION - START ======");

            NisaFatLossClient client = new NisaFatLossClient();
            String jsonResponse = client.detectFatLoss();

            System.out.println("  Response length: " + (jsonResponse != null ? jsonResponse.length() : 0));
            System.out.println("====== NISA FAT LOSS DETECTION - END ======");

            return MagikInteropUtils.toMagikString(jsonResponse);

        } catch (Exception e) {
            System.err.println("ERROR in nisa_fat_loss_detection: " + e.getMessage());
            e.printStackTrace();
            return errorJson(e);
        }
    }

    /**
     * Get FAT loss problem detail for a segment area/hostname/FAT combination.
     *
     * Creates global Magik procedure: nisa_segment_problem_detail(area, hostname, fat)
     *
     * area/hostname/fat are all required by the API itself - a blank value is sent
     * through as-is and the API's own validation message (e.g. "area is required")
     * comes back rather than being duplicated here.
     *
     * @param proc     The Magik proc object (always first parameter for @MagikProc)
     * @param area     Area name (Magik string, e.g. "JAKARTA")
     * @param hostname OLT hostname (Magik string, e.g. "OLT-JKT-01")
     * @param fat      FAT code (Magik string, e.g. "FAT001")
     * @return Raw JSON response string from NISA API (Magik string).
     *
     * Magik usage example:
     *   json_result << nisa_segment_problem_detail("JAKARTA", "OLT-JKT-01", "FAT001")
     */
    @MagikProc(@Name("nisa_segment_problem_detail"))
    public static Object getSegmentProblemDetail(Object proc, Object area, Object hostname, Object fat) {
        try {
            System.out.println("====== NISA SEGMENT PROBLEM DETAIL - START ======");

            String areaStr     = MagikInteropUtils.fromMagikString(area);
            String hostnameStr = MagikInteropUtils.fromMagikString(hostname);
            String fatStr      = MagikInteropUtils.fromMagikString(fat);
            System.out.println("  area: " + areaStr + ", hostname: " + hostnameStr + ", fat: " + fatStr);

            NisaFatLossClient client = new NisaFatLossClient();
            String jsonResponse = client.getSegmentProblemDetail(areaStr, hostnameStr, fatStr);

            System.out.println("  Response length: " + (jsonResponse != null ? jsonResponse.length() : 0));
            System.out.println("====== NISA SEGMENT PROBLEM DETAIL - END ======");

            return MagikInteropUtils.toMagikString(jsonResponse);

        } catch (Exception e) {
            System.err.println("ERROR in nisa_segment_problem_detail: " + e.getMessage());
            e.printStackTrace();
            return errorJson(e);
        }
    }

    /**
     * Wrap an exception as {"success":false,"error":"..."} JSON so Magik can detect failure.
     */
    private static Object errorJson(Exception e) {
        String errorJson = "{\"success\":false,\"error\":" + jsonEscape(e.getMessage()) + "}";
        try {
            return MagikInteropUtils.toMagikString(errorJson);
        } catch (Exception e2) {
            return errorJson; // Fallback if Magik conversion fails
        }
    }

    /**
     * Wrap a string as a JSON string literal (with surrounding quotes and escaping).
     */
    private static String jsonEscape(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t")
               + "\"";
    }
}
