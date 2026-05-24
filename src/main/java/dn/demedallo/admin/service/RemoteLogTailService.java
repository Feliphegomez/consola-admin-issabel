package dn.demedallo.admin.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tails log files on the Issabel server via SSH ({@code tail -n}).
 */
public final class RemoteLogTailService {

    private static final int CONNECT_MS = 12_000;
    private static final int COMMAND_MS = 25_000;
    private static final int SFTP_MS = 120_000;

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

    public String grepRemote(String host, int sshPort, String user, String password,
            String remotePath, String extendedRegex, int maxLines) throws Exception {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host SSH vacío");
        }
        if (remotePath == null || remotePath.isBlank()) {
            throw new IllegalArgumentException("Ruta de log vacía");
        }
        if (extendedRegex == null || extendedRegex.isBlank()) {
            return "";
        }
        int n = Math.max(10, Math.min(maxLines, 150));
        String safePath = remotePath.replace("'", "'\\''");
        String safePat = extendedRegex.replace("'", "'\\''");
        String cmd = "grep -E '" + safePat + "' '" + safePath + "' 2>/dev/null | tail -n " + n;

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
            channel.disconnect();
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            session.disconnect();
        }
    }

    /**
     * Runs a shell command on the remote host and returns combined stdout (stderr on failure).
     */
    public String exec(String host, int sshPort, String user, String password, String shellCommand)
            throws Exception {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host SSH vacío");
        }
        if (shellCommand == null || shellCommand.isBlank()) {
            throw new IllegalArgumentException("Comando vacío");
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host.trim(), sshPort);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(CONNECT_MS);
        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(shellCommand);
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
            String errText = err.toString(StandardCharsets.UTF_8).trim();
            if (exit != 0 && text.isBlank() && !errText.isBlank()) {
                throw new java.io.IOException("Comando remoto exit " + exit + ": " + errText);
            }
            if (!errText.isBlank() && !text.contains(errText)) {
                text = text + (text.isBlank() ? "" : "\n") + errText;
            }
            return text;
        } finally {
            session.disconnect();
        }
    }

    /**
     * Downloads a remote file via SFTP into a local path.
     */
    public void downloadFile(String host, int sshPort, String user, String password,
            String remotePath, Path localFile) throws Exception {
        if (remotePath == null || remotePath.isBlank()) {
            throw new IllegalArgumentException("Ruta remota vacía");
        }
        Files.createDirectories(localFile.getParent());
        withSftp(host, sshPort, user, password, sftp -> {
            try (OutputStream out = Files.newOutputStream(localFile)) {
                sftp.get(remotePath, out);
            }
        });
    }

    /** Returns true if the remote path exists (regular file). */
    public boolean remoteFileExists(String host, int sshPort, String user, String password,
            String remotePath) throws Exception {
        if (remotePath == null || remotePath.isBlank()) {
            return false;
        }
        try {
            withSftp(host, sshPort, user, password, sftp -> sftp.lstat(remotePath));
            return true;
        } catch (SftpException ex) {
            if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw ex;
        }
    }

    /** Deletes a remote file via SFTP. */
    public void deleteRemoteFile(String host, int sshPort, String user, String password,
            String remotePath) throws Exception {
        if (remotePath == null || remotePath.isBlank()) {
            throw new IllegalArgumentException("Ruta remota vacía");
        }
        withSftp(host, sshPort, user, password, sftp -> sftp.rm(remotePath));
    }

    @FunctionalInterface
    public interface SftpAction {
        void run(ChannelSftp sftp) throws Exception;
    }

    private void withSftp(String host, int sshPort, String user, String password, SftpAction action)
            throws Exception {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host SSH vacío");
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host.trim(), sshPort);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(CONNECT_MS);
        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(SFTP_MS);
            try {
                action.run(sftp);
            } finally {
                sftp.disconnect();
            }
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
