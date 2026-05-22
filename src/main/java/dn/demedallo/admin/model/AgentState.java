package dn.demedallo.admin.model;

/**
 * Parsed {@code getagentstatus_response}, aligned with Issabel
 * {@code paloSantoConsola::_traducirEstadoAgente()}.
 */
public final class AgentState {

    public String status = "";
    public String channel;
    public String extension;
    public String agentChannel;
    public boolean onHold;
    public String callChannel;
    public String remoteChannel;

    public Integer pauseId;
    public String pauseName;
    public String pauseStart;

    public String callStatus;
    public String callType;
    public Integer campaignId;
    public Integer callId;
    public String callNumber;
    public String queueNumber;
    public String dialStart;
    public String dialEnd;
    public String queueStart;
    public String linkStart;
    public String trunk;

    /** Predictive dial: call assigned but not yet bridged to agent ({@code waitedcallinfo}). */
    public String waitedCallStatus;
    public String waitedCallType;
    public Integer waitedCampaignId;
    public Integer waitedCallId;

    public boolean waitingForCampaignCall() {
        return waitedCallStatus != null && !waitedCallStatus.isEmpty();
    }

    public boolean hasCallInfo() {
        return callId != null && callId > 0 && callType != null && !callType.isEmpty();
    }

    public boolean onBreak() {
        return pauseId != null && pauseId > 0;
    }

    public String statusBarCssClass() {
        if (onBreak()) {
            return "state-break";
        }
        if (hasCallInfo()) {
            return "state-active";
        }
        if ("offline".equalsIgnoreCase(status)) {
            return "state-idle";
        }
        return "state-waiting";
    }
}
