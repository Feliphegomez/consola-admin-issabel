package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.IncomingPanelSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.IncomingCampaignsPanelService;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

/**
 * Incoming campaigns live panel (rep_incoming_campaigns_panel layout).
 */
public final class IncomingCampaignsPanelPane extends BorderPane implements AutoCloseable {

    private static final int POLL_SECONDS = 5;
    private static final Preferences PREFS = Preferences.userRoot()
            .node("dn.demedallo.admin.incoming.panel");

    private final AdminEccpClient client;
    private final IncomingCampaignsPanelService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "incoming-panel");
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
    private final TableView<IncomingPanelSnapshot.PanelAgentRow> agentsTable =
            new TableView<>(FXCollections.observableArrayList());

    private Timeline pollTimeline;
    private volatile int loadGeneration;

    public IncomingCampaignsPanelPane(AdminEccpClient client, AdminDbSettings dbSettings) {
        this.client = client;
        this.service = new IncomingCampaignsPanelService(client, dbSettings);
        getStyleClass().add("incoming-campaign-panel");
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
        buildAgentsColumns();
        TableViewUtil.prepare(activeCallsTable);
        TableViewUtil.prepare(agentsTable);
        VBox.setVgrow(activeCallsTable, Priority.ALWAYS);
        VBox.setVgrow(agentsTable, Priority.ALWAYS);

        VBox callsBox = titledTable("Llamadas activas:", activeCallsTable, "llamadas-activas-entrantes");
        VBox agentsBox = titledTable("Agentes:", agentsTable, "agentes-entrantes");
        HBox tables = new HBox(12, callsBox, agentsBox);
        HBox.setHgrow(callsBox, Priority.ALWAYS);
        HBox.setHgrow(agentsBox, Priority.ALWAYS);
        callsBox.setMaxWidth(Double.MAX_VALUE);
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

    private static VBox titledTable(String title, TableView<?> table, String exportName) {
        Label header = new Label(title);
        header.getStyleClass().add("panel-table-title");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        javafx.scene.Parent wrapped = TableViewUtil.wrapInScrollPane(table, exportName);
        VBox box = new VBox(6, header, wrapped);
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(wrapped, Priority.ALWAYS);
        return box;
    }

    private void buildActiveCallsColumns() {
        addCol(activeCallsTable, "Campaña", 110, r -> r.campaignName);
        addCol(activeCallsTable, "Estado", 90, r -> r.status);
        addCol(activeCallsTable, "Núm. telf.", 110, r -> r.phone);
        addCol(activeCallsTable, "Troncal", 100, r -> r.trunk);
        addCol(activeCallsTable, "Desde", 80, r -> r.since);
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
                AppLogFile.appendLine("[incoming-panel] " + ex.getMessage());
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
        agentsTable.getItems().setAll(snap.agents);

        String dbNote = snap.statsFromDatabase ? "" : " (estadísticas del día: active MySQL en login)";
        String eccpNote = snap.eccpCampaignsPolled > 0
                ? " · ECCP " + snap.eccpCampaignsPolled + " campaña(s)"
                + (snap.eccpCampaignErrors > 0 ? ", " + snap.eccpCampaignErrors + " sin datos" : "")
                : "";
        status.setText(snap.activeCalls.size() + " llamadas activas, " + snap.agents.size() + " agentes"
                + " · " + range.indicatorText + dbNote + eccpNote);
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
