package dn.demedallo.admin.service;

import dn.demedallo.admin.util.FailureCauseLabels;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a supervisor-readable conclusion from dialer/Asterisk log lines.
 */
public final class CallLogConclusionParser {

    private static final Pattern CAUSE_TXT = Pattern.compile(
            "Cause-txt['\"]?\\s*=>\\s*'?([^'|\\n\\]]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAUSE_NUM = Pattern.compile(
            "\\[Cause\\]\\s*=>\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANGUP_CAUSE = Pattern.compile(
            "(?:HANGUPCAUSE|hangupcause|Cause:)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINATE_FAIL = Pattern.compile(
            "OriginateResponse.*Failure|Response['\"]?\\s*=>\\s*Failure",
            Pattern.CASE_INSENSITIVE);

    private CallLogConclusionParser() {
    }

    public static String buildConclusion(List<String> lines, String phone, int callId) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        for (String line : lines) {
            String fromTxt = extractCauseTxt(line);
            if (fromTxt != null) {
                return fromTxt;
            }
        }
        for (String line : lines) {
            String fromCode = extractCauseCode(line);
            if (fromCode != null) {
                return fromCode;
            }
        }
        for (String line : lines) {
            if (ORIGINATE_FAIL.matcher(line).find()) {
                return "Originate falló en Asterisk (sin causa SIP guardada en BD — revisar troncal/ruta)";
            }
            if (line.contains("fallo de Originate") || line.contains("Originate failure")) {
                return "Fallo al originar la llamada (canal auxiliar / troncal no disponible)";
            }
            if (line.contains("llamada_corta") || line.contains("ShortCall")) {
                return "Llamada corta detectada por el dialer";
            }
            if (line.contains("CHANUNAVAIL") || line.contains("Channel unavailable")) {
                return "Canal no disponible (CHANUNAVAIL)";
            }
            if (line.contains("CONGESTION")) {
                return "Congestión de red o troncal";
            }
            if (line.contains("CALL_REJECTED") || line.contains("Call Rejected")
                    || line.contains("rejected")) {
                return "Llamada rechazada por el destino o la red";
            }
            if (line.contains("NOANSWER") || line.contains("No answer")) {
                return "Sin respuesta del destino";
            }
            if (line.contains("BUSY") || line.contains("User busy")) {
                return "Línea ocupada en el destino";
            }
            if (line.contains("CANCEL") || line.contains("Cancel")) {
                return "Llamada cancelada o rechazada antes de completar";
            }
        }
        boolean dialAccepted = false;
        for (String line : lines) {
            String l = line.toLowerCase();
            if (l.contains("dial ok") || l.contains("dial accepted") || l.contains("originate queued")) {
                dialAccepted = true;
                break;
            }
        }
        if (dialAccepted) {
            return "Marcación aceptada por Asterisk; fallo posterior en destino, troncal o rechazo "
                    + "(causa SIP no guardada en BD)";
        }
        String errLine = findBestErrLine(lines, phone, callId);
        if (errLine != null && !isNoiseLine(errLine)) {
            return shortenErrLine(errLine);
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (!isNoiseLine(line)) {
                return shortenErrLine(line);
            }
        }
        return "Eventos en log sin causa SIP clara — revise asterisk/full alrededor de la hora de la llamada";
    }

    public static List<String> pickRelevantLines(List<String> all, int max) {
        List<String> out = new ArrayList<>();
        for (String line : all) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String t = line.trim();
            if (isInteresting(t)) {
                out.add(t);
            }
            if (out.size() >= max) {
                break;
            }
        }
        if (out.isEmpty() && !all.isEmpty()) {
            int from = Math.max(0, all.size() - max);
            for (int i = from; i < all.size(); i++) {
                out.add(all.get(i).trim());
            }
        }
        return out;
    }

    private static boolean isInteresting(String line) {
        String l = line.toLowerCase();
        return l.contains("cause")
                || l.contains("failure")
                || l.contains("fallo")
                || l.contains("err:")
                || l.contains("hangup")
                || l.contains("originate")
                || l.contains("chanunavail")
                || l.contains("congestion")
                || l.contains("reject")
                || l.contains("busy")
                || l.contains("noanswer")
                || l.contains("cancel");
    }

    private static boolean isNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }
        String t = line.trim();
        return t.matches(".*'id_call_outgoing'\\s*=>\\s*\\d+.*")
                && !t.toLowerCase().contains("cause")
                && !t.toLowerCase().contains("err:");
    }

    private static String extractCauseTxt(String line) {
        Matcher m = CAUSE_TXT.matcher(line);
        if (m.find()) {
            String txt = m.group(1).trim();
            if (!txt.isEmpty() && !"0".equals(txt)) {
                return "Causa SIP: " + txt;
            }
        }
        return null;
    }

    private static String extractCauseCode(String line) {
        Matcher m = CAUSE_NUM.matcher(line);
        if (!m.find()) {
            m = HANGUP_CAUSE.matcher(line);
            if (!m.find()) {
                return null;
            }
        }
        try {
            int code = Integer.parseInt(m.group(1));
            if (code == 0) {
                return null;
            }
            return FailureCauseLabels.formatReason(code, "", "Failure");
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String findBestErrLine(List<String> lines, String phone, int callId) {
        for (String line : lines) {
            if (line.contains("ERR:") && (line.contains(phone) || line.contains("id=" + callId)
                    || line.contains("id_call") || line.contains("id " + callId))) {
                return line;
            }
        }
        for (String line : lines) {
            if (line.contains("ERR:")) {
                return line;
            }
        }
        return null;
    }

    private static String shortenErrLine(String line) {
        if (line == null) {
            return "";
        }
        String t = line.trim();
        int en = t.indexOf(" | EN:");
        if (en > 0) {
            t = t.substring(0, en).trim();
        }
        if (t.length() > 220) {
            return t.substring(0, 217) + "…";
        }
        return t;
    }
}
