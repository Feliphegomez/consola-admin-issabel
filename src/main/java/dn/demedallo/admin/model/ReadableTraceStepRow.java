package dn.demedallo.admin.model;

/**
 * One trace step with a supervisor-friendly description.
 */
public final class ReadableTraceStepRow {

    public final int step;
    public final String datetime;
    public final String summary;
    public final String statusLabel;
    public final String retry;
    public final String trunk;
    public final String agent;
    public final String duration;

    public ReadableTraceStepRow(int step, String datetime, String summary, String statusLabel,
            String retry, String trunk, String agent, String duration) {
        this.step = step;
        this.datetime = datetime;
        this.summary = summary;
        this.statusLabel = statusLabel;
        this.retry = retry;
        this.trunk = trunk;
        this.agent = agent;
        this.duration = duration;
    }
}
