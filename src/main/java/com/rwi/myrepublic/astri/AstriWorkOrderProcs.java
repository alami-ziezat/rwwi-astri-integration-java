package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.WorkOrderClient;

/**
 * ASTRI Work Order API procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriWorkOrderProcs {

    /**
     * Get work orders from ASTRI API.
     *
     * Creates global Magik procedure: astri_get_work_orders(limit, offset, _optional filters)
     *
     * @param proc The Magik proc object (always first parameter for @MagikProc)
     * @param limit Number of records to fetch (Magik integer)
     * @param offset Starting offset (Magik integer)
     * @param filters Optional Magik property_list with filter parameters:
     *                :category_name, :latest_status_name, :assigned_vendor_name,
     *                :target_cluster_topology, :target_cluster_code
     * @return String - JSON response from API containing:
     *         {"success": true/false, "count": N, "count_all": M, "data": [...], "error": "..."}
     */
    @MagikProc(@Name("astri_get_work_orders"))
    public static Object getWorkOrders(Object proc, Object limit, Object offset,
                                       @Optional Object filters) {
        WorkOrderClient client = null;
        try {
            // Convert Magik integers to Java int
            int limitInt = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);

            // Convert Magik property_list to filter string (if provided)
            String filterParams = "";
            if (filters != null) {
                filterParams = buildFilterParams(filters);
            }

            // Create client and make API call
            client = new WorkOrderClient();
            String jsonResponse = client.getWorkOrders(limitInt, offsetInt, filterParams);

            // Return JSON string - Magik will parse it
            return jsonResponse;

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
     * Get single work order by UUID.
     *
     * Creates global Magik procedure: astri_get_work_order(uuid)
     *
     * @param proc The Magik proc object
     * @param uuid Work order UUID (Magik string)
     * @return String - JSON response with work order details
     */
    @MagikProc(@Name("astri_get_work_order"))
    public static Object getWorkOrder(Object proc, Object uuid) {
        WorkOrderClient client = null;
        try {
            String uuidStr = MagikInteropUtils.fromMagikString(uuid);

            client = new WorkOrderClient();
            String jsonResponse = client.getWorkOrder(uuidStr);

            return jsonResponse;

        } catch (Exception e) {
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
     *
     * Note: This is a simplified implementation. In production, you would
     * iterate over the Magik property_list using reflection or Magik interop APIs.
     * For now, this returns empty string - filters will be implemented after testing.
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
