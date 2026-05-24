package dn.demedallo.admin.service;

import dn.demedallo.admin.util.AdminLogSettings;

/**
 * Applies FreePBX/Issabel dialplan changes on the server via {@code fwconsole reload}.
 */
public final class PbxReloadService {

    private static final String RELOAD_CMD = "fwconsole reload 2>&1";

    private final RemoteLogTailService ssh = new RemoteLogTailService();
    private final AdminLogSettings logSettings;
    private final String host;

    public PbxReloadService(String eccpHost) {
        this.logSettings = AdminLogSettings.load();
        this.host = eccpHost == null || eccpHost.isBlank() ? "127.0.0.1" : eccpHost.trim();
    }

    public String sshHostLabel() {
        return host;
    }

    public boolean isSshReady() {
        return logSettings.isSshReady();
    }

    /**
     * Runs {@code fwconsole reload} on the Issabel server (may take 1–3 minutes).
     *
     * @return combined stdout/stderr from the remote command
     */
    public String reloadAsterisk() throws Exception {
        if (!isSshReady()) {
            throw new IllegalStateException(
                    "SSH no configurado. Active SSH en la pestaña «Logs Issabel» (usuario root).");
        }
        return ssh.exec(host, logSettings.sshPort, logSettings.sshUser,
                logSettings.sshPassword, RELOAD_CMD);
    }
}
