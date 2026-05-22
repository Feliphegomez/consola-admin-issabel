package dn.demedallo.admin.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public final class CampaignListParser {

    private CampaignListParser() {
    }

    public static List<CampaignRef> parse(Document response) {
        List<CampaignRef> out = new ArrayList<>();
        Element root = response.getDocumentElement();
        if (root == null) {
            return out;
        }
        Element inner = firstElement(root, "getcampaignlist_response");
        if (inner == null) {
            return out;
        }
        Element campaigns = firstElement(inner, "campaigns");
        if (campaigns == null) {
            return out;
        }
        NodeList nodes = campaigns.getElementsByTagName("campaign");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (!(n instanceof Element c)) {
                continue;
            }
            CampaignRef ref = new CampaignRef();
            ref.type = text(c, "type");
            ref.id = parseInt(text(c, "id"));
            ref.name = text(c, "name");
            ref.status = text(c, "status");
            if (ref.id > 0 && !ref.type.isEmpty()) {
                out.add(ref);
            }
        }
        return out;
    }

    public static List<IncomingQueueRef> parseIncomingQueues(Document response) {
        List<IncomingQueueRef> out = new ArrayList<>();
        Element root = response.getDocumentElement();
        if (root == null) {
            return out;
        }
        Element inner = firstElement(root, "getincomingqueuelist_response");
        if (inner == null) {
            return out;
        }
        Element queues = firstElement(inner, "queues");
        if (queues == null) {
            return out;
        }
        NodeList nodes = queues.getElementsByTagName("queue");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (!(n instanceof Element q)) {
                continue;
            }
            IncomingQueueRef ref = new IncomingQueueRef();
            ref.id = parseInt(text(q, "id"));
            ref.queue = text(q, "queue");
            ref.status = text(q, "status");
            if (!ref.queue.isEmpty()) {
                out.add(ref);
            }
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

    private static int parseInt(String t) {
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
