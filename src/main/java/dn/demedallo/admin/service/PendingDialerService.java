package dn.demedallo.admin.service;

import dn.demedallo.admin.db.CallCenterDb;
import dn.demedallo.admin.db.RetryManagementDao;
import dn.demedallo.admin.db.RetryManagementDao.CampaignSchedule;
import dn.demedallo.admin.service.RetryManagementService.RetrySchedule;
import dn.demedallo.admin.util.AdminDbSettings;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Supervisor actions on outgoing calls waiting for the dialer ({@code calls.status IS NULL}).
 */
public final class PendingDialerService {

    private final boolean dbEnabled;
    private final RetryManagementDao retryDao;

    public PendingDialerService(AdminDbSettings dbSettings) {
        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();
        if (dbEnabled) {
            this.retryDao = new RetryManagementDao(new CallCenterDb(dbSettings));
        } else {
            this.retryDao = null;
        }
    }

    public boolean isDbEnabled() {
        return dbEnabled;
    }

    /**
     * Sets date/time window to include now so CampaignProcess can schedule the call on the next cycle.
     */
    public void forcePendingCall(int callId) throws Exception {
        if (!dbEnabled || retryDao == null) {
            throw new IllegalStateException("MySQL no configurado");
        }
        CampaignSchedule camp = retryDao.loadCampaignSchedule(callId);
        if (camp == null) {
            throw new IllegalArgumentException("Llamada no encontrada: " + callId);
        }
        if (!isPendingStatus(camp.callStatus())) {
            throw new IllegalStateException("La llamada ya no está pendiente (estado: "
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

        RetrySchedule schedule = new RetrySchedule(dateInit, dateEnd, timeInit, timeEnd, camp.callRetries());
        int updated = retryDao.queuePendingForImmediateDial(callId, schedule.dateInit, schedule.dateEnd,
                schedule.timeInit, schedule.timeEnd);
        if (updated != 1) {
            throw new IllegalStateException("No se pudo forzar la llamada (id=" + callId
                    + "). Compruebe que sigue pendiente y no está en lista «no llamar».");
        }
    }

    private static boolean isPendingStatus(String status) {
        return status == null || status.isBlank();
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
