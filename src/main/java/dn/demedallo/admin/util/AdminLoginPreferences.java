package dn.demedallo.admin.util;

import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists ECCP connection fields for the admin console (same idea as agent console).
 */
public final class AdminLoginPreferences {

    private static final String FILE = "admin-login.properties";

    private AdminLoginPreferences() {
    }

    private static Path file() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "demedallo-admin-console", FILE);
        }
        return Path.of(System.getProperty("user.home"), ".demedallo-admin-console", FILE);
    }

    public static void loadInto(TextField host, TextField port, TextField user, PasswordField pass,
                                CheckBox remember) {
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
        host.setText(p.getProperty("host", "127.0.0.1"));
        port.setText(p.getProperty("port", String.valueOf(20005)));
        user.setText(p.getProperty("eccpUser", "agentconsole"));
        remember.setSelected("true".equalsIgnoreCase(p.getProperty("remember", "false")));
        if (remember.isSelected()) {
            pass.setText(p.getProperty("eccpPass", ""));
        }
    }

    public static void saveFrom(TextField host, TextField port, TextField user, PasswordField pass,
                                CheckBox remember) {
        Properties p = new Properties();
        p.setProperty("host", host.getText().trim());
        p.setProperty("port", port.getText().trim());
        p.setProperty("eccpUser", user.getText().trim());
        p.setProperty("remember", Boolean.toString(remember.isSelected()));
        if (remember.isSelected()) {
            p.setProperty("eccpPass", pass.getText());
        } else {
            p.remove("eccpPass");
        }
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (var out = Files.newOutputStream(f)) {
                p.store(out, "Issabel admin console login");
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[prefs] save failed: " + ex.getMessage());
        }
    }
}
