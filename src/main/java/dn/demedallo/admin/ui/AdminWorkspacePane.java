package dn.demedallo.admin.ui;

import dn.demedallo.admin.report.ReportContext;
import dn.demedallo.admin.service.CallListenService;
import dn.demedallo.admin.service.ListenUiActions;
import dn.demedallo.admin.ui.live.LiveOverviewPane;
import dn.demedallo.admin.ui.pbx.PbxAdminPane;
import dn.demedallo.admin.ui.report.CallRecordingsPane;
import dn.demedallo.admin.ui.report.ChannelUsagePane;
import dn.demedallo.admin.ui.report.PhoneTraceSearchPane;
import dn.demedallo.admin.ui.report.RetryManagementPane;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.protocol.AdminEccpClient;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

import java.util.function.Consumer;

public final class AdminWorkspacePane extends BorderPane {

    private final LiveOverviewPane liveOverviewPane;
    private final AdminDashboardPane dashboardPane;
    private final AdminMonitorPane monitorPane;
    private final RetryManagementPane retryManagementPane;
    private final PhoneTraceSearchPane phoneTraceSearchPane;
    private final CallRecordingsPane callRecordingsPane;
    private final ReportsBrowserPane reportsPane;
    private final CampaignDataBrowserPane campaignDataPane;
    private final IssabelLogsPane logsPane;
    private final ServiceMonitoringPane serviceMonitoringPane;
    private final ChannelUsagePane channelUsagePane;
    private final PbxAdminPane pbxAdminPane;

    public AdminWorkspacePane(AdminEccpClient client, AdminMonitorSettings monitorSettings,
                              AdminDbSettings dbSettings, String eccpHost,
                              Consumer<String> onLogout) {
        ReportContext ctx = new ReportContext(client, monitorSettings, dbSettings);
        CallListenService listenService = new CallListenService(monitorSettings);
        ListenUiActions listenActions = new ListenUiActions(monitorSettings, listenService);
        this.liveOverviewPane = new LiveOverviewPane(client, dbSettings, eccpHost);
        this.dashboardPane = new AdminDashboardPane(client, dbSettings, listenActions);
        this.monitorPane = new AdminMonitorPane(client, monitorSettings, dbSettings, onLogout, listenActions);
        this.retryManagementPane = new RetryManagementPane(dbSettings);
        this.phoneTraceSearchPane = new PhoneTraceSearchPane(dbSettings, eccpHost);
        this.callRecordingsPane = new CallRecordingsPane(dbSettings, eccpHost);
        this.reportsPane = new ReportsBrowserPane(ctx);
        this.campaignDataPane = new CampaignDataBrowserPane(ctx);
        this.logsPane = new IssabelLogsPane(eccpHost);
        this.serviceMonitoringPane = new ServiceMonitoringPane(client, dbSettings, eccpHost);
        this.channelUsagePane = new ChannelUsagePane(dbSettings);
        this.pbxAdminPane = new PbxAdminPane(dbSettings, eccpHost);

        TabPane root = new TabPane();
        root.getStyleClass().add("monitor-tabs");
        root.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab inicio = new Tab("Inicio", liveOverviewPane);
        Tab dash = new Tab("Dashboard", dashboardPane);
        Tab services = new Tab("Monitoreo de servicios", serviceMonitoringPane);
        Tab channelUsage = new Tab("Uso de canales", channelUsagePane);
        Tab mon = new Tab("Monitoreo", monitorPane);
        Tab retry = new Tab("Gestión Reintentos", retryManagementPane);
        Tab traceSearch = new Tab("Buscar trazabilidad", phoneTraceSearchPane);
        Tab recordings = new Tab("Grabaciones", callRecordingsPane);
        Tab campData = new Tab("Datos campañas", campaignDataPane);
        Tab rep = new Tab("Informes", reportsPane);
        Tab logs = new Tab("Logs Issabel", logsPane);
        Tab pbx = new Tab("Asterisk / PBX", pbxAdminPane);
        root.getTabs().addAll(inicio, dash, services, channelUsage, mon, retry, traceSearch, recordings, campData, rep, pbx, logs);

        setCenter(root);
        setPadding(new Insets(0));
        getStyleClass().add("app-root");
    }

    public void shutdown() {
        try {
            liveOverviewPane.close();
        } catch (Exception ignored) {
        }
        dashboardPane.shutdown();
        monitorPane.shutdown();
        try {
            retryManagementPane.close();
        } catch (Exception ignored) {
        }
        try {
            phoneTraceSearchPane.close();
        } catch (Exception ignored) {
        }
        try {
            callRecordingsPane.close();
        } catch (Exception ignored) {
        }
        campaignDataPane.shutdown();
        reportsPane.shutdown();
        logsPane.shutdown();
        serviceMonitoringPane.shutdown();
        try {
            channelUsagePane.close();
        } catch (Exception ignored) {
        }
        try {
            pbxAdminPane.close();
        } catch (Exception ignored) {
        }
    }
}
