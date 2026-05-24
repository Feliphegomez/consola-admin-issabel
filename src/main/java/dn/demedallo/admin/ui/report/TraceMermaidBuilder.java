package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.CallProblemDiagnosis.Origin;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.ReadableTraceStepRow;

import java.util.List;

/**
 * Builds Mermaid {@code flowchart LR} source from call trace steps (left to right).
 */
public final class TraceMermaidBuilder {

    private TraceMermaidBuilder() {
    }

    public static String build(PhoneTraceCallRow call, List<ReadableTraceStepRow> steps,
            String displayFailureReason, CallProblemDiagnosis diagnosis) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");
        sb.append("  classDef ok fill:#d4edda,stroke:#28a745,color:#1e4620\n");
        sb.append("  classDef fail fill:#f8d7da,stroke:#dc3545,color:#721c24\n");
        sb.append("  classDef wait fill:#fff3cd,stroke:#ffc107,color:#856404\n");
        sb.append("  classDef dial fill:#cce5ff,stroke:#0d6efd,color:#084298\n");
        sb.append("  classDef neutral fill:#e9ecef,stroke:#6c757d,color:#343a40\n");
        sb.append("  classDef diagExt fill:#f5c6cb,stroke:#c82333,color:#721c24\n");
        sb.append("  classDef diagInt fill:#ffe8a1,stroke:#d39e00,color:#533f03\n");
        sb.append("  classDef diagMix fill:#e2d5f1,stroke:#6f42c1,color:#3d2461\n\n");

        String startLabel = escape(call.directionLabel() + "<br/>" + call.phone + "<br/>"
                + shortText(call.campaignName, 28));
        sb.append("  start([").append(startLabel).append("])\n");

        if (steps == null || steps.isEmpty()) {
            sb.append("  start --> empty[Sin eventos en call_progress_log]\n");
            sb.append("  class empty neutral\n");
            return sb.toString();
        }

        String prev = "start";
        for (ReadableTraceStepRow step : steps) {
            String nodeId = "s" + step.step;
            sb.append("  ").append(prev).append(" --> ").append(nodeId).append(nodeDefinition(step))
                    .append("\n");
            sb.append("  class ").append(nodeId).append(" ").append(styleClass(step.statusLabel))
                    .append("\n");
            prev = nodeId;
        }

        String motive = displayFailureReason != null && !displayFailureReason.isBlank()
                ? displayFailureReason : call.failureReason;
        if (motive != null && !motive.isBlank()
                && !"Success".equals(call.status) && !"Éxito / atendida".equals(call.statusLabel)) {
            String noteId = "motivo";
            sb.append("  ").append(prev).append(" -.-> ").append(noteId)
                    .append("[\"").append(escape(shortText(motive, 70)))
                    .append("\"]\n");
            sb.append("  class ").append(noteId).append(" fail\n");
            prev = noteId;
        }

        if (diagnosis != null && diagnosis.isProblem()) {
            String diagId = "diag";
            String diagText = escape(shortText(
                    diagnosis.originLabel + "<br/>" + diagnosis.locationLabel + "<br/>"
                            + diagnosis.summary, 100));
            sb.append("  ").append(prev).append(" -.-> ").append(diagId)
                    .append("[\"").append(diagText).append("\"]\n");
            sb.append("  class ").append(diagId).append(" ")
                    .append(diagnosisClass(diagnosis.origin)).append("\n");
        }

        return sb.toString();
    }

    private static String diagnosisClass(Origin origin) {
        return switch (origin) {
            case EXTERNAL -> "diagExt";
            case INTERNAL -> "diagInt";
            case MIXED -> "diagMix";
            default -> "fail";
        };
    }

    private static String nodeDefinition(ReadableTraceStepRow step) {
        String time = extractTime(step.datetime);
        String inner = step.statusLabel + (time.isEmpty() ? "" : "<br/>" + time);
        String label = escape(inner);
        return switch (step.statusLabel == null ? "" : step.statusLabel) {
            case "Fallo", "Llamada corta", "No contesta", "Abandonada", "Congestión",
                    "Canal no disponible" -> "{{" + label + "}}";
            case "Éxito / atendida" -> "([" + label + "])";
            default -> "[" + label + "]";
        };
    }

    private static String styleClass(String statusLabel) {
        if (statusLabel == null) {
            return "neutral";
        }
        return switch (statusLabel) {
            case "Éxito / atendida" -> "ok";
            case "Fallo", "Llamada corta", "No contesta", "Abandonada" -> "fail";
            case "Marcando", "Timbrando" -> "dial";
            case "En cola", "En espera (hold)" -> "wait";
            default -> "neutral";
        };
    }

    private static String extractTime(String datetime) {
        if (datetime == null || datetime.isBlank() || "-".equals(datetime)) {
            return "";
        }
        int space = datetime.indexOf(' ');
        return space >= 0 && space + 1 < datetime.length()
                ? datetime.substring(space + 1).trim()
                : datetime.trim();
    }

    private static String shortText(String text, int max) {
        if (text == null || text.isBlank() || "-".equals(text)) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "#quot;")
                .replace("\n", "<br/>")
                .replace("\r", "");
    }
}
