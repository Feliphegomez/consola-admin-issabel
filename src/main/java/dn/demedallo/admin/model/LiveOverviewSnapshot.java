package dn.demedallo.admin.model;

import dn.demedallo.admin.model.DashboardSnapshot.DashboardCallRow;
import dn.demedallo.admin.model.ServiceHealthModels.DiskVolume;
import dn.demedallo.admin.model.ServiceHealthModels.ServerMetrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregated live operational view (ECCP + optional SSH health). */
public final class LiveOverviewSnapshot {

    public int agentsOnline;
    public int agentsOnCall;
    public int agentsPaused;
    public int agentsOffline;
    public int incomingActive;
    public int outgoingActive;
    public int pendingDialer;
    public int waitingInQueues;
    public int activeQueues;
    public int asteriskChannels = -1;
    public String asteriskChannelsDetail = "";
    public String healthSummary = "";
    public boolean sshAvailable;
    public ServerMetrics server;
    public final List<DiskVolume> disks = new ArrayList<>();
    public final Map<String, Integer> trunkUsage = new LinkedHashMap<>();
    public final List<DashboardCallRow> recentIncoming = new ArrayList<>();
    public final List<DashboardCallRow> recentOutgoing = new ArrayList<>();
    public long healthErrors;
    public long healthWarnings;
}
