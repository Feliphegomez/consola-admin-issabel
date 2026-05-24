package dn.demedallo.admin.model;

/**
 * Lightweight motivo/diagnosis for a call row (no trace steps).
 */
public record PhoneTraceRowSummary(String displayFailureReason, CallProblemDiagnosis diagnosis) {

    public PhoneTraceRowSummary {
        displayFailureReason = displayFailureReason == null ? "" : displayFailureReason;
        if (diagnosis == null) {
            diagnosis = new CallProblemDiagnosis(
                    CallProblemDiagnosis.Origin.UNKNOWN, "—", "", java.util.List.of());
        }
    }
}
