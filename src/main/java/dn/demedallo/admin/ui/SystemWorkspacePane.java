package dn.demedallo.admin.ui;

import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.ui.nav.WorkspaceNavigation;
import dn.demedallo.admin.ui.pbx.PbxAdminPane;
import dn.demedallo.admin.util.AdminDbSettings;

import java.util.List;

/**
 * Sistema: salud del servidor, configuración PBX y logs.
 */
public final class SystemWorkspacePane extends NavSectionPane implements AutoCloseable {

    private final ServiceMonitoringPane healthPane;
    private final PbxAdminPane pbxPane;
    private final IssabelLogsPane logsPane;

    public SystemWorkspacePane(AdminEccpClient client, AdminDbSettings dbSettings, String eccpHost) {
        super("Sistema", List.of(
                new NavItem("health", "Salud",
                        "Dialer, Asterisk, disco y servicios Issabel"),
                new NavItem("pbx", "PBX / Asterisk",
                        "Colas, extensiones, rutas e IVR (base FreePBX)"),
                new NavItem("logs", "Logs",
                        "Dialer, Asterisk y consola (vía SSH)")));
        this.healthPane = new ServiceMonitoringPane(client, dbSettings, eccpHost);
        this.pbxPane = new PbxAdminPane(dbSettings, eccpHost);
        this.logsPane = new IssabelLogsPane(eccpHost);
        register("health", () -> healthPane);
        register("pbx", () -> pbxPane);
        register("logs", () -> logsPane);
    }

    public void bindNavigation(WorkspaceNavigation nav, String mainTabLabel, Runnable selectMainTab) {
        super.bindNavigation(nav, mainTabLabel, selectMainTab);
        setOnItemShown(item -> {
            if (item != null && "pbx".equals(item.id())) {
                pbxPane.attachNavigation(nav, mainTabLabel, selectMainTab, item.label(),
                        () -> selectItemById("pbx"));
            }
        });
        publishTrailForSelection();
        NavItem selected = getSelectedItem();
        if (selected != null && "pbx".equals(selected.id())) {
            pbxPane.attachNavigation(nav, mainTabLabel, selectMainTab, selected.label(),
                    () -> selectItemById("pbx"));
        }
    }

    @Override
    public void close() {
        shutdown();
        healthPane.shutdown();
        try {
            pbxPane.close();
        } catch (Exception ignored) {
        }
        logsPane.shutdown();
    }
}
