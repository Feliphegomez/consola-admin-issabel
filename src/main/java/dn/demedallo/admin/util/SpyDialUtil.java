package dn.demedallo.admin.util;

/**
 * Builds Issabel listen dial string: feature prefix + agent extension (no asterisk).
 * Example: prefix 555 + extension 8002 → 5558002
 */
public final class SpyDialUtil {

    private SpyDialUtil() {
    }

    public static String defaultPrefix() {
        return "555";
    }

    public static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return defaultPrefix();
        }
        return prefix.trim().replace("*", "");
    }

    public static String buildDialExtension(String prefix, String agentExtension) {
        String ext = agentExtension == null ? "" : agentExtension.trim();
        return normalizePrefix(prefix) + ext;
    }
}
