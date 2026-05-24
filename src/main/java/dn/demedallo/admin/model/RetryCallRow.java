package dn.demedallo.admin.model;

/**
 * Outgoing campaign call eligible for supervisor retry ({@code calls} table).
 */
public final class RetryCallRow {

    public final int callId;
    public final int campaignId;
    public final String campaignName;
    public final String phone;
    public final String status;
    public final String statusLabel;
    public final String callDatetime;
    public final String duration;
    public final int retries;
    public final int maxRetries;
    public final String failureCode;
    public final String failureCause;
    public final String uniqueid;
    public final String trunk;
    public final String agent;

    public RetryCallRow(int callId, int campaignId, String campaignName, String phone,
            String status, String statusLabel, String callDatetime, String duration,
            int retries, int maxRetries, String failureCode, String failureCause,
            String uniqueid, String trunk, String agent) {
        this.callId = callId;
        this.campaignId = campaignId;
        this.campaignName = campaignName;
        this.phone = phone;
        this.status = status;
        this.statusLabel = statusLabel;
        this.callDatetime = callDatetime;
        this.duration = duration;
        this.retries = retries;
        this.maxRetries = maxRetries;
        this.failureCode = failureCode;
        this.failureCause = failureCause;
        this.uniqueid = uniqueid;
        this.trunk = trunk;
        this.agent = agent;
    }

    /** True when Asterisk never assigned a channel uniqueid (NULL, empty, unknown). */
    public static boolean hasUnknownUniqueid(String uniqueid) {
        if (uniqueid == null || uniqueid.isBlank() || "-".equals(uniqueid.trim())) {
            return true;
        }
        String u = uniqueid.trim().toLowerCase();
        return "unknown".equals(u) || "<unknown>".equals(u);
    }

    public boolean hasUnknownUniqueid() {
        return hasUnknownUniqueid(uniqueid);
    }

    public boolean retriesExhausted() {
        return maxRetries > 0 && retries >= maxRetries;
    }

    public String retriesLabel() {
        return retries + " / " + maxRetries;
    }

    public FailedShortCallRow toFailedShortRow() {
        return new FailedShortCallRow(
                FailedShortCallRow.CallDirection.OUTGOING,
                callId,
                campaignName,
                phone,
                status,
                statusLabel,
                callDatetime,
                duration,
                String.valueOf(retries),
                failureCode,
                failureCause,
                uniqueid,
                trunk,
                agent);
    }
}
