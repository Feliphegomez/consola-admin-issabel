package dn.demedallo.admin.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * SSH and remote log paths for Issabel server log viewer tab.
 */
public final class AdminLogSettings {

    public static final String DEFAULT_DIALER_LOG = "/opt/issabel/dialer/dialerd.log";
    public static final String DEFAULT_ASTERISK_FULL = "/var/log/asterisk/full";
    public static final String DEFAULT_ASTERISK_MESSAGES = "/var/log/asterisk/messages";

    private static final String FILE = "admin-log-settings.properties";

    public boolean sshEnabled;
    public int sshPort = 22;
    public String sshUser = "root";
    public String sshPassword = "";
    public String dialerLogPath = DEFAULT_DIALER_LOG;
    public String asteriskFullPath = DEFAULT_ASTERISK_FULL;
    public String asteriskMessagesPath = DEFAULT_ASTERISK_MESSAGES;
    public int tailLines = 800;
    public boolean autoRefresh = true;
    public int refreshSeconds = 4;
    public String grepFilter = "";

    private AdminLogSettings() {
    }

    private static Path file() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "demedallo-admin-console", FILE);
        }
        return Path.of(System.getProperty("user.home"), ".demedallo-admin-console", FILE);
    }

    public static AdminLogSettings load() {
        AdminLogSettings s = new AdminLogSettings();
        Properties p = new Properties();
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                try (var in = Files.newInputStream(f)) {
                    p.load(in);
                }
            }
        } catch (Exception ignored) {
        }
        s.sshEnabled = "true".equalsIgnoreCase(p.getProperty("sshEnabled", "false"));
        s.sshPort = parseInt(p.getProperty("sshPort", "22"), 22);
        s.sshUser = p.getProperty("sshUser", "root");
        s.sshPassword = p.getProperty("sshPassword", "");
        s.dialerLogPath = p.getProperty("dialerLogPath", DEFAULT_DIALER_LOG);
        s.asteriskFullPath = p.getProperty("asteriskFullPath", DEFAULT_ASTERISK_FULL);
        s.asteriskMessagesPath = p.getProperty("asteriskMessagesPath", DEFAULT_ASTERISK_MESSAGES);
        s.tailLines = parseInt(p.getProperty("tailLines", "800"), 800);
        s.autoRefresh = !"false".equalsIgnoreCase(p.getProperty("autoRefresh", "true"));
        s.refreshSeconds = Math.max(2, parseInt(p.getProperty("refreshSeconds", "4"), 4));
        s.grepFilter = p.getProperty("grepFilter", "");
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("sshEnabled", Boolean.toString(sshEnabled));
        p.setProperty("sshPort", String.valueOf(sshPort));
        p.setProperty("sshUser", sshUser == null ? "" : sshUser.trim());
        if (sshPassword != null && !sshPassword.isEmpty()) {
            p.setProperty("sshPassword", sshPassword);
        }
        p.setProperty("dialerLogPath", dialerLogPath == null ? DEFAULT_DIALER_LOG : dialerLogPath.trim());
        p.setProperty("asteriskFullPath", asteriskFullPath == null ? DEFAULT_ASTERISK_FULL : asteriskFullPath.trim());
        p.setProperty("asteriskMessagesPath",
                asteriskMessagesPath == null ? DEFAULT_ASTERISK_MESSAGES : asteriskMessagesPath.trim());
        p.setProperty("tailLines", String.valueOf(tailLines));
        p.setProperty("autoRefresh", Boolean.toString(autoRefresh));
        p.setProperty("refreshSeconds", String.valueOf(refreshSeconds));
        p.setProperty("grepFilter", grepFilter == null ? "" : grepFilter);
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (var out = Files.newOutputStream(f)) {
                p.store(out, "Issabel admin console log viewer");
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[prefs] log settings save failed: " + ex.getMessage());
        }
    }

    public boolean isSshReady() {
        return sshEnabled
                && sshUser != null && !sshUser.isBlank()
                && sshPassword != null && !sshPassword.isBlank();
    }

    private static int parseInt(String t, int def) {
        try {
            return Integer.parseInt(t.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
