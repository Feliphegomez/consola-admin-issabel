package dn.demedallo.admin.service;



import dn.demedallo.admin.db.CallCenterDb;

import dn.demedallo.admin.db.FailedShortCallsDao.CallDirectionFilter;

import dn.demedallo.admin.db.PhoneTraceSearchDao;

import dn.demedallo.admin.model.CallLogInvestigationResult;
import dn.demedallo.admin.model.CallProblemDiagnosis;

import dn.demedallo.admin.model.PhoneTraceCallRow;

import dn.demedallo.admin.model.PhoneTraceDetail;
import dn.demedallo.admin.model.PhoneTraceRowSummary;

import dn.demedallo.admin.model.ReadableTraceStepRow;

import dn.demedallo.admin.util.AdminDbSettings;

import dn.demedallo.admin.util.ShiftDatetimeRange;



import java.util.ArrayList;

import java.util.List;

import java.util.Optional;



public final class PhoneTraceSearchService {



    private final PhoneTraceSearchDao dao;

    private final RetryManagementService retryService;

    private final CallLogInvestigationService logInvestigation;

    private final boolean dbEnabled;



    public PhoneTraceSearchService(AdminDbSettings dbSettings, String eccpHost) {

        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();

        this.logInvestigation = new CallLogInvestigationService(eccpHost);

        if (dbEnabled) {

            CallCenterDb db = new CallCenterDb(dbSettings);

            this.dao = new PhoneTraceSearchDao(db);

            this.retryService = new RetryManagementService(dbSettings);

        } else {

            this.dao = null;

            this.retryService = null;

        }

    }



    public boolean isDbEnabled() {

        return dbEnabled;

    }



    public String normalizePhone(String input) {

        return PhoneTraceSearchDao.normalizePhoneQuery(input);

    }



    public List<PhoneTraceCallRow> search(String phoneInput, ShiftDatetimeRange range,

            CallDirectionFilter filter) throws Exception {

        if (!dbEnabled || dao == null) {

            return List.of();

        }

        String digits = normalizePhone(phoneInput);

        if (digits.isEmpty()) {

            return List.of();

        }

        return dao.searchByPhone(digits, range.start, range.end, filter);

    }



    public Optional<PhoneTraceCallRow> findCallByUniqueid(String uniqueid) throws Exception {
        if (!dbEnabled || dao == null) {
            return Optional.empty();
        }
        return dao.findByUniqueid(uniqueid);
    }

    public PhoneTraceDetail loadTraceDetail(PhoneTraceCallRow call) throws Exception {

        if (!dbEnabled || dao == null || call == null) {

            return new PhoneTraceDetail(call, List.of(),

                    CallLogInvestigationResult.skipped(""), "",
                    CallProblemDiagnosisService.analyze(call, List.of(), "", null));

        }

        List<ReadableTraceStepRow> steps = new ArrayList<>(dao.loadReadableTrace(call));

        CallLogInvestigationResult log = investigateLogsIfNeeded(call);

        if (log.hasConclusion()) {

            int stepNum = steps.size() + 1;

            steps.add(new ReadableTraceStepRow(

                    stepNum,

                    call.callDatetime,

                    "Conclusión del log (" + log.sourceLabel + "): " + log.conclusion,

                    "Log",

                    call.retriesLabel(),

                    call.trunk,

                    call.agent,

                    call.duration));

        }

        String displayReason = CallLogInvestigationService.effectiveFailureReason(call, log);

        CallProblemDiagnosis diagnosis = CallProblemDiagnosisService.analyze(
                call, steps, displayReason, log);

        return new PhoneTraceDetail(call, steps, log, displayReason, diagnosis);

    }



    /**
     * Motivo + diagnosis without loading {@code call_progress_log} steps (for table columns).
     */
    public PhoneTraceRowSummary loadRowSummary(PhoneTraceCallRow call) throws Exception {
        if (!dbEnabled || call == null) {
            return new PhoneTraceRowSummary("", CallProblemDiagnosisService.analyze(
                    call, List.of(), "", null));
        }
        CallLogInvestigationResult log = investigateLogsIfNeeded(call);
        String displayReason = CallLogInvestigationService.effectiveFailureReason(call, log);
        CallProblemDiagnosis diagnosis = CallProblemDiagnosisService.analyze(
                call, List.of(), displayReason, log);
        return new PhoneTraceRowSummary(displayReason, diagnosis);
    }



    private CallLogInvestigationResult investigateLogsIfNeeded(PhoneTraceCallRow call) {
        if (CallLogInvestigationService.shouldInvestigateLogs(call)) {
            return logInvestigation.investigate(call);
        }
        return CallLogInvestigationResult.skipped("");
    }



    public void rescheduleInCampaign(PhoneTraceCallRow call) throws Exception {

        if (!dbEnabled || retryService == null) {

            throw new IllegalStateException("MySQL no configurado");

        }

        if (call == null || !call.canReschedule()) {

            throw new IllegalStateException("Solo se pueden reagendar llamadas salientes en fallo, "

                    + "corta o sin respuesta");

        }

        retryService.queueForRetry(call.callId);

    }

}

