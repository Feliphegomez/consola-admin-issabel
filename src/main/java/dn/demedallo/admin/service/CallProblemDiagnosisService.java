package dn.demedallo.admin.service;

import dn.demedallo.admin.model.CallLogInvestigationResult;
import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.CallProblemDiagnosis.Origin;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceCallRow.CallDirection;
import dn.demedallo.admin.model.ReadableTraceStepRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifies call issues as internal (PBX/dialer/queue) vs external (customer/trunk/destination).
 */
public final class CallProblemDiagnosisService {

    private CallProblemDiagnosisService() {
    }

    public static CallProblemDiagnosis analyze(PhoneTraceCallRow call, List<ReadableTraceStepRow> steps,
            String failureReason, CallLogInvestigationResult log) {
        if (call == null) {
            return new CallProblemDiagnosis(Origin.UNKNOWN, "—", "Sin datos de llamada", List.of());
        }
        String status = call.status == null ? "" : call.status;
        String reason = combineReasons(call.failureReason, failureReason, log);
        boolean reachedAgent = reachedAgent(steps, call);
        boolean hadQueue = hasStepStatus(steps, "En cola");
        boolean hadRinging = hasStepStatus(steps, "Timbrando", "Marcando");

        if ("Success".equals(status) || "Éxito / atendida".equals(call.statusLabel)) {
            return CallProblemDiagnosis.normal("Llamada finalizada como atendida sin fallo en el registro.");
        }

        if ("Abandoned".equals(status) || "Abandonada".equals(call.statusLabel)) {
            return diagnoseAbandoned(call, hadQueue, reachedAgent);
        }

        if ("NoAnswer".equals(status) || "No contesta".equals(call.statusLabel)) {
            return new CallProblemDiagnosis(Origin.EXTERNAL,
                    call.direction == CallDirection.OUTGOING ? "Teléfono destino" : "Cliente",
                    "El destino no contestó la llamada.",
                    checksOutboundDestino(call));
        }

        if ("ShortCall".equals(status) || "Llamada corta".equals(call.statusLabel)) {
            return new CallProblemDiagnosis(Origin.MIXED,
                    call.direction == CallDirection.OUTGOING ? "Dialer / destino" : "Cliente / agente",
                    "La llamada se colgó muy rápido (corta). Puede ser tono de ocupado, buzón, "
                            + "rechazo del destino o corte del dialer.",
                    List.of(
                            "Duración real en CDR y en log del dialer",
                            "Si es saliente: pruebe marcar el mismo número manualmente",
                            "Revise causa SIP en log si está disponible"));
        }

        if ("Failure".equals(status) || "Fallo".equals(call.statusLabel)) {
            return diagnoseFailure(call, steps, reason, hadRinging, reachedAgent, log);
        }

        if ("Busy".equals(status) || "Congestion".equals(status)
                || "ChannelUnavailable".equals(status)) {
            return new CallProblemDiagnosis(Origin.EXTERNAL, "Troncal / red",
                    "Señal de red o troncal (" + call.statusLabel + ").",
                    checksTrunk(call, reason, call.trunk));
        }

        return new CallProblemDiagnosis(Origin.UNKNOWN, "—",
                "Estado «" + call.statusLabel + "» — revise trazabilidad y logs.",
                List.of("Pestaña Logs Issabel: dialerd.log y asterisk/full",
                        "Confirme agentes y cola si es entrante"));
    }

    /** Short label for calls table before full trace is loaded. */
    public static String quickHint(PhoneTraceCallRow call) {
        if (call == null) {
            return "";
        }
        CallProblemDiagnosis d = analyze(call, List.of(), call.failureReason,
                CallLogInvestigationResult.skipped(""));
        if (!d.isProblem()) {
            return d.originLabel;
        }
        return d.originLabel.replace("Problema ", "") + " · " + d.locationLabel;
    }

    public static String stepOriginLabel(PhoneTraceCallRow call, ReadableTraceStepRow step) {
        if (step == null || "Log".equals(step.statusLabel)) {
            return "—";
        }
        return switch (step.statusLabel) {
            case "Abandonada" -> "Externo";
            case "En cola", "En espera (hold)" -> "Interno";
            case "Marcando" -> call.direction == CallDirection.OUTGOING ? "Mixto" : "Interno";
            case "Timbrando" -> "Externo";
            case "Fallo", "Llamada corta", "No contesta", "Ocupado" -> "Externo*";
            case "Éxito / atendida" -> "—";
            case "CDR", "CEL", "PBX", "Dialplan", "Inicio", "Fin", "Grabación" -> "PBX";
            default -> call.isCdrOnly() ? "PBX" : "?";
        };
    }

