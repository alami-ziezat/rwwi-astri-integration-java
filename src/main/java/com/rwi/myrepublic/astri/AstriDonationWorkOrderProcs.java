package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.gesmallworld.magik.interop.MagikVectorUtils;
import com.rwi.myrepublic.astri.internal.DonationWorkOrderClient;

/**
 * ASTRI Donation Work Order API procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 *
 * Creates: astri_get_donation_work_orders(limit, offset, _optional filters)
 *
 * Calls POST /work-order/cluster/propose-donation/list/all/{limit}/{offset}
 * with a JSON body built from the optional filters property_list.
 *
 * Supported filter keys (Magik symbols → JSON field names):
 *   :assigned_vendor_name  → "assigned_vendor_name"  (e.g. "--not internal")
 *   :target_cluster_code   → "target_cluster_code"
 *   :latest_status_name    → "latest_status_name"    (e.g. "--not cancelled")
 *   :permit_status         → "permit_status"          (e.g. "DONATION APPROVED")
 *   :target_cluster_name   → "target_cluster_name"
 */
public class AstriDonationWorkOrderProcs {

    /**
     * Get donation work orders from ASTRI API.
     *
     * Creates global Magik procedure:
     *   astri_get_donation_work_orders(limit, offset, _optional filters)
     *
     * @param proc    The Magik proc object (always first parameter for @MagikProc)
     * @param limit   Max records to return (Magik integer)
     * @param offset  Pagination offset, 0-based (Magik integer)
     * @param filters Optional Magik property_list with filter keys:
     *                  :assigned_vendor_name, :target_cluster_code,
     *                  :latest_status_name, :permit_status, :target_cluster_name
     * @return Magik string — XML response for parsing with simple_xml
     */
    @MagikProc(@Name("astri_get_donation_work_orders"))
    public static Object getDonationWorkOrders(Object proc, Object limit, Object offset,
                                               @Optional Object filters) {
        DonationWorkOrderClient client = null;
        try {
            System.out.println("====== ASTRI GET DONATION WORK ORDERS - START ======");

            int limitInt  = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);
            System.out.println("Limit: " + limitInt + ", Offset: " + offsetInt);

            String jsonBody = buildJsonBody(filters);
            System.out.println("JSON body: " + jsonBody);

            client = new DonationWorkOrderClient();
            String xmlResponse = client.getDonationWorkOrders(limitInt, offsetInt, jsonBody);

            System.out.println("API call successful, response length: "
                + (xmlResponse != null ? xmlResponse.length() : 0));
            System.out.println("====== ASTRI GET DONATION WORK ORDERS - END ======");

            return MagikInteropUtils.toMagikString(xmlResponse);

        } catch (Exception e) {
            System.err.println("ERROR in getDonationWorkOrders: " + e.getMessage());
            e.printStackTrace();

            String errorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<response>\n"
                + "  <success>false</success>\n"
                + "  <error>" + escapeXml(e.getMessage()) + "</error>\n"
                + "</response>";

            try {
                return MagikInteropUtils.toMagikString(errorXml);
            } catch (Exception e2) {
                return errorXml;
            }
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Build JSON body string from Magik property_list filters.
     *
     * Converts Magik property_list:
     *   property_list.new_with(:assigned_vendor_name, "--not internal", ...)
     * into JSON string:
     *   {"assigned_vendor_name":"--not internal",...}
     *
     * Only includes known filter keys; skips null/unset values.
     */
    private static String buildJsonBody(Object magikFilters) {
        // Known donation API filter keys
        String[] KNOWN_KEYS = {
            "assigned_vendor_name",
            "target_cluster_code",
            "target_subfeeder_code",
            "latest_status_name",
            "permit_status",
            "target_cluster_name"
        };

        if (magikFilters == null || isUnset(magikFilters)) {
            System.out.println("  [buildJsonBody] No filters, returning empty body {}");
            return "{}";
        }

        try {
            // property_list structure: [null, :key1, value1, :key2, value2, ...]
            Object[] filterArray = MagikVectorUtils.getObjectArray(magikFilters);

            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            for (int i = 1; i < filterArray.length - 1; i += 2) {
                Object keyObj   = filterArray[i];
                Object valueObj = filterArray[i + 1];

                if (keyObj == null || valueObj == null || isUnset(valueObj)) continue;

                String keyStr = keyObj.toString();
                if (keyStr.startsWith(":")) keyStr = keyStr.substring(1);

                // Only include known donation filter keys
                if (!isKnownKey(keyStr, KNOWN_KEYS)) continue;

                String valueStr = extractStringValue(valueObj);
                if (valueStr == null || valueStr.isEmpty()) continue;

                if (!first) json.append(",");
                json.append("\"").append(escapeJson(keyStr)).append("\"")
                    .append(":\"").append(escapeJson(valueStr)).append("\"");
                first = false;

                System.out.println("  [buildJsonBody] Added: " + keyStr + " = " + valueStr);
            }

            json.append("}");
            return json.toString();

        } catch (Exception e) {
            System.err.println("  [buildJsonBody] ERROR: " + e.getMessage());
            return "{}";
        }
    }

    private static boolean isKnownKey(String key, String[] knownKeys) {
        for (String k : knownKeys) {
            if (k.equals(key)) return true;
        }
        return false;
    }

    private static boolean isUnset(Object obj) {
        if (obj == null) return true;
        String className = obj.getClass().getName();
        return className.contains("Unset") ||
               className.equals("com.gesmallworld.magik.commons.runtime.MagikUnset");
    }

    private static String extractStringValue(Object obj) {
        if (obj == null) return null;
        try {
            return MagikInteropUtils.fromMagikString(obj);
        } catch (Exception e) {
            return obj.toString();
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

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
