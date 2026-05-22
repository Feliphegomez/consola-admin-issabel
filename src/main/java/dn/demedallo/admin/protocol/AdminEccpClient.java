package dn.demedallo.admin.protocol;

import dn.demedallo.admin.crypto.Md5Util;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.xml.EccpPacketFramer;
import dn.demedallo.admin.xml.XmlEscapes;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * ECCP client for supervisor/admin operations (no agent login).
 */
public final class AdminEccpClient implements AutoCloseable {

    public static final int DEFAULT_PORT = 20005;

    private final EccpPacketFramer framer;
    private final DocumentBuilder docBuilder;
    private final Queue<String> pendingPackets = new ArrayDeque<>();
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int requestId;
    private String appCookie = "";
    private final byte[] readBuf = new byte[65536];

    public AdminEccpClient() throws ParserConfigurationException {
        this.framer = new EccpPacketFramer();
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setIgnoringComments(true);
        f.setCoalescing(true);
        this.docBuilder = f.newDocumentBuilder();
    }

    public void connect(String host, int port, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Objects.requireNonNull(host, "host");
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setSoTimeout(readTimeoutMs);
        s.setTcpNoDelay(true);
        this.socket = s;
        this.in = s.getInputStream();
        this.out = s.getOutputStream();
        AppLogFile.appendLine("[eccp] CONNECT host=" + host + " port=" + port);
    }

    public String getAppCookie() {
        return appCookie;
    }

    public Document login(String username, String password) throws IOException, SAXException {
        String passField = Md5Util.looksLikeMd5Hex32(password) ? password : Md5Util.md5Hex(password);
        String inner = "<login><username>" + XmlEscapes.text(username)
                + "</username><password>" + XmlEscapes.text(passField) + "</password></login>";
        Document r = sendRequest(inner);
        Element root = r.getDocumentElement();
        if (root == null) {
            throw new IOException("ECCP login: empty response");
        }
        var cookies = root.getElementsByTagName("app_cookie");
        if (cookies.getLength() == 0) {
            throw new IOException("ECCP login: missing app_cookie");
        }
        appCookie = cookies.item(0).getTextContent().trim();
        if (appCookie.isEmpty()) {
            throw new IOException("ECCP login: empty app_cookie");
        }
        return r;
    }

    public Document logout() throws IOException, SAXException {
        return sendRequest("<logout/>");
    }

    public Document getAgentActivitySummary(String dateStart, String dateEnd) throws IOException, SAXException {
        StringBuilder inner = new StringBuilder("<getagentactivitysummary>");
        if (dateStart != null && !dateStart.isBlank()) {
            inner.append("<datetime_start>").append(XmlEscapes.text(dateStart)).append("</datetime_start>");
        }
        if (dateEnd != null && !dateEnd.isBlank()) {
            inner.append("<datetime_end>").append(XmlEscapes.text(dateEnd)).append("</datetime_end>");
        }
        inner.append("</getagentactivitysummary>");
        return sendRequest(inner.toString());
    }

    public Document getMultipleAgentStatus(List<String> agentNumbers) throws IOException, SAXException {
        return sendRequest(buildAgentListRequest("getmultipleagentstatus", agentNumbers));
    }

    public Document getMultipleAgentQueues(List<String> agentNumbers) throws IOException, SAXException {
        return sendRequest(buildAgentListRequest("getmultipleagentqueues", agentNumbers));
    }

    public Document getIncomingQueueList() throws IOException, SAXException {
        return sendRequest("<getincomingqueuelist/>");
    }

    public Document getCampaignList(String status) throws IOException, SAXException {
        StringBuilder inner = new StringBuilder("<getcampaignlist>");
        if (status != null && !status.isBlank()) {
            inner.append("<status>").append(XmlEscapes.text(status)).append("</status>");
        }
        inner.append("</getcampaignlist>");
        return sendRequest(inner.toString());
    }

    public Document getCampaignStatus(String campaignType, int campaignId, String dateStart)
            throws IOException, SAXException {
        StringBuilder inner = new StringBuilder("<getcampaignstatus>");
        inner.append("<campaign_type>").append(XmlEscapes.text(campaignType)).append("</campaign_type>");
        inner.append("<campaign_id>").append(campaignId).append("</campaign_id>");
        if (dateStart != null && !dateStart.isBlank()) {
            inner.append("<datetime_start>").append(XmlEscapes.text(dateStart)).append("</datetime_start>");
        }
        inner.append("</getcampaignstatus>");
        return sendRequest(inner.toString());
    }

