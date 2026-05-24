package dn.demedallo.admin.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preview before bulk re-queue of Failure calls without Asterisk uniqueid. */
public final class BulkFailureRetryPreview {

    /** Rows matching Failure + empty uniqueid in range (before business rules). */
    public final int rawTotal;
    /** Rows that will be re-queued if confirmed. */
    public final int totalCalls;
    public final String datetimeStart;
    public final String datetimeEnd;
    public final Map<String, Integer> countByCampaign;

    public BulkFailureRetryPreview(int rawTotal, int totalCalls, String datetimeStart,
            String datetimeEnd, Map<String, Integer> countByCampaign) {
        this.rawTotal = rawTotal;
        this.totalCalls = totalCalls;
        this.datetimeStart = datetimeStart == null ? "" : datetimeStart;
        this.datetimeEnd = datetimeEnd == null ? "" : datetimeEnd;
        this.countByCampaign = countByCampaign == null ? Map.of() : Map.copyOf(countByCampaign);
    }

    public int omittedCount() {
        return Math.max(0, rawTotal - totalCalls);
    }

    public static BulkFailureRetryPreview empty(String datetimeStart, String datetimeEnd) {
        return new BulkFailureRetryPreview(0, 0, datetimeStart, datetimeEnd, Map.of());
    }

    public String campaignSummaryText() {
        if (countByCampaign.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : countByCampaign.entrySet()) {
            sb.append("\n  • ").append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    public static Map<String, Integer> fromPairs(List<CampaignCount> pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (pairs != null) {
            for (CampaignCount p : pairs) {
                m.put(p.campaignName(), p.count());
            }
        }
        return m;
    }

    public record CampaignCount(String campaignName, int count) {
    }
}
