package dn.demedallo.admin.util;

/**
 * Human-readable labels for Issabel call_center statuses (Spanish for supervisors).
 */
public final class CallStatusLabels {

    private CallStatusLabels() {
    }

    public static String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status) {
            case "Failure" -> "Fallo";
            case "ShortCall" -> "Llamada corta";
            case "Success" -> "Éxito / atendida";
            case "NoAnswer" -> "No contesta";
            case "Abandoned" -> "Abandonada";
            case "Placing" -> "Colocando";
            case "Ringing" -> "Timbrando";
            case "OnQueue" -> "En cola";
            case "OnHold" -> "En espera (hold)";
            case "Hangup" -> "Finalizada";
            case "Busy" -> "Ocupado";
            case "Congestion" -> "Congestión";
            case "ChannelUnavailable" -> "Canal no disponible";
            default -> status;
        };
    }

    /**
     * Short narrative for one {@code call_progress_log} step.
     */
    public static String traceSummary(String rawStatus, int retry, String trunk, String agent, String duration) {
        String base = traceEventPhrase(rawStatus);
        StringBuilder sb = new StringBuilder(base);
        if (retry > 0) {
            sb.append(" — intento ").append(retry);
        }
        if (isPresent(trunk)) {
            sb.append(" — troncal ").append(trunk.trim());
        }
        if (isPresent(agent)) {
            sb.append(" — agente ").append(agent.trim());
        }
        if (isPresent(duration) && !"00:00:00".equals(duration.trim())) {
            sb.append(" — duración del paso ").append(duration.trim());
        }
        return sb.toString();
    }

    private static String traceEventPhrase(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "Evento registrado en el sistema";
        }
        return switch (rawStatus) {
            case "Placing" -> "El dialer comenzó a marcar el número";
            case "Ringing" -> "El teléfono destino está timbrando";
            case "OnQueue" -> "La llamada entró en cola de espera";
            case "OnHold" -> "La llamada quedó en espera (hold)";
            case "Success" -> "La llamada fue atendida correctamente";
            case "Failure" -> "La llamada terminó en fallo";
            case "ShortCall" -> "Llamada muy corta (colgó sin conversación útil)";
            case "NoAnswer" -> "No hubo respuesta del destino";
            case "Abandoned" -> "El cliente abandonó la llamada en cola";
            case "Hangup" -> "La llamada finalizó (colgado)";
            case "Busy" -> "Línea ocupada";
            case "Congestion" -> "Congestión en la red o troncal";
            case "ChannelUnavailable" -> "No se pudo usar el canal de salida";
            default -> statusLabel(rawStatus);
        };
    }

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank() && !"-".equals(s.trim());
    }
}
