package dn.demedallo.admin.service;

import dn.demedallo.admin.model.DashboardSnapshot;
import dn.demedallo.admin.model.LiveOverviewSnapshot;
import dn.demedallo.admin.model.QueueMonitorRow;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceCheck;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceHealthSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminDbSettings;

import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds {@link LiveOverviewSnapshot} from ECCP dashboard data and optional SSH health.
 */
public final class LiveOverviewService {

    private static final Pattern ACTIVE_CHANNELS =
            Pattern.compile("(\\d+)\\s+active channel", Pattern.CASE_INSENSITIVE);

    private final AgentMonitorService monitorService;
    private final DashboardService dashboardService;
    private final ServiceHealthMonitorService healthService;

    public LiveOverviewService(AdminEccpClient client, AdminDbSettings dbSettings, String eccpHost) {
        this.monitorService = new AgentMonitorService(client);
        this.dashboardService = new DashboardService(client, dbSettings);
        this.healthService = new ServiceHealthMonitorService(client, dbSettings, eccpHost);
    }

    public LiveOverviewSnapshot load(boolean includeHealth) throws Exception {
        LiveOverviewSnapshot snap = loadEccp();
        if (includeHealth) {
            enrichWithHealth(snap);
        }
        return snap;
    }

    /** ECCP-only snapshot (agents, queues, live calls). */
    public LiveOverviewSnapshot loadEccp() throws Exception {
        DashboardSnapshot dash = dashboardService.loadFromMonitorSnapshot(monitorService.fetchSnapshot());
        LiveOverviewSnapshot snap = new LiveOverviewSnapshot();
        snap.agentsOnline = dash.onlineCount;
        snap.agentsOnCall = dash.onCallCount;
        snap.agentsPaused = dash.pausedCount;
        snap.agentsOffline = dash.offlineCount;
        snap.incomingActive = dash.incomingCalls.size();
        snap.outgoingActive = dash.outgoingCalls.size();
        snap.pendingDialer = dash.pendingCalls.size();
        snap.recentIncoming.addAll(dash.incomingCalls.stream().limit(12).toList());
        snap.recentOutgoing.addAll(dash.outgoingCalls.stream().limit(12).toList());

        int waiting = 0;
        for (QueueMonitorRow q : dash.queues) {
            waiting += parseWaiting(q.getWaiting());
        }
        snap.waitingInQueues = waiting;
        snap.activeQueues = dash.queues.size();
        aggregateTrunks(snap, dash);
        return snap;
    }

    /** Adds SSH server metrics and service health (may take several seconds). */
    public void enrichWithHealth(LiveOverviewSnapshot snap) {
        ServiceHealthSnapshot health = healthService.collect();
        snap.sshAvailable = health.sshUsed();
        snap.server = health.server();
        snap.disks.addAll(health.disks());
        snap.healthErrors = health.errorCount();
        snap.healthWarnings = health.warnCount();
        snap.healthSummary = health.sshError() == null || health.sshError().isBlank()
                ? "Servicios: " + health.checks().size() + " comprobaciones"
                : health.sshError();
        for (ServiceCheck c : health.checks()) {
            if ("asterisk-ch".equals(c.id())) {
                snap.asteriskChannelsDetail = c.summary();
                snap.asteriskChannels = parseActiveChannels(c.cause());
                if (snap.asteriskChannels < 0) {
                    snap.asteriskChannels = parseActiveChannels(c.summary());
                }
                break;
            }
        }
    }

    private static void aggregateTrunks(LiveOverviewSnapshot snap, DashboardSnapshot dash) {
        for (var row : dash.incomingCalls) {
            addTrunk(snap, row.trunk);
        }
        for (var row : dash.outgoingCalls) {
            addTrunk(snap, row.trunk);
        }
        snap.trunkUsage.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .forEach(e -> snap.trunkUsage.put(e.getKey(), e.getValue()));
    }

    private static void addTrunk(LiveOverviewSnapshot snap, String trunk) {
        if (trunk == null || trunk.isBlank() || "—".equals(trunk.trim())) {
            return;
        }
        String key = trunk.trim();
        snap.trunkUsage.merge(key, 1, Integer::sum);
    }

    private static int parseWaiting(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int parseActiveChannels(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        Matcher m = ACTIVE_CHANNELS.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
}
