package dn.demedallo.admin.model;

import java.util.ArrayList;
import java.util.List;

/** Live dashboard state (ECCP + optional MySQL pending calls). */
public final class DashboardSnapshot {

    public int onlineCount;
    public int offlineCount;
    public int onCallCount;
    public int ringingCount;
    public int pausedCount;
    public final List<AgentMonitorRow> agents = new ArrayList<>();
    public final List<QueueMonitorRow> queues = new ArrayList<>();
    public final List<DashboardCallRow> incomingCalls = new ArrayList<>();
    public final List<DashboardCallRow> outgoingCalls = new ArrayList<>();
    public final List<DashboardCallRow> pendingCalls = new ArrayList<>();

    public static final class DashboardCallRow {
        /** {@code calls.id} for pending dialer rows (0 if unknown). */
        public int pendingCallId;
        public String queue = "—";
        public String phone = "—";
        public String status = "—";
        public String callType = "—";
        public String callId = "—";
        public String trunk = "—";
        public String campaign = "—";
        /** Agent channel or extra info (pending dialer rows). */
        public String detail = "—";
    }
}
