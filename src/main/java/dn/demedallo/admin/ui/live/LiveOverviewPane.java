package dn.demedallo.admin.ui.live;

import dn.demedallo.admin.model.DashboardSnapshot.DashboardCallRow;
import dn.demedallo.admin.model.LiveOverviewSnapshot;
import dn.demedallo.admin.model.ServiceHealthModels.DiskVolume;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.LiveOverviewService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Live operational dashboard: resources, trunks, calls and agents at a glance.
 */
public final class LiveOverviewPane extends VBox implements AutoCloseable {

    private static final int POLL_SECONDS = 10;

    private final LiveOverviewService overviewService;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-overview");
        t.setDaemon(true);
        return t;
    });

    private final Label status = new Label("Cargando…");
    private final Label metricAgents = metricLabel("0");
    private final Label metricOnCall = metricLabel("0");
    private final Label metricIncoming = metricLabel("0");
    private final Label metricOutgoing = metricLabel("0");
    private final Label metricWaiting = metricLabel("0");
    private final Label metricChannels = metricLabel("—");
    private final Label metricCpu = metricLabel("—");
    private final Label metricRam = metricLabel("—");
    private final Label metricDisk = metricLabel("—");
    private final Label metricHealth = metricLabel("—");
    private final Label trunkSummary = new Label("—");
    private final CheckBox includeHealth = new CheckBox("Incluir recursos del servidor (SSH)");
    private final TableView<DashboardCallRow> incomingTable = new TableView<>();
    private final TableView<DashboardCallRow> outgoingTable = new TableView<>();

    private Timeline pollTimeline;
    private volatile int loadGen;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    public LiveOverviewPane(AdminEccpClient client, AdminDbSettings dbSettings, String eccpHost) {
        this.overviewService = new LiveOverviewService(client, dbSettings, eccpHost);
        getStyleClass().add("live-overview-pane");
        setPadding(new Insets(10));
        setSpacing(10);

        includeHealth.setSelected(true);
        includeHealth.selectedProperty().addListener((o, a, b) -> refreshNow());

        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refreshNow());

        HBox toolbar = new HBox(10, refresh, includeHealth, status);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("monitor-status");
        status.setWrapText(true);

        GridPane metrics = buildMetricsGrid();
        trunkSummary.setWrapText(true);
        trunkSummary.getStyleClass().add("live-trunk-summary");

        configureCallTables();
        TitledPane trunkPane = new TitledPane("Uso de troncales (llamadas activas)", trunkSummary);
        trunkPane.setCollapsible(false);
        trunkPane.getStyleClass().add("dashboard-section");

        TitledPane inPane = new TitledPane("Entrantes en curso",
                TableViewUtil.wrapInScrollPane(incomingTable, "vivo-entrantes", true));
        inPane.setCollapsible(false);
        TitledPane outPane = new TitledPane("Salientes en curso",
                TableViewUtil.wrapInScrollPane(outgoingTable, "vivo-salientes", true));
        outPane.setCollapsible(false);

        HBox calls = new HBox(10, inPane, outPane);
        HBox.setHgrow(inPane, Priority.ALWAYS);
        HBox.setHgrow(outPane, Priority.ALWAYS);
        inPane.setMaxWidth(Double.MAX_VALUE);
        outPane.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(toolbar, metrics, trunkPane, calls);
        VBox.setVgrow(calls, Priority.ALWAYS);

        startPolling();
    }

    private static Label metricLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("live-metric-value");
        return l;
    }

    private GridPane buildMetricsGrid() {
        GridPane g = new GridPane();
        g.getStyleClass().add("live-metrics-grid");
        g.setHgap(12);
        g.setVgap(12);
        g.setPadding(new Insets(4, 0, 8, 0));
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(cc);
        }
        int row = 0;
        g.add(metricTile("Agentes en línea", metricAgents), 0, row);
        g.add(metricTile("En llamada", metricOnCall), 1, row);
        g.add(metricTile("Entrantes vivas", metricIncoming), 2, row);
        g.add(metricTile("Salientes vivas", metricOutgoing), 3, row);
        row++;
        g.add(metricTile("En espera (colas)", metricWaiting), 0, row);
        g.add(metricTile("Canales Asterisk", metricChannels), 1, row);
        g.add(metricTile("CPU (load)", metricCpu), 2, row);
        g.add(metricTile("RAM usada", metricRam), 3, row);
        row++;
        g.add(metricTile("Disco /", metricDisk), 0, row);
        g.add(metricTile("Salud servicios", metricHealth), 1, row, 3, 1);
        return g;
    }

    private static VBox metricTile(String title, Label value) {
        Label t = new Label(title);
        t.getStyleClass().add("live-metric-title");
        VBox box = new VBox(4, t, value);
        box.getStyleClass().add("live-metric-tile");
        box.setPadding(new Insets(10, 12, 10, 12));
        return box;
    }

    private void configureCallTables() {
        for (TableView<DashboardCallRow> table : List.of(incomingTable, outgoingTable)) {
            table.getColumns().clear();
            table.getColumns().addAll(
                    col("Cola", 90, r -> r.queue),
                    col("Teléfono", 110, r -> r.phone),
                    col("Estado", 90, r -> r.status),
                    col("Troncal", 120, r -> r.trunk),
                    col("Campaña", 120, r -> r.campaign));
            TableViewUtil.prepareFillWidth(table);
            table.setPlaceholder(new Label("Sin llamadas activas."));
        }
    }

    private static TableColumn<DashboardCallRow, String> col(String title, double w,
            Function<DashboardCallRow, String> getter) {
        TableColumn<DashboardCallRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, w, true);
        return c;
    }

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(POLL_SECONDS), e -> refreshNow()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
        refreshNow();
    }

    private void refreshNow() {
        if (!refreshRunning.compareAndSet(false, true)) {
            return;
        }
        int gen = ++loadGen;
        boolean health = includeHealth.isSelected();
        status.setText("Actualizando vista en vivo…");
        worker.execute(() -> {
            try {
                LiveOverviewSnapshot snap = overviewService.loadEccp();
                Platform.runLater(() -> {
                    if (gen != loadGen) {
                        return;
                    }
                    applySnapshot(snap);
                    status.setText("Actualizado · ECCP en vivo" + (health ? " · recursos…" : ""));
                });
                if (health) {
                    overviewService.enrichWithHealth(snap);
                    Platform.runLater(() -> {
                        if (gen != loadGen) {
                            return;
                        }
                        applySnapshot(snap);
                        status.setText("Actualizado · ECCP en vivo · SSH recursos");
                    });
                }
            } catch (Exception ex) {
                AppLogFile.appendLine("[live-overview] refresh | EN: " + ex.getMessage());
                Platform.runLater(() -> {
                    if (gen == loadGen) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            } finally {
                refreshRunning.set(false);
            }
        });
    }

    private void applySnapshot(LiveOverviewSnapshot snap) {
        metricAgents.setText(String.valueOf(snap.agentsOnline));
        metricOnCall.setText(String.valueOf(snap.agentsOnCall));
        metricIncoming.setText(String.valueOf(snap.incomingActive));
        metricOutgoing.setText(String.valueOf(snap.outgoingActive));
        metricWaiting.setText(String.valueOf(snap.waitingInQueues));
        metricChannels.setText(snap.asteriskChannels >= 0
                ? String.valueOf(snap.asteriskChannels)
                : (snap.asteriskChannelsDetail.isBlank() ? "—" : "n/d"));
        if (snap.server != null) {
            metricCpu.setText(String.format("%.2f / %d CPU", snap.server.loadPerCpu(), snap.server.cpuCount()));
            metricRam.setText(String.format("%.0f%% (%d MB)", snap.server.memUsedPercent(), snap.server.memUsedMb()));
        } else {
            metricCpu.setText("—");
            metricRam.setText(snap.sshAvailable ? "—" : "SSH?");
        }
        DiskVolume root = snap.disks.stream().filter(d -> "/".equals(d.mount())).findFirst()
                .orElse(snap.disks.isEmpty() ? null : snap.disks.get(0));
        metricDisk.setText(root == null ? "—" : root.usePercent() + "%");
        metricHealth.setText(snap.healthErrors > 0
                ? snap.healthErrors + " error(es)"
                : snap.healthWarnings > 0
                        ? snap.healthWarnings + " aviso(s)"
                        : "OK");

        StringBuilder trunks = new StringBuilder();
        if (snap.trunkUsage.isEmpty()) {
            trunks.append("Sin troncales identificadas en llamadas activas.");
        } else {
            for (Map.Entry<String, Integer> e : snap.trunkUsage.entrySet()) {
                if (trunks.length() > 0) {
                    trunks.append(" · ");
                }
                trunks.append(e.getKey()).append(" (").append(e.getValue()).append(")");
            }
        }
        trunkSummary.setText(trunks.toString());

        incomingTable.setItems(FXCollections.observableArrayList(snap.recentIncoming));
        outgoingTable.setItems(FXCollections.observableArrayList(snap.recentOutgoing));
    }

    @Override
    public void close() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        worker.shutdownNow();
    }
}
