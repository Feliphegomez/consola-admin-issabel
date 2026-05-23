package dn.demedallo.admin.service;

import dn.demedallo.admin.db.CallCenterDb;
import dn.demedallo.admin.db.OutgoingCampaignStatsDao;
import dn.demedallo.admin.model.CampaignListParser;
import dn.demedallo.admin.model.CampaignRef;
import dn.demedallo.admin.model.CampaignStatusDetailParser;
import dn.demedallo.admin.model.IncomingPanelSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads combined outgoing campaigns panel data (rep_outgoing_campaigns_panel).
 */
public final class OutgoingCampaignsPanelService {

    private static final int MAX_ECCP_CAMPAIGNS = 64;

    private final AdminEccpClient client;
    private final OutgoingCampaignStatsDao statsDao;
    private final boolean dbEnabled;

    public OutgoingCampaignsPanelService(AdminEccpClient client, AdminDbSettings dbSettings) {
        this.client = client;
        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();
        this.statsDao = dbEnabled ? new OutgoingCampaignStatsDao(new CallCenterDb(dbSettings)) : null;
    }

    public IncomingPanelSnapshot load(ShiftDatetimeRange range) throws Exception {
        IncomingPanelSnapshot snap = new IncomingPanelSnapshot();
        snap.statsFromDatabase = dbEnabled;

        List<Integer> statsCampaignIds = resolveStatsCampaignIds();
        if (!statsCampaignIds.isEmpty() && dbEnabled && statsDao != null) {
            statsDao.fillDbStats(snap, statsCampaignIds, range.start, range.end);
            snap.pendingCalls.addAll(statsDao.loadPendingOutgoingCalls(statsCampaignIds, 500));
        }

        List<CampaignSource> liveCampaigns = resolveLiveCampaigns();
        if (liveCampaigns.isEmpty()) {
            return snap;
        }

        String eccpDate = LocalDate.now().toString();
        Map<String, IncomingPanelSnapshot.PanelActiveCallRow> callsById = new LinkedHashMap<>();
        Map<String, IncomingPanelSnapshot.PanelAgentRow> agentsByChannel = new LinkedHashMap<>();
        int eccpErrors = 0;
        int eccpOk = 0;

        for (CampaignSource camp : liveCampaigns) {
            if (loadCampaignLiveData(camp, eccpDate, callsById, agentsByChannel, range)) {
                eccpOk++;
            } else {
                eccpErrors++;
            }
        }

        if (agentsByChannel.isEmpty()) {
            Set<String> queues = CampaignPanelAgents.nonEmptyQueues(
                    liveCampaigns.stream().map(c -> c.queue).toList());
            try {
                CampaignPanelAgents.fillFromGlobalAgentStatus(client, agentsByChannel, queues);
                if (agentsByChannel.isEmpty()) {
                    CampaignPanelAgents.fillFromGlobalAgentStatus(client, agentsByChannel, Set.of());
                }
                snap.agentsFromGlobalFallback = !agentsByChannel.isEmpty();
                if (snap.agentsFromGlobalFallback) {
                    AppLogFile.appendLine("[outgoing-panel] agents via global ECCP fallback, queues="
                            + queues);
                }
            } catch (Exception ex) {
                AppLogFile.appendLine("[outgoing-panel] global agent fallback: " + ex.getMessage());
            }
        }

        try {
            CampaignPanelAgents.enrichCallDetails(client, agentsByChannel);
        } catch (Exception ex) {
            AppLogFile.appendLine("[outgoing-panel] enrich call details: " + ex.getMessage());
        }

        snap.activeCalls.addAll(callsById.values());
        snap.agents.addAll(agentsByChannel.values());
        snap.agents.sort(Comparator
                .comparing((IncomingPanelSnapshot.PanelAgentRow a) -> "offline".equals(a.rawStatus) ? 1 : 0)
                .thenComparing(a -> a.agent));
        snap.eccpCampaignErrors = eccpErrors;
        snap.eccpCampaignsPolled = liveCampaigns.size();
        if (eccpOk > 0 && eccpErrors > 0) {
            AppLogFile.appendLine("[outgoing-panel] ECCP ok=" + eccpOk + " failed=" + eccpErrors);
        }
        return snap;
    }

    private boolean loadCampaignLiveData(CampaignSource camp, String eccpDate,
            Map<String, IncomingPanelSnapshot.PanelActiveCallRow> callsById,
            Map<String, IncomingPanelSnapshot.PanelAgentRow> agentsByChannel,
            ShiftDatetimeRange range) {
        if (tryMergeCampaignStatus(camp, eccpDate, callsById, agentsByChannel, range, false)) {
            return true;
        }
        if (!camp.queue.isBlank()) {
            if (tryMergeCampaignStatus(camp, eccpDate, callsById, agentsByChannel, range, true)) {
                AppLogFile.appendLine("[outgoing-panel] campaign " + camp.id + " via queue " + camp.queue);
                return true;
            }
        }
        return false;
    }

