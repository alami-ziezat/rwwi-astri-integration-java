package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.PriceListClient;

/**
 * ASTRI Price List API procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriPriceListProcs {

    /**
     * Get price list from ASTRI API.
     *
     * Creates global Magik procedure: astri_get_price_list(_optional filters)
     *
     * @param proc The Magik proc object (always first parameter for @MagikProc)
     * @param filters Optional Magik property_list with filter parameters:
     *                :project_type, :vendor_name, :equipment_name,
     *                :valid_date_start, :valid_date_end
     * @return String - JSON response from API containing:
     *         {"success": true/false, "count": N, "count_all": M, "data": [...], "error": "..."}
     */
    @MagikProc(@Name("astri_get_price_list"))
    public static Object getPriceList(Object proc, @Optional Object filters) {
        PriceListClient client = null;
        try {
            // Convert Magik property_list to filter string (if provided)
            String filterParams = "";
            if (filters != null) {
                filterParams = buildFilterParams(filters);
            }

            // Create client and make API call
            client = new PriceListClient();
            String jsonResponse = client.getPriceList(filterParams);

            // Convert Java String to Magik string
            Object magikString = MagikInteropUtils.toMagikString(jsonResponse);
            return magikString;

        } catch (Exception e) {
            // Return error as JSON string
            return "{\"success\":false,\"error\":\"" +
                   escapeJson(e.getMessage()) + "\"}";
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
     * Helper to build filter parameter string from Magik property_list.
     */
    private static String buildFilterParams(Object magikFilters) {
        // TODO: Implement proper Magik property_list iteration
        // For now, return empty - basic functionality works without filters
        return "";
    }

    /**
     * Escape special characters for JSON string.
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
