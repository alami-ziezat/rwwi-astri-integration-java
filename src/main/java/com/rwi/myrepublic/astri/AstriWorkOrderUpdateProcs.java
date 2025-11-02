package com.rwi.myrepublic.astri;

import com.gesmallworld.magik.commons.interop.annotations.MagikProc;
import com.gesmallworld.magik.commons.interop.annotations.Name;
import com.gesmallworld.magik.interop.MagikInteropUtils;
import com.rwi.myrepublic.astri.internal.WorkOrderUpdateClient;

/**
 * ASTRI Work Order Update procedures exposed to Magik.
 * Uses @MagikProc annotation to automatically create global Magik procedures.
 */
public class AstriWorkOrderUpdateProcs {

    /**
     * Update work order in ASTRI API.
     *
     * Creates global Magik procedure: astri_update_work_order(number, latest_status_name, detail)
     *
     * @param proc The Magik proc object
     * @param number Work order number (Magik string) - required
     * @param latestStatusName Latest status name (Magik string) - required
     * @param detail Detail (Magik string) - required
     * @return String - JSON response or empty string on error
     */
    @MagikProc(@Name("astri_update_work_order"))
    public static Object updateWorkOrder(Object proc,
                                         Object number,
                                         Object latestStatusName,
                                         Object detail) {
        System.out.println("=== DEBUG: astri_update_work_order called ===");

        WorkOrderUpdateClient client = null;
        try {
            String numberStr = MagikInteropUtils.fromMagikString(number);
            String statusNameStr = MagikInteropUtils.fromMagikString(latestStatusName);
            String detailStr = MagikInteropUtils.fromMagikString(detail);

            System.out.println("Parameters: number=" + numberStr +
                             ", status=" + statusNameStr + ", detail=" + detailStr);

            client = new WorkOrderUpdateClient();
            String jsonResponse = client.updateWorkOrder(numberStr, statusNameStr, detailStr);

            System.out.println("=== DEBUG: Work order updated successfully ===");

            // Convert Java String to Magik string
            Object magikString = MagikInteropUtils.toMagikString(jsonResponse);
            return magikString;

        } catch (Exception e) {
            System.err.println("=== DEBUG: ERROR in updateWorkOrder ===");
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
            System.out.println("=== DEBUG: astri_update_work_order completed ===");
        }
    }
}
