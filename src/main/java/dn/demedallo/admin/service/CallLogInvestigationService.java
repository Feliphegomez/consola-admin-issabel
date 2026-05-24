package dn.demedallo.admin.service;

import dn.demedallo.admin.model.CallLogInvestigationResult;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.util.AdminLogSettings;
import dn.demedallo.admin.util.AppLogFile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Searches dialer and Asterisk logs via SSH when DB has no SIP failure code.
 */
public final class CallLogInvestigationService {

    private static final int MAX_GREP_LINES = 80;
    private static final int DISPLAY_LINES = 12;

    private final RemoteLogTailService remoteTail = new RemoteLogTailService();
    private final AdminLogSettings logSettings;
    private final String defaultSshHost;

    public CallLogInvestigationService(String eccpHost) {
        this.logSettings = AdminLogSettings.load();
        this.defaultSshHost = eccpHost == null ? "" : eccpHost.trim();
    }

    public static boolean shouldInvestigateLogs(PhoneTraceCallRow call) {
        if (call == null) {
            return false;
        }
        if (!"Failure".equals(call.status) && !"ShortCall".equals(call.status)
                && !"NoAnswer".equals(call.status)) {
            return false;
        }
        if (call.failureCause != null && !call.failureCause.isBlank()
                && !"-".equals(call.failureCause)) {
            return false;
        }
        if (call.failureCode != null && !"-".equals(call.failureCode) && !"0".equals(call.failureCode)) {
            return false;
        }
        String reason = call.failureReason == null ? "" : call.failureReason;
        return reason.contains("no guardó código SIP")
                || reason.contains("no guardo codigo SIP")
                || reason.contains("sin código SIP en BD")
                || reason.contains("Fallo genérico")
                || reason.contains("sin detalle");
    }

    public CallLogInvestigationResult investigate(PhoneTraceCallRow call) {
        if (call == null) {
            return CallLogInvestigationResult.skipped("Sin llamada seleccionada");
        }
        if (!logSettings.isSshReady()) {
            return CallLogInvestigationResult.skipped(
                    "Active SSH en pestaña Logs Issabel (usuario/clave) para buscar en el servidor.");
        }
        String host = defaultSshHost;
        if (host.isBlank()) {
            return CallLogInvestigationResult.skipped("Host ECCP no disponible para SSH.");
        }

        String pattern = buildGrepPattern(call);
        List<String> merged = new ArrayList<>();
        String source = "";

        try {
            String dialer = remoteTail.grepRemote(host, logSettings.sshPort, logSettings.sshUser,
                    logSettings.sshPassword, logSettings.dialerLogPath, pattern, MAX_GREP_LINES);
            appendLines(merged, dialer);
            if (!merged.isEmpty()) {
                source = "dialerd.log";
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[phone-trace] dialer grep | EN: " + ex.getMessage());
        }

        if (merged.size() < 5 && call.uniqueid != null && !"-".equals(call.uniqueid)) {
            try {
                String byUid = remoteTail.grepRemote(host, logSettings.sshPort, logSettings.sshUser,
                        logSettings.sshPassword, logSettings.dialerLogPath,
                        PatternUtil.escapeRegex(call.uniqueid), MAX_GREP_LINES);
                appendLines(merged, byUid);
            } catch (Exception ignored) {
            }
        }

        if (merged.isEmpty()) {
            try {
                String ast = remoteTail.grepRemote(host, logSettings.sshPort, logSettings.sshUser,
                        logSettings.sshPassword, logSettings.asteriskFullPath, pattern, MAX_GREP_LINES);
                appendLines(merged, ast);
                if (!merged.isEmpty()) {
                    source = "asterisk/full";
                }
            } catch (Exception ex) {
                AppLogFile.appendLine("[phone-trace] asterisk grep | EN: " + ex.getMessage());
            }
        }

        if (merged.isEmpty()) {
            return CallLogInvestigationResult.notFound(
                    "Sin líneas en logs para " + call.phone + " (patrón: " + pattern + "). "
                            + "Amplíe rango de fechas o revise Logs Issabel.");
        }

        List<String> relevant = CallLogConclusionParser.pickRelevantLines(merged, DISPLAY_LINES);
        String conclusion = CallLogConclusionParser.buildConclusion(merged, call.phone, call.callId);
        if (conclusion.isBlank()) {
            conclusion = "Eventos encontrados en log — ver líneas abajo";
        }
        return CallLogInvestigationResult.found(source, conclusion, relevant);
    }

    private static String buildGrepPattern(PhoneTraceCallRow call) {
        Set<String> parts = new LinkedHashSet<>();
        String phone = call.phone == null ? "" : call.phone.replaceAll("\\D", "");
        if (!phone.isEmpty()) {
            parts.add(PatternUtil.escapeRegex(phone));
        }
        if (call.uniqueid != null && !"-".equals(call.uniqueid)) {
            parts.add(PatternUtil.escapeRegex(call.uniqueid.trim()));
        }
        parts.add("id[_ ]?" + call.callId);
        parts.add("id_call[^0-9]*" + call.callId);
        return String.join("|", parts);
    }

    private static void appendLines(List<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !target.contains(t)) {
                target.add(t);
            }
        }
    }

    /** Regex-safe alternation parts. */
    private static final class PatternUtil {
        static String escapeRegex(String s) {
            if (s == null) {
                return "";
            }
            return s.replaceAll("([\\\\.^$|?*+()\\[\\]{}])", "\\\\$1");
        }
    }

    /**
     * Merge DB failure reason with log conclusion (log wins when DB had no SIP code).
     */
    public static String effectiveFailureReason(PhoneTraceCallRow call, CallLogInvestigationResult log) {
        if (log != null && log.hasConclusion()) {
            return log.conclusion;
        }
        return call == null ? "" : call.failureReason;
    }
}
