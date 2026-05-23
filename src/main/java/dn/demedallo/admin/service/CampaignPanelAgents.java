package dn.demedallo.admin.service;

import dn.demedallo.admin.model.AgentActivityEntry;
import dn.demedallo.admin.model.AgentActivitySummaryParser;
import dn.demedallo.admin.model.AgentState;
import dn.demedallo.admin.model.IncomingPanelSnapshot;
import dn.demedallo.admin.model.MultipleAgentQueuesParser;
import dn.demedallo.admin.model.MultipleAgentStatusParser;
import dn.demedallo.admin.protocol.AdminEccpClient;
import org.w3c.dom.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent rows for campaign panels, including call detail enrichment via {@code getmultipleagentstatus}.
 */
public final class CampaignPanelAgents {

    private CampaignPanelAgents() {
    }

    public static void fillFromGlobalAgentStatus(AdminEccpClient client,
            Map<String, IncomingPanelSnapshot.PanelAgentRow> target,
            Set<String> limitToQueues) throws Exception {
        String today = LocalDate.now().toString();
        Document summaryDoc = client.getAgentActivitySummary(today, today);
        List<AgentActivityEntry> agents = AgentActivitySummaryParser.parse(summaryDoc);
        if (agents.isEmpty()) {
            return;
        }
        List<String> channels = new ArrayList<>();
        for (AgentActivityEntry a : agents) {
            if (a.agentChannel != null && !a.agentChannel.isBlank()) {
                channels.add(a.agentChannel.trim());
            }
        }
        if (channels.isEmpty()) {
            return;
        }
        Map<String, AgentState> states = MultipleAgentStatusParser.parse(
                client.getMultipleAgentStatus(channels));
        Map<String, List<String>> queuesByAgent = MultipleAgentQueuesParser.parse(
                client.getMultipleAgentQueues(channels));

        boolean filterQueues = limitToQueues != null && !limitToQueues.isEmpty();
        for (AgentActivityEntry entry : agents) {
            String channel = entry.agentChannel == null ? "" : entry.agentChannel.trim();
            if (channel.isEmpty()) {
                continue;
            }
            if (filterQueues) {
                List<String> qs = queuesByAgent.get(channel);
                if (qs == null || qs.stream().noneMatch(limitToQueues::contains)) {
                    continue;
                }
            }
            AgentState st = states.get(channel);
            if (st == null) {
                st = new AgentState();
                st.status = "offline";
            }
            IncomingPanelSnapshot.PanelAgentRow row = toPanelRow(channel, st);
            mergeInto(target, channel, row);
        }
    }

    /**
     * Fills phone / trunk / since for agents already in the panel (e.g. from queue status without callinfo).
     */
    public static void enrichCallDetails(AdminEccpClient client,
            Map<String, IncomingPanelSnapshot.PanelAgentRow> agents) throws Exception {
        if (agents.isEmpty()) {
            return;
        }
        List<String> channels = agents.values().stream()
                .map(r -> r.agent)
                .filter(a -> a != null && !a.isBlank() && !"-".equals(a))
                .distinct()
                .toList();
        if (channels.isEmpty()) {
            return;
        }
        Map<String, AgentState> states = MultipleAgentStatusParser.parse(
                client.getMultipleAgentStatus(channels));
        for (IncomingPanelSnapshot.PanelAgentRow row : agents.values()) {
            AgentState st = states.get(row.agent);
            if (st != null) {
                applyCallDetails(row, st);
            }
        }
    }

    public static void mergeInto(Map<String, IncomingPanelSnapshot.PanelAgentRow> target,
            String channel, IncomingPanelSnapshot.PanelAgentRow row) {
        IncomingPanelSnapshot.PanelAgentRow existing = target.get(channel);
        if (existing == null) {
            target.put(channel, row);
            return;
        }
        target.put(channel, mergeRows(existing, row));
    }

    public static IncomingPanelSnapshot.PanelAgentRow mergeRows(
            IncomingPanelSnapshot.PanelAgentRow existing,
            IncomingPanelSnapshot.PanelAgentRow incoming) {
        if (hasCallDetail(incoming) && isActiveCall(incoming)) {
            if (!"-".equals(existing.campaignName) && "-".equals(incoming.campaignName)) {
                incoming.campaignName = existing.campaignName;
            }
            return incoming;
        }
        if (hasCallDetail(existing) && isActiveCall(existing)) {
            return existing;
        }
        if (isActiveCall(incoming)) {
            return incoming;
        }
        return existing;
    }

    public static void applyCallDetails(IncomingPanelSnapshot.PanelAgentRow row, AgentState st) {
        if (st == null || row == null) {
            return;
        }
        if (st.status != null && !st.status.isBlank()) {
            row.rawStatus = st.status;
            row.status = panelStatusLabel(st);
        }
        if (!isActiveCall(row)) {
            return;
        }
        String phone = resolvePhone(st);
        if (!phone.isEmpty()) {
            row.phone = phone;
        }
        if (st.trunk != null && !st.trunk.isBlank()) {
            row.trunk = st.trunk.trim();
        }
        String since = resolveSince(st);
        if (!since.isEmpty()) {
            row.since = since;
        }
    }

    public static Set<String> nonEmptyQueues(List<String> queues) {
        return queues.stream()
                .filter(q -> q != null && !q.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static IncomingPanelSnapshot.PanelAgentRow toPanelRow(String channel, AgentState st) {
        IncomingPanelSnapshot.PanelAgentRow row = new IncomingPanelSnapshot.PanelAgentRow();
        row.agent = channel;
        row.campaignName = "-";
        applyCallDetails(row, st);
        if (!isActiveCall(row)) {
            row.phone = "-";
            row.trunk = "-";
            row.since = "-";
        }
        return row;
    }

    private static boolean isActiveCall(IncomingPanelSnapshot.PanelAgentRow row) {
        return "oncall".equals(row.rawStatus) || "ringing".equals(row.rawStatus);
    }

    private static boolean hasCallDetail(IncomingPanelSnapshot.PanelAgentRow row) {
        return row != null && row.phone != null && !row.phone.isBlank() && !"-".equals(row.phone);
    }

    private static String resolvePhone(AgentState st) {
        if (st.callNumber != null && !st.callNumber.isBlank()) {
            return st.callNumber.trim();
        }
        return "";
    }

    private static String resolveSince(AgentState st) {
        String t = firstNonEmpty(st.linkStart, st.queueStart, st.dialStart);
        if (t.isEmpty()) {
            return "";
        }
        String today = LocalDate.now().toString();
        if (t.startsWith(today)) {
            return t.substring(today.length() + 1).trim();
        }
        return t;
    }

    private static String panelStatusLabel(AgentState st) {
        boolean onHold = st.onHold;
        if ("paused".equals(st.status)) {
            return onHold ? "En espera (hold)" : (st.pauseName != null && !st.pauseName.isBlank()
                    ? "En pausa: " + st.pauseName : "En pausa");
        }
        if ("oncall".equals(st.status)) {
            return onHold ? "En llamada (hold)" : "En llamada";
        }
        return switch (st.status == null ? "offline" : st.status) {
            case "offline" -> "No logon";
            case "online" -> "Libre";
            case "ringing" -> "Sonando";
            default -> st.status;
        };
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
