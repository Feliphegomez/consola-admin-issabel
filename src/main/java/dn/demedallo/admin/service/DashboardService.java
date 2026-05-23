package dn.demedallo.admin.service;

import dn.demedallo.admin.model.ActiveCallRow;
import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.DashboardSnapshot;
import dn.demedallo.admin.model.DashboardSnapshot.DashboardCallRow;
import dn.demedallo.admin.model.IncomingPanelSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates ECCP monitor data and campaign panels for the Dashboard tab.
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
        MonitorSnapshot mon = monitorService.fetchSnapshot();
        snap.agents.addAll(mon.agents());
        snap.queues.addAll(mon.queues());
        countAgents(snap, mon.agents());
        Map<String, DashboardCallRow> incomingMap = new LinkedHashMap<>();
        Map<String, DashboardCallRow> outgoingMap = new LinkedHashMap<>();
        Map<String, DashboardCallRow> pendingMap = new LinkedHashMap<>();
        mergeMonitorCalls(incomingMap, outgoingMap, mon.activeCalls());

        try {
            IncomingPanelSnapshot incoming = incomingService.load(range);
            for (IncomingPanelSnapshot.PanelActiveCallRow row : incoming.activeCalls) {
                putCall(incomingMap, fromPanelRow(row, "Entrante"));
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[dashboard] incoming panel | EN: " + ex.getMessage());
        }

        try {
            IncomingPanelSnapshot outgoing = outgoingService.load(range);
            for (IncomingPanelSnapshot.PanelActiveCallRow row : outgoing.activeCalls) {
                putCall(outgoingMap, fromPanelRow(row, "Saliente"));
            }
            for (IncomingPanelSnapshot.PanelPendingCallRow row : outgoing.pendingCalls) {
                DashboardCallRow c = new DashboardCallRow();
                c.campaign = empty(row.campaignName);
                c.phone = empty(row.phone);
                c.status = "Pendiente";
                c.detail = "Reintentos: " + empty(row.retries) + " · " + empty(row.schedule);
                c.queue = empty(row.agent);
                putCall(pendingMap, c);
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[dashboard] outgoing panel | EN: " + ex.getMessage());
        }

        snap.incomingCalls.addAll(incomingMap.values());
        snap.outgoingCalls.addAll(outgoingMap.values());
        snap.pendingCalls.addAll(pendingMap.values());
        return snap;
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

    private static void mergeMonitorCalls(Map<String, DashboardCallRow> incomingMap,
                                          Map<String, DashboardCallRow> outgoingMap,
                                          Iterable<ActiveCallRow> calls) {
        for (ActiveCallRow row : calls) {
            DashboardCallRow c = new DashboardCallRow();
            c.queue = empty(row.queueProperty().get());
            c.campaign = empty(row.campaignProperty().get());
            c.phone = empty(row.numberProperty().get());
            c.status = empty(row.statusProperty().get());
            c.trunk = empty(row.trunkProperty().get());
            c.detail = empty(row.callIdProperty().get());
            String type = row.typeProperty().get();
            if (isOutgoingType(type)) {
                c.status = appendType(c.status, "Saliente");
                putCall(outgoingMap, c);
            } else {
                c.status = appendType(c.status, "Entrante");
                putCall(incomingMap, c);
            }
        }
    }

    private static DashboardCallRow fromPanelRow(IncomingPanelSnapshot.PanelActiveCallRow row, String typeLabel) {
        DashboardCallRow c = new DashboardCallRow();
        c.campaign = empty(row.campaignName);
        c.phone = empty(row.phone);
        c.status = empty(row.status);
        c.trunk = empty(row.trunk);
        c.detail = empty(row.since);
        c.status = appendType(c.status, typeLabel);
        return c;
    }

    private static void putCall(Map<String, DashboardCallRow> map, DashboardCallRow row) {
        map.put(row.phone + "|" + row.campaign + "|" + row.queue, row);
    }

    private static boolean isIncomingType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("incoming") || t.contains("entrante");
    }

    private static boolean isOutgoingType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("outgoing") || t.contains("saliente");
    }

    private static String appendType(String status, String typeLabel) {
        if (status == null || status.isBlank() || "—".equals(status)) {
            return typeLabel;
        }
        if (status.contains(typeLabel)) {
            return status;
        }
        return status + " · " + typeLabel;
    }

    private static String empty(String s) {
        return s == null || s.isBlank() ? "—" : s.trim();
    }
}
