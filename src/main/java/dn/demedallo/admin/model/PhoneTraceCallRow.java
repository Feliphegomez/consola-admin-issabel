package dn.demedallo.admin.model;

/**
 * Call row for phone trace search (inbound {@code call_entry} or outbound {@code calls}).
 */
public final class PhoneTraceCallRow {

    public enum CallDirection {
        OUTGOING,
        INCOMING
    }

    public final CallDirection direction;
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
    public final String failureReason;
    public final String uniqueid;
    public final String trunk;
    public final String agent;
    /** Populated for rows loaded from {@code asteriskcdrdb.cdr}. */
    public String cdrRecordingFile = "";
    public String cdrChannel = "";

    public PhoneTraceCallRow(CallDirection direction, int callId, int campaignId, String campaignName,
            String phone, String status, String statusLabel, String callDatetime, String duration,
            int retries, int maxRetries, String failureCode, String failureCause, String failureReason,
            String uniqueid, String trunk, String agent) {
        this.direction = direction;
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
        this.failureReason = failureReason;
        this.uniqueid = uniqueid;
        this.trunk = trunk;
        this.agent = agent;
    }

    public String directionLabel() {
        return direction == CallDirection.OUTGOING ? "Saliente" : "Entrante";
    }

    public boolean isCdrOnly() {
        return callId <= 0 && cdrRecordingFile != null && !cdrRecordingFile.isBlank();
    }

    public boolean canReschedule() {
        return direction == CallDirection.OUTGOING
                && ("Failure".equals(status) || "ShortCall".equals(status) || "NoAnswer".equals(status));
    }

    public boolean retriesExhausted() {
        return maxRetries > 0 && retries >= maxRetries;
    }

    public String retriesLabel() {
        return maxRetries > 0 ? retries + " / " + maxRetries : String.valueOf(retries);
    }

    public FailedShortCallRow toFailedShortRow() {
        return new FailedShortCallRow(
                direction == CallDirection.OUTGOING
                        ? FailedShortCallRow.CallDirection.OUTGOING
                        : FailedShortCallRow.CallDirection.INCOMING,
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
