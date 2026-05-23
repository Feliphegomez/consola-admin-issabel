package dn.demedallo.admin.service;

import dn.demedallo.admin.db.CallCenterDb;
import dn.demedallo.admin.db.FailedShortCallsDao;
import dn.demedallo.admin.db.FailedShortCallsDao.CallDirectionFilter;
import dn.demedallo.admin.model.CallProgressStepRow;
import dn.demedallo.admin.model.FailedShortCallRow;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.ShiftDatetimeRange;

import java.util.List;

public final class FailedShortCallsService {

    private final FailedShortCallsDao dao;
    private final boolean dbEnabled;

    public FailedShortCallsService(AdminDbSettings dbSettings) {
        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();
        this.dao = dbEnabled ? new FailedShortCallsDao(new CallCenterDb(dbSettings)) : null;
    }

    public boolean isDbEnabled() {
        return dbEnabled;
    }

    public List<FailedShortCallRow> loadCalls(ShiftDatetimeRange range, CallDirectionFilter filter)
            throws Exception {
        if (!dbEnabled || dao == null) {
            return List.of();
        }
        return dao.loadFailedShortCalls(range.start, range.end, filter);
    }

    public List<CallProgressStepRow> loadTrace(FailedShortCallRow call) throws Exception {
        if (!dbEnabled || dao == null || call == null) {
            return List.of();
        }
        return dao.loadProgressLog(call);
    }
}
