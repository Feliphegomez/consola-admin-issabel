package dn.demedallo.admin.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code getcampaignstatus_response} and {@code getincomingqueuestatus_response}.
 */
public final class QueueStatusParser {

    private QueueStatusParser() {
    }

    public static QueueStatusSnapshot parse(Document response) {
        QueueStatusSnapshot snap = new QueueStatusSnapshot();
        Element root = response.getDocumentElement();
        if (root == null) {
            return snap;
        }
        Element inner = firstElement(root, "getcampaignstatus_response");
        if (inner == null) {
            inner = firstElement(root, "getincomingqueuestatus_response");
        }
        if (inner == null) {
            return snap;
        }

        Element statusCount = firstElement(inner, "statuscount");
        if (statusCount != null) {
            snap.totalCalls = parseInt(text(statusCount, "total"));
            snap.onQueue = parseInt(text(statusCount, "onqueue"));
            snap.success = parseInt(text(statusCount, "success"));
            snap.onHold = parseInt(text(statusCount, "onhold"));
            snap.abandoned = parseInt(text(statusCount, "abandoned"));
            snap.pending = parseInt(text(statusCount, "pending"));
            snap.placing = parseInt(text(statusCount, "placing"));
            snap.ringing = parseInt(text(statusCount, "ringing"));
        }

        Element agents = firstElement(inner, "agents");
        if (agents != null) {
            NodeList agentNodes = agents.getElementsByTagName("agent");
            for (int i = 0; i < agentNodes.getLength(); i++) {
                Node n = agentNodes.item(i);
                if (!(n instanceof Element agentEl)) {
                    continue;
                }
                String st = text(agentEl, "status");
                if (st.isEmpty()) {
                    continue;
                }
                snap.agentStatusCounts.merge(st, 1, Integer::sum);
            }
        }

        Element activeCalls = firstElement(inner, "activecalls");
        if (activeCalls != null) {
            NodeList callNodes = activeCalls.getElementsByTagName("activecall");
            for (int i = 0; i < callNodes.getLength(); i++) {
                Node n = callNodes.item(i);
                if (!(n instanceof Element callEl)) {
                    continue;
                }
                ActiveCallRow row = new ActiveCallRow();
                row.setNumber(empty(text(callEl, "callnumber")));
                row.setStatus(empty(text(callEl, "callstatus")));
                row.setType(empty(text(callEl, "calltype")));
                String cid = text(callEl, "callid");
                if (!cid.isEmpty()) {
                    row.setCallId(cid);
                }
                row.setQueue(empty(text(callEl, "queuenumber")));
                String campId = text(callEl, "campaign_id");
                if (!campId.isEmpty()) {
                    row.setCampaign(campId);
                }
                row.setTrunk(empty(text(callEl, "trunk")));
                snap.activeCalls.add(row);
            }
        }
        return snap;
    }

    public static String formatAgentSummary(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "0 agentes";
        }
        List<String> parts = new ArrayList<>();
        int online = counts.getOrDefault("online", 0);
        int oncall = counts.getOrDefault("oncall", 0);
        int paused = counts.getOrDefault("paused", 0);
        int ringing = counts.getOrDefault("ringing", 0);
        int offline = counts.getOrDefault("offline", 0);
        if (online > 0) {
            parts.add(online + " disp.");
        }
        if (oncall > 0) {
            parts.add(oncall + " llamada");
        }
        if (ringing > 0) {
            parts.add(ringing + " sonando");
        }
        if (paused > 0) {
            parts.add(paused + " pausa");
        }
        if (offline > 0) {
            parts.add(offline + " off");
        }
        return String.join(", ", parts);
    }

    public static String formatCallStates(QueueStatusSnapshot snap) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (snap.onQueue > 0) {
            m.put("en cola", snap.onQueue);
        }
        if (snap.success > 0) {
            m.put("atendidas", snap.success);
        }
        if (snap.pending > 0) {
            m.put("pendientes", snap.pending);
        }
        if (snap.placing + snap.ringing > 0) {
            m.put("marcando", snap.placing + snap.ringing);
        }
        if (snap.abandoned > 0) {
            m.put("abandonadas", snap.abandoned);
        }
        if (m.isEmpty()) {
            return "—";
        }
        StringBuilder b = new StringBuilder();
        for (var e : m.entrySet()) {
            if (b.length() > 0) {
                b.append(" · ");
            }
            b.append(e.getValue()).append(' ').append(e.getKey());
        }
        return b.toString();
    }

    private static Element firstElement(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return null;
        }
        Node n = nl.item(0);
        return n instanceof Element e ? e : null;
    }

    private static String text(Element parent, String tag) {
        Element c = firstElement(parent, tag);
        if (c == null) {
            return "";
        }
        String t = c.getTextContent();
        return t == null ? "" : t.trim();
    }

    private static String empty(String t) {
        return t == null || t.isEmpty() ? "—" : t;
    }

    private static int parseInt(String t) {
        if (t == null || t.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
