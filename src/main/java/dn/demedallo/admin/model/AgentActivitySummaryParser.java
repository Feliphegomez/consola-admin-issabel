package dn.demedallo.admin.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public final class AgentActivitySummaryParser {

    private AgentActivitySummaryParser() {
    }

    public static List<AgentActivityEntry> parse(Document response) {
        List<AgentActivityEntry> out = new ArrayList<>();
        Element root = response.getDocumentElement();
        if (root == null) {
            return out;
        }
        Element inner = firstElement(root, "getagentactivitysummary_response");
        if (inner == null) {
            return out;
        }
        Element agents = firstElement(inner, "agents");
        if (agents == null) {
            return out;
        }
        NodeList agentNodes = agents.getElementsByTagName("agent");
        for (int i = 0; i < agentNodes.getLength(); i++) {
            Node n = agentNodes.item(i);
            if (!(n instanceof Element agentEl)) {
                continue;
            }
            AgentActivityEntry e = new AgentActivityEntry();
            e.agentChannel = text(agentEl, "agentchannel");
            e.agentName = text(agentEl, "agentname");
            e.loginTimeSec = parseLong(text(agentEl, "logintime"));
            e.lastSessionStart = emptyToNull(text(agentEl, "lastsessionstart"));
            e.lastSessionEnd = emptyToNull(text(agentEl, "lastsessionend"));
            e.lastPauseStart = emptyToNull(text(agentEl, "lastpausestart"));
            e.lastPauseEnd = emptyToNull(text(agentEl, "lastpauseend"));
            parseCallSummary(agentEl, e);
            out.add(e);
        }
        return out;
    }

    private static void parseCallSummary(Element agentEl, AgentActivityEntry e) {
        Element summary = firstElement(agentEl, "callsummary");
        if (summary == null) {
            return;
        }
        accumulateQueues(firstElement(summary, "incoming"), e, true);
        accumulateQueues(firstElement(summary, "outgoing"), e, false);
    }

    private static void accumulateQueues(Element typeEl, AgentActivityEntry e, boolean incoming) {
        if (typeEl == null) {
            return;
        }
        NodeList queues = typeEl.getElementsByTagName("queue");
        for (int i = 0; i < queues.getLength(); i++) {
            Node n = queues.item(i);
            if (!(n instanceof Element qEl)) {
                continue;
            }
            int num = (int) parseLong(text(qEl, "num_calls"));
            long sec = parseLong(text(qEl, "sec_calls"));
            if (incoming) {
                e.incomingCallsToday += num;
                e.incomingTalkSecToday += sec;
            } else {
                e.outgoingCallsToday += num;
                e.outgoingTalkSecToday += sec;
            }
            e.totalCallsToday += num;
            e.totalTalkSecToday += sec;
        }
    }

    private static Element firstElement(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return null;
        }
        Node n = nl.item(0);
        return n instanceof Element el ? el : null;
    }

    private static String text(Element parent, String tag) {
        Element c = firstElement(parent, tag);
        if (c == null) {
            return "";
        }
        String t = c.getTextContent();
        return t == null ? "" : t.trim();
    }

    private static String emptyToNull(String t) {
        return t == null || t.isEmpty() ? null : t;
    }

    private static long parseLong(String t) {
        if (t == null || t.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
