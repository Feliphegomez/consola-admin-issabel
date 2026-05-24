package dn.demedallo.admin.service;

import dn.demedallo.admin.db.CallCenterDb;
import dn.demedallo.admin.db.FailedShortCallsDao;
import dn.demedallo.admin.db.RetryManagementDao;
import dn.demedallo.admin.db.RetryManagementDao.CampaignSchedule;
import dn.demedallo.admin.model.BulkFailureRetryPreview;
import dn.demedallo.admin.model.BulkFailureRetryResult;
import dn.demedallo.admin.model.CallProgressStepRow;
import dn.demedallo.admin.model.RetryCallRow;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.ShiftDatetimeRange;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class RetryManagementService {

    public static final class RetrySchedule {
        public final LocalDate dateInit;
        public final LocalDate dateEnd;
        public final LocalTime timeInit;
        public final LocalTime timeEnd;
        public final int retries;

        public RetrySchedule(LocalDate dateInit, LocalDate dateEnd, LocalTime timeInit,
                LocalTime timeEnd, int retries) {
            this.dateInit = dateInit;
            this.dateEnd = dateEnd;
            this.timeInit = timeInit;
            this.timeEnd = timeEnd;
            this.retries = retries;
        }
    }

    private final RetryManagementDao retryDao;
    private final FailedShortCallsDao traceDao;
    private final boolean dbEnabled;

    public RetryManagementService(AdminDbSettings dbSettings) {
        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();
        if (dbEnabled) {
            CallCenterDb db = new CallCenterDb(dbSettings);
            this.retryDao = new RetryManagementDao(db);
            this.traceDao = new FailedShortCallsDao(db);
        } else {
            this.retryDao = null;
            this.traceDao = null;
        }
    }

    public boolean isDbEnabled() {
        return dbEnabled;
    }

    public List<RetryCallRow> loadCalls(ShiftDatetimeRange range) throws Exception {
        if (!dbEnabled || retryDao == null) {
            return List.of();
        }
        return retryDao.loadRetryCandidates(range.start, range.end);
    }

    /** Rolling 24 hours ending now (computed in Java, not in SQL). */
    public static ShiftDatetimeRange last24Hours() {
        return ShiftDatetimeRange.ofLastHours(24);
    }

    public BulkFailureRetryPreview previewBulkFailureUnknownUniqueid() throws Exception {
        if (!dbEnabled || retryDao == null) {
            return BulkFailureRetryPreview.empty("", "");
        }
        ShiftDatetimeRange range = last24Hours();
        LocalDate today = LocalDate.now();
        int raw = retryDao.countFailureUnknownRaw(range.start, range.end);
        int eligible = retryDao.countFailureUnknownEligible(range.start, range.end, today);
        List<BulkFailureRetryPreview.CampaignCount> counts =
                retryDao.countFailureUnknownEligibleByCampaign(range.start, range.end, today);
        return new BulkFailureRetryPreview(
                raw,
                eligible,
                range.start,
                range.end,
                BulkFailureRetryPreview.fromPairs(counts));
    }

    public BulkFailureRetryResult queueBulkFailureUnknownUniqueid() throws Exception {
        if (!dbEnabled || retryDao == null) {
            throw new IllegalStateException("MySQL no configurado");
        }
        ShiftDatetimeRange range = last24Hours();
        LocalDate today = LocalDate.now();
        int raw = retryDao.countFailureUnknownRaw(range.start, range.end);
        int eligibleTotal = retryDao.countFailureUnknownEligible(range.start, range.end, today);
        List<Integer> ids = retryDao.loadFailureUnknownEligibleCallIds(
                range.start, range.end, today);
        int skipped = Math.max(0, raw - eligibleTotal);
        int ok = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        for (int callId : ids) {
            try {
                queueForRetry(callId);
                ok++;
            } catch (Exception ex) {
                fail++;
                if (errors.size() < 8) {
                    errors.add("ID " + callId + ": " + ex.getMessage());
                }
            }
        }
        return new BulkFailureRetryResult(ok, fail, skipped, errors);
    }

    public List<CallProgressStepRow> loadTrace(RetryCallRow call) throws Exception {
        if (!dbEnabled || traceDao == null || call == null) {
            return List.of();
        }
        return traceDao.loadProgressLog(call.toFailedShortRow());
    }

    /**
     * Builds schedule for immediate retry and updates {@code calls} so the dialer can place again.
     */
    public void queueForRetry(int callId) throws Exception {
        if (!dbEnabled || retryDao == null) {
            throw new IllegalStateException("MySQL no configurado");
        }
        CampaignSchedule camp = retryDao.loadCampaignSchedule(callId);
        if (camp == null) {
            throw new IllegalArgumentException("Llamada no encontrada: " + callId);
        }
        if (!isRetryableStatus(camp.callStatus())) {
            throw new IllegalStateException("La llamada ya no está en estado reintentable ("
                    + camp.callStatus() + ")");
        }

        LocalDate today = LocalDate.now();
        if (today.isAfter(camp.campaignDateEnd())) {
            throw new IllegalStateException("La campaña ya finalizó (" + camp.campaignDateEnd() + ")");
        }

        LocalDate dateInit = today.isBefore(camp.campaignDateStart()) ? camp.campaignDateStart() : today;
        LocalDate dateEnd = camp.campaignDateEnd();
        LocalTime now = LocalTime.now();
        LocalTime timeInit = clampTime(now, camp.daytimeStart(), camp.daytimeEnd());
        LocalTime timeEnd = camp.daytimeEnd();

        int adjustedRetries = camp.callRetries();
        if (camp.maxRetries() > 0 && adjustedRetries >= camp.maxRetries()) {
            adjustedRetries = Math.max(0, camp.maxRetries() - 1);
        }

        RetrySchedule schedule = new RetrySchedule(dateInit, dateEnd, timeInit, timeEnd, adjustedRetries);
        int updated = retryDao.queueOutgoingForRetry(callId, schedule.dateInit, schedule.dateEnd,
                schedule.timeInit, schedule.timeEnd, schedule.retries);
        if (updated != 1) {
            throw new IllegalStateException("No se pudo reagendar la llamada (id=" + callId + ")");
        }
    }

    private static boolean isRetryableStatus(String status) {
        return "Failure".equals(status) || "ShortCall".equals(status) || "NoAnswer".equals(status);
    }

    private static LocalTime clampTime(LocalTime value, LocalTime min, LocalTime max) {
        if (value.isBefore(min)) {
            return min;
        }
        if (value.isAfter(max)) {
            return max;
        }
        return value;
    }
}
