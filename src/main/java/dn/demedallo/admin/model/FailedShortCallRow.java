package dn.demedallo.admin.model;

/**
 * Failed or short call summary row (outgoing {@code calls} or incoming {@code call_entry}).
 */
public final class FailedShortCallRow {

    public enum CallDirection {
        OUTGOING,
        INCOMING
    }

    public final CallDirection direction;
    public final int callId;
    public final String campaignName;
    public final String phone;
    public final String status;
    public final String statusLabel;
    public final String callDatetime;
    public final String duration;
    public final String retries;
    public final String failureCode;
    public final String failureCause;
    public final String uniqueid;
    public final String trunk;
    public final String agent;

    public FailedShortCallRow(CallDirection direction, int callId, String campaignName, String phone,
            String status, String statusLabel, String callDatetime, String duration, String retries,
            String failureCode, String failureCause, String uniqueid, String trunk, String agent) {
        this.direction = direction;
        this.callId = callId;
        this.campaignName = campaignName;
        this.phone = phone;
        this.status = status;
        this.statusLabel = statusLabel;
        this.callDatetime = callDatetime;
        this.duration = duration;
        this.retries = retries;
        this.failureCode = failureCode;
        this.failureCause = failureCause;
        this.uniqueid = uniqueid;
        this.trunk = trunk;
        this.agent = agent;
    }

    public String directionLabel() {
        return direction == CallDirection.OUTGOING ? "Saliente" : "Entrante";
    }
}
