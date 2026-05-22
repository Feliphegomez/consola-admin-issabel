package dn.demedallo.admin.model;

public final class AgentActivityEntry {
    public String agentChannel = "";
    public String agentName = "";
    public long loginTimeSec;
    public String lastSessionStart;
    public String lastSessionEnd;
    public String lastPauseStart;
    public String lastPauseEnd;

    /** Total answered calls today (incoming + outgoing) from callsummary. */
    public int totalCallsToday;
    /** Total talk seconds today from callsummary. */
    public long totalTalkSecToday;
    public int incomingCallsToday;
    public long incomingTalkSecToday;
    public int outgoingCallsToday;
    public long outgoingTalkSecToday;
}
