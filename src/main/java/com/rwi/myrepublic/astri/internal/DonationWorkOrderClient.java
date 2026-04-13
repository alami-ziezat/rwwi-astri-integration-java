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
 * Internal HTTP client for ASTRI Donation Work Order API.
 * NOT exposed to Magik - used only by AstriDonationWorkOrderProcs.
 * Uses Java 11+ HttpClient with HTTP POST and JSON body.
 *
 * Endpoint: POST /work-order/cluster/propose-donation/list/all/{limit}/{offset}
 *
 * Supported filter keys in JSON body:
 *   assigned_vendor_name  - e.g. "--not internal"
 *   target_cluster_code   - specific cluster code (optional, omit for full list fetch)
 *   latest_status_name    - e.g. "--not cancelled"
 *   permit_status         - e.g. "DONATION APPROVED"
 *   target_cluster_name   - cluster name filter (optional)
 */
public class DonationWorkOrderClient {

    private static final String DONATION_PATH = "/work-order/cluster/propose-donation/list/all";

    private final HttpClient client;
    private final AstriConfig config;
    private final String authHeader;

    public DonationWorkOrderClient() {
        this.config = AstriConfig.getInstance();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
            .build();

        String credentials = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Fetch donation work orders with pagination and a pre-built JSON body.
     *
     * @param limit    Maximum records to return (must be > 0)
     * @param offset   Pagination offset (must be >= 0)
     * @param jsonBody JSON string with filter fields (built by AstriDonationWorkOrderProcs)
     * @return XML string converted from API JSON response, for Magik simple_xml parsing
     */
    public String getDonationWorkOrders(int limit, int offset, String jsonBody)
            throws IOException, InterruptedException {

        String baseUrl = config.getApiBaseUrl();
        String url = baseUrl + DONATION_PATH + "/" + limit + "/" + offset;

        System.out.println("  [DonationWorkOrderClient] URL: " + url);
        System.out.println("  [DonationWorkOrderClient] Body: " + jsonBody);

        String body = (jsonBody != null && !jsonBody.isEmpty()) ? jsonBody : "{}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.getRequestTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        System.out.println("  [DonationWorkOrderClient] Sending HTTP POST request...");

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();

        System.out.println("  [DonationWorkOrderClient] Response status: " + response.statusCode());
        System.out.println("  [DonationWorkOrderClient] Response length: "
            + (jsonResponse != null ? jsonResponse.length() : 0));

        return convertJsonToXml(jsonResponse);
    }

    /**
     * Convert JSON response to XML for Magik simple_xml parsing.
     *
     * JSON:
     * {
     *   "success": true,
     *   "count": 5,
     *   "count_all": 5,
     *   "data": [
     *     {
     *       "number": "WO/ALL/2025/...",
     *       "target_cluster_code": "PLB006435",
     *       "target_cluster_final_donation_value": "150000",
     *       "target_cluster_subfeeder_donation_value": "75000",
     *       ...
     *     }
     *   ]
     * }
     *
     * XML:
     * <response>
     *   <success>true</success>
     *   <count>5</count>
     *   <count_all>5</count_all>
     *   <data>
     *     <workorder>
     *       <number>WO/ALL/2025/...</number>
     *       <target_cluster_code>PLB006435</target_cluster_code>
     *       <target_cluster_final_donation_value>150000</target_cluster_final_donation_value>
     *       <target_cluster_subfeeder_donation_value>75000</target_cluster_subfeeder_donation_value>
     *       ...
     *     </workorder>
     *   </data>
     * </response>
     */
    private String convertJsonToXml(String json) {
        try {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<response>\n");

            String success  = extractJsonValue(json, "success");
            String count    = extractJsonValue(json, "count");
            String countAll = extractJsonValue(json, "count_all");
            String error    = extractJsonValue(json, "error");

            if (success  != null && !success.isEmpty())  xml.append("  <success>").append(escapeXml(success)).append("</success>\n");
            if (count    != null && !count.isEmpty())    xml.append("  <count>").append(escapeXml(count)).append("</count>\n");
            if (countAll != null && !countAll.isEmpty()) xml.append("  <count_all>").append(escapeXml(countAll)).append("</count_all>\n");
            if (error    != null && !error.isEmpty())    xml.append("  <error>").append(escapeXml(error)).append("</error>\n");

            String dataArray = extractDataArray(json);
            if (dataArray != null && !dataArray.isEmpty()) {
                xml.append("  <data>\n");

                for (String woJson : splitJsonObjects(dataArray)) {
                    if (woJson.trim().isEmpty()) continue;
                    xml.append("    <workorder>\n");

                    // Common identification fields
                    appendXmlField(xml, woJson, "uuid", 6);
                    appendXmlField(xml, woJson, "number", 6);
                    appendXmlField(xml, woJson, "appointment_date", 6);
                    appendXmlField(xml, woJson, "assigned_vendor_name", 6);
                    appendXmlField(xml, woJson, "latest_status_label", 6);
                    appendXmlField(xml, woJson, "latest_status_name", 6);
                    appendXmlField(xml, woJson, "created_at", 6);

                    // Cluster / target fields
                    appendXmlField(xml, woJson, "target_cluster_code", 6);
                    appendXmlField(xml, woJson, "target_cluster_name", 6);

                    // Donation value fields — the key fields for this client
                    appendXmlField(xml, woJson, "target_cluster_final_donation_value", 6);
                    appendXmlField(xml, woJson, "target_cluster_subfeeder_donation_value", 6);

                    xml.append("    </workorder>\n");
                }

                xml.append("  </data>\n");
            }

            xml.append("</response>");
            return xml.toString();

        } catch (Exception e) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                 + "<response>\n"
                 + "  <success>false</success>\n"
                 + "  <error>" + escapeXml(e.getMessage()) + "</error>\n"
                 + "</response>";
        }
    }

