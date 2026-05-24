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
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Viewer for form_data_recolected (outgoing) and form_data_recolected_entry (incoming). */
public final class FormDataViewerPane extends BorderPane implements AutoCloseable {

    private final DbReportService dbReports;
    private final boolean dbConfigured;
    private final ExecutorService worker;

    private final DateRangeFilterPane dateFilter = new DateRangeFilterPane();
    private final ComboBox<String> tipoCombo = new ComboBox<>(
            FXCollections.observableArrayList("Ambos", "Entrante", "Saliente"));
    private final TextField campaignFilter = new TextField();
    private final Label status = new Label();
    private final TableView<List<String>> table = new TableView<>();

    public FormDataViewerPane(DbReportService dbReports, boolean dbConfigured) {
        this.dbReports = dbReports;
        this.dbConfigured = dbConfigured;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admin-form-data");
            t.setDaemon(true);
            return t;
        });
        getStyleClass().add("report-pane");
        setPadding(new Insets(12));

        Label desc = new Label(ReportId.FORM_DATA_VIEWER.description);
        desc.setWrapText(true);
        desc.getStyleClass().add("report-desc");

        tipoCombo.setValue("Ambos");
        campaignFilter.setPromptText("Filtrar por nombre de campaña (opcional)");
        campaignFilter.setPrefWidth(260);

        Button run = new Button("Buscar datos");
        run.getStyleClass().add("monitor-btn");
        run.setOnAction(e -> generate());

        HBox filters = new HBox(10,
                dateFilter,
                new Label("Tipo:"), tipoCombo,
                new Label("Campaña:"), campaignFilter,
                run);
        filters.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("monitor-status");
        if (!dbConfigured) {
            status.setText("Configure la base de datos call_center en el login.");
        }

        TableViewUtil.prepare(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        setTop(new VBox(8, desc, filters, status));
        setCenter(TableViewUtil.wrapInScrollPane(table));
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
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        ReportQueryParams params = new ReportQueryParams(from, to);
        String tipo = tipoCombo.getValue();
        if ("Entrante".equalsIgnoreCase(tipo)) {
            params.setOption("tipo", "E");
        } else if ("Saliente".equalsIgnoreCase(tipo)) {
            params.setOption("tipo", "S");
        } else {
            params.setOption("tipo", "all");
        }
        params.setOption("campaign", campaignFilter.getText().trim());
        final LocalDate f = from;
        final LocalDate t = to;
        status.setText("Consultando…");
        worker.execute(() -> {
            try {
                ReportTableData data = dbReports.run(ReportId.FORM_DATA_VIEWER, params);
                Platform.runLater(() -> applyTable(data, f, t));
            } catch (Exception ex) {
                AppLogFile.appendLine("[form-data] " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void applyTable(ReportTableData data, LocalDate from, LocalDate to) {
        TableViewUtil.applyStringColumns(table, data);
        status.setText("Período " + from + " — " + to + " · " + data.getRows().size()
                + " registro(s) (máx. 5000)");
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