    public Document getIncomingQueueStatus(String queue, String dateStart) throws IOException, SAXException {
        StringBuilder inner = new StringBuilder("<getincomingqueuestatus>");
        inner.append("<queue>").append(XmlEscapes.text(queue)).append("</queue>");
        if (dateStart != null && !dateStart.isBlank()) {
            inner.append("<datetime_start>").append(XmlEscapes.text(dateStart)).append("</datetime_start>");
        }
        inner.append("</getincomingqueuestatus>");
        return sendRequest(inner.toString());
    }

    private static String buildAgentListRequest(String tag, List<String> agentNumbers) {
        StringBuilder inner = new StringBuilder("<").append(tag).append("><agents>");
        for (String n : agentNumbers) {
            if (n != null && !n.isBlank()) {
                inner.append("<agent_number>").append(XmlEscapes.text(n.trim())).append("</agent_number>");
            }
        }
        inner.append("</agents></").append(tag).append(">");
        return inner.toString();
    }

    private Document sendRequest(String innerRequestXml) throws IOException, SAXException {
        if (socket == null || !socket.isConnected()) {
            throw new IOException("not connected");
        }
        int id = ++requestId;
        String cmd = commandTag(innerRequestXml);
        boolean logThis = !"getmultipleagentstatus".equals(cmd) && !"getmultipleagentqueues".equals(cmd);
        if (logThis) {
            AppLogFile.appendLine("[eccp] -> id=" + id + " cmd=" + cmd);
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request id=\"" + id + "\">"
                + innerRequestXml + "</request>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        long t0 = System.nanoTime();
        try {
            out.write(bytes);
            out.flush();
            while (true) {
                Document doc = readNextPacketDom();
                Element root = doc.getDocumentElement();
                if (root == null || !"response".equals(root.getNodeName())) {
                    continue;
                }
                String rid = root.getAttribute("id");
                if (rid.isEmpty() || Integer.parseInt(rid) != id) {
                    throw new IOException("ECCP id mismatch: expected " + id + " got " + rid);
                }
                String fail = failureMessage(root);
                if (fail != null) {
                    throw new IOException("ECCP failure: " + fail);
                }
                if (logThis) {
                    long dt = (System.nanoTime() - t0) / 1_000_000L;
                    AppLogFile.appendLine("[eccp] <- id=" + id + " cmd=" + cmd + " ok " + dt + "ms");
                }
                return doc;
            }
        } catch (IOException | SAXException ex) {
            if (logThis) {
                AppLogFile.appendLine("[eccp] ERROR id=" + id + " cmd=" + cmd + ": " + ex.getMessage());
            }
            throw ex;
        }
    }

    private Document readNextPacketDom() throws IOException, SAXException {
        String p = nextPacketXml();
        return docBuilder.parse(new ByteArrayInputStream(p.getBytes(StandardCharsets.UTF_8)));
    }

    private String nextPacketXml() throws IOException, SAXException {
        while (true) {
            if (!pendingPackets.isEmpty()) {
                return pendingPackets.poll();
            }
            for (String p : framer.drainCompletePackets()) {
                pendingPackets.offer(p);
            }
            if (!pendingPackets.isEmpty()) {
                return pendingPackets.poll();
            }
            int n = in.read(readBuf);
            if (n < 0) {
                throw new IOException("ECCP socket closed");
            }
            if (n > 0) {
                framer.appendUtf8(readBuf, n);
            }
        }
    }

    private static String failureMessage(Element responseRoot) {
        var failures = responseRoot.getElementsByTagName("failure");
        if (failures.getLength() == 0) {
            return null;
        }
        Element f = (Element) failures.item(0);
        var msgs = f.getElementsByTagName("message");
        if (msgs.getLength() == 0) {
            return f.getTextContent();
        }
        return msgs.item(0).getTextContent();
    }

    private static String commandTag(String innerRequestXml) {
        if (innerRequestXml == null) {
            return "unknown";
        }
        int lt = innerRequestXml.indexOf('<');
        if (lt < 0 || lt + 1 >= innerRequestXml.length()) {
            return "unknown";
        }
        int i = lt + 1;
        if (innerRequestXml.charAt(i) == '/') {
            i++;
        }
        int j = i;
        while (j < innerRequestXml.length()) {
            char c = innerRequestXml.charAt(j);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                break;
            }
            j++;
        }
        return j <= i ? "unknown" : innerRequestXml.substring(i, j);
    }

    @Override
    public void close() {
        try {
            if (socket != null && socket.isConnected() && appCookie != null && !appCookie.isEmpty()) {
                logout();
            }
        } catch (Exception ignored) {
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        in = null;
        out = null;
        appCookie = "";
    }
}
