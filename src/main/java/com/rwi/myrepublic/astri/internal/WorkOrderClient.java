package com.rwi.myrepublic.astri.internal;

import com.rwi.myrepublic.astri.AstriConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal HTTP client for ASTRI Work Order API.
 * NOT exposed to Magik - used only by AstriWorkOrderProcs.
 * Uses Java 11+ HttpClient.
 */
public class WorkOrderClient {
    private HttpClient client;
    private AstriConfig config;
    private String authHeader;

    public WorkOrderClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        // Prepare Basic Authentication header
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Get work orders from API with pagination and optional filters.
     * Returns XML format for easier parsing in Magik with simple_xml.
     *
     * @param infrastructureType Infrastructure type: "cluster", "subfeeder", or "feeder"
     * @param limit Number of records to fetch
     * @param offset Starting offset
     * @param filterParams Filter query string (e.g. "category_name=cluster_boq&latest_status_name=in_progress")
     * @return XML string converted from API JSON response
     */
    public String getWorkOrders(String infrastructureType, int limit, int offset, String filterParams) throws IOException, InterruptedException {

        String baseUrl = config.getApiBaseUrl();
        System.out.println("  [WorkOrderClient] Base URL: " + baseUrl);
        System.out.println("  [WorkOrderClient] Infrastructure Type: " + infrastructureType);

        // Build endpoint based on infrastructure type
        // cluster:   /work-order/cluster/boq/simple/list/all/{limit}/{offset}
        // subfeeder: /work-order/subfeeder/boq/simple/list/all/{limit}/{offset}
        // feeder:    /work-order/feeder/boq/simple/list/all/{limit}/{offset}
        String path = "/work-order/" + infrastructureType + "/boq/simple/list/all/" + limit + "/" + offset;

        // Build URL with optional filter query parameters
        String url = baseUrl + path;
        if (filterParams != null && !filterParams.isEmpty()) {
            url += "?" + filterParams;
            System.out.println("  [WorkOrderClient] Added filter params: " + filterParams);
        } else {
            System.out.println("  [WorkOrderClient] No filter params to add");
        }

        System.out.println("  [WorkOrderClient] URL: " + url);

        // Build POST request with empty body (API uses POST method)
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

        System.out.println("  [WorkOrderClient] Sending HTTP POST request...");

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();

        System.out.println("  [WorkOrderClient] Response status: " + response.statusCode());
        System.out.println("  [WorkOrderClient] Response body length: " + (jsonResponse != null ? jsonResponse.length() : 0));

        // Convert JSON to XML for Magik simple_xml parsing
        System.out.println("  [WorkOrderClient] Converting JSON to XML...");
        String xmlResult = convertJsonToXml(jsonResponse, infrastructureType);
        System.out.println("  [WorkOrderClient] XML length: " + (xmlResult != null ? xmlResult.length() : 0));
        System.out.println("  [WorkOrderClient.getWorkOrders] END");

        return xmlResult;
    }

    /**
     * Get single work order by UUID.
     * Returns XML format for easier parsing in Magik.
     *
     * @param uuid Work order UUID
     * @return XML string converted from API JSON response
     */
    public String getWorkOrder(String uuid) throws IOException, InterruptedException {
        String baseUrl = config.getApiBaseUrl();
        String path = "/api/work-order/" + uuid;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", authHeader)
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();

        // Convert JSON to XML for Magik simple_xml parsing
        // Default to "cluster" for single work order retrieval
        return convertJsonToXml(jsonResponse, "cluster");
    }

    /**
     * Convert JSON response to XML format for Magik simple_xml parsing.
     * Overload for backward compatibility - defaults to "cluster" infrastructure type.
     *
     * @param json JSON response from API
     * @return XML string
     */
    private String convertJsonToXml(String json) {
        return convertJsonToXml(json, "cluster");
    }