    public static String stepWhereLabel(PhoneTraceCallRow call, ReadableTraceStepRow step) {
        if (step == null) {
            return "";
        }
        if ("Log".equals(step.statusLabel)) {
            return "Log servidor";
        }
        String trunk = step.trunk != null && !"-".equals(step.trunk) ? step.trunk : call.trunk;
        return switch (step.statusLabel) {
            case "Abandonada" -> "Cliente colgó · cola " + shortName(call.campaignName);
            case "En cola" -> "Cola/campaña «" + shortName(call.campaignName) + "»";
            case "Marcando" -> call.direction == CallDirection.OUTGOING
                    ? "Dialer → " + call.phone
                    : "Entrada PBX";
            case "Timbrando" -> "Destino " + call.phone + trunkSuffix(trunk);
            case "Fallo" -> trunkSuffix(trunk).isBlank() ? "Destino o ruta" : trunkSuffix(trunk);
            case "Éxito / atendida" -> agentLabel(step, call);
            case "CDR", "CEL", "PBX", "Dialplan", "Inicio", "Fin", "Grabación" -> "CDR Issabel / Asterisk";
            default -> step.statusLabel;
        };
    }

    private static CallProblemDiagnosis diagnoseAbandoned(PhoneTraceCallRow call, boolean hadQueue,
            boolean reachedAgent) {
        List<String> checks = new ArrayList<>();
        checks.add("Cola/campaña: «" + call.campaignName + "» — ¿había agentes logueados?");
        checks.add("Tiempo máximo en cola y anuncios (IVR/cola) en Issabel");
        checks.add("Reporte de abandonos de la cola en consola web");
        if (!"-".equals(call.trunk)) {
            checks.add("Troncal de entrada: " + call.trunk);
        }

        if (call.direction == CallDirection.INCOMING) {
            if (reachedAgent) {
                return new CallProblemDiagnosis(Origin.MIXED, "Cliente / agente",
                        "Entrante abandonada después de pasar por agente o cola — "
                                + "puede ser cuelgue del cliente o transferencia.",
                        checks);
            }
            if (hadQueue) {
                return new CallProblemDiagnosis(Origin.EXTERNAL,
                        "Cliente en cola («" + shortName(call.campaignName) + "»)",
                        "El llamante colgó mientras esperaba en cola (no fue atendido o colgó antes). "
                                + "No suele ser fallo del dialer saliente.",
                        checks);
            }
            return new CallProblemDiagnosis(Origin.EXTERNAL, "Cliente / IVR",
                    "Abandono entrante sin paso «En cola» visible — posible cuelgue en IVR o antes de encolar.",
                    checks);
        }

        return new CallProblemDiagnosis(Origin.EXTERNAL, "Cliente",
                "Llamada marcada como abandonada.", checks);
    }

    private static CallProblemDiagnosis diagnoseFailure(PhoneTraceCallRow call,
            List<ReadableTraceStepRow> steps, String reason, boolean hadRinging,
            boolean reachedAgent, CallLogInvestigationResult log) {
        String trunk = resolveTrunk(steps, call);

        if (reasonIndicatesDestination(reason, log)) {
            return diagnoseDestinationFailure(call, reason, trunk);
        }
        if (hasKnownTrunk(trunk) || reasonIndicatesTrunk(reason)) {
            return new CallProblemDiagnosis(Origin.EXTERNAL,
                    hasKnownTrunk(trunk) ? "Troncal / proveedor (" + shortTrunk(trunk) + ")"
                            : "Troncal / operador",
                    "El fallo ocurrió en la salida hacia la red o el destino (troncal/proveedor), "
                            + "no en la cola interna del PBX.",
                    checksTrunk(call, reason, trunk));
        }
        if (reasonIndicatesDialerProcessFailure(reason, log)) {
            return new CallProblemDiagnosis(Origin.INTERNAL, "Dialer Issabel",
                    "Indicios de fallo del proceso dialer o del Originate antes de salir a red.",
                    List.of(
                            "Servicio issabeldialer: systemctl status issabeldialer",
                            "Log: /opt/issabel/dialer/dialerd.log (pestaña Logs Issabel)",
                            "Reinicie dialer solo si su procedimiento lo permite"));
        }

        if (call.direction == CallDirection.OUTGOING) {
            if (hadRinging && !reachedAgent) {
                return new CallProblemDiagnosis(Origin.EXTERNAL, "Teléfono destino",
                        "Timbró pero no hubo respuesta útil — destino no contesta, ocupado o rechazo.",
                        checksOutboundDestino(call));
            }
            if (hasKnownTrunk(trunk) || hasStepStatus(steps, "Fallo", "Timbrando")) {
                return new CallProblemDiagnosis(Origin.EXTERNAL,
                        "Destino o troncal «" + call.phone + "»",
                        "Saliente en fallo con troncal o paso de marcación — suele ser rechazo del "
                                + "cliente, no contesta, ocupado o problema del operador ("
                                + trunkLabel(trunk) + ").",
                        checksOutboundDestino(call));
            }
            if (!hadRinging && traceStepCount(steps) <= 2) {
                return new CallProblemDiagnosis(Origin.EXTERNAL,
                        "Troncal o número «" + call.phone + "»",
                        "Falló al marcar sin llegar a timbrar — revise ruta, troncal "
                                + trunkLabel(trunk) + " y formato del número.",
                        checksTrunk(call, reason, trunk));
            }
            return new CallProblemDiagnosis(Origin.EXTERNAL, "Destino / troncal",
                    "Fallo saliente «" + shortName(call.campaignName) + "» sin causa SIP en BD — "
                            + "lo habitual es destino (rechazo/no contesta) o proveedor, no el dialer.",
                    checksOutboundDestino(call));
        }

        return new CallProblemDiagnosis(Origin.INTERNAL, "PBX / cola entrante",
                "Fallo en llamada entrante — revise cola, agentes y ruta de entrada.",
                List.of("Estado de cola en Monitoreo", "Log Asterisk en momento de la llamada"));
    }

