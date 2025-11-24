package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.BoqClient;

/**
 * ASTRI BOQ (Bill of Quantities) DRM procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriBoqProcs {

    /**
     * Add BOQ DRM Cluster to ASTRI API.
     *
     * Creates global Magik procedure: astri_add_boq_drm_cluster(cluster_code, vendor_name,
     *                                   subcont_vendor_name, equipment_name, description,
     *                                   quantity_material, quantity_service, remarks,
     *                                   phase, area, area_plant_code, override_price_material,
     *                                   override_price_service)
     *
     * @param proc The Magik proc object
     * @param clusterCode Cluster code (Magik string)
     * @param vendorName Vendor name (Magik string)
     * @param subcontVendorName Subcontractor vendor name (Magik string)
     * @param equipmentName Equipment name (Magik string)
     * @param description Description (Magik string)
     * @param quantityMaterial Quantity of material (Magik number - integer or float)
     * @param quantityService Quantity of service (Magik number - integer or float)
     * @param remarks Remarks (Magik string)
     * @param phase Phase (Magik string)
     * @param area Area (Magik string)
     * @param areaPlantCode Area plant code (Magik string)
     * @param overridePriceMaterial Override price for material (Magik number - integer or float)
     * @param overridePriceService Override price for service (Magik number - integer or float)
     * @return String - JSON response with {"success":true/false, "error":"..."} format
     */
    @MagikProc(@Name("astri_add_boq_drm_cluster"))
    public static Object addBoqDrmCluster(Object proc,
                                          Object clusterCode,
                                          Object vendorName,
                                          Object subcontVendorName,
                                          Object equipmentName,
                                          Object description,
                                          Object quantityMaterial,
                                          Object quantityService,
                                          Object remarks,
                                          Object phase,
                                          Object area,
                                          Object areaPlantCode,
                                          Object overridePriceMaterial,
                                          Object overridePriceService) {
        System.out.println("=== DEBUG: astri_add_boq_drm_cluster called ===");
        /*System.out.println("Parameters received: "+
              "clusterCode="+ clusterCode +
              ", vendorName="+ vendorName +
              ", subcontVendorName="+ subcontVendorName +
              ", equipmentName="+ equipmentName +
              ", description="+ description +
              ", quantityMaterial="+ quantityMaterial +
              ", quantityService="+ quantityService +
              ", remarks="+ remarks +
              ", phase="+ phase +
              ", area="+ area +
              ", areaPlantCode="+ areaPlantCode +
              ", overridePriceMaterial="+ overridePriceMaterial +
              ", overridePriceService="+ overridePriceService);
        */
        BoqClient client = null;
        try {
            // Handle _unset (null) values from Magik
            String clusterCodeStr = (clusterCode == null) ? null : MagikInteropUtils.fromMagikString(clusterCode);
            String vendorNameStr = (vendorName == null) ? null : MagikInteropUtils.fromMagikString(vendorName);
            String subcontVendorNameStr = (subcontVendorName == null) ? null : MagikInteropUtils.fromMagikString(subcontVendorName);
            String equipmentNameStr = (equipmentName == null) ? null : MagikInteropUtils.fromMagikString(equipmentName);
            String descriptionStr = (description == null) ? null : MagikInteropUtils.fromMagikString(description);

            // Convert numeric values - handle both integer and float/double from Magik
            // Round to 2 decimal places and convert to Double
            Double quantityMaterialDbl = convertMagikNumberToDouble(quantityMaterial);
            Double quantityServiceDbl = convertMagikNumberToDouble(quantityService);
            Double overridePriceMaterialDbl = convertMagikNumberToDouble(overridePriceMaterial);
            Double overridePriceServiceDbl = convertMagikNumberToDouble(overridePriceService);

            String remarksStr = (remarks == null) ? null : MagikInteropUtils.fromMagikString(remarks);
            String phaseStr = (phase == null) ? null : MagikInteropUtils.fromMagikString(phase);
            String areaStr = (area == null) ? null : MagikInteropUtils.fromMagikString(area);
            String areaPlantCodeStr = (areaPlantCode == null) ? null : MagikInteropUtils.fromMagikString(areaPlantCode);

            System.out.println("Parameters: cluster_code=" + clusterCodeStr +
                             ", vendor=" + vendorNameStr + ", equipment=" + equipmentNameStr);
            System.out.println("Quantities: material=" + quantityMaterialDbl + ", service=" + quantityServiceDbl);

            client = new BoqClient();
            String jsonResponse = client.addBoqDrmCluster(
                clusterCodeStr, vendorNameStr, subcontVendorNameStr, equipmentNameStr,
                descriptionStr, quantityMaterialDbl, quantityServiceDbl, remarksStr,
                phaseStr, areaStr, areaPlantCodeStr, overridePriceMaterialDbl, overridePriceServiceDbl
            );

            System.out.println("=== DEBUG: BOQ DRM Cluster added successfully ===");

            // Convert Java String to Magik string
            Object magikString = MagikInteropUtils.toMagikString(jsonResponse);
            return magikString;

        } catch (Exception e) {
            System.err.println("=== DEBUG: ERROR in addBoqDrmCluster ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();

            // Return error as JSON string with proper format
            String errorJson = "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            return MagikInteropUtils.toMagikString(errorJson);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("Error closing client: " + e.getMessage());
                }
            }
            System.out.println("=== DEBUG: astri_add_boq_drm_cluster completed ===");
        }
    }

    /**
     * Convert Magik number (integer or float) to Double rounded to 2 decimal places.
     *
     * @param magikNumber Magik number object (can be integer or float/double)
     * @return Double rounded to 2 decimal places, or null if input is null
     */
    private static Double convertMagikNumberToDouble(Object magikNumber) {
        if (magikNumber == null) {
            return null;
        }

        try {
            // Try to get as float first (works for both integer and float from Magik)
            Float floatValue = MagikInteropUtils.fromMagikFloat(magikNumber);
            Double value = floatValue.doubleValue();

            // Round to 2 decimal places
            return Math.round(value * 100.0) / 100.0;
        } catch (Exception e) {
            // If fromMagikFloat fails, try as integer
            try {
                Integer intValue = MagikInteropUtils.fromMagikInteger(magikNumber);
                return intValue.doubleValue();
            } catch (Exception e2) {
                System.err.println("Warning: Could not convert Magik number to Double: " + e2.getMessage());
                return null;
            }
        }
    }

    /**
     * Escape special characters in JSON strings.
     *
     * @param str String to escape
     * @return Escaped string safe for JSON
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
