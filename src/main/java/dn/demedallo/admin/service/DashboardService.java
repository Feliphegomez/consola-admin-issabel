package dn.demedallo.admin.service;

import dn.demedallo.admin.i18n.AdminLabels;
import dn.demedallo.admin.model.ActiveCallRow;
import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.DashboardSnapshot;
import dn.demedallo.admin.model.DashboardSnapshot.DashboardCallRow;
import dn.demedallo.admin.model.IncomingPanelSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates ECCP monitor data for the Dashboard tab.
 * Live incoming/outgoing tables use the same full active-call feed as Monitoreo → Llamadas activas.
 */
public final class DashboardService {

    private final AgentMonitorService monitorService;
    private final IncomingCampaignsPanelService incomingService;
    private final OutgoingCampaignsPanelService outgoingService;

    public DashboardService(AdminEccpClient client, AdminDbSettings dbSettings) {
        this.monitorService = new AgentMonitorService(client);
        this.incomingService = new IncomingCampaignsPanelService(client, dbSettings);
        this.outgoingService = new OutgoingCampaignsPanelService(client, dbSettings);
    }

    public DashboardSnapshot load() throws Exception {
        ShiftDatetimeRange range = ShiftDatetimeRange.ofHours(0, 23);
        DashboardSnapshot snap = new DashboardSnapshot();
        MonitorSnapshot mon = monitorService.fetchSnapshotForDashboard();
        snap.agents.addAll(mon.agents());
        snap.queues.addAll(mon.queues());
        countAgents(snap, mon.agents());

        List<DashboardCallRow> incoming = new ArrayList<>();
        List<DashboardCallRow> outgoing = new ArrayList<>();
        collectLiveCalls(incoming, outgoing, mon.activeCalls(), mon.agents());

        try {
            IncomingPanelSnapshot incomingPanel = incomingService.load(range);
            mergePanelActiveCalls(incoming, incomingPanel.activeCalls, false);
        } catch (Exception ex) {
            AppLogFile.appendLine("[dashboard] incoming panel activecalls | EN: " + ex.getMessage());
        }

        try {
            IncomingPanelSnapshot outgoingPanel = outgoingService.load(range);
            mergePanelActiveCalls(outgoing, outgoingPanel.activeCalls, true);
            for (IncomingPanelSnapshot.PanelPendingCallRow row : outgoingPanel.pendingCalls) {
                DashboardCallRow c = new DashboardCallRow();
                c.pendingCallId = row.callId;
                c.callId = row.callId > 0 ? String.valueOf(row.callId) : "—";
                c.campaign = empty(row.campaignName);
                c.phone = empty(row.phone);
                c.status = "Pendiente";
                c.detail = "Reintentos: " + empty(row.retries) + " · " + empty(row.schedule);
                c.queue = empty(row.agent);
                snap.pendingCalls.add(c);
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[dashboard] outgoing panel | EN: " + ex.getMessage());
        }

        incoming.sort(liveCallComparator());
        outgoing.sort(liveCallComparator());
        snap.incomingCalls.addAll(incoming);
        snap.outgoingCalls.addAll(outgoing);

        return snap;
    }

    /**
     * Adds campaign-panel active calls (e.g. Colocando) missing from global monitor ECCP feed.
     */
    private static void mergePanelActiveCalls(List<DashboardCallRow> target,
            List<IncomingPanelSnapshot.PanelActiveCallRow> panelCalls, boolean outgoing) {
        if (panelCalls == null || panelCalls.isEmpty()) {
            return;
        }
        Map<String, DashboardCallRow> byKey = new LinkedHashMap<>();
        for (DashboardCallRow existing : target) {
            byKey.put(liveCallKey(existing), existing);
        }
        for (IncomingPanelSnapshot.PanelActiveCallRow row : panelCalls) {
            DashboardCallRow c = fromPanelActiveCall(row, outgoing);
            byKey.putIfAbsent(liveCallKey(c), c);
        }
        target.clear();
        target.addAll(byKey.values());
    }

    private static DashboardCallRow fromPanelActiveCall(
            IncomingPanelSnapshot.PanelActiveCallRow row, boolean outgoing) {
        DashboardCallRow c = new DashboardCallRow();
        c.campaign = empty(row.campaignName);
        c.phone = empty(row.phone);
        c.status = formatPanelStatus(row.status);
        c.trunk = empty(row.trunk);
        c.queue = outgoing ? "—" : empty(row.campaignName);
        c.callType = outgoing ? "Saliente" : "Entrante";
        c.callId = "—";
        c.detail = empty(row.since);
        return c;
    }

    private static String formatPanelStatus(String status) {
        if (status == null || status.isBlank() || "-".equals(status)) {
            return "—";
        }
        String s = status.trim();
        if ("Marcando".equalsIgnoreCase(s) || "placing".equalsIgnoreCase(s)) {
            return "Colocando";
        }
        return s;
    }

    private static Comparator<DashboardCallRow> liveCallComparator() {
        return Comparator
                .comparing((DashboardCallRow r) -> r.queue, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.phone, String.CASE_INSENSITIVE_ORDER);
    }

    private static void collectLiveCalls(List<DashboardCallRow> incoming,
                                           List<DashboardCallRow> outgoing,
                                           Iterable<ActiveCallRow> activeCalls,
                                           Iterable<AgentMonitorRow> agents) {
        Map<String, DashboardCallRow> incomingByKey = new LinkedHashMap<>();
        Map<String, DashboardCallRow> outgoingByKey = new LinkedHashMap<>();

        for (ActiveCallRow row : activeCalls) {
            DashboardCallRow c = fromActiveCall(row);
            if (isOutgoingType(row.typeProperty().get())) {
                putLiveCall(outgoingByKey, c);
            } else {
                putLiveCall(incomingByKey, c);
            }
        }

        for (AgentMonitorRow agent : agents) {
            String code = agent.getStatusCode() == null ? "" : agent.getStatusCode();
            if (!"oncall".equals(code) && !"ringing".equals(code)) {
                continue;
            }
            DashboardCallRow c = fromAgent(agent);
            if (isOutgoingType(agent.callTypeProperty().get())) {
                putLiveCall(outgoingByKey, c);
            } else {
                putLiveCall(incomingByKey, c);
            }
        }

        incoming.addAll(incomingByKey.values());
        outgoing.addAll(outgoingByKey.values());
    }

    private static DashboardCallRow fromActiveCall(ActiveCallRow row) {
        DashboardCallRow c = new DashboardCallRow();
        c.queue = empty(row.queueProperty().get());
        c.campaign = empty(row.campaignProperty().get());
        c.phone = empty(row.numberProperty().get());
        c.status = formatPanelStatus(empty(row.statusProperty().get()));
        c.trunk = empty(row.trunkProperty().get());
        c.callId = empty(row.callIdProperty().get());
        c.callType = AdminLabels.callTypeLabel(row.typeProperty().get());
        c.detail = "—";
        return c;
    }

    private static DashboardCallRow fromAgent(AgentMonitorRow agent) {
        DashboardCallRow c = new DashboardCallRow();
        c.queue = empty(agent.activeQueueProperty().get());
        c.campaign = "—";
        c.phone = empty(agent.phoneNumberProperty().get());
        c.status = empty(agent.callStatusProperty().get());
        c.trunk = empty(agent.trunkProperty().get());
        c.callId = empty(agent.callIdProperty().get());
        c.callType = empty(agent.callTypeProperty().get());
        c.detail = empty(agent.getAgentNumber());
        return c;
    }

    private static void putLiveCall(Map<String, DashboardCallRow> map, DashboardCallRow row) {
        map.put(liveCallKey(row), row);
    }

    private static String liveCallKey(DashboardCallRow row) {
        if (row.callId != null && !row.callId.isBlank() && !"—".equals(row.callId)) {
            return "id:" + row.callId;
        }
        return row.phone + "|" + row.queue + "|" + row.status;
    }

    private static void countAgents(DashboardSnapshot snap, Iterable<AgentMonitorRow> agents) {
        for (AgentMonitorRow a : agents) {
            String code = a.getStatusCode() == null ? "offline" : a.getStatusCode().toLowerCase(Locale.ROOT);
            switch (code) {
                case "online" -> snap.onlineCount++;
                case "oncall" -> {
                    snap.onCallCount++;
                    snap.onlineCount++;
                }
                case "ringing" -> {
                    snap.ringingCount++;
                    snap.onlineCount++;
                }
                case "paused" -> {
                    snap.pausedCount++;
                    snap.onlineCount++;
                }
                default -> snap.offlineCount++;
            }
        }
    }

    private static boolean isOutgoingType(String type) {
        if (type == null || type.isBlank() || "—".equals(type)) {
            return false;
        }
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("outgoing") || t.contains("saliente") || t.contains("salida");
    }

    private static String empty(String s) {
        return s == null || s.isBlank() ? "—" : s.trim();
    }
}
