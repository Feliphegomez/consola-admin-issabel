package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.report.DbReportService;
import dn.demedallo.admin.report.ReportId;
import dn.demedallo.admin.report.ReportQueryParams;
import dn.demedallo.admin.report.ReportTableData;
import dn.demedallo.admin.ui.util.DateRangeFilterPane;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AppLogFile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

public final class HistoricalReportPane extends BorderPane implements AutoCloseable {

    private final ReportId reportId;
    private final DbReportService dbReports;
    private final boolean dbConfigured;
    private final ExecutorService worker;

    private final DateRangeFilterPane dateFilter = new DateRangeFilterPane();
    private final ComboBox<String> tipoCombo = new ComboBox<>(FXCollections.observableArrayList("E", "S"));
    private final ComboBox<String> estadoCombo = new ComboBox<>(FXCollections.observableArrayList("T", "E", "A", "N"));
    private final ComboBox<String> callTypeCombo = new ComboBox<>(FXCollections.observableArrayList("incoming", "outgoing"));
    private final Label status = new Label();
    private final TableView<List<String>> table = new TableView<>();

    public HistoricalReportPane(ReportId reportId, DbReportService dbReports, boolean dbConfigured) {
        this.reportId = reportId;
        this.dbReports = dbReports;
        this.dbConfigured = dbConfigured;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admin-report-" + reportId.moduleName);
            t.setDaemon(true);
            return t;
        });
        getStyleClass().add("report-pane");
        setPadding(new Insets(12));

        Label desc = new Label(reportId.description);
        desc.setWrapText(true);
        desc.getStyleClass().add("report-desc");

        tipoCombo.setValue("E");
        estadoCombo.setValue("T");
        callTypeCombo.setValue("incoming");

        Button run = new Button("Generar informe");
        run.getStyleClass().add("monitor-btn");
        run.setOnAction(e -> generate());

        HBox filters = new HBox(10, dateFilter);
        if (reportId == ReportId.CALLS_PER_HOUR || reportId == ReportId.GRAPHIC_CALLS) {
            tipoCombo.setPromptText("E/S");
            estadoCombo.setPromptText("Estado");
            filters.getChildren().addAll(new Label("Tipo:"), tipoCombo, new Label("Estado:"), estadoCombo);
        }
        if (reportId == ReportId.HOLD_TIME) {
            filters.getChildren().addAll(new Label("Tipo:"), callTypeCombo);
        }
        filters.getChildren().add(run);
        filters.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("monitor-status");
        if (!dbConfigured) {
            status.setText("Configure la base de datos call_center en el login para usar este informe.");
        }

        TableViewUtil.prepare(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        setTop(new VBox(8, desc, filters, status));
        setCenter(TableViewUtil.wrapInScrollPane(table, "informe-" + reportId.moduleName));
    }

    private void generate() {
        if (!dbConfigured) {
            status.setText("Base de datos no configurada.");
            return;
        }
        String dateErr = dateFilter.validate();
        if (dateErr != null) {
            status.setText(dateErr);
            return;
        }
        LocalDate from = dateFilter.getFrom();
        LocalDate to = dateFilter.getTo();
        if (to.isBefore(from)) {
            LocalDate t = from;
            from = to;
            to = t;
        }
        status.setText("Consultando…");
        ReportQueryParams params = new ReportQueryParams(from, to);
        if (reportId == ReportId.CALLS_PER_HOUR || reportId == ReportId.GRAPHIC_CALLS) {
            params.setOption("tipo", tipoCombo.getValue());
            params.setOption("estado", estadoCombo.getValue());
        }
        if (reportId == ReportId.HOLD_TIME) {
            params.setOption("call_type", callTypeCombo.getValue());
        }
        final LocalDate f = from;
        final LocalDate t = to;
        worker.execute(() -> {
            try {
                ReportTableData data = dbReports.run(reportId, params);
                Platform.runLater(() -> applyTable(data, f, t));
            } catch (Exception ex) {
                AppLogFile.appendLine("[report] " + reportId.moduleName + ": " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void applyTable(ReportTableData data, LocalDate from, LocalDate to) {
        TableViewUtil.applyStringColumns(table, data);
        status.setText("Período " + from + " — " + to + " · " + data.getRows().size() + " filas");
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
