package dn.demedallo.admin.service;

import dn.demedallo.admin.db.AsteriskDb;
import dn.demedallo.admin.db.CdrRecordingDao;
import dn.demedallo.admin.model.CallLogInvestigationResult;
import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.CdrCallDetail;
import dn.demedallo.admin.model.CelEventRow;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceDetail;
import dn.demedallo.admin.model.ReadableTraceStepRow;
import dn.demedallo.admin.util.AdminDbSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds call trace steps from Issabel CDR/CEL when {@code call_progress_log} is not available.
 */
public final class CdrTraceService {

    private final CdrRecordingDao cdrDao;

    public CdrTraceService(AdminDbSettings settings) {
        this.cdrDao = new CdrRecordingDao(new AsteriskDb(settings));
    }

    public PhoneTraceDetail loadCdrTraceDetail(PhoneTraceCallRow call) throws Exception {
        if (call == null) {
            return new PhoneTraceDetail(null, List.of(), CallLogInvestigationResult.skipped(""),
                    "", new CallProblemDiagnosis(CallProblemDiagnosis.Origin.UNKNOWN, "—", "", List.of()));
        }
        CdrCallDetail detail = cdrDao.loadByUniqueid(call.uniqueid);
        List<CelEventRow> cel = List.of();
        try {
            cel = cdrDao.loadCelByUniqueid(call.uniqueid);
        } catch (Exception ignored) {
            // CEL optional; CDR steps still built below
        }
        List<ReadableTraceStepRow> steps = buildSteps(call, detail, cel);
        String displayReason = dispositionSummary(call, detail);
        CallProblemDiagnosis diagnosis = CallProblemDiagnosisService.analyze(
                call, steps, displayReason, CallLogInvestigationResult.skipped(""));
        return new PhoneTraceDetail(call, steps,
                CallLogInvestigationResult.skipped(
                        cel.isEmpty() ? "Trazabilidad desde CDR Issabel (sin CEL)." : "Trazabilidad CDR + CEL."),
                displayReason, diagnosis);
    }

    static List<ReadableTraceStepRow> buildSteps(PhoneTraceCallRow call, CdrCallDetail detail,
            List<CelEventRow> cel) {
        List<ReadableTraceStepRow> steps = new ArrayList<>();
        if (!cel.isEmpty()) {
            int n = 1;
            for (CelEventRow ev : cel) {
                steps.add(new ReadableTraceStepRow(
                        n++,
                        ev.eventtime.isBlank() ? call.callDatetime : ev.eventtime,
                        celSummary(ev),
                        celStatusLabel(ev.eventtype),
                        "-",
                        extractTrunk(ev.channame),
                        extractExtension(ev.channame),
                        "-"));
            }
            return steps;
        }
        if (detail == null) {
            steps.add(new ReadableTraceStepRow(
                    1,
                    call.callDatetime,
                    "Registro CDR (sin detalle adicional en base de datos).",
                    "CDR",
                    "-",
                    call.trunk,
                    extractExtension(call.cdrChannel),
                    call.duration));
            if (call.cdrRecordingFile != null && !call.cdrRecordingFile.isBlank()) {
                steps.add(new ReadableTraceStepRow(
                        2,
                        call.callDatetime,
                        "Grabación: " + shortName(call.cdrRecordingFile, 80),
                        "Grabación",
                        "-",
                        "-",
                        extractExtension(call.cdrChannel),
                        call.duration));
            }
            return steps;
        }

        String agent = firstNonEmpty(
                extractExtension(detail.channel),
                extractExtension(detail.dstchannel),
                extractExtension(call.cdrChannel));
        String trunk = extractTrunk(detail.dstchannel);
        int n = 1;
        steps.add(new ReadableTraceStepRow(
                n++,
                detail.calldate,
                "Inicio de llamada: " + detail.src + " → " + detail.dst
                        + (detail.clid.isBlank() ? "" : " · CLID " + detail.clid),
                "Inicio",
                "-",
                trunk,
                agent,
                "-"));
        if (!detail.channel.isBlank()) {
            steps.add(new ReadableTraceStepRow(
                    n++,
                    detail.calldate,
                    "Canal origen: " + detail.channel,
                    "PBX",
                    "-",
                    trunk,
                    agent,
                    "-"));
        }
        if (!detail.dstchannel.isBlank()) {
            steps.add(new ReadableTraceStepRow(
                    n++,
                    detail.calldate,
                    "Canal destino: " + detail.dstchannel,
                    "PBX",
                    "-",
                    trunk,
                    extractExtension(detail.dstchannel),
                    "-"));
        }
        if (!detail.lastapp.isBlank()) {
            String app = detail.lastapp;
            if (!detail.lastdata.isBlank()) {
                app += " (" + shortName(detail.lastdata, 60) + ")";
            }
            steps.add(new ReadableTraceStepRow(
                    n++,
                    detail.calldate,
                    "Última aplicación dialplan: " + app,
                    "Dialplan",
                    "-",
                    trunk,
                    agent,
                    "-"));
        }
        String dispLabel = dispositionLabel(detail.disposition);
        steps.add(new ReadableTraceStepRow(
                n++,
                detail.calldate,
                "Resultado CDR: " + detail.disposition + " — " + dispLabel,
                dispLabel,
                "-",
                trunk,
                agent,
                formatDuration(detail.billsec > 0 ? detail.billsec : detail.duration)));
        if (!detail.recordingfile.isBlank()) {
            steps.add(new ReadableTraceStepRow(
                    n,
                    detail.calldate,
                    "Grabación MixMonitor: " + shortName(detail.recordingfile, 80),
                    "Grabación",
                    "-",
                    trunk,
                    agent,
                    formatDuration(detail.billsec > 0 ? detail.billsec : detail.duration)));
        }
        return steps;
    }

