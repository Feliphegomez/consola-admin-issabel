package dn.demedallo.admin.model;

import java.util.ArrayList;
import java.util.List;

/** Combined incoming campaigns panel state (rep_incoming_campaigns_panel). */
public final class IncomingPanelSnapshot {

    public final StatusCount statusCount = new StatusCount();
    public final DurationStats stats = new DurationStats();
    public final List<PanelActiveCallRow> activeCalls = new ArrayList<>();
    public final List<PanelPendingCallRow> pendingCalls = new ArrayList<>();
    public final List<PanelAgentRow> agents = new ArrayList<>();
    public boolean statsFromDatabase;
    public int eccpCampaignsPolled;
    public int eccpCampaignErrors;
    public boolean agentsFromGlobalFallback;

    public static final class StatusCount {
        public int total;
        public int onQueue;
        public int success;
        public int abandoned;
        public int finished;
        public int lostTrack;
    }

    public static final class DurationStats {
        public long totalSec;
        public long maxDurationSec;
    }

    public static final class PanelActiveCallRow {
        public String campaignName = "-";
        public String status = "-";
        public String phone = "-";
        public String trunk = "-";
        public String since = "-";
    }

    /** Outgoing calls not yet placed by the dialer (calls.status IS NULL). */
    public static final class PanelPendingCallRow {
        public String campaignName = "-";
        public String phone = "-";
        public String retries = "0";
        public String schedule = "-";
        public String agent = "-";
    }

    public static final class PanelAgentRow {
        public String campaignName = "-";
        public String agent = "-";
        public String status = "-";
        public String phone = "-";
        public String trunk = "-";
        public String since = "-";
        public String rawStatus = "offline";
    }
}
