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
        public String campaign = "—";
        public String queue = "—";
        public String phone = "—";
        public String status = "—";
        public String trunk = "—";
        public String detail = "—";
    }
}
