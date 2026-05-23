package dn.demedallo.admin.ui;

import dn.demedallo.admin.report.ReportContext;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.protocol.AdminEccpClient;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

import java.util.function.Consumer;

public final class AdminWorkspacePane extends BorderPane {

    private final AdminDashboardPane dashboardPane;
    private final AdminMonitorPane monitorPane;
    private final ReportsBrowserPane reportsPane;
    private final CampaignDataBrowserPane campaignDataPane;
    private final IssabelLogsPane logsPane;

    public AdminWorkspacePane(AdminEccpClient client, AdminMonitorSettings monitorSettings,
                              AdminDbSettings dbSettings, String eccpHost,
                              Consumer<String> onLogout) {
        ReportContext ctx = new ReportContext(client, monitorSettings, dbSettings);
        this.dashboardPane = new AdminDashboardPane(client, dbSettings);
        this.monitorPane = new AdminMonitorPane(client, monitorSettings, dbSettings, onLogout);
        this.reportsPane = new ReportsBrowserPane(ctx);
        this.campaignDataPane = new CampaignDataBrowserPane(ctx);
        this.logsPane = new IssabelLogsPane(eccpHost);

        TabPane root = new TabPane();
        root.getStyleClass().add("monitor-tabs");
        root.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab dash = new Tab("Dashboard", dashboardPane);
        Tab mon = new Tab("Monitoreo", monitorPane);
        Tab campData = new Tab("Datos campañas", campaignDataPane);
        Tab rep = new Tab("Informes", reportsPane);
        Tab logs = new Tab("Logs Issabel", logsPane);
        root.getTabs().addAll(dash, mon, campData, rep, logs);

        setCenter(root);
        setPadding(new Insets(0));
        getStyleClass().add("app-root");
    }

    public void shutdown() {
        dashboardPane.shutdown();
        monitorPane.shutdown();
        campaignDataPane.shutdown();
        reportsPane.shutdown();
        logsPane.shutdown();
    }
}
