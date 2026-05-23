package dn.demedallo.admin.report;

import dn.demedallo.admin.db.CallCenterDb;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AdminMonitorSettings;

public final class ReportContext {

    public final AdminEccpClient eccp;
    public final AdminMonitorSettings monitorSettings;
    public final AdminDbSettings dbSettings;
    public final CallCenterDb db;
    public final DbReportService dbReports;

    public ReportContext(AdminEccpClient eccp, AdminMonitorSettings monitorSettings, AdminDbSettings dbSettings) {
        this.eccp = eccp;
        this.monitorSettings = monitorSettings;
        this.dbSettings = dbSettings;
        this.db = new CallCenterDb(dbSettings);
        this.dbReports = new DbReportService(db);
    }
}