    private boolean tryMergeCampaignStatus(CampaignSource camp, String eccpDate,
            Map<String, IncomingPanelSnapshot.PanelActiveCallRow> callsById,
            Map<String, IncomingPanelSnapshot.PanelAgentRow> agentsByChannel,
            ShiftDatetimeRange range, boolean useQueueStatus) {
        try {
            Document doc = useQueueStatus
                    ? client.getIncomingQueueStatus(camp.queue, eccpDate)
                    : client.getCampaignStatus("outgoing", camp.id, eccpDate);
            Element inner = CampaignStatusDetailParser.campaignStatusInner(doc);
            if (inner == null || CampaignStatusDetailParser.hasFailure(inner)) {
                if (inner != null) {
                    AppLogFile.appendLine("[outgoing-panel] "
                            + (useQueueStatus ? "queue " + camp.queue : "campaign " + camp.id)
                            + " failure: " + CampaignStatusDetailParser.failureDetail(inner));
                }
                return false;
            }
            mergeActiveCalls(callsById, camp, doc, range);
            mergeAgents(agentsByChannel, camp, doc);
            return true;
        } catch (Exception ex) {
            AppLogFile.appendLine("[outgoing-panel] "
                    + (useQueueStatus ? "queue " + camp.queue : "campaign " + camp.id)
                    + " ECCP: " + ex.getMessage());
            return false;
        }
    }

    private List<Integer> resolveStatsCampaignIds() throws Exception {
        if (dbEnabled && statsDao != null) {
            return statsDao.loadActiveOutgoingCampaigns().stream()
                    .map(OutgoingCampaignStatsDao.OutgoingCampaignRef::id)
                    .toList();
        }
        return resolveLiveCampaigns().stream().map(c -> c.id).toList();
    }

    private List<CampaignSource> resolveLiveCampaigns() throws Exception {
        if (dbEnabled && statsDao != null) {
            List<OutgoingCampaignStatsDao.OutgoingCampaignRef> camps = new ArrayList<>(
                    statsDao.loadActiveOutgoingCampaigns());
            camps.sort(Comparator
                    .comparing((OutgoingCampaignStatsDao.OutgoingCampaignRef c) ->
                            "A".equalsIgnoreCase(c.status()) ? 0 : 1)
                    .thenComparing(OutgoingCampaignStatsDao.OutgoingCampaignRef::name,
                            String.CASE_INSENSITIVE_ORDER));
            List<CampaignSource> out = new ArrayList<>();
            for (OutgoingCampaignStatsDao.OutgoingCampaignRef c : camps) {
                out.add(new CampaignSource(c.id(), c.name(), c.queue()));
                if (out.size() >= MAX_ECCP_CAMPAIGNS) {
                    break;
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        List<CampaignSource> fromEccp = new ArrayList<>();
        for (CampaignRef c : CampaignListParser.parse(client.getCampaignList(null))) {
            if ("outgoing".equalsIgnoreCase(c.type)) {
                fromEccp.add(new CampaignSource(c.id, c.name, ""));
                if (fromEccp.size() >= MAX_ECCP_CAMPAIGNS) {
                    break;
                }
            }
        }
        return fromEccp;
    }

    private static void mergeActiveCalls(Map<String, IncomingPanelSnapshot.PanelActiveCallRow> target,
            CampaignSource camp, Document doc, ShiftDatetimeRange range) {
        Element inner = CampaignStatusDetailParser.campaignStatusInner(doc);
        if (inner == null || CampaignStatusDetailParser.hasFailure(inner)) {
            return;
        }
        Element activeCalls = firstElement(inner, "activecalls");
        if (activeCalls == null) {
            return;
        }
        NodeList nodes = activeCalls.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element callEl)
                    || !"activecall".equals(callEl.getTagName())) {
                continue;
            }
            if (!CampaignStatusDetailParser.isCallInRange(
                    CampaignStatusDetailParser.queueStart(callEl),
                    CampaignStatusDetailParser.dialStart(callEl),
                    range.start, range.end)) {
                continue;
            }
            String callId = CampaignStatusDetailParser.callId(callEl);
            if (callId.isEmpty()) {
                callId = "out-" + camp.id + "-" + i;
            }
            IncomingPanelSnapshot.PanelActiveCallRow row =
                    CampaignStatusDetailParser.parseActiveCallElement(callEl, camp.name);
            target.putIfAbsent(callId, row);
        }
    }

    private static void mergeAgents(Map<String, IncomingPanelSnapshot.PanelAgentRow> target,
            CampaignSource camp, Document doc) {
        Element inner = CampaignStatusDetailParser.campaignStatusInner(doc);
        if (inner == null || CampaignStatusDetailParser.hasFailure(inner)) {
            return;
        }
        Element agents = firstElement(inner, "agents");
        if (agents == null) {
            return;
        }
        NodeList nodes = agents.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element agentEl)
                    || !"agent".equals(agentEl.getTagName())) {
                continue;
            }
            IncomingPanelSnapshot.PanelAgentRow row = CampaignStatusDetailParser.parseAgentElement(agentEl);
            String channel = row.agent;
            if (channel.isEmpty() || "-".equals(channel)) {
                continue;
            }
            row.campaignName = campaignForAgent(agentEl, row, camp);
            CampaignPanelAgents.mergeInto(target, channel, row);
        }
    }

    private static String campaignForAgent(Element agentEl, IncomingPanelSnapshot.PanelAgentRow row,
            CampaignSource camp) {
        if ("oncall".equals(row.rawStatus)) {
            String q = CampaignStatusDetailParser.agentQueueNumber(agentEl);
            if (!q.isEmpty() && !camp.queue.isEmpty() && q.equals(camp.queue)) {
                return camp.name;
            }
        }
        return "-";
    }

    private record CampaignSource(int id, String name, String queue) {
    }

    private static Element firstElement(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return null;
        }
        return nl.item(0) instanceof Element e ? e : null;
    }
}
