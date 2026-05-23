package dn.demedallo.admin.ui;

import dn.demedallo.admin.report.CampaignDataService;
import dn.demedallo.admin.report.ReportContext;
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
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Campaign form data browser with full call context (rep_incoming/outgoing campaign data).
 */
public final class CampaignDataBrowserPane extends BorderPane implements AutoCloseable {

    private final CampaignDataService campaignData;
    private final boolean dbConfigured;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-campaign-data");
        t.setDaemon(true);
        return t;
    });

    private final ComboBox<String> typeCombo = new ComboBox<>(
            FXCollections.observableArrayList("Saliente", "Entrante"));
    private final ComboBox<CampaignDataService.CampaignOption> campaignCombo = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker(LocalDate.now());
    private final DatePicker dateTo = new DatePicker(LocalDate.now());
    private final TextField phoneFilter = new TextField();
    private final Label status = new Label();
    private final Label callDetail = new Label();
    private final TableView<List<String>> callsTable = new TableView<>();
    private final TableView<List<String>> formTable = new TableView<>();

    private ReportTableData lastCallsData = ReportTableData.empty("");

    public CampaignDataBrowserPane(ReportContext ctx) {
        this.campaignData = new CampaignDataService(ctx.db);
        this.dbConfigured = ctx.dbSettings.isConfigured();
        getStyleClass().add("monitor-root");
        setPadding(new Insets(12));

        typeCombo.setValue("Saliente");
        typeCombo.setOnAction(e -> loadCampaignList());
        campaignCombo.setPrefWidth(360);
        campaignCombo.setPromptText("Seleccione campaña");
        phoneFilter.setPromptText("Filtrar teléfono (opcional)");
        phoneFilter.setPrefWidth(160);

        Button search = new Button("Buscar llamadas");
        search.getStyleClass().add("monitor-btn");
        search.setOnAction(e -> searchCalls());

        HBox filters = new HBox(10,
                new Label("Tipo:"), typeCombo,
                new Label("Campaña:"), campaignCombo,
                new Label("Desde:"), dateFrom,
                new Label("Hasta:"), dateTo,
                new Label("Teléfono:"), phoneFilter,
                search);
        filters.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("monitor-status");
        if (!dbConfigured) {
            status.setText("Configure la base de datos call_center en el login.");
        } else {
            loadCampaignList();
        }

        TableViewUtil.prepare(callsTable);
        callsTable.getSelectionModel().selectedItemProperty()
                .addListener((o, old, row) -> loadFormForRow(row));

        TableViewUtil.prepare(formTable);

        callDetail.setWrapText(true);
        callDetail.getStyleClass().add("report-desc");
        callDetail.setText("Seleccione una llamada para ver el formulario y el detalle.");

        VBox detailBox = new VBox(6, callDetail, TableViewUtil.wrapInScrollPane(formTable, "datos-formulario"));
        VBox.setVgrow(formTable, Priority.ALWAYS);

        SplitPane split = new SplitPane(TableViewUtil.wrapInScrollPane(callsTable, "datos-campana-llamadas"), detailBox);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        VBox.setVgrow(split, Priority.ALWAYS);

        Label desc = new Label(
                "Navegue por campaña: cada fila incluye agente, cola, intentos al mismo número y datos de la llamada. "
                        + "Abajo se muestran los campos del formulario capturados.");
        desc.setWrapText(true);
        desc.getStyleClass().add("report-desc");

        setTop(new VBox(8, desc, filters, status));
        setCenter(split);
    }

    private void loadCampaignList() {
        if (!dbConfigured) {
            return;
        }
        String type = "Entrante".equals(typeCombo.getValue()) ? "incoming" : "outgoing";
        worker.execute(() -> {
            try {
                List<CampaignDataService.CampaignOption> list = campaignData.listCampaigns(type);
                Platform.runLater(() -> {
                    campaignCombo.setItems(FXCollections.observableArrayList(list));
                    if (!list.isEmpty()) {
                        campaignCombo.getSelectionModel().select(0);
                    }
                    status.setText(list.size() + " campaña(s) " + typeCombo.getValue().toLowerCase());
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[campaign-data] list: " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error listando campañas: " + ex.getMessage()));
            }
        });
    }

    private void searchCalls() {
        if (!dbConfigured) {
            status.setText("Base de datos no configurada.");
            return;
        }
        CampaignDataService.CampaignOption camp = campaignCombo.getSelectionModel().getSelectedItem();
        if (camp == null) {
            status.setText("Seleccione una campaña.");
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
        String type = camp.type();
        String fromDt = from + " 00:00:00";
        String toDt = to + " 23:59:59";
        String phone = phoneFilter.getText();
        status.setText("Consultando llamadas…");
        formTable.getColumns().clear();
        formTable.getItems().clear();
        callDetail.setText("Cargando…");
        worker.execute(() -> {
            try {
                ReportTableData data = campaignData.campaignCalls(type, camp.id(), fromDt, toDt, phone);
                Platform.runLater(() -> {
                    lastCallsData = data;
                    applyCallsTable(data);
                    status.setText(camp.name() + " · " + data.getRows().size() + " llamada(s) · cola "
                            + camp.queue());
                    if (!data.getRows().isEmpty()) {
                        callsTable.getSelectionModel().select(0);
                    } else {
                        callDetail.setText("Sin llamadas en el período seleccionado.");
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[campaign-data] calls: " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void loadFormForRow(List<String> row) {
        if (row == null || row.isEmpty() || lastCallsData.getColumns().isEmpty()) {
            return;
        }
        CampaignDataService.CampaignOption camp = campaignCombo.getSelectionModel().getSelectedItem();
        if (camp == null) {
            return;
        }
        int callIdCol = columnIndex(lastCallsData.getColumns(), "ID_llamada");
        if (callIdCol < 0 || callIdCol >= row.size()) {
            return;
        }
        int callId;
        try {
            callId = Integer.parseInt(row.get(callIdCol));
        } catch (NumberFormatException e) {
            return;
        }
        callDetail.setText(buildCallSummary(row, lastCallsData.getColumns()));
        worker.execute(() -> {
            try {
                ReportTableData forms = campaignData.formDataForCall(camp.type(), callId);
                Platform.runLater(() -> {
                    applyTable(formTable, forms);
                    if (forms.getRows().isEmpty()) {
                        callDetail.setText(callDetail.getText() + "\n\n(Sin datos de formulario para esta llamada)");
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[campaign-data] form: " + ex.getMessage());
                Platform.runLater(() -> callDetail.setText(callDetail.getText()
                        + "\n\nError formulario: " + ex.getMessage()));
            }
        });
    }

    private static String buildCallSummary(List<String> row, List<String> cols) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(row.size(), cols.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(cols.get(i)).append(": ").append(row.get(i));
        }
        return sb.toString();
    }

    private void applyCallsTable(ReportTableData data) {
        TableViewUtil.applyStringColumns(callsTable, data);
        for (TableColumn<List<String>, ?> col : callsTable.getColumns()) {
            if ("Datos".equals(col.getText())) {
                TableViewUtil.styleColumn(col, 48);
                col.setStyle("-fx-alignment: CENTER;");
            }
        }
    }

    private static void applyTable(TableView<List<String>> table, ReportTableData data) {
        TableViewUtil.applyStringColumns(table, data);
    }

    private static int columnIndex(List<String> columns, String name) {
        if (columns == null) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (name.equals(columns.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
