package dn.demedallo.admin.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * call_center database credentials for historical Issabel reports.
 */
public final class AdminDbSettings {

    private static final String FILE = "admin-db.properties";

    public boolean dbEnabled;
    public String dbHost = "127.0.0.1";
    public int dbPort = 3306;
    public String dbName = "call_center";
    /** FreePBX/Issabel PBX configuration database (usually {@code asterisk}). */
    public String pbxDbName = "asterisk";
    /** Issabel CDR database (usually {@code asteriskcdrdb}). */
    public String cdrDbName = "asteriskcdrdb";
    public String dbUser = "asterisk";
    public String dbPassword = "";

    public static AdminDbSettings load() {
        AdminDbSettings s = new AdminDbSettings();
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
        s.dbEnabled = "true".equalsIgnoreCase(p.getProperty("dbEnabled", "false"));
        s.dbHost = p.getProperty("dbHost", "127.0.0.1");
        s.dbPort = parseInt(p.getProperty("dbPort", "3306"), 3306);
        s.dbName = p.getProperty("dbName", "call_center");
        s.pbxDbName = p.getProperty("pbxDbName", "asterisk");
        s.cdrDbName = p.getProperty("cdrDbName", "asteriskcdrdb");
        s.dbUser = p.getProperty("dbUser", "asterisk");
        s.dbPassword = p.getProperty("dbPassword", "");
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("dbEnabled", Boolean.toString(dbEnabled));
        p.setProperty("dbHost", dbHost == null ? "" : dbHost.trim());
        p.setProperty("dbPort", String.valueOf(dbPort));
        p.setProperty("dbName", dbName == null ? "call_center" : dbName.trim());
        p.setProperty("pbxDbName", pbxDbName == null || pbxDbName.isBlank() ? "asterisk" : pbxDbName.trim());
        p.setProperty("cdrDbName", cdrDbName == null || cdrDbName.isBlank() ? "asteriskcdrdb" : cdrDbName.trim());
        p.setProperty("dbUser", dbUser == null ? "" : dbUser.trim());
        if (dbPassword != null && !dbPassword.isEmpty()) {
            p.setProperty("dbPassword", dbPassword);
        }
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (var out = Files.newOutputStream(f)) {
                p.store(out, "Issabel admin console DB");
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[prefs] db settings save failed: " + ex.getMessage());
        }
    }

    public boolean isConfigured() {
        return dbEnabled
                && dbHost != null && !dbHost.isBlank()
                && dbUser != null && !dbUser.isBlank()
                && dbPassword != null && !dbPassword.isBlank();
    }

    public String jdbcUrl() {
        return jdbcUrlFor(dbName == null || dbName.isBlank() ? "call_center" : dbName.trim());
    }

    public String jdbcUrlPbx() {
        return jdbcUrlFor(pbxDbName == null || pbxDbName.isBlank() ? "asterisk" : pbxDbName.trim());
    }

    public String jdbcUrlCdr() {
        return jdbcUrlFor(cdrDbName == null || cdrDbName.isBlank() ? "asteriskcdrdb" : cdrDbName.trim());
    }

    private String jdbcUrlFor(String database) {
        String host = dbHost == null ? "127.0.0.1" : dbHost.trim();
        return "jdbc:mariadb://" + host + ":" + dbPort + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8";
    }

    private static Path file() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "demedallo-admin-console", FILE);
        }
        return Path.of(System.getProperty("user.home"), ".demedallo-admin-console", FILE);
    }

    private static int parseInt(String t, int def) {
        try {
            return Integer.parseInt(t.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
