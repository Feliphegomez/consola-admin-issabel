package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.report.DbReportService;
import dn.demedallo.admin.report.ReportId;
import dn.demedallo.admin.report.ReportQueryParams;
import dn.demedallo.admin.report.ReportTableData;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AppLogFile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Historical per-agent metrics (sessions, breaks, inbound/outbound calls). */
public final class AgentDetailReportPane extends BorderPane implements AutoCloseable {

    private static final String ALL_AGENTS = "(Todos los agentes)";

    private final DbReportService dbReports;
    private final boolean dbConfigured;
    private final ExecutorService worker;

    private final DatePicker dateFrom = new DatePicker(LocalDate.now());
    private final DatePicker dateTo = new DatePicker(LocalDate.now());
    private final ComboBox<String> agentCombo = new ComboBox<>();
    private final Label status = new Label();
    private final TableView<List<String>> table = new TableView<>();

    public AgentDetailReportPane(DbReportService dbReports, boolean dbConfigured) {
        this.dbReports = dbReports;
        this.dbConfigured = dbConfigured;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admin-agent-detail");
            t.setDaemon(true);
            return t;
        });
        getStyleClass().add("report-pane");
        setPadding(new Insets(12));

        Label desc = new Label(ReportId.AGENT_DETAIL_REPORT.description);
        desc.setWrapText(true);
        desc.getStyleClass().add("report-desc");

        agentCombo.setPromptText("Filtrar agente");
        agentCombo.setMaxWidth(220);

        Button run = new Button("Generar informe");
        run.getStyleClass().add("monitor-btn");
        run.setOnAction(e -> generate());

        HBox filters = new HBox(10,
                new Label("Desde:"), dateFrom,
                new Label("Hasta:"), dateTo,
                new Label("Agente:"), agentCombo,
                run);
        filters.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("monitor-status");
        if (!dbConfigured) {
            status.setText("Configure la base de datos call_center en el login.");
        } else {
            loadAgentList();
        }

        TableViewUtil.prepare(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        setTop(new VBox(8, desc, filters, status));
        setCenter(TableViewUtil.wrapInScrollPane(table));
    }

    private void loadAgentList() {
        worker.execute(() -> {
            try {
                ReportTableData agents = dbReports.listAgentsForFilter();
                List<String> items = new ArrayList<>();
                items.add(ALL_AGENTS);
                for (List<String> row : agents.getRows()) {
                    if (!row.isEmpty()) {
                        String label = row.size() > 1 && !row.get(1).isBlank()
                                ? row.get(0) + " — " + row.get(1)
                                : row.get(0);
                        items.add(label);
                    }
                }
                Platform.runLater(() -> {
                    agentCombo.setItems(FXCollections.observableArrayList(items));
                    agentCombo.getSelectionModel().select(0);
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[agent-detail] agents: " + ex.getMessage());
            }
        });
    }

    private void generate() {
        if (!dbConfigured) {
            status.setText("Base de datos no configurada.");
            return;
        }
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) {
            status.setText("Seleccione fechas válidas.");
            return;
        }
        if (to.isBefore(from)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        ReportQueryParams params = new ReportQueryParams(from, to);
        String sel = agentCombo.getSelectionModel().getSelectedItem();
        if (sel != null && !sel.isBlank() && !ALL_AGENTS.equals(sel)) {
            int dash = sel.indexOf(" — ");
            params.setOption("agent", dash > 0 ? sel.substring(0, dash).trim() : sel.trim());
        }
        final LocalDate f = from;
        final LocalDate t = to;
        status.setText("Consultando…");
        worker.execute(() -> {
            try {
                ReportTableData data = dbReports.run(ReportId.AGENT_DETAIL_REPORT, params);
                Platform.runLater(() -> applyTable(data, f, t));
            } catch (Exception ex) {
                AppLogFile.appendLine("[agent-detail] " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void applyTable(ReportTableData data, LocalDate from, LocalDate to) {
        TableViewUtil.applyStringColumns(table, data);
        status.setText("Período " + from + " — " + to + " · " + data.getRows().size() + " agente(s)");
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
