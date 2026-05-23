package dn.demedallo.admin.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tails log files on the Issabel server via SSH ({@code tail -n}).
 */
public final class RemoteLogTailService {

    private static final int CONNECT_MS = 12_000;
    private static final int COMMAND_MS = 25_000;

    public String tail(String host, int sshPort, String user, String password, String remotePath, int lines)
            throws Exception {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host SSH vacío");
        }
        if (remotePath == null || remotePath.isBlank()) {
            throw new IllegalArgumentException("Ruta de log vacía");
        }
        int n = Math.max(50, Math.min(lines, 5000));
        String safePath = remotePath.replace("'", "'\\''");
        String cmd = "tail -n " + n + " '" + safePath + "' 2>&1";

        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host.trim(), sshPort);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(CONNECT_MS);
        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(cmd);
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setErrStream(err);
            InputStream in = channel.getInputStream();
            channel.connect(COMMAND_MS);
            byte[] buf = new byte[8192];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (true) {
                while (in.available() > 0) {
                    int r = in.read(buf);
                    if (r < 0) {
                        break;
                    }
                    out.write(buf, 0, r);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) {
                        continue;
                    }
                    break;
                }
                Thread.sleep(50);
            }
            int exit = channel.getExitStatus();
            channel.disconnect();
            String text = out.toString(StandardCharsets.UTF_8);
            if (exit != 0 && text.isBlank()) {
                String errText = err.toString(StandardCharsets.UTF_8);
                throw new java.io.IOException(
                        "tail exit " + exit + (errText.isBlank() ? "" : ": " + errText.trim()));
            }
            return text;
        } finally {
            session.disconnect();
        }
    }

    public static String applyGrepFilter(String raw, String grepPattern) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (grepPattern == null || grepPattern.isBlank()) {
            return raw;
        }
        Pattern p;
        try {
            p = Pattern.compile(grepPattern, Pattern.CASE_INSENSITIVE);
        } catch (Exception ex) {
            p = Pattern.compile(Pattern.quote(grepPattern), Pattern.CASE_INSENSITIVE);
        }
        List<String> kept = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (p.matcher(line).find()) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }
}
