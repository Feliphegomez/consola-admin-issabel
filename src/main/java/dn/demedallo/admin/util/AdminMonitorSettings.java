package dn.demedallo.admin.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Supervisor extension and optional AMI credentials for call listening (feature code + ChanSpy).
 */
public final class AdminMonitorSettings {

    /** Originate to dialplan extension (e.g. 5558002 = prefix 555 + agent ext). */
    public static final String MODE_DIALPLAN = "DIALPLAN";
    /** @deprecated use {@link #MODE_DIALPLAN}; kept for properties compatibility */
    public static final String MODE_SPAGE = MODE_DIALPLAN;
    public static final String MODE_CHANSPY = "CHANSPY";

    private static final String FILE = "admin-monitor.properties";

    public String supervisorExtension = "";
    public String channelTech = "PJSIP";
    public String spyPrefix = SpyDialUtil.defaultPrefix();
    public String listenMode = MODE_DIALPLAN;
    public String dialContext = "from-internal";

    public boolean amiEnabled;
    public String amiHost = "127.0.0.1";
    public int amiPort = 5038;
    public String amiUser = "";
    public String amiSecret = "";

    private AdminMonitorSettings() {
    }

    private static Path file() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "demedallo-admin-console", FILE);
        }
        return Path.of(System.getProperty("user.home"), ".demedallo-admin-console", FILE);
    }

    public static AdminMonitorSettings load() {
        AdminMonitorSettings s = new AdminMonitorSettings();
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
        s.supervisorExtension = p.getProperty("supervisorExtension", "");
        s.channelTech = p.getProperty("channelTech", "PJSIP");
        s.spyPrefix = SpyDialUtil.normalizePrefix(p.getProperty("spyPrefix", SpyDialUtil.defaultPrefix()));
        if ("SPAGE".equalsIgnoreCase(p.getProperty("spyPrefix", "").trim())) {
            s.spyPrefix = SpyDialUtil.defaultPrefix();
        }
        s.listenMode = normalizeListenMode(p.getProperty("listenMode", MODE_DIALPLAN));
        s.dialContext = p.getProperty("dialContext", "from-internal");
        s.amiEnabled = "true".equalsIgnoreCase(p.getProperty("amiEnabled", "false"));
        s.amiHost = p.getProperty("amiHost", "127.0.0.1");
        s.amiPort = parseInt(p.getProperty("amiPort", "5038"), 5038);
        s.amiUser = p.getProperty("amiUser", "");
        s.amiSecret = p.getProperty("amiSecret", "");
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("supervisorExtension", supervisorExtension == null ? "" : supervisorExtension.trim());
        p.setProperty("channelTech", channelTech == null ? "PJSIP" : channelTech.trim());
        p.setProperty("spyPrefix", spyPrefix == null ? SpyDialUtil.defaultPrefix() : SpyDialUtil.normalizePrefix(spyPrefix));
        p.setProperty("listenMode", listenMode == null ? MODE_DIALPLAN : listenMode.trim());
        p.setProperty("dialContext", dialContext == null ? "from-internal" : dialContext.trim());
        p.setProperty("amiEnabled", Boolean.toString(amiEnabled));
        p.setProperty("amiHost", amiHost == null ? "" : amiHost.trim());
        p.setProperty("amiPort", String.valueOf(amiPort));
        p.setProperty("amiUser", amiUser == null ? "" : amiUser.trim());
        if (amiSecret != null && !amiSecret.isEmpty()) {
            p.setProperty("amiSecret", amiSecret);
        }
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (var out = Files.newOutputStream(f)) {
                p.store(out, "Issabel admin console monitor");
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[prefs] monitor settings save failed: " + ex.getMessage());
        }
    }

    public boolean isListenConfigured() {
        return supervisorExtension != null && !supervisorExtension.isBlank()
                && amiEnabled
                && amiUser != null && !amiUser.isBlank()
                && amiSecret != null && !amiSecret.isBlank();
    }

    private static int parseInt(String t, int def) {
        try {
            return Integer.parseInt(t.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String normalizeListenMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_DIALPLAN;
        }
        if ("SPAGE".equalsIgnoreCase(mode.trim())) {
            return MODE_DIALPLAN;
        }
        return mode.trim();
    }
}
