package dn.demedallo.admin.ui;

import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.DashboardSnapshot;
import dn.demedallo.admin.model.DashboardSnapshot.DashboardCallRow;
import dn.demedallo.admin.model.QueueMonitorRow;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.DashboardService;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Issabel-style live dashboard: agent/extension cards, queues, and call panels.
 */
public final class AdminDashboardPane extends BorderPane implements AutoCloseable {

    private static final int POLL_SECONDS = 5;
    private static final int AGENT_GRID_COLUMNS = 5;
    private static final double AGENT_CARD_WIDTH = 172;
    private static final double AGENT_GRID_GAP = 8;
    private static final int SUMMARY_STAT_COUNT = 7;

    private final DashboardService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-dashboard");
        t.setDaemon(true);
        return t;
    });

    private final Label statusBar = new Label("Cargando…");
    private final Label statOnline = new Label("0");
    private final Label statOffline = new Label("0");
    private final Label statOnCall = new Label("0");
    private final Label statQueues = new Label("0");
    private final Label statIncoming = new Label("0");
    private final Label statOutgoing = new Label("0");
    private final Label statPending = new Label("0");

    private final VBox agentGroupsRoot = new VBox(12);
    private final VBox queueCards = new VBox(8);
    private final TableView<DashboardCallRow> incomingTable = new TableView<>();
    private final TableView<DashboardCallRow> outgoingTable = new TableView<>();
    private final TableView<DashboardCallRow> pendingTable = new TableView<>();

    private Timeline pollTimeline;
    private volatile int loadGeneration;

    public AdminDashboardPane(AdminEccpClient client, AdminDbSettings dbSettings) {
        this.service = new DashboardService(client, dbSettings);
        getStyleClass().add("dashboard-pane");
        setPadding(new Insets(10));

        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refreshNow());

        HBox toolbar = new HBox(10, refresh, statusBar);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().addAll("monitor-status", "dashboard-status-text");

        GridPane summary = buildSummaryGrid();

        agentGroupsRoot.getStyleClass().add("dashboard-agent-groups");
        ScrollPane agentsScroll = new ScrollPane(agentGroupsRoot);
        agentsScroll.setFitToWidth(true);
        agentsScroll.getStyleClass().add("dashboard-scroll");
        TitledPane agentsPane = new TitledPane("Extensiones / agentes", agentsScroll);
        agentsPane.setCollapsible(false);
        agentsPane.getStyleClass().add("dashboard-section");

        queueCards.getStyleClass().add("dashboard-queue-list");
        ScrollPane queuesScroll = new ScrollPane(queueCards);
        queuesScroll.setFitToWidth(true);
        queuesScroll.getStyleClass().add("dashboard-scroll");
        TitledPane queuesPane = new TitledPane("Colas y campañas", queuesScroll);
        queuesPane.setCollapsible(false);
        queuesPane.getStyleClass().add("dashboard-section");
        queuesPane.setMinWidth(280);
        queuesPane.setPrefWidth(320);

        SplitPane topSplit = new SplitPane(agentsPane, queuesPane);
        topSplit.setDividerPositions(0.62);

        buildCallTables();
        SplitPane callsSplit = new SplitPane(
                callSection("Llamadas de entrada", incomingTable, "dashboard-incoming"),
                callSection("Llamadas de salida", outgoingTable, "dashboard-outgoing"),
                callSection("Pendientes del dialer", pendingTable, "dashboard-pending"));
        callsSplit.setDividerPositions(0.333, 0.666);
        callsSplit.setMinHeight(160);
        callsSplit.setPrefHeight(220);

        SplitPane mainSplit = new SplitPane(topSplit, callsSplit);
        mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.52);

        VBox top = new VBox(8, toolbar, summary);
        setTop(top);
        setCenter(mainSplit);

        startPolling();
    }

    private GridPane buildSummaryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.getStyleClass().add("dashboard-summary");
        double pct = 100.0 / SUMMARY_STAT_COUNT;
        for (int i = 0; i < SUMMARY_STAT_COUNT; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(pct);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }
        VBox[] boxes = {
                summaryBox("En línea", statOnline, "dashboard-stat-online"),
                summaryBox("Desconectados", statOffline, "dashboard-stat-offline"),
                summaryBox("En llamada", statOnCall, "monitor-chip-call"),
                summaryBox("Colas", statQueues, "monitor-chip-queue"),
                summaryBox("Entrantes", statIncoming, "dashboard-stat-incoming"),
                summaryBox("Salientes", statOutgoing, "dashboard-stat-outgoing"),
                summaryBox("Pendientes dialer", statPending, "dashboard-stat-pending")
        };
        for (int i = 0; i < boxes.length; i++) {
            GridPane.setHgrow(boxes[i], Priority.ALWAYS);
            GridPane.setVgrow(boxes[i], Priority.ALWAYS);
            grid.add(boxes[i], i, 0);
        }
        return grid;
    }

    private static VBox summaryBox(String title, Label value, String styleClass) {
        Label t = new Label(title);
        t.getStyleClass().add("dashboard-stat-title");
        value.getStyleClass().addAll("dashboard-stat-value", styleClass);
        VBox box = new VBox(2, t, value);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("dashboard-stat-box");
        return box;
    }

    private TitledPane callSection(String title, TableView<DashboardCallRow> table, String styleClass) {
        table.getStyleClass().add(styleClass);
        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        TitledPane pane = new TitledPane(title, scroll);
        pane.setCollapsible(false);
        pane.getStyleClass().add("dashboard-section");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return pane;
    }

    private void buildCallTables() {
        buildCallColumns(incomingTable);
        buildCallColumns(outgoingTable);
        buildCallColumns(pendingTable);
        prepareDashboardTable(incomingTable);
        prepareDashboardTable(outgoingTable);
        prepareDashboardTable(pendingTable);
        incomingTable.setItems(FXCollections.observableArrayList());
        outgoingTable.setItems(FXCollections.observableArrayList());
        pendingTable.setItems(FXCollections.observableArrayList());
        incomingTable.setPlaceholder(new Label("Sin llamadas entrantes activas"));
        outgoingTable.setPlaceholder(new Label("Sin llamadas salientes activas"));
        pendingTable.setPlaceholder(new Label("Sin llamadas pendientes (requiere MySQL)"));
    }

    private static void prepareDashboardTable(TableView<DashboardCallRow> table) {
        if (!table.getStyleClass().contains("monitor-table")) {
            table.getStyleClass().add("monitor-table");
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(28);
    }

    private static void buildCallColumns(TableView<DashboardCallRow> table) {
        TableColumn<DashboardCallRow, String> camp = col("Campaña", r -> r.campaign);
        TableColumn<DashboardCallRow, String> queue = col("Cola", r -> r.queue);
        TableColumn<DashboardCallRow, String> phone = col("Teléfono", r -> r.phone);
        TableColumn<DashboardCallRow, String> status = col("Estado", r -> r.status);
        TableColumn<DashboardCallRow, String> trunk = col("Troncal", r -> r.trunk);
        TableColumn<DashboardCallRow, String> detail = col("Detalle", r -> r.detail);
        camp.setMinWidth(48);
        queue.setMinWidth(36);
        phone.setMinWidth(56);
        status.setMinWidth(44);
        trunk.setMinWidth(40);
        detail.setMinWidth(40);
        table.getColumns().addAll(camp, queue, phone, status, trunk, detail);
    }

    private static TableColumn<DashboardCallRow, String> col(String title,
                                                             Function<DashboardCallRow, String> fn) {
        TableColumn<DashboardCallRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        return c;
    }

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(POLL_SECONDS), e -> refresh()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
        refresh();
    }

    private void refreshNow() {
        refresh();
    }

    private void refresh() {
        int gen = ++loadGeneration;
        worker.execute(() -> {
            try {
                DashboardSnapshot snap = service.load();
                Platform.runLater(() -> {
                    if (gen != loadGeneration) {
                        return;
                    }
                    applySnapshot(snap);
                    statusBar.setText("Actualizado · " + snap.agents.size() + " agentes · "
                            + snap.queues.size() + " colas");
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[dashboard] refresh | EN: " + ex.getMessage());
                Platform.runLater(() -> statusBar.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void applySnapshot(DashboardSnapshot snap) {
        statOnline.setText(String.valueOf(snap.onlineCount));
        statOffline.setText(String.valueOf(snap.offlineCount));
        statOnCall.setText(String.valueOf(snap.onCallCount));
        statQueues.setText(String.valueOf(snap.queues.size()));
        statIncoming.setText(String.valueOf(snap.incomingCalls.size()));
        statOutgoing.setText(String.valueOf(snap.outgoingCalls.size()));
        statPending.setText(String.valueOf(snap.pendingCalls.size()));

        rebuildAgentCards(snap.agents);
        rebuildQueueCards(snap.queues);
        incomingTable.getItems().setAll(snap.incomingCalls);
        outgoingTable.getItems().setAll(snap.outgoingCalls);
        pendingTable.getItems().setAll(snap.pendingCalls);
    }

    private void rebuildAgentCards(List<AgentMonitorRow> agents) {
        agentGroupsRoot.getChildren().clear();
        Map<String, List<AgentMonitorRow>> groups = new LinkedHashMap<>();
        for (AgentMonitorRow a : agents) {
            String group = firstQueue(a.getQueues());
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(a);
        }
        for (Map.Entry<String, List<AgentMonitorRow>> entry : groups.entrySet()) {
            agentGroupsRoot.getChildren().add(buildAgentGroupSection(entry.getKey(), entry.getValue()));
        }
    }

    private VBox buildAgentGroupSection(String groupName, List<AgentMonitorRow> agents) {
        Label section = new Label(groupName + " — " + agents.size() + " ext.");
        section.getStyleClass().add("dashboard-group-title");
        section.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("dashboard-agent-grid");
        grid.setHgap(AGENT_GRID_GAP);
        grid.setVgap(AGENT_GRID_GAP);
        for (int c = 0; c < AGENT_GRID_COLUMNS; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setMinWidth(AGENT_CARD_WIDTH);
            cc.setPrefWidth(AGENT_CARD_WIDTH);
            cc.setMaxWidth(AGENT_CARD_WIDTH);
            grid.getColumnConstraints().add(cc);
        }

        int rows = (agents.size() + AGENT_GRID_COLUMNS - 1) / AGENT_GRID_COLUMNS;
        for (int r = 0; r < rows; r++) {
            grid.getRowConstraints().add(new RowConstraints());
        }

        for (int i = 0; i < agents.size(); i++) {
            VBox card = buildAgentCard(agents.get(i));
            grid.add(card, i % AGENT_GRID_COLUMNS, i / AGENT_GRID_COLUMNS);
        }

        VBox sectionBox = new VBox(6, section, grid);
        sectionBox.getStyleClass().add("dashboard-agent-group");
        return sectionBox;
    }

    private static String firstQueue(String queues) {
        if (queues == null || queues.isBlank() || "—".equals(queues)) {
            return "Sin cola asignada";
        }
        int comma = queues.indexOf(',');
        return comma > 0 ? queues.substring(0, comma).trim() : queues.trim();
    }

    private static VBox buildAgentCard(AgentMonitorRow a) {
        String ext = a.extensionProperty().get();
        if (ext == null || ext.isBlank() || "—".equals(ext)) {
            ext = a.getAgentNumber();
        }
        Label line1 = new Label(ext + ": " + a.getAgentName());
        line1.getStyleClass().add("dashboard-ext-line1");
        String code = a.getStatusCode() == null ? "offline" : a.getStatusCode();
        Label line2 = new Label("Estado: " + a.getStatusLabel());
        line2.getStyleClass().add("dashboard-ext-line2");
        VBox card;
        if ("oncall".equals(code) || "ringing".equals(code)) {
            Label line3 = new Label("Tel: " + a.phoneNumberProperty().get());
            line3.getStyleClass().add("dashboard-ext-line2");
            card = new VBox(3, line1, line2, line3);
        } else {
            card = new VBox(3, line1, line2);
        }
        card.getStyleClass().addAll("dashboard-ext-card", statusStyleClass(code));
        card.setMinWidth(AGENT_CARD_WIDTH);
        card.setPrefWidth(AGENT_CARD_WIDTH);
        card.setMaxWidth(AGENT_CARD_WIDTH);
        return card;
    }

    private static String statusStyleClass(String code) {
        return switch (code) {
            case "online" -> "dashboard-ext-online";
            case "oncall" -> "dashboard-ext-oncall";
            case "ringing" -> "dashboard-ext-ringing";
            case "paused" -> "dashboard-ext-paused";
            default -> "dashboard-ext-offline";
        };
    }

    private void rebuildQueueCards(List<QueueMonitorRow> queues) {
        queueCards.getChildren().clear();
        if (queues.isEmpty()) {
            queueCards.getChildren().add(new Label("Sin colas activas"));
            return;
        }
        for (QueueMonitorRow q : queues) {
            Label title = new Label("Cola: " + q.getQueue() + " (" + q.getCampaignName() + ")");
            title.getStyleClass().add("dashboard-queue-title");
            Label meta = new Label(q.getTypeLabel() + " · " + q.getStatusLabel()
                    + " · Hoy: " + q.getCallsToday());
            meta.getStyleClass().add("dashboard-queue-meta");
            Label wait = new Label("En espera: " + q.getWaiting() + " · Agentes: " + q.getAgentsSummary());
            wait.getStyleClass().add("dashboard-queue-meta");
            Label states = new Label(q.getCallStates());
            states.setWrapText(true);
            states.getStyleClass().add("dashboard-queue-meta");
            VBox card = new VBox(4, title, meta, wait, states);
            card.getStyleClass().add("dashboard-queue-card");
            queueCards.getChildren().add(card);
        }
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
