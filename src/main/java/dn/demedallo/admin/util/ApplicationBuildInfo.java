package dn.demedallo.admin.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application version baked at build time ({@code META-INF/demedallo-admin-build.properties}).
 */
public final class ApplicationBuildInfo {

    private static volatile String cachedVersion;

    private ApplicationBuildInfo() {
    }

    public static String version() {
        String v = cachedVersion;
        if (v != null) {
            return v;
        }
        synchronized (ApplicationBuildInfo.class) {
            v = cachedVersion;
            if (v != null) {
                return v;
            }
            v = loadVersionFromResource();
            cachedVersion = v;
            return v;
        }
    }

    private static String loadVersionFromResource() {
        try (InputStream in = ApplicationBuildInfo.class
                .getResourceAsStream("/META-INF/demedallo-admin-build.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String s = p.getProperty("app.version", "").trim();
                if (!s.isEmpty() && !s.startsWith("${")) {
                    return stripSnapshot(s);
                }
            }
        } catch (IOException ignored) {
        }
        Package pkg = ApplicationBuildInfo.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return stripSnapshot(pkg.getImplementationVersion().trim());
        }
        return "0.0.0-dev";
    }

    private static String stripSnapshot(String v) {
        String lower = v.toLowerCase();
        if (lower.endsWith("-snapshot")) {
            return v.substring(0, v.length() - "-SNAPSHOT".length());
        }
        return v;
    }
}
