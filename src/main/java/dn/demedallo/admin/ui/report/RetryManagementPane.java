package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.BulkFailureRetryPreview;
import dn.demedallo.admin.model.BulkFailureRetryResult;
import dn.demedallo.admin.model.CallProgressStepRow;
import dn.demedallo.admin.model.RetryCallRow;
import dn.demedallo.admin.service.RetryManagementService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

/**
 * Outgoing calls eligible for supervisor retry (re-queue for dialer).
 */
public final class RetryManagementPane extends BorderPane implements AutoCloseable {

    private static final Preferences PREFS = Preferences.userRoot()
            .node("dn.demedallo.admin.retry.management");

    private final RetryManagementService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "retry-management");
        t.setDaemon(true);
        return t;
    });

    private final ComboBox<String> shiftFrom = new ComboBox<>();
    private final ComboBox<String> shiftTo = new ComboBox<>();
    private final Label shiftIndicator = new Label();
    private final Label status = new Label();

    private final TableView<RetryCallRow> callsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<CallProgressStepRow> traceTable =
            new TableView<>(FXCollections.observableArrayList());

    private volatile int loadGeneration;
    private volatile int traceGeneration;

    public RetryManagementPane(AdminDbSettings dbSettings) {
        this.service = new RetryManagementService(dbSettings);
        getStyleClass().add("retry-management-pane");
        setPadding(new Insets(8));

        buildHourCombos();
        loadShiftPrefs();

        Button apply = new Button("Aplicar");
        apply.getStyleClass().add("monitor-btn");
        apply.setOnAction(e -> {
            saveShiftPrefs();
            refreshCalls();
        });

        Button bulkUnknown = new Button("Reagendar fallos sin uniqueid (24 h)");
        bulkUnknown.getStyleClass().add("monitor-btn");
        bulkUnknown.setDisable(!service.isDbEnabled());
        bulkUnknown.setOnAction(e -> confirmBulkFailureUnknown());

        shiftIndicator.getStyleClass().add("panel-shift-indicator");
        HBox shiftBar = new HBox(10,
                new Label("Desde:"), shiftFrom,
                new Label("Hasta:"), shiftTo,
                apply, bulkUnknown, shiftIndicator);
        shiftBar.setAlignment(Pos.CENTER_LEFT);
        shiftBar.getStyleClass().add("panel-shift-bar");

        buildCallsColumns();
        buildTraceColumns();
        callsTable.setPlaceholder(new Label("Sin llamadas salientes para reintentar en el rango"));
        traceTable.setPlaceholder(new Label("Seleccione una llamada para ver su trazabilidad"));
        callsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, row) -> {
            if (row != null) {
                loadTrace(row);
            } else {
                traceTable.getItems().clear();
            }
        });

        Parent callsWrap = TableViewUtil.wrapInScrollPane(callsTable, "llamadas-reintentos", true);
        Parent traceWrap = TableViewUtil.wrapInScrollPane(traceTable, "trazabilidad-reintentos", true);

        VBox callsBox = new VBox(6, sectionTitle("Llamadas salientes — gestión de reintentos"), callsWrap);
        VBox.setVgrow(callsWrap, Priority.ALWAYS);
        callsBox.setMaxWidth(Double.MAX_VALUE);
        callsBox.setMaxHeight(Double.MAX_VALUE);

        VBox traceBox = new VBox(6, sectionTitle("Trazabilidad (call_progress_log)"), traceWrap);
        VBox.setVgrow(traceWrap, Priority.ALWAYS);
        traceBox.setMaxWidth(Double.MAX_VALUE);
        traceBox.setMaxHeight(Double.MAX_VALUE);

        SplitPane split = new SplitPane(callsBox, traceBox);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.62);
        split.setMaxWidth(Double.MAX_VALUE);
        split.setMaxHeight(Double.MAX_VALUE);

        Label hint = new Label(
                "Solo campañas salientes. «Reagendar» deja la llamada pendiente para el dialer "
                        + "(ventana hoy / horario de campaña). El botón masivo reagenda Fallo sin "
                        + "uniqueid (24 h) omitiendo: último Success al número, otra fila pendiente, "
                        + "y lista «no llamar» (dont_call / dnc). Máx. 500. Requiere MySQL y dialer.");
        hint.getStyleClass().add("panel-hint");
        hint.setWrapText(true);

        VBox center = new VBox(8, hint, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        center.setFillWidth(true);

        status.getStyleClass().add("monitor-status");
        setTop(new VBox(8, shiftBar, status));
        setCenter(center);

        updateShiftIndicator();
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado — active la base de datos en el login.");
        } else {
            refreshCalls();
        }
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-table-title");
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER_LEFT);
        return l;
    }

    private void buildHourCombos() {
        List<String> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(String.format("%02d", h));
        }
        shiftFrom.getItems().addAll(hours);
        shiftTo.getItems().addAll(hours);
        shiftFrom.setValue("00");
        shiftTo.setValue("23");
        shiftFrom.setMaxWidth(72);
        shiftTo.setMaxWidth(72);
    }

    private void buildCallsColumns() {
        callsTable.getColumns().add(rescheduleColumn());
        addCol(callsTable, "ID", 52, r -> String.valueOf(r.callId));
        addCol(callsTable, "Campaña", 100, r -> r.campaignName, true);
        addCol(callsTable, "Teléfono", 100, r -> r.phone);
        addCol(callsTable, "Estado", 88, r -> r.statusLabel);
        addCol(callsTable, "Fecha/hora", 130, r -> r.callDatetime);
        addCol(callsTable, "Duración", 68, r -> r.duration);
        addCol(callsTable, "Reint.", 56, RetryCallRow::retriesLabel);
        addCol(callsTable, "Cód. fallo", 60, r -> r.failureCode);
        addCol(callsTable, "Causa fallo", 120, r -> r.failureCause, true);
        addCol(callsTable, "Troncal", 80, r -> r.trunk);
        addCol(callsTable, "Agente", 64, r -> r.agent);
        addCol(callsTable, "Uniqueid", 100, r -> r.uniqueid, true);
    }

    private TableColumn<RetryCallRow, Void> rescheduleColumn() {
        TableColumn<RetryCallRow, Void> c = new TableColumn<>("Acción");
        c.setPrefWidth(118);
        c.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Reagendar");

            {
                btn.getStyleClass().add("monitor-btn-small");
                btn.setOnAction(e -> {
                    RetryCallRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) {
                        confirmAndReschedule(row);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                RetryCallRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || !service.isDbEnabled()) {
                    setGraphic(null);
                    return;
                }
                btn.setDisable(false);
                if (row.retriesExhausted()) {
                    btn.setText("Reagendar *");
                } else {
                    btn.setText("Reagendar");
                }
                setGraphic(btn);
            }
        });
        return c;
    }

    private void buildTraceColumns() {
        addCol(traceTable, "Fecha/hora", 130, r -> r.datetime);
        addCol(traceTable, "Estado", 90, r -> r.status, true);
        addCol(traceTable, "Reint.", 48, r -> r.retry);
        addCol(traceTable, "Troncal", 80, r -> r.trunk);
        addCol(traceTable, "Duración", 68, r -> r.duration);
        addCol(traceTable, "Uniqueid", 100, r -> r.uniqueid, true);
        addCol(traceTable, "Agente", 64, r -> r.agent);
    }

    private static <T> void addCol(TableView<T> table, String title, double width,
            java.util.function.Function<T, String> fn) {
        addCol(table, title, width, fn, false);
    }

    private static <T> void addCol(TableView<T> table, String title, double width,
            java.util.function.Function<T, String> fn, boolean grow) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, width, grow);
        table.getColumns().add(c);
    }

    private void confirmAndReschedule(RetryCallRow row) {
        String warn = row.retriesExhausted()
                ? "Esta llamada agotó los reintentos de campaña (" + row.retriesLabel()
                + "). Se forzará un intento adicional.\n\n"
                : "";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reagendar llamada");
        alert.setHeaderText("Reagendar " + row.phone + " — " + row.campaignName);
        alert.setContentText(warn + "ID llamada: " + row.callId + "\n"
                + "El dialer volverá a marcar cuando haya canal disponible.\n\n"
                + "¿Continuar?");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                runReschedule(row);
            }
        });
    }

    private void confirmBulkFailureUnknown() {
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado.");
            return;
        }
        status.setText("Contando fallos sin uniqueid (24 h)…");
        worker.execute(() -> {
            try {
                BulkFailureRetryPreview preview = service.previewBulkFailureUnknownUniqueid();
                Platform.runLater(() -> showBulkConfirmDialog(preview));
            } catch (Exception ex) {
                AppLogFile.appendLine("[retry-mgmt] bulk preview: " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error al preparar reintento masivo: "
                        + ex.getMessage()));
            }
        });
    }

    private void showBulkConfirmDialog(BulkFailureRetryPreview preview) {
        if (preview.totalCalls == 0) {
            status.setText("No hay llamadas Fallo sin uniqueid en las últimas 24 horas.");
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Reintento masivo");
            info.setHeaderText("Sin llamadas para reagendar");
            info.setContentText("No se encontraron filas con estado Fallo y uniqueid vacío "
                    + "en las últimas 24 horas.");
            info.showAndWait();
            return;
        }
        String warn = preview.totalCalls >= 500
                ? "Se procesarán como máximo 500 llamadas (límite de seguridad).\n\n"
                : "";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reintento masivo");
        alert.setHeaderText("Reagendar " + preview.totalCalls + " llamadas Fallo sin uniqueid");
        alert.setContentText(warn
                + "Ventana: " + RetryManagementService.last24Hours().indicatorText + "\n"
                + "Campañas:" + preview.campaignSummaryText()
                + "\n\nCada fila se trata igual que «Reagendar *» (incluye reintentos agotados). "
                + "El dialer volverá a marcar según horario de campaña.\n\n¿Continuar?");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                runBulkFailureUnknown();
            } else {
                status.setText(preview.totalCalls + " llamadas listas para reintento masivo "
                        + "(cancelado).");
            }
        });
    }

    private void runBulkFailureUnknown() {
        status.setText("Reagendando fallos sin uniqueid (24 h)…");
        worker.execute(() -> {
            try {
                BulkFailureRetryResult result = service.queueBulkFailureUnknownUniqueid();
                AppLogFile.appendLine("[retry-mgmt] bulk reagendadas ok=" + result.successCount
                        + " fail=" + result.failedCount);
                Platform.runLater(() -> {
                    String msg = "Reintento masivo: " + result.successCount + " reagendadas";
                    if (result.skippedCount > 0) {
                        msg += ", " + result.skippedCount + " omitidas";
                    }
                    if (result.failedCount > 0) {
                        msg += ", " + result.failedCount + " con error";
                        if (!result.errorSamples.isEmpty()) {
                            msg += " — " + result.errorSamples.get(0);
                        }
                    }
                    status.setText(msg);
                    refreshCalls();
                    if (result.failedCount > 0) {
                        Alert warn = new Alert(Alert.AlertType.WARNING);
                        warn.setTitle("Reintento masivo");
                        warn.setHeaderText(result.successCount + " OK · " + result.failedCount
                                + " errores");
                        warn.setContentText(String.join("\n", result.errorSamples));
                        warn.showAndWait();
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[retry-mgmt] bulk error: " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error en reintento masivo: "
                        + ex.getMessage()));
            }
        });
    }

    private void runReschedule(RetryCallRow row) {
        status.setText("Reagendando llamada " + row.callId + "…");
        worker.execute(() -> {
            try {
                service.queueForRetry(row.callId);
                AppLogFile.appendLine("[retry-mgmt] reagendada call_id=" + row.callId
                        + " phone=" + row.phone);
                Platform.runLater(() -> {
                    status.setText("Llamada " + row.callId + " reagendada — el dialer reintentará "
                            + row.phone);
                    refreshCalls();
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[retry-mgmt] error call_id=" + row.callId + ": " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error al reagendar: " + ex.getMessage()));
            }
        });
    }

    private void refreshCalls() {
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado — active la base de datos en el login.");
            return;
        }
        ShiftDatetimeRange range = currentRange();
        int generation = ++loadGeneration;
        traceTable.getItems().clear();
        status.setText("Cargando llamadas para reintento…");
        worker.execute(() -> {
            try {
                List<RetryCallRow> rows = service.loadCalls(range);
                Platform.runLater(() -> {
                    if (generation != loadGeneration) {
                        return;
                    }
                    callsTable.getItems().setAll(rows);
                    int exhausted = 0;
                    for (RetryCallRow r : rows) {
                        if (r.retriesExhausted()) {
                            exhausted++;
                        }
                    }
                    status.setText(rows.size() + " llamadas · " + exhausted
                            + " con reintentos agotados · " + range.indicatorText
                            + " · seleccione una fila para trazabilidad");
                    if (!rows.isEmpty()) {
                        callsTable.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[retry-mgmt] load " + ex.getMessage());
                Platform.runLater(() -> {
                    if (generation == loadGeneration) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void loadTrace(RetryCallRow row) {
        int generation = ++traceGeneration;
        traceTable.getItems().clear();
        worker.execute(() -> {
            try {
                List<CallProgressStepRow> steps = service.loadTrace(row);
                Platform.runLater(() -> {
                    if (generation != traceGeneration) {
                        return;
                    }
                    traceTable.getItems().setAll(steps);
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[retry-mgmt] trace " + ex.getMessage());
                Platform.runLater(() -> status.setText("Trazabilidad: " + ex.getMessage()));
            }
        });
    }

    private ShiftDatetimeRange currentRange() {
        updateShiftIndicator();
        return ShiftDatetimeRange.ofHours(parseHour(shiftFrom.getValue()), parseHour(shiftTo.getValue()));
    }

    private void updateShiftIndicator() {
        shiftIndicator.setText(ShiftDatetimeRange.ofHours(
                parseHour(shiftFrom.getValue()), parseHour(shiftTo.getValue())).indicatorText);
    }

    private static int parseHour(String v) {
        if (v == null || v.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void loadShiftPrefs() {
        shiftFrom.setValue(String.format("%02d", PREFS.getInt("shift_from", 0)));
        shiftTo.setValue(String.format("%02d", PREFS.getInt("shift_to", 23)));
    }

    private void saveShiftPrefs() {
        PREFS.putInt("shift_from", parseHour(shiftFrom.getValue()));
        PREFS.putInt("shift_to", parseHour(shiftTo.getValue()));
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
