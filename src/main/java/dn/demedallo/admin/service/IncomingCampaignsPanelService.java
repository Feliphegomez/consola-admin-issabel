package dn.demedallo.admin.service;

import dn.demedallo.admin.db.CallCenterDb;
import dn.demedallo.admin.db.IncomingCampaignStatsDao;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads combined incoming campaigns panel data (ECCP + optional MySQL stats).
 */
public final class IncomingCampaignsPanelService {

    private static final int MAX_ECCP_CAMPAIGNS = 48;

    private final AdminEccpClient client;
    private final IncomingCampaignStatsDao statsDao;
    private final boolean dbEnabled;

    public IncomingCampaignsPanelService(AdminEccpClient client, AdminDbSettings dbSettings) {
        this.client = client;
        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();
        this.statsDao = dbEnabled ? new IncomingCampaignStatsDao(new CallCenterDb(dbSettings)) : null;
    }

    public IncomingPanelSnapshot load(ShiftDatetimeRange range) throws Exception {
        IncomingPanelSnapshot snap = new IncomingPanelSnapshot();
        snap.statsFromDatabase = dbEnabled;

        List<Integer> statsCampaignIds = resolveStatsCampaignIds();
        if (!statsCampaignIds.isEmpty() && dbEnabled && statsDao != null) {
            statsDao.fillDbStats(snap, statsCampaignIds, range.start, range.end);
        }

        List<CampaignSource> liveCampaigns = resolveLiveCampaigns();
        if (liveCampaigns.isEmpty()) {
            return snap;
        }

        String eccpDate = range.start.substring(0, 10);
        Map<String, IncomingPanelSnapshot.PanelActiveCallRow> callsById = new LinkedHashMap<>();
        Map<String, IncomingPanelSnapshot.PanelAgentRow> agentsByChannel = new LinkedHashMap<>();
        int eccpErrors = 0;

        for (CampaignSource camp : liveCampaigns) {
            try {
                Document doc = client.getCampaignStatus("incoming", camp.id, eccpDate);
                Element inner = CampaignStatusDetailParser.campaignStatusInner(doc);
                if (CampaignStatusDetailParser.hasFailure(inner)) {
                    eccpErrors++;
                    continue;
                }
                mergeActiveCalls(callsById, camp, doc, range);
                mergeAgents(agentsByChannel, camp, doc);
            } catch (Exception ex) {
                eccpErrors++;
                AppLogFile.appendLine("[incoming-panel] campaign " + camp.id + " ECCP: " + ex.getMessage());
            }
        }

        try {
            CampaignPanelAgents.enrichCallDetails(client, agentsByChannel);
        } catch (Exception ex) {
            AppLogFile.appendLine("[incoming-panel] enrich call details: " + ex.getMessage());
        }

        snap.activeCalls.addAll(callsById.values());
        snap.agents.addAll(agentsByChannel.values());
        snap.agents.sort(Comparator
                .comparing((IncomingPanelSnapshot.PanelAgentRow a) -> "offline".equals(a.rawStatus) ? 1 : 0)
                .thenComparing(a -> a.agent));
        snap.eccpCampaignErrors = eccpErrors;
        snap.eccpCampaignsPolled = liveCampaigns.size();
        return snap;
    }

    private List<Integer> resolveStatsCampaignIds() throws Exception {
        if (dbEnabled && statsDao != null) {
            return statsDao.loadActiveIncomingCampaigns().stream()
                    .map(IncomingCampaignStatsDao.IncomingCampaignRef::id)
                    .toList();
        }
        return resolveLiveCampaigns().stream().map(c -> c.id).toList();
    }

    private List<CampaignSource> resolveLiveCampaigns() throws Exception {
        if (dbEnabled && statsDao != null) {
            List<CampaignSource> out = new ArrayList<>();
            for (IncomingCampaignStatsDao.IncomingCampaignRef c : statsDao.loadActiveIncomingCampaigns()) {
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
        for (CampaignRef c : CampaignListParser.parse(client.getCampaignList("active"))) {
            if (!"incoming".equalsIgnoreCase(c.type) || !"active".equalsIgnoreCase(c.status)) {
                continue;
            }
            fromEccp.add(new CampaignSource(c.id, c.name, c.name));
            if (fromEccp.size() >= MAX_ECCP_CAMPAIGNS) {
                break;
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
                callId = camp.id + "-" + i;
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
            if (!q.isEmpty() && q.equals(camp.queue)) {
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
