package dn.demedallo.admin.ui;

import dn.demedallo.admin.model.ActiveCallRow;
import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.QueueMonitorRow;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.AgentMonitorService;
import dn.demedallo.admin.service.ListenUiActions;
import dn.demedallo.admin.service.MonitorSnapshot;
import dn.demedallo.admin.util.SpyTargetUtil;
import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.util.AppLogFile;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Alert;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import dn.demedallo.admin.ui.nav.WorkspaceNavigation;
import dn.demedallo.admin.ui.report.FailedShortCallsPane;
import dn.demedallo.admin.ui.report.IncomingCampaignsPanelPane;
import dn.demedallo.admin.ui.report.OutgoingCampaignsPanelPane;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.ui.util.TableViewUtil;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminMonitorPane extends BorderPane {

    private static final int POLL_SECONDS = 5;
    /** Sentinel item for “no queue filter” (must not match a real queue name). */
    private static final String ALL_QUEUES_LABEL = "— Todas las colas —";

    private final AdminEccpClient client;
    private final Consumer<String> onLogout;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-eccp-worker");
        t.setDaemon(true);
        return t;
    });

    private final ObservableList<AgentMonitorRow> allAgents = FXCollections.observableArrayList();
    private final FilteredList<AgentMonitorRow> filteredAgents = new FilteredList<>(allAgents);
    private final ObservableList<QueueMonitorRow> allQueues = FXCollections.observableArrayList();
    private final ObservableList<ActiveCallRow> allActiveCalls = FXCollections.observableArrayList();

    private final AgentMonitorService monitorService;
    private final ListenUiActions listenActions;
    private Timeline pollTimeline;
    private volatile boolean polling;

    private final Label statusBar = new Label("Cargando…");
    private final Label summaryOnline = new Label("0");
    private final Label summaryOffline = new Label("0");
    private final Label summaryOnCall = new Label("0");
    private final Label summaryPaused = new Label("0");
    private final Label summaryQueues = new Label("0");
    private TabPane monitorTabs;
    private WorkspaceNavigation navigation;
    private Runnable selectMainTab;
    private final Label summaryActiveCalls = new Label("0");
    private final ComboBox<String> queueFilter = new ComboBox<>();
    private volatile boolean rebuildingQueueFilter;
    private final IncomingCampaignsPanelPane incomingCampaignPanel;
    private final OutgoingCampaignsPanelPane outgoingCampaignPanel;
    private final FailedShortCallsPane failedShortCallsPane;

    public AdminMonitorPane(AdminEccpClient client, AdminMonitorSettings settings,
                            AdminDbSettings dbSettings, Consumer<String> onLogout,
                            ListenUiActions listenActions) {
        this.client = client;
        this.onLogout = onLogout;
        this.monitorService = new AgentMonitorService(client);
        this.listenActions = listenActions;
        getStyleClass().add("monitor-root");
        setPadding(new Insets(12));

        queueFilter.setEditable(false);
        queueFilter.setMaxWidth(260);
        queueFilter.getItems().add(ALL_QUEUES_LABEL);
        queueFilter.setValue(ALL_QUEUES_LABEL);
        filteredAgents.setPredicate(null);
        queueFilter.valueProperty().addListener((obs, o, n) -> {
            if (!rebuildingQueueFilter) {
                applyQueueFilter(n);
            }
        });
        queueFilter.getStyleClass().add("monitor-filter");

        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refreshNow());

        Button disconnect = new Button("Cerrar sesión");
        disconnect.getStyleClass().add("monitor-btn-secondary");
        disconnect.setOnAction(e -> stopPollingAndLogout());

        HBox toolbar = new HBox(10, new Label("Filtrar cola:"), queueFilter, refresh, disconnect);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("monitor-toolbar");

        HBox counters = new HBox(14,
                counterChip("Disponibles", summaryOnline, "monitor-chip-online"),
                counterChip("Desconectados", summaryOffline, "monitor-chip-offline"),
                counterChip("En llamada", summaryOnCall, "monitor-chip-call"),
                counterChip("En pausa", summaryPaused, "monitor-chip-pause"),
                counterChip("Colas activas", summaryQueues, "monitor-chip-queue"),
                counterChip("Llamadas vivas", summaryActiveCalls, "monitor-chip-call"));
        counters.setAlignment(Pos.CENTER_LEFT);
        counters.getStyleClass().add("monitor-counters");

        monitorTabs = new TabPane();
        monitorTabs.getStyleClass().add("monitor-tabs");
        monitorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        incomingCampaignPanel = new IncomingCampaignsPanelPane(client, dbSettings);
        outgoingCampaignPanel = new OutgoingCampaignsPanelPane(client, dbSettings);
        failedShortCallsPane = new FailedShortCallsPane(dbSettings);
        SplitPane campaignPanels = new SplitPane(incomingCampaignPanel, outgoingCampaignPanel);
        campaignPanels.setDividerPositions(0.5);

        Tab tabAgents = new Tab("Agentes", wrapMonitorTable(buildAgentTable(), "agentes"));
        Tab tabQueues = new Tab("Colas y campañas", wrapMonitorTable(buildQueueTable(), "colas-campanas"));
        Tab tabCalls = new Tab("Llamadas activas", wrapMonitorTable(buildActiveCallTable(), "llamadas-activas"));
        Tab tabCampaignPanels = new Tab("Paneles campaña", campaignPanels);
        Tab tabFailedShort = new Tab("Fallidas y cortas", failedShortCallsPane);
        monitorTabs.getTabs().addAll(tabAgents, tabQueues, tabCalls, tabCampaignPanels, tabFailedShort);

        statusBar.getStyleClass().add("monitor-status");
        VBox top = new VBox(8, toolbar, counters);
        setTop(top);
        setCenter(monitorTabs);
        setBottom(statusBar);
        BorderPane.setMargin(statusBar, new Insets(8, 0, 0, 0));

        startPolling();
        refreshNow();
    }

    private TableView<AgentMonitorRow> buildAgentTable() {
        TableView<AgentMonitorRow> table = new TableView<>();
        TableViewUtil.prepare(table);
        table.setItems(filteredAgents);
        table.getColumns().addAll(
                listenColumn(),
                col("Agente", AgentMonitorRow::agentNumberProperty, 100),
                col("Nombre", AgentMonitorRow::agentNameProperty, 130),
                col("Estado", AgentMonitorRow::statusLabelProperty, 100),
                col("Ext.", AgentMonitorRow::extensionProperty, 55),
                col("Canal", AgentMonitorRow::channelProperty, 90),
                col("Colas", AgentMonitorRow::queuesProperty, 100),
                col("Nº llamadas", AgentMonitorRow::callsTodayProperty, 70),
                col("Tiempo hablado", AgentMonitorRow::talkTimeTodayProperty, 85),
                col("Ent/Sal", AgentMonitorRow::callsBreakdownProperty, 90),
                col("Login hoy", AgentMonitorRow::loginTimeProperty, 75),
                col("Última sesión", AgentMonitorRow::lastSessionProperty, 140),
                col("Teléfono", AgentMonitorRow::phoneNumberProperty, 95),
                col("Cola activa", AgentMonitorRow::activeQueueProperty, 70),
                col("Tipo", AgentMonitorRow::callTypeProperty, 65),
                col("ID llamada", AgentMonitorRow::callIdProperty, 65),
                col("Estado llamada", AgentMonitorRow::callStatusProperty, 90),
                col("Trunk", AgentMonitorRow::trunkProperty, 70),
                col("Pausa", AgentMonitorRow::pauseInfoProperty, 90),
                col("Desde pausa", AgentMonitorRow::pauseSinceProperty, 75));
        return table;
    }

    private TableView<QueueMonitorRow> buildQueueTable() {
        TableView<QueueMonitorRow> table = new TableView<>();
        TableViewUtil.prepare(table);
        table.setItems(allQueues);
        table.getColumns().addAll(
                queueListenColumn(),
                colQ("Cola", QueueMonitorRow::queueProperty, 80),
                colQ("Tipo", QueueMonitorRow::typeLabelProperty, 70),
                colQ("Campaña", QueueMonitorRow::campaignNameProperty, 140),
                colQ("Estado camp.", QueueMonitorRow::statusLabelProperty, 85),
                colQ("Llamadas hoy", QueueMonitorRow::callsTodayProperty, 80),
                colQ("En espera", QueueMonitorRow::waitingProperty, 70),
                colQ("Agentes", QueueMonitorRow::agentsSummaryProperty, 160),
                colQ("Estados llamadas", QueueMonitorRow::callStatesProperty, 200));
        return table;
    }

    private TableView<ActiveCallRow> buildActiveCallTable() {
        TableView<ActiveCallRow> table = new TableView<>();
        TableViewUtil.prepare(table);
        table.setItems(allActiveCalls);
        table.getColumns().addAll(
                activeCallListenColumn(),
                colC("Cola", ActiveCallRow::queueProperty, 80),
                colC("Campaña", ActiveCallRow::campaignProperty, 140),
                colC("Teléfono", ActiveCallRow::numberProperty, 110),
                colC("Estado", ActiveCallRow::statusProperty, 100),
                colC("Tipo", ActiveCallRow::typeProperty, 80),
                colC("ID", ActiveCallRow::callIdProperty, 60),
                colC("Trunk", ActiveCallRow::trunkProperty, 90));
        return table;
    }

    private HBox counterChip(String title, Label value, String styleClass) {
        Label t = new Label(title + ":");
        t.getStyleClass().add("monitor-counter-title");
        value.getStyleClass().addAll("monitor-counter-value", styleClass);
        HBox box = new HBox(4, t, value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private TableColumn<AgentMonitorRow, Void> listenColumn() {
        TableColumn<AgentMonitorRow, Void> c = new TableColumn<>("Escuchar");
        c.setPrefWidth(88);
        c.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Escuchar");

            {
                btn.getStyleClass().add("monitor-btn-small");
                btn.setOnAction(e -> {
                    AgentMonitorRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null && row.isListenAvailable()) {
                        startListen(row);
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
                AgentMonitorRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                btn.setDisable(!row.isListenAvailable());
                setGraphic(btn);
            }
        });
        return c;
    }

    private void startListen(AgentMonitorRow row) {
        String ch = row.channelProperty().get();
        if ("—".equals(ch)) {
            ch = "";
        }
        listenActions.listenSpyTarget(row.getSpyExtension(), ch, row.getAgentName(), statusBar::setText);
    }

    private TableColumn<QueueMonitorRow, Void> queueListenColumn() {
        TableColumn<QueueMonitorRow, Void> c = new TableColumn<>("Escuchar");
        c.setPrefWidth(88);
        c.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Cola");

            {
                btn.getStyleClass().add("monitor-btn-small");
                btn.setOnAction(e -> {
                    QueueMonitorRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) {
                        listenQueue(row);
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
                QueueMonitorRow row = getTableRow() == null ? null : getTableRow().getItem();
                boolean hasCalls = row != null && queueHasActiveCalls(row.getQueue());
                btn.setDisable(!hasCalls);
                setGraphic(btn);
            }
        });
        return c;
    }

    private TableColumn<ActiveCallRow, Void> activeCallListenColumn() {
        TableColumn<ActiveCallRow, Void> c = new TableColumn<>("Escuchar");
        c.setPrefWidth(88);
        c.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Escuchar");

            {
                btn.getStyleClass().add("monitor-btn-small");
                btn.setOnAction(e -> {
                    ActiveCallRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) {
                        listenActiveCall(row);
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
                ActiveCallRow row = getTableRow() == null ? null : getTableRow().getItem();
                setGraphic(btn);
                btn.setDisable(row == null || resolveSpyForActiveCall(row).isBlank());
            }
        });
        return c;
    }

    private boolean queueHasActiveCalls(String queue) {
        if (queue == null || queue.isBlank()) {
            return false;
        }
        for (ActiveCallRow c : allActiveCalls) {
            String q = c.queueProperty().get();
            if (queue.equals(q)) {
                return true;
            }
        }
        return false;
    }

    private void listenQueue(QueueMonitorRow queueRow) {
        String queue = queueRow.getQueue();
        for (ActiveCallRow call : allActiveCalls) {
            if (!queue.equals(call.queueProperty().get())) {
                continue;
            }
            String spy = resolveSpyForActiveCall(call);
            if (!spy.isBlank()) {
                listenActions.listenSpyTarget(spy, "", "Cola " + queue + " / " + call.numberProperty().get(),
                        statusBar::setText);
                return;
            }
        }
        statusBar.setText("Cola " + queue + ": sin llamada con agente asignado para escuchar");
    }

    private void listenActiveCall(ActiveCallRow call) {
        String spy = resolveSpyForActiveCall(call);
        listenActions.listenSpyTarget(spy, "", "Llamada " + call.numberProperty().get(), statusBar::setText);
    }

    private String resolveSpyForActiveCall(ActiveCallRow call) {
        String phone = call.numberProperty().get();
        String queue = call.queueProperty().get();
        for (AgentMonitorRow a : allAgents) {
            if (!SpyTargetUtil.canListen(a.getStatusCode())) {
                continue;
            }
            if (phone != null && phone.equals(a.phoneNumberProperty().get())) {
                String q = a.activeQueueProperty().get();
                if (queue == null || "—".equals(queue) || queue.equals(q) || "—".equals(q)) {
                    return a.getSpyExtension();
                }
            }
        }
        if (phone != null && phone.matches("\\d{3,15}")) {
            return phone.trim();
        }
        return "";
    }

    private static <T> TableColumn<T, String> col(String title,
                                                   Function<T, javafx.beans.value.ObservableValue<String>> prop,
                                                   double pref) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(data -> prop.apply(data.getValue()));
        TableViewUtil.styleColumn(c, pref);
        return c;
    }

    private static TableColumn<QueueMonitorRow, String> colQ(String title,
                                                              Function<QueueMonitorRow, javafx.beans.value.ObservableValue<String>> prop,
                                                              double pref) {
        return col(title, prop, pref);
    }

    private static TableColumn<ActiveCallRow, String> colC(String title,
                                                            Function<ActiveCallRow, javafx.beans.value.ObservableValue<String>> prop,
                                                            double pref) {
        return col(title, prop, pref);
    }

    private void applyQueueFilter(String queue) {
        if (isAllQueuesSelection(queue)) {
            filteredAgents.setPredicate(null);
            return;
        }
        String q = queue == null ? "" : queue.trim();
        filteredAgents.setPredicate(row -> agentMatchesQueue(row, q));
    }

    private static boolean isAllQueuesSelection(String queue) {
        return queue == null
                || queue.isBlank()
                || ALL_QUEUES_LABEL.equals(queue)
                || "Todas las colas".equals(queue);
    }

    private static boolean agentMatchesQueue(AgentMonitorRow row, String queue) {
        if (queue.isEmpty()) {
            return true;
        }
        String assigned = row.getQueues();
        if (assigned != null && !assigned.isBlank() && !"—".equals(assigned)) {
            for (String part : assigned.split(",")) {
                if (queue.equalsIgnoreCase(part.trim())) {
                    return true;
                }
            }
        }
        String active = row.activeQueueProperty().get();
        return active != null && !active.isBlank() && !"—".equals(active)
                && queue.equalsIgnoreCase(active.trim());
    }

    private static BorderPane wrapMonitorTable(TableView<?> table, String exportName) {
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMaxHeight(Double.MAX_VALUE);
        BorderPane pane = new BorderPane(TableViewUtil.wrapInScrollPane(table, exportName));
        pane.setMinHeight(280);
        BorderPane.setAlignment(table, Pos.CENTER);
        return pane;
    }

    private void startPolling() {
        polling = true;
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(POLL_SECONDS), e -> refreshNow()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    public void refreshNow() {
        if (!polling) {
            return;
        }
        statusBar.setText("Actualizando agentes, colas y llamadas…");
        worker.execute(() -> {
            try {
                MonitorSnapshot snap = monitorService.fetchSnapshot();
                Platform.runLater(() -> applySnapshot(snap));
            } catch (Exception ex) {
                AppLogFile.appendLine("[monitor] refresh error: " + ex.getMessage());
                Platform.runLater(() -> statusBar.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void applySnapshot(MonitorSnapshot snap) {
        allAgents.setAll(snap.agents());
        allQueues.setAll(snap.queues());
        allActiveCalls.setAll(snap.activeCalls());
        rebuildQueueFilter(snap.agents());
        updateCounters(snap);
        statusBar.setText("Última actualización: " + java.time.LocalTime.now().withNano(0)
                + " · " + snap.agents().size() + " agentes · "
                + snap.queues().size() + " colas · "
                + snap.activeCalls().size() + " llamadas activas · cada " + POLL_SECONDS + " s");
    }

    private void rebuildQueueFilter(List<AgentMonitorRow> rows) {
        String prev = queueFilter.getValue();
        boolean wasAll = isAllQueuesSelection(prev);
        Set<String> queues = new HashSet<>();
        for (AgentMonitorRow r : rows) {
            String qs = r.getQueues();
            if (qs == null || "—".equals(qs)) {
                continue;
            }
            for (String q : qs.split(",")) {
                String t = q.trim();
                if (!t.isEmpty()) {
                    queues.add(t);
                }
            }
        }
        for (QueueMonitorRow q : allQueues) {
            if (q.getQueue() != null && !q.getQueue().isBlank()) {
                queues.add(q.getQueue().trim());
            }
        }
        List<String> items = new ArrayList<>();
        items.add(ALL_QUEUES_LABEL);
        items.addAll(queues.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        rebuildingQueueFilter = true;
        try {
            queueFilter.getItems().setAll(items);
            if (wasAll) {
                queueFilter.setValue(ALL_QUEUES_LABEL);
            } else if (prev != null && items.contains(prev)) {
                queueFilter.setValue(prev);
            } else {
                queueFilter.setValue(ALL_QUEUES_LABEL);
            }
        } finally {
            rebuildingQueueFilter = false;
        }
        applyQueueFilter(queueFilter.getValue());
    }

    private void updateCounters(MonitorSnapshot snap) {
        int online = 0;
        int offline = 0;
        int oncall = 0;
        int paused = 0;
        for (AgentMonitorRow r : snap.agents()) {
            String code = r.getStatusCode();
            if (code == null) {
                continue;
            }
            switch (code) {
                case "offline" -> offline++;
                case "online", "ringing" -> online++;
                case "oncall" -> oncall++;
                case "paused" -> paused++;
                default -> {
                }
            }
        }
        summaryOnline.setText(String.valueOf(online));
        summaryOffline.setText(String.valueOf(offline));
        summaryOnCall.setText(String.valueOf(oncall));
        summaryPaused.setText(String.valueOf(paused));
        summaryQueues.setText(String.valueOf(snap.queues().size()));
        summaryActiveCalls.setText(String.valueOf(snap.activeCalls().size()));
    }

    public void bindNavigation(WorkspaceNavigation nav, String mainTabLabel, Runnable selectMainTab) {
        this.navigation = nav;
        this.selectMainTab = selectMainTab;
        monitorTabs.getSelectionModel().selectedItemProperty().addListener((o, old, tab) -> publishMonitorTrail(tab));
        publishMonitorTrail(monitorTabs.getSelectionModel().getSelectedItem());
    }

    public void refreshNavigationTrail() {
        publishMonitorTrail(monitorTabs.getSelectionModel().getSelectedItem());
    }

    private void publishMonitorTrail(Tab tab) {
        if (navigation == null || tab == null) {
            return;
        }
        navigation.setMainAndSub("Monitoreo", selectMainTab, tab.getText());
    }

    public void shutdown() {
        polling = false;
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        worker.shutdownNow();
        try {
            incomingCampaignPanel.close();
        } catch (Exception ignored) {
        }
        try {
            outgoingCampaignPanel.close();
        } catch (Exception ignored) {
        }
        try {
            failedShortCallsPane.close();
        } catch (Exception ignored) {
        }
    }

    private void stopPollingAndLogout() {
        shutdown();
        try {
            client.close();
        } catch (Exception ignored) {
        }
        onLogout.accept(null);
    }
}
