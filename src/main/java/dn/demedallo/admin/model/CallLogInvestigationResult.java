package dn.demedallo.admin.model;

import java.util.Collections;
import java.util.List;

/**
 * Result of searching Issabel dialer/Asterisk logs for a call failure.
 */
public final class CallLogInvestigationResult {

    public final boolean searched;
    public final String statusMessage;
    public final String conclusion;
    public final String sourceLabel;
    public final List<String> matchedLines;

    public CallLogInvestigationResult(boolean searched, String statusMessage, String conclusion,
            String sourceLabel, List<String> matchedLines) {
        this.searched = searched;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
        this.conclusion = conclusion == null ? "" : conclusion;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        this.matchedLines = matchedLines == null ? List.of() : List.copyOf(matchedLines);
    }

    public static CallLogInvestigationResult skipped(String reason) {
        return new CallLogInvestigationResult(false, reason, "", "", List.of());
    }

    public static CallLogInvestigationResult notFound(String message) {
        return new CallLogInvestigationResult(true, message, "", "", List.of());
    }

    public static CallLogInvestigationResult found(String source, String conclusion, List<String> lines) {
        return new CallLogInvestigationResult(true, "Encontrado en " + source, conclusion, source, lines);
    }

    public boolean hasConclusion() {
        return conclusion != null && !conclusion.isBlank();
    }

    public List<String> linesOrEmpty() {
        return matchedLines == null ? Collections.emptyList() : matchedLines;
    }
}
