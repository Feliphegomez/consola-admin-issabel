package dn.demedallo.admin.util;

import dn.demedallo.admin.model.AgentState;

/**
 * Resolves Issabel ChanSpy / SPAGE target from agent ECCP fields.
 */
public final class SpyTargetUtil {

    private SpyTargetUtil() {
    }

    public static String resolveSpyExtension(AgentState st, String agentChannel) {
        if (st != null && st.extension != null && !st.extension.isBlank()) {
            return st.extension.trim();
        }
        return extensionFromAgentChannel(agentChannel);
    }

    public static String extensionFromAgentChannel(String agentChannel) {
        if (agentChannel == null || agentChannel.isBlank()) {
            return "";
        }
        String ch = agentChannel.trim();
        int slash = ch.indexOf('/');
        String ext = slash >= 0 && slash + 1 < ch.length()
                ? ch.substring(slash + 1).trim()
                : ch;
        int dash = ext.indexOf('-');
        if (dash > 0) {
            ext = ext.substring(0, dash);
        }
        return ext;
    }

    /** PJSIP, SIP, IAX2 from channel string (e.g. PJSIP/8002-xxx). */
    public static String techFromChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "";
        }
        String ch = channel.trim();
        int slash = ch.indexOf('/');
        if (slash <= 0) {
            return "";
        }
        return ch.substring(0, slash).trim().toUpperCase();
    }

    public static String channelName(String tech, String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String t = tech == null || tech.isBlank() ? "PJSIP" : tech.trim();
        return t + "/" + extension.trim();
    }

    public static boolean canListen(String statusCode) {
        return "oncall".equals(statusCode) || "ringing".equals(statusCode);
    }
}
