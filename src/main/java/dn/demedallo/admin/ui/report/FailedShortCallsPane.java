package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.db.FailedShortCallsDao.CallDirectionFilter;
import dn.demedallo.admin.model.CallProgressStepRow;
import dn.demedallo.admin.model.FailedShortCallRow;
import dn.demedallo.admin.service.FailedShortCallsService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
 * Failed and short calls with {@code call_progress_log} traceability.
 */
public final class FailedShortCallsPane extends BorderPane implements AutoCloseable {

    private static final Preferences PREFS = Preferences.userRoot()
            .node("dn.demedallo.admin.failed.short.calls");

    private final FailedShortCallsService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "failed-short-calls");
        t.setDaemon(true);
        return t;
    });

    private final ComboBox<String> shiftFrom = new ComboBox<>();
    private final ComboBox<String> shiftTo = new ComboBox<>();
    private final ComboBox<String> directionFilter = new ComboBox<>();
    private final Label shiftIndicator = new Label();
    private final Label status = new Label();

    private final TableView<FailedShortCallRow> callsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<CallProgressStepRow> traceTable =
            new TableView<>(FXCollections.observableArrayList());

    private volatile int loadGeneration;
    private volatile int traceGeneration;

    public FailedShortCallsPane(AdminDbSettings dbSettings) {
        this.service = new FailedShortCallsService(dbSettings);
        getStyleClass().add("failed-short-calls-pane");
        setPadding(new Insets(8));

        buildHourCombos();
        directionFilter.getItems().addAll("Todas", "Salientes", "Entrantes");
        directionFilter.setValue("Todas");
        directionFilter.setMaxWidth(120);
        loadShiftPrefs();

        Button apply = new Button("Aplicar");
        apply.getStyleClass().add("monitor-btn");
        apply.setOnAction(e -> {
            saveShiftPrefs();
            refreshCalls();
        });

        shiftIndicator.getStyleClass().add("panel-shift-indicator");
        HBox shiftBar = new HBox(10,
                new Label("Desde:"), shiftFrom,
                new Label("Hasta:"), shiftTo,
                new Label("Tipo:"), directionFilter,
                apply, shiftIndicator);
        shiftBar.setAlignment(Pos.CENTER_LEFT);
        shiftBar.getStyleClass().add("panel-shift-bar");

        buildCallsColumns();
        buildTraceColumns();
        TableViewUtil.prepare(callsTable);
        TableViewUtil.prepare(traceTable);
        callsTable.setPlaceholder(new Label("Sin llamadas fallidas o cortas en el rango"));
        traceTable.setPlaceholder(new Label("Seleccione una llamada para ver su trazabilidad"));
        callsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, row) -> {
            if (row != null) {
                loadTrace(row);
            } else {
                traceTable.getItems().clear();
            }
        });

        VBox traceBox = new VBox(6,
                sectionTitle("Trazabilidad (call_progress_log)"),
                TableViewUtil.wrapInScrollPane(traceTable, "trazabilidad-fallidas-cortas"));
        VBox.setVgrow(traceTable, Priority.ALWAYS);
        traceBox.setMaxHeight(Double.MAX_VALUE);

        VBox callsBox = new VBox(6,
                sectionTitle("Llamadas fallidas y cortas"),
                TableViewUtil.wrapInScrollPane(callsTable, "llamadas-fallidas-cortas"));
        VBox.setVgrow(callsTable, Priority.ALWAYS);

        SplitPane split = new SplitPane(callsBox, traceBox);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.62);
        VBox.setVgrow(split, Priority.ALWAYS);

        status.getStyleClass().add("monitor-status");
        VBox center = new VBox(8, split);
        VBox.setVgrow(center, Priority.ALWAYS);
        center.setFillWidth(true);

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
        addCol(callsTable, "Tipo", 72, FailedShortCallRow::directionLabel);
        addCol(callsTable, "ID", 52, r -> String.valueOf(r.callId));
        addCol(callsTable, "Campaña", 120, r -> r.campaignName);
        addCol(callsTable, "Teléfono", 105, r -> r.phone);
        addCol(callsTable, "Estado", 95, r -> r.statusLabel);
        addCol(callsTable, "Fecha/hora", 140, r -> r.callDatetime);
        addCol(callsTable, "Duración", 72, r -> r.duration);
        addCol(callsTable, "Reint.", 48, r -> r.retries);
        addCol(callsTable, "Cód. fallo", 65, r -> r.failureCode);
        addCol(callsTable, "Causa fallo", 140, r -> r.failureCause);
        addCol(callsTable, "Troncal", 90, r -> r.trunk);
        addCol(callsTable, "Uniqueid", 110, r -> r.uniqueid);
        addCol(callsTable, "Agente", 70, r -> r.agent);
    }

    private void buildTraceColumns() {
        addCol(traceTable, "Fecha/hora", 140, r -> r.datetime);
        addCol(traceTable, "Estado", 100, r -> r.status);
        addCol(traceTable, "Reint.", 48, r -> r.retry);
        addCol(traceTable, "Troncal", 90, r -> r.trunk);
        addCol(traceTable, "Duración", 72, r -> r.duration);
        addCol(traceTable, "Uniqueid", 110, r -> r.uniqueid);
        addCol(traceTable, "Agente", 70, r -> r.agent);
    }

    private static <T> void addCol(TableView<T> table, String title, double width,
            java.util.function.Function<T, String> fn) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, width);
        table.getColumns().add(c);
    }

    private void refreshCalls() {
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado — active la base de datos en el login.");
            return;
        }
        ShiftDatetimeRange range = currentRange();
        CallDirectionFilter filter = mapDirectionFilter(directionFilter.getValue());
        int generation = ++loadGeneration;
        traceTable.getItems().clear();
        status.setText("Cargando llamadas fallidas y cortas…");
        worker.execute(() -> {
            try {
                List<FailedShortCallRow> rows = service.loadCalls(range, filter);
                Platform.runLater(() -> {
                    if (generation != loadGeneration) {
                        return;
                    }
                    callsTable.getItems().setAll(rows);
                    int failures = 0;
                    int shortCalls = 0;
                    for (FailedShortCallRow r : rows) {
                        if ("Failure".equals(r.status)) {
                            failures++;
                        } else if ("ShortCall".equals(r.status)) {
                            shortCalls++;
                        }
                    }
                    status.setText(rows.size() + " llamadas · " + failures + " fallidas · "
                            + shortCalls + " cortas · " + range.indicatorText
                            + " · seleccione una fila para ver trazabilidad");
                    if (!rows.isEmpty()) {
                        callsTable.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[failed-short-calls] " + ex.getMessage());
                Platform.runLater(() -> {
                    if (generation == loadGeneration) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void loadTrace(FailedShortCallRow row) {
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
                    if (steps.isEmpty()) {
                        status.setText("Llamada " + row.directionLabel() + " #" + row.callId
                                + " — sin eventos en call_progress_log");
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[failed-short-calls] trace " + ex.getMessage());
                Platform.runLater(() -> status.setText("Trazabilidad: " + ex.getMessage()));
            }
        });
    }

    private static CallDirectionFilter mapDirectionFilter(String value) {
        if ("Salientes".equals(value)) {
            return CallDirectionFilter.OUTGOING;
        }
        if ("Entrantes".equals(value)) {
            return CallDirectionFilter.INCOMING;
        }
        return CallDirectionFilter.ALL;
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
        String dir = PREFS.get("direction", "Todas");
        if (directionFilter.getItems().contains(dir)) {
            directionFilter.setValue(dir);
        }
    }

    private void saveShiftPrefs() {
        PREFS.putInt("shift_from", parseHour(shiftFrom.getValue()));
        PREFS.putInt("shift_to", parseHour(shiftTo.getValue()));
        PREFS.put("direction", directionFilter.getValue());
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