    /**
     * Convert JSON response to XML format for Magik simple_xml parsing.
     *
     * Converts JSON structure:
     * {
     *   "success": true,
     *   "count": 50,
     *   "count_all": 127,
     *   "data": [
     *     {
     *       "uuid": "d35ed679-0b5e-4c33-953c-2740b5cc7772",
     *       "number": "WO/ALL/2025/DOCU/16/54556",
     *       ...
     *     }
     *   ]
     * }
     *
     * To XML:
     * <response>
     *   <success>true</success>
     *   <count>50</count>
     *   <count_all>127</count_all>
     *   <data>
     *     <workorder>
     *       <uuid>d35ed679-0b5e-4c33-953c-2740b5cc7772</uuid>
     *       <number>WO/ALL/2025/DOCU/16/54556</number>
     *       ...
     *     </workorder>
     *   </data>
     * </response>
     *
     * @param json JSON response from API
     * @param infrastructureType Infrastructure type (cluster, subfeeder, feeder)
     */
    private String convertJsonToXml(String json, String infrastructureType) {
        try {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<response>\n");

            // Extract top-level fields
            String success = extractJsonValue(json, "success");
            String count = extractJsonValue(json, "count");
            String countAll = extractJsonValue(json, "count_all");
            String error = extractJsonValue(json, "error");

            if (success != null && !success.isEmpty()) {
                xml.append("  <success>").append(escapeXml(success)).append("</success>\n");
            }
            if (count != null && !count.isEmpty()) {
                xml.append("  <count>").append(escapeXml(count)).append("</count>\n");
            }
            if (countAll != null && !countAll.isEmpty()) {
                xml.append("  <count_all>").append(escapeXml(countAll)).append("</count_all>\n");
            }
            if (error != null && !error.isEmpty()) {
                xml.append("  <error>").append(escapeXml(error)).append("</error>\n");
            }

            // Extract data array and convert work orders
            String dataArray = extractDataArray(json);
            if (dataArray != null && !dataArray.isEmpty()) {
                xml.append("  <data>\n");

                // Split into individual work order objects
                String[] workOrders = splitJsonObjects(dataArray);
                for (String woJson : workOrders) {
                    if (woJson.trim().isEmpty()) continue;

                    xml.append("    <workorder>\n");

                    // Extract common fields
                    appendXmlField(xml, woJson, "uuid", 6);
                    appendXmlField(xml, woJson, "number", 6);
                    appendXmlField(xml, woJson, "category_label", 6);
                    appendXmlField(xml, woJson, "category_name", 6);
                    appendXmlField(xml, woJson, "latest_status_name", 6);
                    appendXmlField(xml, woJson, "assigned_vendor_label", 6);
                    appendXmlField(xml, woJson, "assigned_vendor_name", 6);
                    appendXmlField(xml, woJson, "created_at", 6);
                    appendXmlField(xml, woJson, "updated_at", 6);
                    appendXmlField(xml, woJson, "kmz_uuid", 6);

                    // Extract infrastructure-specific fields based on type
                    // cluster:   target_cluster_code, target_cluster_name, target_cluster_topology
                    // subfeeder: target_subfeeder_code, target_subfeeder_name, target_subfeeder_topology
                    // feeder:    target_feeder_code, target_feeder_name, target_feeder_topology
                    String targetPrefix = "target_" + infrastructureType;
                    appendXmlField(xml, woJson, targetPrefix + "_code", 6);
                    appendXmlField(xml, woJson, targetPrefix + "_name", 6);
                    appendXmlField(xml, woJson, targetPrefix + "_topology", 6);

                    xml.append("    </workorder>\n");
                }

                xml.append("  </data>\n");
            }

            xml.append("</response>");
            return xml.toString();

        } catch (Exception e) {
            // Return error as XML
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   "<response>\n" +
                   "  <success>false</success>\n" +
                   "  <error>" + escapeXml(e.getMessage()) + "</error>\n" +
                   "</response>";
        }
    }

    /**
     * Extract a JSON field value.
     */
    private String extractJsonValue(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"?([^,}\"\\n]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extract the data array from JSON response.
     */
    private String extractDataArray(String json) {
        int dataStart = json.indexOf("\"data\"");
        if (dataStart == -1) return null;

        int arrayStart = json.indexOf("[", dataStart);
        if (arrayStart == -1) return null;

        int level = 0;
        int arrayEnd = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') level++;
            else if (c == ']') {
                level--;
                if (level == 0) {
                    arrayEnd = i;
                    break;
                }
            }
        }

        if (arrayEnd == -1) return null;
        return json.substring(arrayStart + 1, arrayEnd);
    }

    /**
     * Split JSON array into individual objects.
     */
    private String[] splitJsonObjects(String jsonArray) {
        java.util.List<String> objects = new java.util.ArrayList<>();
        int level = 0;
        int start = -1;

        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '{') {
                if (level == 0) start = i;
                level++;
            } else if (c == '}') {
                level--;
                if (level == 0 && start != -1) {
                    objects.add(jsonArray.substring(start + 1, i));
                    start = -1;
                }
            }
        }

        return objects.toArray(new String[0]);
    }

    /**
     * Append XML field from JSON object.
     */
    private void appendXmlField(StringBuilder xml, String json, String fieldName, int indent) {
        String value = extractJsonValue(json, fieldName);
        if (value != null && !value.isEmpty()) {
            String spaces = " ".repeat(indent);
            xml.append(spaces).append("<").append(fieldName).append(">")
               .append(escapeXml(value))
               .append("</").append(fieldName).append(">\n");
        }
    }

    /**
     * Escape XML special characters.
     */
    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    /**
     * Close and cleanup resources.
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
