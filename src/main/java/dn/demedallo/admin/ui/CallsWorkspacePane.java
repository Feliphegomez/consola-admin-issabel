package dn.demedallo.admin.ui;

import dn.demedallo.admin.ui.report.CallRecordingsPane;
import dn.demedallo.admin.ui.report.PhoneTraceSearchPane;
import dn.demedallo.admin.ui.report.RetryManagementPane;
import dn.demedallo.admin.util.AdminDbSettings;

import java.util.List;

/**
 * Llamadas: trazabilidad, grabaciones y reintentos (menú lateral).
 */
public final class CallsWorkspacePane extends NavSectionPane implements AutoCloseable {

    private final PhoneTraceSearchPane tracePane;
    private final CallRecordingsPane recordingsPane;
    private final RetryManagementPane retryPane;

    public CallsWorkspacePane(AdminDbSettings dbSettings, String eccpHost) {
        super("Llamadas", List.of(
                new NavItem("trace", "Trazabilidad",
                        "Buscar historial por número de teléfono"),
                new NavItem("recordings", "Grabaciones",
                        "Escuchar WAV y ver trazabilidad (CDR o campaña)"),
                new NavItem("retry", "Reintentos",
                        "Reprogramar llamadas salientes fallidas")));
        this.tracePane = new PhoneTraceSearchPane(dbSettings, eccpHost);
        this.recordingsPane = new CallRecordingsPane(dbSettings, eccpHost);
        this.retryPane = new RetryManagementPane(dbSettings);
        register("trace", () -> tracePane);
        register("recordings", () -> recordingsPane);
        register("retry", () -> retryPane);
    }

    @Override
    public void close() {
        shutdown();
        try {
            tracePane.close();
        } catch (Exception ignored) {
        }
        try {
            recordingsPane.close();
        } catch (Exception ignored) {
        }
        try {
            retryPane.close();
        } catch (Exception ignored) {
        }
    }
}
