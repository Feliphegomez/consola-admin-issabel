package dn.demedallo.admin.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

public final class MultipleAgentStatusParser {

    private MultipleAgentStatusParser() {
    }

    public static Map<String, AgentState> parse(Document response) {
        Map<String, AgentState> out = new HashMap<>();
        Element root = response.getDocumentElement();
        if (root == null) {
            return out;
        }
        Element inner = firstElement(root, "getmultipleagentstatus_response");
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
            String key = text(agentEl, "agent_number");
            if (key.isEmpty()) {
                key = text(agentEl, "agentchannel");
            }
            if (key.isEmpty()) {
                continue;
            }
            out.put(key, parseAgentElement(agentEl));
        }
        return out;
    }

    private static AgentState parseAgentElement(Element agentEl) {
        AgentState s = new AgentState();
        s.status = text(agentEl, "status");
        s.channel = emptyToNull(text(agentEl, "channel"));
        s.extension = emptyToNull(text(agentEl, "extension"));
        s.agentChannel = emptyToNull(text(agentEl, "agentchannel"));
        s.onHold = "1".equals(text(agentEl, "onhold"));
        s.callChannel = emptyToNull(text(agentEl, "callchannel"));
        s.remoteChannel = emptyToNull(text(agentEl, "remote_channel"));

        Element pauseInfo = firstElement(agentEl, "pauseinfo");
        if (pauseInfo != null) {
            s.pauseId = parseInt(text(pauseInfo, "pauseid"));
            s.pauseName = emptyToNull(text(pauseInfo, "pausename"));
            s.pauseStart = emptyToNull(text(pauseInfo, "pausestart"));
        } else {
            s.pauseId = parseInt(text(agentEl, "pauseid"));
            s.pauseName = emptyToNull(text(agentEl, "pausename"));
            s.pauseStart = emptyToNull(text(agentEl, "pausestart"));
        }

        Element callInfo = firstElement(agentEl, "callinfo");
        if (callInfo != null) {
            fillCallInfo(s, callInfo);
        }
        Element waited = firstElement(agentEl, "waitedcallinfo");
        if (waited != null) {
            s.waitedCallStatus = emptyToNull(text(waited, "status"));
            s.waitedCallType = emptyToNull(text(waited, "calltype"));
            s.waitedCampaignId = parseInt(text(waited, "campaign_id"));
            s.waitedCallId = parseInt(text(waited, "callid"));
        }
        return s;
    }

    private static void fillCallInfo(AgentState s, Element callInfo) {
        s.callStatus = text(callInfo, "callstatus");
        s.callType = text(callInfo, "calltype");
        s.campaignId = parseInt(text(callInfo, "campaign_id"));
        s.callId = parseInt(text(callInfo, "callid"));
        s.callNumber = emptyToNull(text(callInfo, "callnumber"));
        s.queueNumber = emptyToNull(text(callInfo, "queuenumber"));
        s.dialStart = emptyToNull(text(callInfo, "dialstart"));
        s.dialEnd = emptyToNull(text(callInfo, "dialend"));
        s.queueStart = emptyToNull(text(callInfo, "queuestart"));
        s.linkStart = emptyToNull(text(callInfo, "linkstart"));
        s.trunk = emptyToNull(text(callInfo, "trunk"));
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

    private static String emptyToNull(String t) {
        return t == null || t.isEmpty() ? null : t;
    }

    private static Integer parseInt(String t) {
        if (t == null || t.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
