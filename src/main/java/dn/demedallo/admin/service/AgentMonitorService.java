package dn.demedallo.admin.service;

import dn.demedallo.admin.i18n.AdminLabels;
import dn.demedallo.admin.model.ActiveCallRow;
import dn.demedallo.admin.model.AgentActivityEntry;
import dn.demedallo.admin.model.AgentActivitySummaryParser;
import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.AgentState;
import dn.demedallo.admin.model.CampaignListParser;
import dn.demedallo.admin.model.CampaignRef;
import dn.demedallo.admin.model.IncomingQueueRef;
import dn.demedallo.admin.model.MultipleAgentQueuesParser;
import dn.demedallo.admin.model.MultipleAgentStatusParser;
import dn.demedallo.admin.model.QueueMonitorRow;
import dn.demedallo.admin.model.QueueStatusParser;
import dn.demedallo.admin.model.QueueStatusSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.SpyTargetUtil;
import org.w3c.dom.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AgentMonitorService {

    private static final int MAX_CAMPAIGN_STATUS_POLLS = 30;
    /** Dashboard needs all active calls, not a capped campaign sample. */
    private static final int DASHBOARD_MAX_CAMPAIGN_STATUS_POLLS = 256;

    private final AdminEccpClient client;

    public AgentMonitorService(AdminEccpClient client) {
        this.client = client;
    }

    public MonitorSnapshot fetchSnapshot() throws Exception {
        return fetchSnapshot(MAX_CAMPAIGN_STATUS_POLLS);
    }

    /** Full ECCP poll for dashboard live call tables (incoming / outgoing). */
    public MonitorSnapshot fetchSnapshotForDashboard() throws Exception {
        return fetchSnapshot(DASHBOARD_MAX_CAMPAIGN_STATUS_POLLS);
    }

    private MonitorSnapshot fetchSnapshot(int maxCampaignPolls) throws Exception {
        String today = LocalDate.now().toString();
        List<AgentMonitorRow> agents = fetchAgents(today);
        List<QueueMonitorRow> queues = new ArrayList<>();
        List<ActiveCallRow> activeCalls = new ArrayList<>();
        fetchQueuesAndCalls(today, queues, activeCalls, maxCampaignPolls);
        return new MonitorSnapshot(agents, queues, activeCalls);
    }

    private List<AgentMonitorRow> fetchAgents(String today) throws Exception {
        Document summaryDoc = client.getAgentActivitySummary(today, today);
        List<AgentActivityEntry> agents = AgentActivitySummaryParser.parse(summaryDoc);
        if (agents.isEmpty()) {
            return List.of();
        }

        List<String> agentNumbers = agents.stream()
                .map(a -> a.agentChannel)
                .filter(ch -> ch != null && !ch.isBlank())
                .collect(Collectors.toList());

        Map<String, AgentState> statusByAgent = MultipleAgentStatusParser.parse(
                client.getMultipleAgentStatus(agentNumbers));
        Map<String, List<String>> queuesByAgent = MultipleAgentQueuesParser.parse(
                client.getMultipleAgentQueues(agentNumbers));

        List<AgentMonitorRow> rows = new ArrayList<>();
        for (AgentActivityEntry a : agents) {
            AgentMonitorRow row = new AgentMonitorRow();
            row.setAgentNumber(a.agentChannel);
            row.setAgentName(a.agentName == null || a.agentName.isBlank() ? a.agentChannel : a.agentName);
            row.setLoginTime(AdminLabels.formatLoginSeconds(a.loginTimeSec));
            row.setCallsToday(String.valueOf(a.totalCallsToday));
            row.setTalkTimeToday(AdminLabels.formatDurationSeconds(a.totalTalkSecToday));
            row.setCallsBreakdown(AdminLabels.formatCallsBreakdown(
                    a.incomingCallsToday, a.outgoingCallsToday));
            row.setLastSession(AdminLabels.formatSessionRange(
                    a.lastSessionStart, a.lastSessionEnd));

            AgentState st = statusByAgent.get(a.agentChannel);
            if (st == null) {
                st = new AgentState();
                st.status = "offline";
            }
            row.setStatusCode(st.status == null ? "offline" : st.status);
            row.setStatusLabel(AdminLabels.statusLabel(st));
            row.setExtension(st.extension == null ? "—" : st.extension);
            row.setChannel(st.channel == null ? "—" : st.channel);
            row.setPauseInfo(AdminLabels.formatPauseInfo(st));
            row.setPauseSince(st.pauseStart == null ? "—" : st.pauseStart);

            if ("oncall".equals(st.status) || "ringing".equals(st.status)) {
                row.setPhoneNumber(st.callNumber == null ? "—" : st.callNumber);
                row.setActiveQueue(st.queueNumber == null ? "—" : st.queueNumber);
                row.setCallType(AdminLabels.callTypeLabel(st.callType));
                row.setCallId(st.callId == null ? "—" : String.valueOf(st.callId));
                row.setCallStatus(st.callStatus == null || st.callStatus.isBlank() ? "—" : st.callStatus);
                row.setTrunk(st.trunk == null ? "—" : st.trunk);
            } else if (st.waitingForCampaignCall()) {
                row.setPhoneNumber("—");
                row.setActiveQueue("—");
                row.setCallType(AdminLabels.callTypeLabel(st.waitedCallType));
                row.setCallId(st.waitedCallId == null ? "—" : String.valueOf(st.waitedCallId));
                row.setCallStatus("Esperando campaña");
                row.setTrunk("—");
            } else {
                row.setPhoneNumber("—");
                row.setActiveQueue("—");
                row.setCallType("—");
                row.setCallId("—");
                row.setCallStatus("—");
                row.setTrunk("—");
            }

            List<String> qs = queuesByAgent.get(a.agentChannel);
            row.setQueues(qs == null || qs.isEmpty() ? "—" : String.join(", ", qs));

            String spyExt = SpyTargetUtil.resolveSpyExtension(st, a.agentChannel);
            row.setSpyExtension(spyExt);
            row.setListenAvailable(SpyTargetUtil.canListen(row.getStatusCode()) && !spyExt.isBlank());

            rows.add(row);
        }

        rows.sort(Comparator.comparing(AgentMonitorRow::getAgentName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private void fetchQueuesAndCalls(String today, List<QueueMonitorRow> queueRows,
                                     List<ActiveCallRow> allActiveCalls, int maxCampaignPolls)
            throws Exception {
        Map<String, QueueMonitorRow> byQueue = new LinkedHashMap<>();
        Set<String> polledStandaloneQueues = new LinkedHashSet<>();

        List<CampaignRef> campaigns = CampaignListParser.parse(
                client.getCampaignList("active"));
        int polls = 0;
        for (CampaignRef c : campaigns) {
            if (polls >= maxCampaignPolls) {
                break;
            }
            if (!"active".equalsIgnoreCase(c.status)) {
                continue;
            }
            try {
                Document statusDoc = client.getCampaignStatus(c.type, c.id, today);
                applyQueueStatus(byQueue, allActiveCalls, statusDoc, c, null);
                polls++;
            } catch (Exception ex) {
                AppLogFile.appendLine("[monitor] campaign status " + c.type + "/" + c.id
                        + " failed: " + ex.getMessage());
            }
        }

        List<IncomingQueueRef> incomingQueues = CampaignListParser.parseIncomingQueues(
                client.getIncomingQueueList());
        for (IncomingQueueRef q : incomingQueues) {
            if (!"active".equalsIgnoreCase(q.status)) {
                continue;
            }
            if (byQueue.containsKey(q.queue) || polledStandaloneQueues.contains(q.queue)) {
                continue;
            }
            if (polls >= maxCampaignPolls) {
                break;
            }
            try {
                Document statusDoc = client.getIncomingQueueStatus(q.queue, today);
                CampaignRef pseudo = new CampaignRef();
                pseudo.type = "incoming";
                pseudo.id = q.id;
                pseudo.name = "Cola " + q.queue;
                pseudo.status = q.status;
                applyQueueStatus(byQueue, allActiveCalls, statusDoc, pseudo, q.queue);
                polledStandaloneQueues.add(q.queue);
                polls++;
            } catch (Exception ex) {
                AppLogFile.appendLine("[monitor] queue status " + q.queue + " failed: " + ex.getMessage());
            }
        }

        queueRows.addAll(byQueue.values());
        queueRows.sort(Comparator.comparing(QueueMonitorRow::getQueue, String.CASE_INSENSITIVE_ORDER));
        allActiveCalls.sort(Comparator.comparing(r -> r.queueProperty().get(), String.CASE_INSENSITIVE_ORDER));
    }

    private static void applyQueueStatus(Map<String, QueueMonitorRow> byQueue, List<ActiveCallRow> allActiveCalls,
                                         Document statusDoc, CampaignRef campaign, String queueOverride) {
        QueueStatusSnapshot snap = QueueStatusParser.parse(statusDoc);
        String queueKey = queueOverride;
        if (queueKey == null || queueKey.isBlank()) {
            queueKey = snap.activeCalls.stream()
                    .map(r -> r.queueProperty().get())
                    .filter(q -> q != null && !q.isBlank() && !"—".equals(q))
                    .findFirst()
                    .orElse(campaign.name);
        }
        for (ActiveCallRow call : snap.activeCalls) {
            if (call.queueProperty().get() == null || "—".equals(call.queueProperty().get())) {
                call.setQueue(queueKey);
            }
            call.setCampaign(campaign.name);
            allActiveCalls.add(call);
        }

        QueueMonitorRow row = new QueueMonitorRow();
        row.setQueue(queueKey);
        row.setTypeLabel(AdminLabels.campaignTypeLabel(campaign.type));
        row.setCampaignName(campaign.name);
        row.setStatusLabel(AdminLabels.campaignStatusLabel(campaign.status));
        row.setCallsToday(String.valueOf(snap.totalCalls));
        int waiting = snap.activeCalls.size() + snap.onQueue;
        row.setWaiting(String.valueOf(waiting));
        row.setAgentsSummary(QueueStatusParser.formatAgentSummary(snap.agentStatusCounts));
        row.setCallStates(QueueStatusParser.formatCallStates(snap));
        byQueue.put(queueKey, row);
    }
}
