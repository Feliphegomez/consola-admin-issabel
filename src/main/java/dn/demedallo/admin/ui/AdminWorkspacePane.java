package dn.demedallo.admin.ui;

import dn.demedallo.admin.report.ReportContext;
import dn.demedallo.admin.service.CallListenService;
import dn.demedallo.admin.service.ListenUiActions;
import dn.demedallo.admin.ui.nav.NavigationBarPane;
import dn.demedallo.admin.ui.nav.WorkspaceNavigation;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.protocol.AdminEccpClient;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;

import java.util.function.Consumer;

/**
 * Main workspace: six top-level areas and a top navigation trail bar.
 */
public final class AdminWorkspacePane extends BorderPane {

    private final AdminDashboardPane dashboardPane;
    private final AdminMonitorPane monitorPane;
    private final CallsWorkspacePane callsWorkspacePane;
    private final ReportsBrowserPane reportsPane;
    private final CampaignDataBrowserPane campaignDataPane;
    private final SystemWorkspacePane systemWorkspacePane;
    private final WorkspaceNavigation navigation = new WorkspaceNavigation();
    private final TabPane mainTabs = new TabPane();

    public AdminWorkspacePane(AdminEccpClient client, AdminMonitorSettings monitorSettings,
                              AdminDbSettings dbSettings, String eccpHost,
                              Consumer<String> onLogout) {
        ReportContext ctx = new ReportContext(client, monitorSettings, dbSettings);
        CallListenService listenService = new CallListenService(monitorSettings);
        ListenUiActions listenActions = new ListenUiActions(monitorSettings, listenService);
        this.dashboardPane = new AdminDashboardPane(client, dbSettings, listenActions);
        this.monitorPane = new AdminMonitorPane(client, monitorSettings, dbSettings, onLogout, listenActions);
        this.callsWorkspacePane = new CallsWorkspacePane(dbSettings, eccpHost);
        this.reportsPane = new ReportsBrowserPane(ctx);
        this.campaignDataPane = new CampaignDataBrowserPane(ctx);
        this.systemWorkspacePane = new SystemWorkspacePane(client, dbSettings, eccpHost);

        mainTabs.getStyleClass().add("monitor-tabs");
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabInicio = tab("Inicio", "Resumen en vivo: agentes, colas y llamadas", dashboardPane);
        Tab tabMon = tab("Monitoreo", "Tablas y paneles de campaña en tiempo real", monitorPane);
        Tab tabLlamadas = tab("Llamadas", "Trazabilidad, grabaciones y reintentos", callsWorkspacePane);
        Tab tabInformes = tab("Informes", "Históricos, en vivo y uso de canales", reportsPane);
        Tab tabCamp = tab("Campañas", "Llamadas y formularios capturados", campaignDataPane);
        Tab tabSistema = tab("Sistema", "Salud, PBX y logs del servidor", systemWorkspacePane);

        mainTabs.getTabs().addAll(tabInicio, tabMon, tabLlamadas, tabInformes, tabCamp, tabSistema);

        Runnable selectInicio = () -> selectMainTab(0);
        Runnable selectMon = () -> selectMainTab(1);
        Runnable selectLlamadas = () -> selectMainTab(2);
        Runnable selectInformes = () -> selectMainTab(3);
        Runnable selectCamp = () -> selectMainTab(4);
        Runnable selectSistema = () -> selectMainTab(5);

        callsWorkspacePane.bindNavigation(navigation, "Llamadas", selectLlamadas);
        reportsPane.bindNavigation(navigation, "Informes", selectInformes);
        monitorPane.bindNavigation(navigation, "Monitoreo", selectMon);
        systemWorkspacePane.bindNavigation(navigation, "Sistema", selectSistema);

        mainTabs.getSelectionModel().selectedItemProperty().addListener((o, old, tab) -> {
            if (tab == null) {
                return;
            }
            String title = tab.getText();
            Runnable select = switch (title) {
                case "Inicio" -> selectInicio;
                case "Monitoreo" -> selectMon;
                case "Llamadas" -> selectLlamadas;
                case "Informes" -> selectInformes;
                case "Campañas" -> selectCamp;
                case "Sistema" -> selectSistema;
                default -> null;
            };
            if (select != null) {
                navigation.setMainOnly(title, select);
            }
            refreshSubNavigation(tab);
        });

        BorderPane body = new BorderPane();
        body.setCenter(mainTabs);
        body.getStyleClass().add("workspace-body");

        setTop(new NavigationBarPane(navigation));
        setCenter(body);
        setPadding(new Insets(0));
        getStyleClass().add("app-root");

        mainTabs.getSelectionModel().selectFirst();
    }

    private void refreshSubNavigation(Tab mainTab) {
        javafx.scene.Node content = mainTab.getContent();
        if (content instanceof NavSectionPane nav) {
            nav.publishTrailForSelection();
        } else if (content instanceof AdminMonitorPane mon) {
            mon.refreshNavigationTrail();
        } else if (content instanceof ReportsBrowserPane rep) {
            rep.refreshNavigationTrail();
        }
    }

    private void selectMainTab(int index) {
        if (index >= 0 && index < mainTabs.getTabs().size()) {
            mainTabs.getSelectionModel().select(index);
        }
    }

    private static Tab tab(String title, String tooltip, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setTooltip(new Tooltip(tooltip));
        return t;
    }

    public void shutdown() {
        dashboardPane.shutdown();
        monitorPane.shutdown();
        try {
            callsWorkspacePane.close();
        } catch (Exception ignored) {
        }
        campaignDataPane.shutdown();
        reportsPane.shutdown();
        try {
            systemWorkspacePane.close();
        } catch (Exception ignored) {
        }
    }
}
