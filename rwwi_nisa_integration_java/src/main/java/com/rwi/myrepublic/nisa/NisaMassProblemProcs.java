package com.rwi.myrepublic.nisa;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.nisa.internal.NisaMassProblemClient;

/**
 * NISA Mass Problem API procedures exposed to Magik.
 *
 * Uses @MagikProc annotation to create global Magik procedures automatically.
 *
 * Authentication: JWT Bearer token (2-step):
 *   1. POST /authentication/gettoken → receives muse_token
 *   2. POST endpoint with Authorization: Bearer <muse_token>
 */
public class NisaMassProblemProcs {

    /**
     * Query active mass problems for a cluster from the NISA API.
     *
     * Creates global Magik procedure: nisa_get_massproblem_active_cluster(cluster_code)
     *
     * Authentication is handled automatically:
     *   - Calls POST /authentication/gettoken to get JWT token
     *   - Uses token as Bearer auth for POST /transaction/massproblem/active/cluster
     *
     * @param proc       The Magik proc object (always first parameter for @MagikProc)
     * @param clusterCode Cluster code to query (Magik string, e.g. "CLUSTER-001")
     * @return Raw JSON response string from NISA API (Magik string).
     *         Parse in Magik using json_parser.
     *
     * Magik usage example:
     *   json_result << nisa_get_massproblem_active_cluster("CLUSTER-001")
     *   json_obj << json_parser.parse(json_result)
     *   success << json_obj[:success]
     *   data    << json_obj[:data]
     */
    @MagikProc(@Name("nisa_get_massproblem_active_cluster"))
    public static Object getMassProblemActiveCluster(Object proc, Object clusterCode) {
        try {
            System.out.println("====== NISA GET MASSPROBLEM ACTIVE CLUSTER - START ======");

            // Convert Magik string → Java String
            String cluster = MagikInteropUtils.fromMagikString(clusterCode);
            System.out.println("  Cluster code: " + cluster);

            if (cluster == null || cluster.trim().isEmpty()) {
                throw new IllegalArgumentException("cluster_code must not be empty");
            }

            // Call NISA API (auth + massproblem endpoint)
            NisaMassProblemClient client = new NisaMassProblemClient();
            String jsonResponse = client.getMassProblemActiveCluster(cluster.trim());

            System.out.println("  Response length: " + (jsonResponse != null ? jsonResponse.length() : 0));
            System.out.println("====== NISA GET MASSPROBLEM ACTIVE CLUSTER - END ======");

            // Return raw JSON to Magik
            return MagikInteropUtils.toMagikString(jsonResponse);

        } catch (Exception e) {
            System.err.println("ERROR in nisa_get_massproblem_active_cluster: " + e.getMessage());
            e.printStackTrace();

            // Return error as JSON string so Magik can detect failure
            String errorJson = "{\"success\":false,\"error\":" + jsonEscape(e.getMessage()) + "}";
            try {
                return MagikInteropUtils.toMagikString(errorJson);
            } catch (Exception e2) {
                return errorJson; // Fallback if Magik conversion fails
            }
        }
    }

    /**
     * Search active mass problems by area name from the NISA API.
     *
     * Creates global Magik procedure: nisa_search_massproblem_by_area(area_name)
     *
     * Calls POST /transaction/massproblem/active/cluster/search with the area name.
     * Response contains mass problems with sites that include "clusterid" (stella ID).
     *
     * @param proc      Magik proc object (always first parameter for @MagikProc)
     * @param areaName  Area name to search (Magik string, e.g. "Tangerang")
     * @return Raw JSON response string from NISA API (Magik string).
     */
    @MagikProc(@Name("nisa_search_massproblem_by_area"))
    public static Object searchMassProblemByArea(Object proc, Object areaName) {
        try {
            System.out.println("====== NISA SEARCH MASSPROBLEM BY AREA - START ======");

            String area = MagikInteropUtils.fromMagikString(areaName);
            System.out.println("  Area: " + area);

            if (area == null || area.trim().isEmpty()) {
                throw new IllegalArgumentException("area_name must not be empty");
            }

            NisaMassProblemClient client = new NisaMassProblemClient();
            String jsonResponse = client.searchMassProblemByArea(area.trim());

            System.out.println("  Response length: " + (jsonResponse != null ? jsonResponse.length() : 0));
            System.out.println("====== NISA SEARCH MASSPROBLEM BY AREA - END ======");

            return MagikInteropUtils.toMagikString(jsonResponse);

        } catch (Exception e) {
            System.err.println("ERROR in nisa_search_massproblem_by_area: " + e.getMessage());
            e.printStackTrace();

            String errorJson = "{\"success\":false,\"error\":" + jsonEscape(e.getMessage()) + "}";
            try {
                return MagikInteropUtils.toMagikString(errorJson);
            } catch (Exception e2) {
                return errorJson;
            }
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