    // -------------------------------------------------------------------------
    // JSON parsing helpers (mirrors WorkOrderClient pattern)
    // -------------------------------------------------------------------------

    private String extractJsonValue(String json, String fieldName) {
        Pattern quoted = Pattern.compile(
            "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher m = quoted.matcher(json);
        if (m.find()) return m.group(1).trim();

        Pattern unquoted = Pattern.compile(
            "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*([^,}\\s]+)");
        m = unquoted.matcher(json);
        if (m.find()) return m.group(1).trim();

        return null;
    }

    private String extractDataArray(String json) {
        int dataStart = json.indexOf("\"data\"");
        if (dataStart == -1) return null;

        int arrayStart = json.indexOf("[", dataStart);
        if (arrayStart == -1) return null;

        int level = 0, arrayEnd = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') level++;
            else if (c == ']') { level--; if (level == 0) { arrayEnd = i; break; } }
        }

        return arrayEnd == -1 ? null : json.substring(arrayStart + 1, arrayEnd);
    }

    private String[] splitJsonObjects(String jsonArray) {
        java.util.List<String> objects = new java.util.ArrayList<>();
        int level = 0, start = -1;
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '{') { if (level == 0) start = i; level++; }
            else if (c == '}') {
                level--;
                if (level == 0 && start != -1) { objects.add(jsonArray.substring(start + 1, i)); start = -1; }
            }
        }
        return objects.toArray(new String[0]);
    }

    private void appendXmlField(StringBuilder xml, String json, String fieldName, int indent) {
        String value = extractJsonValue(json, fieldName);
        if (value != null && !value.isEmpty()) {
            String spaces = " ".repeat(indent);
            xml.append(spaces).append("<").append(fieldName).append(">")
               .append(escapeXml(value))
               .append("</").append(fieldName).append(">\n");
        }
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    public void close() {
        // HttpClient does not need explicit closing in Java 11+
    }
}
