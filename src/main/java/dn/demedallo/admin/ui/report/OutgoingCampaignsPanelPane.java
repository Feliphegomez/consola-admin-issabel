package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.IncomingPanelSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.OutgoingCampaignsPanelService;
import dn.demedallo.admin.service.PendingDialerService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

/**
 * Outgoing campaigns live panel (rep_outgoing_campaigns_panel layout).
 */
public final class OutgoingCampaignsPanelPane extends BorderPane implements AutoCloseable {

    private static final int POLL_SECONDS = 5;
    private static final Preferences PREFS = Preferences.userRoot()
            .node("dn.demedallo.admin.outgoing.panel");

    private final OutgoingCampaignsPanelService service;
    private final PendingDialerService pendingDialerService;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "outgoing-panel");
        t.setDaemon(true);
        return t;
    });

    private final ComboBox<String> shiftFrom = new ComboBox<>();
    private final ComboBox<String> shiftTo = new ComboBox<>();
    private final Label shiftIndicator = new Label();
    private final Label status = new Label();

    private final Label statTotal = new Label("0");
    private final Label statOnQueue = new Label("0");
    private final Label statSuccess = new Label("0");
    private final Label statLost = new Label("0");
    private final Label statAbandoned = new Label("0");
    private final Label statFinished = new Label("0");
    private final Label statAvg = new Label("00:00:00");
    private final Label statMax = new Label("00:00:00");

    private final TableView<IncomingPanelSnapshot.PanelActiveCallRow> activeCallsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<IncomingPanelSnapshot.PanelPendingCallRow> pendingCallsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<IncomingPanelSnapshot.PanelAgentRow> agentsTable =
            new TableView<>(FXCollections.observableArrayList());

    private Accordion callsAccordion;
    private TitledPane dialingPane;
    private TitledPane pendingPane;

    private Timeline pollTimeline;
    private volatile int loadGeneration;

    public OutgoingCampaignsPanelPane(AdminEccpClient client, AdminDbSettings dbSettings) {
        this.service = new OutgoingCampaignsPanelService(client, dbSettings);
        this.pendingDialerService = new PendingDialerService(dbSettings);
        getStyleClass().add("outgoing-campaign-panel");
        setPadding(new Insets(8));

        buildHourCombos();
        loadShiftPrefs();

        Button apply = new Button("Aplicar");
        apply.getStyleClass().add("monitor-btn");
        apply.setOnAction(e -> {
            saveShiftPrefs();
            refresh();
        });

        shiftIndicator.getStyleClass().add("panel-shift-indicator");
        HBox shiftBar = new HBox(10,
                new Label("Desde:"), shiftFrom,
                new Label("Hasta:"), shiftTo,
                apply, shiftIndicator);
        shiftBar.setAlignment(Pos.CENTER_LEFT);
        shiftBar.getStyleClass().add("panel-shift-bar");

        GridPane stats = buildStatsGrid();

        buildActiveCallsColumns();
        buildPendingCallsColumns();
        buildAgentsColumns();
        TableViewUtil.prepare(activeCallsTable);
        TableViewUtil.prepare(pendingCallsTable);
        TableViewUtil.prepare(agentsTable);
        activeCallsTable.setPrefHeight(140);
        activeCallsTable.setMinHeight(80);
        pendingCallsTable.setPrefHeight(200);
        pendingCallsTable.setMinHeight(100);
        VBox.setVgrow(agentsTable, Priority.ALWAYS);

        dialingPane = tableTitledPane("Llamadas marcando", activeCallsTable, "llamadas-marcando");
        pendingPane = tableTitledPane("Llamadas pendientes por salir", pendingCallsTable, "llamadas-pendientes");
        callsAccordion = new Accordion(dialingPane, pendingPane);
        callsAccordion.getStyleClass().add("panel-calls-accordion");
        callsAccordion.setExpandedPane(pendingPane);
        callsAccordion.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(callsAccordion, Priority.ALWAYS);
        VBox agentsBox = titledTable("Agentes:", agentsTable, true, "agentes-salientes");
        HBox tables = new HBox(12, callsAccordion, agentsBox);
        HBox.setHgrow(callsAccordion, Priority.ALWAYS);
        HBox.setHgrow(agentsBox, Priority.ALWAYS);
        agentsBox.setMaxWidth(Double.MAX_VALUE);

        status.getStyleClass().add("monitor-status");
        VBox center = new VBox(10, stats, tables);
        VBox.setVgrow(tables, Priority.ALWAYS);
        center.setFillWidth(true);

        setTop(new VBox(8, shiftBar, status));
        setCenter(center);

        updateShiftIndicator();
        startPolling();
        refresh();
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

    private GridPane buildStatsGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("panel-stats-grid");
        grid.setHgap(12);
        grid.setVgap(6);
        for (int c = 0; c < 6; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            if (c % 2 == 1) {
                cc.setHgrow(Priority.ALWAYS);
            }
            grid.getColumnConstraints().add(cc);
        }
        addStatCell(grid, 0, 0, "Total llamadas:", statTotal);
        addStatCell(grid, 0, 2, "Llamadas en cola:", statOnQueue);
        addStatCell(grid, 0, 4, "Llamadas conectadas:", statSuccess);
        addStatCell(grid, 1, 0, "Llamadas sin pista:", statLost);
        addStatCell(grid, 1, 2, "Llamadas abandonadas:", statAbandoned);
        addStatCell(grid, 1, 4, "Llamadas terminadas:", statFinished);
        addStatCell(grid, 2, 0, "Promedio duración:", statAvg);
        addStatCell(grid, 2, 2, "Duración máxima:", statMax);
        return grid;
    }

    private static void addStatCell(GridPane grid, int row, int col, String label, Label value) {
        grid.add(statLabel(label), col, row);
        value.getStyleClass().add("panel-stat-value");
        grid.add(value, col + 1, row);
    }

    private static Label statLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-stat-label");
        return l;
    }

    private static TitledPane tableTitledPane(String title, TableView<?> table, String exportName) {
        javafx.scene.Parent wrapped = TableViewUtil.wrapInScrollPane(table, exportName);
        VBox content = new VBox(wrapped);
        content.setFillWidth(true);
        VBox.setVgrow(wrapped, Priority.ALWAYS);
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(true);
        pane.setAnimated(true);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private static VBox titledTable(String title, TableView<?> table, boolean growTable, String exportName) {
        Label header = new Label(title);
        header.getStyleClass().add("panel-table-title");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        javafx.scene.Parent wrapped = TableViewUtil.wrapInScrollPane(table, exportName);
        VBox box = new VBox(6, header, wrapped);
        box.setMaxWidth(Double.MAX_VALUE);
        if (growTable) {
            VBox.setVgrow(wrapped, Priority.ALWAYS);
        }
        return box;
    }

    private static String exportNameForTitle(String title) {
        if (title == null) {
            return "tabla";
        }
        return title.toLowerCase()
                .replace(":", "")
                .replaceAll("[^a-z0-9áéíóúñ]+", "-")
                .replaceAll("-{2,}", "-");
    }

    private void buildActiveCallsColumns() {
        addCol(activeCallsTable, "Campaña", 110, r -> r.campaignName);
        addCol(activeCallsTable, "Estado", 90, r -> r.status);
        addCol(activeCallsTable, "Núm. telf.", 110, r -> r.phone);
        addCol(activeCallsTable, "Troncal", 100, r -> r.trunk);
        addCol(activeCallsTable, "Desde", 80, r -> r.since);
    }

    private void buildPendingCallsColumns() {
        addCol(pendingCallsTable, "Campaña", 110, r -> r.campaignName);
        addCol(pendingCallsTable, "Núm. telf.", 110, r -> r.phone);
        addCol(pendingCallsTable, "Reintentos", 72, r -> r.retries);
        addCol(pendingCallsTable, "Ventana", 160, r -> r.schedule);
        addCol(pendingCallsTable, "Agente", 100, r -> r.agent);
        pendingCallsTable.getColumns().add(pendingForceColumn());
    }

    private TableColumn<IncomingPanelSnapshot.PanelPendingCallRow, String> pendingForceColumn() {
        TableColumn<IncomingPanelSnapshot.PanelPendingCallRow, String> c = new TableColumn<>("Forzar");
        TableViewUtil.styleColumn(c, 72);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(""));
        c.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Forzar");

            {
                btn.getStyleClass().add("monitor-btn-small");
                btn.setOnAction(e -> {
                    IncomingPanelSnapshot.PanelPendingCallRow row =
                            getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null && row.callId > 0) {
                        confirmForcePending(row);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                IncomingPanelSnapshot.PanelPendingCallRow row =
                        getTableRow() == null ? null : getTableRow().getItem();
                boolean canForce = row != null && row.callId > 0 && pendingDialerService.isDbEnabled();
                btn.setDisable(!canForce);
                setGraphic(canForce ? btn : null);
            }
        });
        return c;
    }

    private void confirmForcePending(IncomingPanelSnapshot.PanelPendingCallRow row) {
        if (!pendingDialerService.isDbEnabled()) {
            status.setText("MySQL no configurado.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Forzar llamada pendiente");
        alert.setHeaderText(row.phone + " — " + row.campaignName);
        alert.setContentText("ID: " + row.callId + "\n\nAjusta la ventana a «ahora» para el dialer.\n\n¿Continuar?");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                runForcePending(row.callId, row.phone);
            }
        });
    }

    private void runForcePending(int callId, String phone) {
        status.setText("Forzando " + callId + "…");
        worker.execute(() -> {
            try {
                pendingDialerService.forcePendingCall(callId);
                Platform.runLater(() -> {
                    status.setText("Forzada " + callId + " (" + phone + ")");
                    refresh();
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[outgoing-panel] force " + callId + ": " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void buildAgentsColumns() {
        addCol(agentsTable, "Campaña", 100, r -> r.campaignName);
        addCol(agentsTable, "Agente", 100, r -> r.agent);
        addCol(agentsTable, "Estado", 120, r -> r.status);
        addCol(agentsTable, "Núm. telf.", 100, r -> r.phone);
        addCol(agentsTable, "Troncal", 90, r -> r.trunk);
        addCol(agentsTable, "Desde", 80, r -> r.since);
    }

    private static <T> void addCol(TableView<T> table, String title, double width,
            java.util.function.Function<T, String> fn) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, width);
        table.getColumns().add(c);
    }

    private void refresh() {
        ShiftDatetimeRange range = currentRange();
        int generation = ++loadGeneration;
        status.setText("Cargando…");
        worker.execute(() -> {
            try {
                IncomingPanelSnapshot snap = service.load(range);
                Platform.runLater(() -> {
                    if (generation != loadGeneration) {
                        return;
                    }
                    applySnapshot(snap, range);
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[outgoing-panel] " + ex.getMessage());
                Platform.runLater(() -> {
                    if (generation == loadGeneration) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void applySnapshot(IncomingPanelSnapshot snap, ShiftDatetimeRange range) {
        statTotal.setText(String.valueOf(snap.statusCount.total));
        statOnQueue.setText(String.valueOf(snap.statusCount.onQueue));
        statSuccess.setText(String.valueOf(snap.statusCount.success));
        statLost.setText(String.valueOf(snap.statusCount.lostTrack));
        statAbandoned.setText(String.valueOf(snap.statusCount.abandoned));
        statFinished.setText(String.valueOf(snap.statusCount.finished));
        statMax.setText(formatHms(snap.stats.maxDurationSec));
        int finished = snap.statusCount.finished > 0 ? snap.statusCount.finished : snap.statusCount.success;
        long avg = finished > 0 ? snap.stats.totalSec / finished : 0;
        statAvg.setText(formatHms(avg));

        activeCallsTable.getItems().setAll(snap.activeCalls);
        pendingCallsTable.getItems().setAll(snap.pendingCalls);
        agentsTable.getItems().setAll(snap.agents);

        if (dialingPane != null) {
            dialingPane.setText("Llamadas marcando (" + snap.activeCalls.size() + ")");
        }
        if (pendingPane != null) {
            pendingPane.setText("Llamadas pendientes por salir (" + snap.pendingCalls.size() + ")");
        }
        if (callsAccordion != null && callsAccordion.getExpandedPane() == null) {
            callsAccordion.setExpandedPane(
                    snap.activeCalls.isEmpty() ? pendingPane : dialingPane);
        }

        String dbNote = snap.statsFromDatabase ? "" : " (estadísticas del día: active MySQL en login)";
        String eccpNote = snap.eccpCampaignsPolled > 0
                ? " · ECCP " + snap.eccpCampaignsPolled + " campaña(s)"
                + (snap.eccpCampaignErrors > 0 ? ", " + snap.eccpCampaignErrors + " sin datos" : "")
                : "";
        String fallbackNote = snap.agentsFromGlobalFallback ? " · agentes vía estado global" : "";
        status.setText(snap.activeCalls.size() + " marcando, " + snap.pendingCalls.size()
                + " pendientes, " + snap.agents.size() + " agentes"
                + " · " + range.indicatorText + dbNote + eccpNote + fallbackNote);
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

    private static String formatHms(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(POLL_SECONDS), e -> refresh()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    private void loadShiftPrefs() {
        shiftFrom.setValue(String.format("%02d", PREFS.getInt("shift_from", 0)));
        shiftTo.setValue(String.format("%02d", PREFS.getInt("shift_to", 23)));
    }

    private void saveShiftPrefs() {
        PREFS.putInt("shift_from", parseHour(shiftFrom.getValue()));
        PREFS.putInt("shift_to", parseHour(shiftTo.getValue()));
    }

    public void shutdown() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