    private static CallProblemDiagnosis diagnoseDestinationFailure(PhoneTraceCallRow call,
            String reason, String trunk) {
        String r = reason == null ? "" : reason.toLowerCase();
        if (r.contains("rechaz") || r.contains("reject") || r.contains("cód. 21")
                || r.contains("código 21")) {
            return new CallProblemDiagnosis(Origin.EXTERNAL,
                    call.direction == CallDirection.OUTGOING ? "Cliente / destino" : "Cliente",
                    "Llamada rechazada por el destino o el operador (colgado/rechazo explícito).",
                    checksOutboundDestino(call));
        }
        if (r.contains("ocupad") || r.contains("busy") || r.contains("cód. 17")) {
            return new CallProblemDiagnosis(Origin.EXTERNAL, "Teléfono destino",
                    "Línea ocupada en el destino.", checksOutboundDestino(call));
        }
        if (r.contains("no contest") || r.contains("noanswer") || r.contains("sin respuesta")
                || r.contains("cód. 18") || r.contains("cód. 19")) {
            return new CallProblemDiagnosis(Origin.EXTERNAL, "Teléfono destino",
                    "El destino no contestó la llamada.", checksOutboundDestino(call));
        }
        return new CallProblemDiagnosis(Origin.EXTERNAL,
                hasKnownTrunk(trunk) ? "Troncal / destino (" + shortTrunk(trunk) + ")"
                        : "Teléfono destino / troncal",
                "Problema en el destino o en la red del proveedor (no en la cola Issabel).",
                checksTrunk(call, reason, trunk));
    }

    private static boolean reasonIndicatesTrunk(String reason) {
        if (reason == null) {
            return false;
        }
        String r = reason.toLowerCase();
        return r.contains("troncal") || r.contains("proveedor") || r.contains("operador")
                || r.contains("congest") || r.contains("chanunavail")
                || r.contains("circuit") || r.contains("sip/")
                || r.contains("canal no disponible") || r.contains("sin circuito")
                || r.contains("código 34") || r.contains("cód. 34")
                || r.contains("cód. 44") || r.contains("cód. 38");
    }

