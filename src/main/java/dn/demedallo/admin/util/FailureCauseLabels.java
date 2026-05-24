package dn.demedallo.admin.util;

/**
 * Human-readable failure reasons from Issabel {@code calls.failure_cause} / {@code failure_cause_txt}.
 */
public final class FailureCauseLabels {

    private FailureCauseLabels() {
    }

    /**
     * Combined label for supervisors (Spanish).
     */
    public static String formatReason(Object failureCode, String failureText, String callStatus) {
        String txt = failureText == null ? "" : failureText.trim();
        Integer code = parseCode(failureCode);

        if (!txt.isEmpty() && !"-".equals(txt)) {
            if (code != null) {
                return txt + " (cód. " + code + " — " + codeLabel(code) + ")";
            }
            return txt;
        }
        if (code != null) {
            return codeLabel(code) + " (cód. " + code + ")";
        }
        return inferFromStatus(callStatus);
    }

    private static Integer parseCode(Object failureCode) {
        if (failureCode == null) {
            return null;
        }
        if (failureCode instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(failureCode).trim();
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Q.850 / Asterisk hangup cause codes commonly stored by the dialer. */
    private static String codeLabel(int code) {
        return switch (code) {
            case 0 -> "Fallo genérico / sin detalle SIP";
            case 1 -> "Número no asignado";
            case 2 -> "Sin ruta a la red";
            case 3 -> "Sin ruta al destino";
            case 16 -> "Colgado normal";
            case 17 -> "Usuario ocupado";
            case 18 -> "Sin respuesta del usuario";
            case 19 -> "Sin respuesta (alertado)";
            case 20 -> "Suscriptor ausente";
            case 21 -> "Llamada rechazada";
            case 22 -> "Número cambiado";
            case 27 -> "Destino fuera de servicio";
            case 28 -> "Formato de número inválido";
            case 29 -> "Facilidad rechazada";
            case 31 -> "Normal, sin especificar";
            case 34 -> "Sin circuito disponible";
            case 38 -> "Red fuera de servicio";
            case 41 -> "Congestión temporal";
            case 42 -> "Conmutador congestionado";
            case 44 -> "Canal no disponible";
            case 47 -> "Recursos no disponibles";
            case 50 -> "Facilidad no suscrita";
            case 52 -> "Llamada no completada";
            case 54 -> "Llamada bloqueada";
            case 57 -> "Capacidad de portador no autorizada";
            case 58 -> "Capacidad no disponible en este momento";
            case 65 -> "Capacidad de portador no implementada";
            case 66 -> "Tipo de canal no implementado";
            case 69 -> "Facilidad solicitada no implementada";
            case 79 -> "Servicio no implementado";
            case 87 -> "Usuario no disponible";
            case 88 -> "Destino incompatible";
            case 102 -> "Tiempo de recuperación expirado";
            default -> "Causa telefónica " + code;
        };
    }

    private static String inferFromStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Sin motivo registrado en base de datos";
        }
        return switch (status) {
            case "Failure" -> "Fallo — sin código SIP en BD (revise destino, troncal o logs Asterisk)";
            case "ShortCall" -> "Llamada corta — colgó antes de una conversación útil";
            case "NoAnswer" -> "No contestó — sin respuesta del destino";
            case "Abandoned" -> "Abandonada — el cliente colgó en cola";
            case "Busy" -> "Línea ocupada";
            case "Congestion" -> "Congestión de red o troncal";
            case "ChannelUnavailable" -> "Canal de salida no disponible";
            case "Hangup" -> "Finalizada por colgado";
            case "Success" -> "No aplica (llamada exitosa)";
            default -> CallStatusLabels.statusLabel(status);
        };
    }
}
