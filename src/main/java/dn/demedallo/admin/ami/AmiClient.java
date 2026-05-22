package dn.demedallo.admin.ami;

import dn.demedallo.admin.util.AppLogFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal Asterisk AMI client (Login + Originate only).
 */
public final class AmiClient implements AutoCloseable {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int readTimeoutMs = 8000;

    public void connect(String host, int port, int timeoutMs, String username, String secret) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        s.setSoTimeout(timeoutMs);
        this.readTimeoutMs = timeoutMs;
        this.socket = s;
        this.out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        drainWelcomeBanner();
        Map<String, String> login = sendAction(mapOf(
                "Action", "Login",
                "Username", username,
                "Secret", secret));
        if (!"Success".equalsIgnoreCase(login.get("Response"))) {
            throw new IOException("AMI login failed: " + login.getOrDefault("Message", "unknown"));
        }
        AppLogFile.appendLine("[ami] login ok user=" + username);
    }

    /**
     * Rings the supervisor extension and connects to spy target (SPAGE exten or ChanSpy app).
     */
    public void originateListen(String supervisorChannel, AdminMonitorOriginate params) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Action", "Originate");
        fields.put("ActionID", "listen-" + UUID.randomUUID());
        fields.put("Channel", supervisorChannel);
        fields.put("Async", "true");
        fields.put("Timeout", "30000");
        fields.put("CallerID", "\"Monitor\" <" + params.callerIdNum + ">");
        if (AdminMonitorOriginate.MODE_CHANSPY.equalsIgnoreCase(params.mode)) {
            fields.put("Application", "ChanSpy");
            fields.put("Data", params.chanSpyData);
        } else {
            fields.put("Context", params.context);
            fields.put("Exten", params.extension);
            fields.put("Priority", "1");
        }
        Map<String, String> resp = sendAction(fields);
        if (!"Success".equalsIgnoreCase(resp.get("Response"))) {
            String detail = formatAmiMessage(resp);
            AppLogFile.appendLine("[ami] originate failed: " + detail);
            throw new IOException("AMI Originate failed: " + detail);
        }
        AppLogFile.appendLine("[ami] originate listen ok channel=" + supervisorChannel
                + " target=" + (params.chanSpyData != null ? params.chanSpyData : params.extension));
    }

    private void drainWelcomeBanner() throws IOException {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    return;
                }
            }
        } catch (SocketTimeoutException ignored) {
        }
    }

    private static String formatAmiMessage(Map<String, String> resp) {
        String msg = resp.get("Message");
        if (msg == null || msg.isBlank()) {
            msg = resp.get("Response");
        }
        String reason = resp.get("Reason");
        if (reason != null && !reason.isBlank()) {
            return msg + " (" + reason + ")";
        }
        return msg == null ? "unknown" : msg;
    }

    private Map<String, String> sendAction(Map<String, String> fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (var e : fields.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        out.print(sb);
        out.flush();
        return readUntilResponseComplete();
    }

    private Map<String, String> readUntilResponseComplete() throws IOException {
        long deadline = System.currentTimeMillis() + readTimeoutMs;
        Map<String, String> msg = new LinkedHashMap<>();
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(500, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            String line;
            try {
                line = in.readLine();
            } catch (SocketTimeoutException ex) {
                throw new IOException("AMI timeout waiting for response", ex);
            }
            if (line == null) {
                throw new IOException("AMI connection closed");
            }
            if (line.isEmpty()) {
                if (msg.containsKey("Response")) {
                    return msg;
                }
                if (msg.containsKey("Event")) {
                    msg.clear();
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                msg.put(key, val);
            }
        }
        throw new IOException("AMI timeout waiting for response");
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Override
    public void close() {
        try {
            if (out != null) {
                out.print("Action: Logoff\r\n\r\n");
                out.flush();
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
        out = null;
        in = null;
    }
}
