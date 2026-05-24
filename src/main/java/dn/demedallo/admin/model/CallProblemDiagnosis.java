package dn.demedallo.admin.model;

import java.util.Collections;
import java.util.List;

/**
 * Supervisor-oriented diagnosis: internal vs external failure and where to look.
 */
public final class CallProblemDiagnosis {

    public enum Origin {
        INTERNAL,
        EXTERNAL,
        MIXED,
        NORMAL,
        UNKNOWN
    }

    public final Origin origin;
    public final String originLabel;
    public final String locationLabel;
    public final String summary;
    public final List<String> whatToCheck;

    public CallProblemDiagnosis(Origin origin, String locationLabel, String summary,
            List<String> whatToCheck) {
        this.origin = origin == null ? Origin.UNKNOWN : origin;
        this.originLabel = originLabel(this.origin);
        this.locationLabel = locationLabel == null ? "-" : locationLabel;
        this.summary = summary == null ? "" : summary;
        this.whatToCheck = whatToCheck == null ? List.of() : List.copyOf(whatToCheck);
    }

    public static CallProblemDiagnosis normal(String summary) {
        return new CallProblemDiagnosis(Origin.NORMAL, "—", summary, List.of());
    }

    public boolean isProblem() {
        return origin == Origin.INTERNAL || origin == Origin.EXTERNAL || origin == Origin.MIXED;
    }

    public String formattedBlock() {
        if (!isProblem() && summary.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(originLabel);
        if (!locationLabel.isBlank() && !"-".equals(locationLabel)) {
            sb.append(" · ").append(locationLabel);
        }
        if (!summary.isBlank()) {
            sb.append("\n").append(summary);
        }
        if (!whatToCheck.isEmpty()) {
            sb.append("\n\nRevise:");
            for (String c : whatToCheck) {
                sb.append("\n  • ").append(c);
            }
        }
        return sb.toString();
    }

    private static String originLabel(Origin o) {
        return switch (o) {
            case INTERNAL -> "Problema interno (Issabel / dialer / cola)";
            case EXTERNAL -> "Problema externo (cliente / troncal / destino)";
            case MIXED -> "Problema mixto (interno + externo)";
            case NORMAL -> "Sin fallo reportado";
            case UNKNOWN -> "Origen no determinado";
        };
    }

    public List<String> checksOrEmpty() {
        return whatToCheck == null ? Collections.emptyList() : whatToCheck;
    }
}
