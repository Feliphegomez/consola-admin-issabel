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
        return formatLoginSeconds(sec);
    }

    public static String formatLoginSeconds(long sec) {
        if (sec <= 0) {
            return "—";
        }
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
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
