package dn.demedallo.admin.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MultipleAgentQueuesParser {

    private MultipleAgentQueuesParser() {
    }

    public static Map<String, List<String>> parse(Document response) {
        Map<String, List<String>> out = new HashMap<>();
        Element root = response.getDocumentElement();
        if (root == null) {
            return out;
        }
        Element inner = firstElement(root, "getmultipleagentqueues_response");
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
                continue;
            }
            List<String> queues = new ArrayList<>();
            Element queuesEl = firstElement(agentEl, "queues");
            if (queuesEl != null) {
                NodeList qn = queuesEl.getElementsByTagName("queue");
                for (int j = 0; j < qn.getLength(); j++) {
                    String q = qn.item(j).getTextContent();
                    if (q != null && !q.trim().isEmpty()) {
                        queues.add(q.trim());
                    }
                }
            }
            out.put(key, queues);
        }
        return out;
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
}
