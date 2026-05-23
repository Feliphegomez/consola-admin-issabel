package dn.demedallo.admin.model;

/**
 * One step in {@code call_progress_log} for a call.
 */
public final class CallProgressStepRow {

    public final String datetime;
    public final String status;
    public final String retry;
    public final String uniqueid;
    public final String trunk;
    public final String duration;
    public final String agent;

    public CallProgressStepRow(String datetime, String status, String retry, String uniqueid,
            String trunk, String duration, String agent) {
        this.datetime = datetime;
        this.status = status;
        this.retry = retry;
        this.uniqueid = uniqueid;
        this.trunk = trunk;
        this.duration = duration;
        this.agent = agent;
    }
}
