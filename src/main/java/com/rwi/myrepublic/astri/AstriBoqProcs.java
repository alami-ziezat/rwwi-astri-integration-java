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
     * @param quantityMaterial Quantity of material (Magik integer)
     * @param quantityService Quantity of service (Magik integer)
     * @param remarks Remarks (Magik string)
     * @param phase Phase (Magik string)
     * @param area Area (Magik string)
     * @param areaPlantCode Area plant code (Magik string)
     * @param overridePriceMaterial Override price for material (Magik integer)
     * @param overridePriceService Override price for service (Magik integer)
     * @return String - JSON response or empty string on error
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

        BoqClient client = null;
        try {
            String clusterCodeStr = MagikInteropUtils.fromMagikString(clusterCode);
            String vendorNameStr = MagikInteropUtils.fromMagikString(vendorName);
            String subcontVendorNameStr = MagikInteropUtils.fromMagikString(subcontVendorName);
            String equipmentNameStr = MagikInteropUtils.fromMagikString(equipmentName);
            String descriptionStr = MagikInteropUtils.fromMagikString(description);
            int quantityMaterialInt = MagikInteropUtils.fromMagikInteger(quantityMaterial);
            int quantityServiceInt = MagikInteropUtils.fromMagikInteger(quantityService);
            String remarksStr = MagikInteropUtils.fromMagikString(remarks);
            String phaseStr = MagikInteropUtils.fromMagikString(phase);
            String areaStr = MagikInteropUtils.fromMagikString(area);
            String areaPlantCodeStr = MagikInteropUtils.fromMagikString(areaPlantCode);
            int overridePriceMaterialInt = MagikInteropUtils.fromMagikInteger(overridePriceMaterial);
            int overridePriceServiceInt = MagikInteropUtils.fromMagikInteger(overridePriceService);

            System.out.println("Parameters: cluster_code=" + clusterCodeStr +
                             ", vendor=" + vendorNameStr + ", equipment=" + equipmentNameStr);

            client = new BoqClient();
            String jsonResponse = client.addBoqDrmCluster(
                clusterCodeStr, vendorNameStr, subcontVendorNameStr, equipmentNameStr,
                descriptionStr, quantityMaterialInt, quantityServiceInt, remarksStr,
                phaseStr, areaStr, areaPlantCodeStr, overridePriceMaterialInt, overridePriceServiceInt
            );

            System.out.println("=== DEBUG: BOQ DRM Cluster added successfully ===");

            // Convert Java String to Magik string
            Object magikString = MagikInteropUtils.toMagikString(jsonResponse);
            return magikString;

        } catch (Exception e) {
            System.err.println("=== DEBUG: ERROR in addBoqDrmCluster ===");
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
            System.out.println("=== DEBUG: astri_add_boq_drm_cluster completed ===");
        }
    }
}