    /**
     * True when reason/log point to customer, destination, or carrier — not dialer orphan/sync.
     * Ignores the generic DB hint «el dialer no guardó código SIP».
     */
    private static boolean reasonIndicatesDestination(String reason, CallLogInvestigationResult log) {
        if (matchesDestinationKeywords(reason)) {
            return true;
        }
        if (log != null && log.hasConclusion() && matchesDestinationKeywords(log.conclusion)) {
            return true;
        }
        if (log != null && log.matchedLines != null) {
            for (String line : log.matchedLines) {
                if (matchesDestinationKeywords(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesDestinationKeywords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String r = text.toLowerCase();
        if (isGenericMissingSipHint(r)) {
            return false;
        }
        return r.contains("rechaz") || r.contains("reject") || r.contains("cancel")
                || r.contains("no contest") || r.contains("noanswer") || r.contains("no answer")
                || r.contains("sin respuesta") || r.contains("usuario ocupad") || r.contains("busy")
                || r.contains("ocupad") || r.contains("llamada corta")
                || r.contains("cód. 17") || r.contains("cód. 18") || r.contains("cód. 19")
                || r.contains("cód. 21") || r.contains("código 17") || r.contains("código 18")
                || r.contains("código 19") || r.contains("código 21")
                || r.contains("user busy") || r.contains("call rejected")
                || r.contains("decline") || r.contains("hangup") && r.contains("cause");
    }

    /** Real dialer-process failure, not «BD sin código SIP». */
    private static boolean reasonIndicatesDialerProcessFailure(String reason,
            CallLogInvestigationResult log) {
        if (matchesDialerFailureKeywords(reason)) {
            return true;
        }
        if (log != null && log.matchedLines != null) {
            for (String line : log.matchedLines) {
                if (matchesDialerFailureKeywords(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesDialerFailureKeywords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String r = text.toLowerCase();
        if (isGenericMissingSipHint(r)) {
            return false;
        }
        return r.contains("orphan") || r.contains("issabeldialer")
                || r.contains("proceso dialer") || r.contains("dialer no responde")
                || r.contains("err:") && r.contains("campaignprocess")
                || r.contains("originate falló") && !r.contains("troncal")
                || r.contains("fallo de originate") && r.contains("auxiliar");
    }

    private static boolean isGenericMissingSipHint(String lowerReason) {
        return lowerReason.contains("no guardó código sip")
                || lowerReason.contains("no guardo codigo sip")
                || lowerReason.contains("revise logs asterisk/dialer");
    }

    private static boolean reachedAgent(List<ReadableTraceStepRow> steps, PhoneTraceCallRow call) {
        if (steps != null) {
            for (ReadableTraceStepRow s : steps) {
                if ("Éxito / atendida".equals(s.statusLabel)) {
                    return true;
                }
                if (s.agent != null && !"-".equals(s.agent) && !s.agent.isBlank()) {
                    return true;
                }
            }
        }
        return call.agent != null && !"-".equals(call.agent) && !call.agent.isBlank();
    }

    private static boolean hasStepStatus(List<ReadableTraceStepRow> steps, String... labels) {
        if (steps == null) {
            return false;
        }
        for (ReadableTraceStepRow s : steps) {
            for (String l : labels) {
                if (l.equals(s.statusLabel)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String combineReasons(String dbReason, String displayReason,
            CallLogInvestigationResult log) {
        StringBuilder sb = new StringBuilder();
        if (dbReason != null && !dbReason.isBlank() && !"-".equals(dbReason)) {
            sb.append(dbReason);
        }
        if (displayReason != null && !displayReason.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(displayReason);
        }
        if (log != null && log.hasConclusion()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(log.conclusion);
        }
        return sb.toString();
    }

    private static String resolveTrunk(List<ReadableTraceStepRow> steps, PhoneTraceCallRow call) {
        if (steps != null) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                ReadableTraceStepRow s = steps.get(i);
                if (s.trunk != null && hasKnownTrunk(s.trunk)) {
                    return s.trunk.trim();
                }
            }
        }
        return call.trunk;
    }

    private static boolean hasKnownTrunk(String trunk) {
        return trunk != null && !trunk.isBlank() && !"-".equals(trunk);
    }

    private static String shortTrunk(String trunk) {
        if (!hasKnownTrunk(trunk)) {
            return "";
        }
        String t = trunk.trim();
        int slash = t.lastIndexOf('/');
        return slash >= 0 ? t.substring(slash + 1) : t;
    }

    private static int traceStepCount(List<ReadableTraceStepRow> steps) {
        if (steps == null) {
            return 0;
        }
        int n = 0;
        for (ReadableTraceStepRow s : steps) {
            if (!"Log".equals(s.statusLabel)) {
                n++;
            }
        }
        return n;
    }

    private static List<String> checksTrunk(PhoneTraceCallRow call, String reason, String trunk) {
        List<String> c = new ArrayList<>();
        c.add("Troncal: " + trunkLabel(hasKnownTrunk(trunk) ? trunk : call.trunk));
        c.add("Estado del trunk en Issabel / Asterisk: sip show peers");
        c.add("Llamada de prueba al mismo número por esa troncal");
        if (reason != null && !reason.isBlank()) {
            c.add("Detalle: " + (reason.length() > 120 ? reason.substring(0, 117) + "…" : reason));
        }
        return c;
    }

    private static List<String> checksOutboundDestino(PhoneTraceCallRow call) {
        return List.of(
                "Marque " + call.phone + " desde un teléfono o softphone",
                "Verifique formato/número en campaña «" + call.campaignName + "»",
                "Troncal: " + trunkLabel(call.trunk));
    }

    private static String trunkLabel(String trunk) {
        return trunk == null || "-".equals(trunk) ? "(sin troncal en BD)" : trunk;
    }

    private static String trunkSuffix(String trunk) {
        return trunk == null || "-".equals(trunk) ? "" : " · " + trunk;
    }

    private static String shortName(String name) {
        if (name == null || name.isBlank() || "-".equals(name)) {
            return "—";
        }
        return name.length() > 40 ? name.substring(0, 37) + "…" : name;
    }

    private static String agentLabel(ReadableTraceStepRow step, PhoneTraceCallRow call) {
        if (step.agent != null && !"-".equals(step.agent)) {
            return "Agente " + step.agent;
        }
        if (call.agent != null && !"-".equals(call.agent)) {
            return "Agente " + call.agent;
        }
        return "Agente";
    }
}
