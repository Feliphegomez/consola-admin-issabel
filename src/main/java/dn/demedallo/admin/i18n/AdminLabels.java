package dn.demedallo.admin.i18n;

import dn.demedallo.admin.model.AgentState;

public final class AdminLabels {

    private AdminLabels() {
    }

    public static String statusLabel(AgentState s) {
        if (s == null || s.status == null || s.status.isBlank()) {
            return "Desconocido";
        }
        return switch (s.status) {
            case "offline" -> "Desconectado";
            case "online" -> "Disponible";
            case "ringing" -> "Sonando";
            case "oncall" -> s.onHold ? "En espera (hold)" : "En llamada";
            case "paused" -> s.onHold ? "Pausa + hold" : "En pausa";
            default -> s.status;
        };
    }

    public static String campaignTypeLabel(String type) {
        if (type == null) {
            return "—";
        }
        return switch (type) {
            case "incoming" -> "Entrante";
            case "outgoing" -> "Saliente";
            default -> type;
        };
    }

    public static String campaignStatusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return switch (status) {
            case "active" -> "Activa";
            case "inactive" -> "Inactiva";
            case "finished" -> "Finalizada";
            default -> status;
        };
    }

    public static String callTypeLabel(String callType) {
        if (callType == null || callType.isBlank()) {
            return "—";
        }
        return switch (callType) {
            case "incoming" -> "Entrante";
            case "outgoing" -> "Saliente";
            default -> callType;
        };
    }

    public static String formatDurationSeconds(long sec) {
        return formatDaysHms(sec);
    }

    public static String formatLoginSeconds(long sec) {
        return formatDaysHms(sec);
    }

    /** Duration as {@code days H:mm:ss} (e.g. {@code 0 08:11:56}, {@code 1 05:30:00}). */
    public static String formatDaysHms(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 00:00:00";
        }
        long days = totalSeconds / 86400L;
        long remainder = totalSeconds % 86400L;
        long hours = remainder / 3600L;
        long minutes = (remainder % 3600L) / 60L;
        long seconds = remainder % 60L;
        return String.format("%d %d:%02d:%02d", days, hours, minutes, seconds);
    }

    public static String formatSessionRange(String start, String end) {
        if (start == null || start.isBlank()) {
            return "—";
        }
        if (end == null || end.isBlank()) {
            return start + " → activa";
        }
        return start + " → " + end;
    }

    public static String formatPauseInfo(AgentState s) {
        if (s == null || !"paused".equals(s.status)) {
            return "";
        }
        if (s.pauseName != null && !s.pauseName.isBlank()) {
            return s.pauseName;
        }
        if (s.pauseId != null) {
            return "Pausa #" + s.pauseId;
        }
        return "Pausa";
    }

    public static String formatCallsBreakdown(int incoming, int outgoing) {
        if (incoming == 0 && outgoing == 0) {
            return "—";
        }
        return "Ent: " + incoming + " · Sal: " + outgoing;
    }
}
