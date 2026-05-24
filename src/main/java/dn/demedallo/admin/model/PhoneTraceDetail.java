package dn.demedallo.admin.model;

import java.util.List;

/**
 * Call trace steps plus optional log investigation and problem diagnosis.
 */
public final class PhoneTraceDetail {

    public final PhoneTraceCallRow call;
    public final List<ReadableTraceStepRow> steps;
    public final CallLogInvestigationResult logInvestigation;
    public final String displayFailureReason;
    public final CallProblemDiagnosis diagnosis;

    public PhoneTraceDetail(PhoneTraceCallRow call, List<ReadableTraceStepRow> steps,
            CallLogInvestigationResult logInvestigation, String displayFailureReason,
            CallProblemDiagnosis diagnosis) {
        this.call = call;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.logInvestigation = logInvestigation == null
                ? CallLogInvestigationResult.skipped("")
                : logInvestigation;
        this.displayFailureReason = displayFailureReason == null ? "" : displayFailureReason;
        this.diagnosis = diagnosis == null
                ? new CallProblemDiagnosis(CallProblemDiagnosis.Origin.UNKNOWN, "—", "", List.of())
                : diagnosis;
    }
}
