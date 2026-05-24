package dn.demedallo.admin.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code getcampaignstatus_response} agents and active calls for campaign panels.
 */
public final class CampaignStatusDetailParser {

    private CampaignStatusDetailParser() {
    }

    public static List<IncomingPanelSnapshot.PanelActiveCallRow> parseActiveCalls(Document response) {
        List<IncomingPanelSnapshot.PanelActiveCallRow> out = new ArrayList<>();
        Element inner = responseRoot(response);
        if (inner == null) {
            return out;
        }
        Element activeCalls = firstElement(inner, "activecalls");
        if (activeCalls == null) {
            return out;
        }
        NodeList nodes = activeCalls.getElementsByTagName("activecall");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element callEl) {
                out.add(parseActiveCallElement(callEl, "-"));
            }
        }
        return out;
    }

    public static IncomingPanelSnapshot.PanelActiveCallRow parseActiveCallElement(Element callEl, String campaignName) {
        IncomingPanelSnapshot.PanelActiveCallRow row = new IncomingPanelSnapshot.PanelActiveCallRow();
        row.campaignName = campaignName;
        row.phone = dash(text(callEl, "callnumber"));
        row.trunk = dash(text(callEl, "trunk"));
        row.status = formatCallStatus(text(callEl, "callstatus"), text(callEl, "trunk"));
        row.since = formatSinceTime(text(callEl, "queuestart"), text(callEl, "dialstart"));
        return row;
    }

    public static String callId(Element callEl) {
        return text(callEl, "callid");
    }

    public static String queueStart(Element callEl) {
        return text(callEl, "queuestart");
    }

    public static String dialStart(Element callEl) {
        return text(callEl, "dialstart");
    }

    public static List<IncomingPanelSnapshot.PanelAgentRow> parseAgents(Document response) {
        List<IncomingPanelSnapshot.PanelAgentRow> out = new ArrayList<>();
        Element inner = responseRoot(response);
        if (inner == null) {
            return out;
        }
        Element agents = firstElement(inner, "agents");
        if (agents == null) {
            return out;
        }
        NodeList nodes = agents.getElementsByTagName("agent");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element agentEl) {
                out.add(parseAgentElement(agentEl));
            }
        }
        return out;
    }

    public static IncomingPanelSnapshot.PanelAgentRow parseAgentElement(Element agentEl) {
        IncomingPanelSnapshot.PanelAgentRow row = new IncomingPanelSnapshot.PanelAgentRow();
        row.agent = dash(text(agentEl, "agentchannel"));
        row.rawStatus = text(agentEl, "status");
        if (row.rawStatus.isEmpty()) {
            row.rawStatus = "offline";
        }
        row.status = formatAgentStatus(agentEl, row.rawStatus);
        row.phone = dash(firstNonEmpty(text(agentEl, "callnumber"), nestedText(agentEl, "callinfo", "callnumber")));
        row.trunk = dash(firstNonEmpty(text(agentEl, "trunk"), nestedText(agentEl, "callinfo", "trunk")));
        row.since = formatAgentSince(agentEl, row.rawStatus);
        return row;
    }

    public static String agentQueueNumber(Element agentEl) {
        return firstNonEmpty(text(agentEl, "queuenumber"), nestedText(agentEl, "callinfo", "queuenumber"));
    }

    public static boolean isCallInRange(String queuestart, String dialstart,
            String rangeStart, String rangeEnd) {
        String t = !queuestart.isEmpty() ? queuestart : dialstart;
        if (t.isEmpty()) {
            return true;
        }
        String normalized = normalizeDateTime(t);
        if (rangeStart != null && !rangeStart.isEmpty() && normalized.compareTo(rangeStart) < 0) {
            return false;
        }
        return rangeEnd == null || rangeEnd.isEmpty() || normalized.compareTo(rangeEnd) <= 0;
    }

    private static String formatCallStatus(String status, String trunk) {
        if (status == null || status.isEmpty()) {
            return "-";
        }
        return switch (status.toLowerCase()) {
            case "onqueue" -> "En cola";
            case "ringing" -> "Sonando";
            case "success", "terminada" -> "Atendida";
            case "abandoned", "abandonada" -> "Abandonada";
            case "shortcall" -> "Corta";
            case "noanswer" -> "Sin respuesta";
            case "failure" -> "Fallo";
            case "placing" -> "Colocando";
            default -> status;
        };
    }

    private static String formatAgentStatus(Element agentEl, String raw) {
        boolean onHold = "1".equals(text(agentEl, "onhold"));
        if ("paused".equals(raw)) {
            if (onHold) {
                return "En espera (hold)";
            }
            Element pause = firstElement(agentEl, "pauseinfo");
            String name = pause != null ? text(pause, "pausename") : "";
            if (!name.isEmpty()) {
                return "En pausa: " + name;
            }
            return "En pausa";
        }
        if ("oncall".equals(raw)) {
            return onHold ? "En llamada (hold)" : "En llamada";
        }
        return switch (raw) {
            case "offline" -> "No logon";
            case "online" -> "Libre";
            case "ringing" -> "Sonando";
            default -> raw;
        };
    }

    private static String formatAgentSince(Element agentEl, String raw) {
        if ("paused".equals(raw)) {
            Element pause = firstElement(agentEl, "pauseinfo");
            if (pause != null) {
                return formatSinceTime(text(pause, "pausestart"), "");
            }
        }
        if ("oncall".equals(raw)) {
            return formatSinceTime(
                    firstNonEmpty(text(agentEl, "linkstart"), nestedText(agentEl, "callinfo", "linkstart")),
                    "");
        }
        return "-";
    }

    private static String formatSinceTime(String primary, String fallback) {
        String t = !primary.isEmpty() ? primary : fallback;
        if (t.isEmpty()) {
            return "-";
        }
        String today = LocalDate.now().toString();
        if (t.startsWith(today)) {
            return t.substring(today.length() + 1);
        }
        if (t.length() <= 8 && t.contains(":")) {
            return t;
        }
        return t;
    }

    private static String normalizeDateTime(String t) {
        String today = LocalDate.now().toString();
        if (t.length() <= 8 || !t.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            return today + " " + t;
        }
        return t;
    }

    public static Element campaignStatusInner(Document response) {
        return responseRoot(response);
    }

    public static boolean hasFailure(Element statusInner) {
        return statusInner != null && firstElement(statusInner, "failure") != null;
    }

    public static String failureDetail(Element statusInner) {
        if (statusInner == null) {
            return "";
        }
        Element failure = firstElement(statusInner, "failure");
        if (failure == null) {
            return "";
        }
        String code = text(failure, "code");
        String message = text(failure, "message");
        if (code.isEmpty() && message.isEmpty()) {
            return failure.getTextContent() == null ? "" : failure.getTextContent().trim();
        }
        return (code + " " + message).trim();
    }

    private static Element responseRoot(Document response) {
        Element root = response.getDocumentElement();
        if (root == null) {
            return null;
        }
        Element inner = firstElement(root, "getcampaignstatus_response");
        return inner != null ? inner : firstElement(root, "getincomingqueuestatus_response");
    }

    private static String nestedText(Element parent, String containerTag, String leafTag) {
        Element c = firstElement(parent, containerTag);
        return c == null ? "" : text(c, leafTag);
    }

    private static String firstNonEmpty(String a, String b) {
        return !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static String dash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
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