    private static String dispositionSummary(PhoneTraceCallRow call, CdrCallDetail detail) {
        if (detail != null && !detail.disposition.isBlank()) {
            return dispositionLabel(detail.disposition);
        }
        return call.statusLabel == null ? "" : call.statusLabel;
    }

    private static String dispositionLabel(String disposition) {
        if (disposition == null || disposition.isBlank()) {
            return "CDR";
        }
        return switch (disposition.toUpperCase(Locale.ROOT)) {
            case "ANSWERED" -> "Éxito / atendida";
            case "NO ANSWER" -> "No contesta";
            case "BUSY" -> "Ocupado";
            case "FAILED" -> "Fallo";
            case "CONGESTION" -> "Fallo";
            default -> disposition;
        };
    }

    private static String celStatusLabel(String eventtype) {
        if (eventtype == null || eventtype.isBlank()) {
            return "CEL";
        }
        String u = eventtype.toUpperCase(Locale.ROOT);
        if (u.contains("ANSWER")) {
            return "Éxito / atendida";
        }
        if (u.contains("HANGUP") || u.contains("CHAN_END")) {
            return "Fin";
        }
        if (u.contains("BRIDGE") || u.contains("LINK")) {
            return "PBX";
        }
        if (u.contains("APP_START")) {
            return "Dialplan";
        }
        return "CEL";
    }

    private static String celSummary(CelEventRow ev) {
        StringBuilder sb = new StringBuilder(ev.eventtype);
        if (!ev.channame.isBlank()) {
            sb.append(" · ").append(ev.channame);
        }
        if (!ev.appname.isBlank()) {
            sb.append(" · ").append(ev.appname);
        }
        if (!ev.appdata.isBlank()) {
            sb.append(" (").append(shortName(ev.appdata, 50)).append(")");
        }
        if (!ev.extra.isBlank()) {
            sb.append(" — ").append(shortName(ev.extra, 40));
        }
        return sb.toString();
    }

    private static String extractExtension(String channel) {
        if (channel == null || channel.isBlank()) {
            return "-";
        }
        int slash = channel.indexOf('/');
        int dash = channel.indexOf('-', slash + 1);
        if (slash >= 0 && dash > slash) {
            return channel.substring(slash + 1, dash);
        }
        if (slash >= 0) {
            return channel.substring(slash + 1);
        }
        return "-";
    }

    private static String extractTrunk(String channel) {
        if (channel == null || channel.isBlank()) {
            return "-";
        }
        String u = channel.toUpperCase(Locale.ROOT);
        if (u.contains("TRUNK") || u.startsWith("SIP/") && channel.length() > 20) {
            return channel;
        }
        return "-";
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"-".equals(v)) {
                return v;
            }
        }
        return "-";
    }

    private static String shortName(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 3) + "...";
    }

    private static String formatDuration(int sec) {
        if (sec <= 0) {
            return "00:00:00";
        }
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
