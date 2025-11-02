package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.commons.interop.annotations.Optional;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.gesmallworld.magik.interop.MagikVectorUtils;
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
     * @return String - XML response converted from API JSON for easy parsing in Magik with simple_xml.
     *         XML structure:
     *         <response>
     *           <success>true/false</success>
     *           <count>N</count>
     *           <count_all>M</count_all>
     *           <data>
     *             <workorder>...</workorder>
     *             <workorder>...</workorder>
     *           </data>
     *         </response>
     */
    @MagikProc(@Name("astri_get_work_orders"))
    public static Object getWorkOrders(Object proc, Object limit, Object offset,
                                       @Optional Object filters) {
        WorkOrderClient client = null;
        try {
            System.out.println("====== ASTRI GET WORK ORDERS - START ======");

            // Convert Magik integers to Java int
            int limitInt = MagikInteropUtils.fromMagikInteger(limit);
            int offsetInt = MagikInteropUtils.fromMagikInteger(offset);

            System.out.println("Limit: " + limitInt +", Offset: " + offsetInt);

            // Convert Magik property_list to filter string (if provided)
            String filterParams = "";
            if (filters != null) {
                filterParams = buildFilterParams(filters);
            } else {
                System.out.println("No filters provided (null)");
            }

            // Create client and make API call
            client = new WorkOrderClient();
            System.out.println("Calling API with filter params: '" + filterParams + "'");
            String xmlResponse = client.getWorkOrders(limitInt, offsetInt, filterParams);

            System.out.println("API call successful, response length: " + (xmlResponse != null ? xmlResponse.length() : 0));

            // Convert Java String to Magik string
            Object magikString = MagikInteropUtils.toMagikString(xmlResponse);
            System.out.println("====== ASTRI GET WORK ORDERS - END ======");

            // Return Magik string - Magik will parse it with simple_xml
            return magikString;

        } catch (Exception e) {
            System.err.println("ERROR in getWorkOrders: " + e.getMessage());
            e.printStackTrace();

            // Return error as Magik string
            String errorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   "<response>\n" +
                   "  <success>false</success>\n" +
                   "  <error>" + escapeXml(e.getMessage()) + "</error>\n" +
                   "</response>";

            try {
                return MagikInteropUtils.toMagikString(errorXml);
            } catch (Exception e2) {
                System.err.println("Failed to convert error XML to Magik string: " + e2.getMessage());
                return errorXml; // Fallback to Java string
            }
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
     * @return String - XML response converted from API JSON for easy parsing in Magik with simple_xml
     */
    @MagikProc(@Name("astri_get_work_order"))
    public static Object getWorkOrder(Object proc, Object uuid) {
        WorkOrderClient client = null;
        try {
            System.out.println("====== ASTRI GET WORK ORDER (single) - START ======");

            String uuidStr = MagikInteropUtils.fromMagikString(uuid);
            System.out.println("UUID: " + uuidStr);

            client = new WorkOrderClient();
            String xmlResponse = client.getWorkOrder(uuidStr);

            System.out.println("API call successful, response length: " + (xmlResponse != null ? xmlResponse.length() : 0));

            // Convert Java String to Magik string
            System.out.println("Converting Java String to Magik String...");
            Object magikString = MagikInteropUtils.toMagikString(xmlResponse);
            System.out.println("Converted to: " + (magikString != null ? magikString.getClass().getName() : "null"));
            System.out.println("====== ASTRI GET WORK ORDER (single) - END ======");

            return magikString;

        } catch (Exception e) {
            System.err.println("ERROR in getWorkOrder: " + e.getMessage());
            e.printStackTrace();

            String errorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   "<response>\n" +
                   "  <success>false</success>\n" +
                   "  <error>" + escapeXml(e.getMessage()) + "</error>\n" +
                   "</response>";

            try {
                return MagikInteropUtils.toMagikString(errorXml);
            } catch (Exception e2) {
                System.err.println("Failed to convert error XML to Magik string: " + e2.getMessage());
                return errorXml; // Fallback to Java string
            }
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
     * Extracts filter values from Magik property_list and builds URL query string.
     * Supported filters:
     *   :category_name - Category filter
     *   :latest_status_name - Status filter
     *   :assigned_vendor_name - Vendor name filter
     *   :target_cluster_topology - Topology filter (AE/UG/OH)
     *   :target_cluster_code - Cluster code filter
     *
     * @param magikFilters Magik property_list object or unset
     * @return URL query string (e.g., "category_name=cluster_boq&status=in_progress")
     */
    private static String buildFilterParams(Object magikFilters) {
        System.out.println("  [buildFilterParams] START");

        if (magikFilters == null) {
            System.out.println("  [buildFilterParams] magikFilters is null, returning empty string");
            System.out.println("  [buildFilterParams] END");
            return "";
        }

        try {
            // Convert property_list to Object array
            // property_list structure: [null, :key1, value1, :key2, value2, ...]
            // - Index 0 is always null
            // - Odd indices (1, 3, 5...) are Symbol keys
            // - Even indices (2, 4, 6...) are values (Char16Vector, etc.)
            Object[] filterArray = MagikVectorUtils.getObjectArray(magikFilters);

            StringBuilder params = new StringBuilder();

            // Pattern: [null, :key1, value1, :key2, value2, ...]
            // Skip index 0 (always null)
            // Process pairs: (1, 2), (3, 4), (5, 6)...
            for (int i = 1; i < filterArray.length - 1; i += 2) {
                Object keyObj = filterArray[i];      // Odd index = key (Symbol)
                Object valueObj = filterArray[i + 1]; // Even index = value

                // Skip if key or value is null
                if (keyObj == null) {
                    continue;
                }

                if (valueObj == null || isUnset(valueObj)) {
                    continue;
                }

                // Extract key string (remove leading : from symbol)
                String keyStr = keyObj.toString();
                if (keyStr.startsWith(":")) {
                    keyStr = keyStr.substring(1);
                }

                // Extract value as string
                String valueStr = extractStringValue(valueObj);
                if (valueStr == null || valueStr.isEmpty()) {
                    continue;
                }

                // Append to query params
                if (params.length() > 0) {
                    params.append("&");
                }
                params.append(keyStr).append("=").append(java.net.URLEncoder.encode(valueStr, "UTF-8"));
            }

            String result = params.toString();
            System.out.println("  [buildFilterParams] Query Params: '" + result + "'");
            System.out.println("  [buildFilterParams] END");
            return result;

        } catch (Exception e) {
            System.err.println("  [buildFilterParams] ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("  [buildFilterParams] END");
            return "";
        }
    }

    /**
     * Convert Java string to Magik symbol.
     */
    private static Object convertToMagikSymbol(String str) throws Exception {
        System.out.println("      [convertToMagikSymbol] Converting: " + str);

        // Use MagikInteropUtils to convert to Magik symbol
        try {
            Class<?> utilsClass = Class.forName("com.gesmallworld.magik.interop.MagikInteropUtils");
            java.lang.reflect.Method method = utilsClass.getMethod("toMagikSymbol", String.class);
            Object result = method.invoke(null, str);
            System.out.println("      [convertToMagikSymbol] Created using MagikInteropUtils: " + result);
            return result;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            System.out.println("      [convertToMagikSymbol] MagikInteropUtils not found, trying fallback");
            // Fallback: try to create symbol directly
            Class<?> symbolClass = Class.forName("com.gesmallworld.magik.commons.runtime.MagikSymbol");
            java.lang.reflect.Method getMethod = symbolClass.getMethod("get", String.class);
            Object result = getMethod.invoke(null, str);
            System.out.println("      [convertToMagikSymbol] Created using MagikSymbol.get: " + result);
            return result;
        }
    }

    /**
     * Check if object is Magik unset.
     */
    private static boolean isUnset(Object obj) {
        if (obj == null) return true;

        String className = obj.getClass().getName();
        return className.contains("Unset") ||
               className.equals("com.gesmallworld.magik.commons.runtime.MagikUnset");
    }

    /**
     * Extract string value from Magik object.
     */
    private static String extractStringValue(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            // Try fromMagikString first
            String result = MagikInteropUtils.fromMagikString(obj);
            return result;
        } catch (Exception e) {
            System.out.println("      [extractStringValue] fromMagikString failed: " + e.getMessage());
            // Fallback to toString
            String result = obj.toString();
            System.out.println("      [extractStringValue] Using toString: '" + result + "'");
            return result;
        }
    }

    /**
     * Escape special characters for XML string.
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
