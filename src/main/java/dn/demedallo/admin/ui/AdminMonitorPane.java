package dn.demedallo.admin.ui;

import dn.demedallo.admin.model.ActiveCallRow;
import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.QueueMonitorRow;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.AgentMonitorService;
import dn.demedallo.admin.service.CallListenService;
import dn.demedallo.admin.service.ListenException;
import dn.demedallo.admin.service.MonitorSnapshot;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminMonitorPane extends BorderPane {

    private static final int POLL_SECONDS = 5;

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
    private final CallListenService listenService;
    private Timeline pollTimeline;
    private volatile boolean polling;

    private final Label statusBar = new Label("Cargando…");
    private final Label summaryOnline = new Label("0");
    private final Label summaryOffline = new Label("0");
    private final Label summaryOnCall = new Label("0");
    private final Label summaryPaused = new Label("0");
    private final Label summaryQueues = new Label("0");
    private final Label summaryActiveCalls = new Label("0");
    private final ComboBox<String> queueFilter = new ComboBox<>();

    public AdminMonitorPane(AdminEccpClient client, AdminMonitorSettings settings, Consumer<String> onLogout) {
        this.client = client;
        this.onLogout = onLogout;
        this.monitorService = new AgentMonitorService(client);
        this.listenService = new CallListenService(settings);
        getStyleClass().add("monitor-root");
        setPadding(new Insets(12));

        queueFilter.setPromptText("Todas las colas");
        queueFilter.setMaxWidth(240);
        queueFilter.valueProperty().addListener((obs, o, n) -> applyQueueFilter(n));
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

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("monitor-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabAgents = new Tab("Agentes", buildAgentTable());
        Tab tabQueues = new Tab("Colas y campañas", buildQueueTable());
        Tab tabCalls = new Tab("Llamadas activas", buildActiveCallTable());
        tabs.getTabs().addAll(tabAgents, tabQueues, tabCalls);

        statusBar.getStyleClass().add("monitor-status");
        VBox top = new VBox(8, toolbar, counters);
        setTop(top);
        setCenter(tabs);
        setBottom(statusBar);
        BorderPane.setMargin(statusBar, new Insets(8, 0, 0, 0));

        startPolling();
        refreshNow();
    }

    private TableView<AgentMonitorRow> buildAgentTable() {
        TableView<AgentMonitorRow> table = new TableView<>();
        table.getStyleClass().add("monitor-table");
        table.setItems(filteredAgents);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
        table.getStyleClass().add("monitor-table");
        table.setItems(allQueues);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(
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
        table.getStyleClass().add("monitor-table");
        table.setItems(allActiveCalls);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(
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
        statusBar.setText("Iniciando escucha de " + row.getAgentName() + "…");
        worker.execute(() -> {
            try {
                String ch = row.channelProperty().get();
                if ("—".equals(ch)) {
                    ch = "";
                }
                listenService.startListen(row.getSpyExtension(), row.getAgentNumber(), ch);
                Platform.runLater(() -> statusBar.setText(
                        "Llamada de monitoreo enviada — conteste en su extensión (spy " + row.getSpyExtension() + ")."));
            } catch (ListenException ex) {
                AppLogFile.appendLine("[listen] " + ex.getReason() + ": " + ex.getMessage());
                Platform.runLater(() -> showListenError(ex));
            } catch (Exception ex) {
                AppLogFile.appendLine("[listen] " + ex.getMessage());
                Platform.runLater(() -> {
                    statusBar.setText("Escucha: " + ex.getMessage());
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Escuchar llamada");
                    alert.setHeaderText("Error al iniciar la escucha");
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void showListenError(ListenException ex) {
        statusBar.setText("Escucha: " + ex.getMessage());
        Alert.AlertType type = ex.getReason() == ListenException.Reason.AMI_DISABLED
                || ex.getReason() == ListenException.Reason.AMI_NOT_CONFIGURED
                ? Alert.AlertType.WARNING
                : Alert.AlertType.ERROR;
        Alert alert = new Alert(type);
        alert.setTitle("Escuchar llamada");
        if (ex.getReason() == ListenException.Reason.AMI_DISABLED
                || ex.getReason() == ListenException.Reason.AMI_NOT_CONFIGURED) {
            alert.setHeaderText("Escucha automática no configurada");
        } else if (ex.getReason() == ListenException.Reason.AMI_ERROR) {
            alert.setHeaderText("No se pudo iniciar la escucha automática (AMI)");
        } else {
            alert.setHeaderText("No se pudo iniciar la escucha");
        }
        String body = ex.getMessage();
        if (ex.getManualDialHint() != null && !ex.getManualDialHint().isBlank()) {
            body = body + "\n\nAlternativa manual:\n" + ex.getManualDialHint();
        }
        alert.setContentText(body);
        alert.showAndWait();
    }

    private static <T> TableColumn<T, String> col(String title,
                                                   Function<T, javafx.beans.value.ObservableValue<String>> prop,
                                                   double pref) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(data -> prop.apply(data.getValue()));
        c.setPrefWidth(pref);
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
        if (queue == null || queue.isBlank() || "Todas las colas".equals(queue)) {
            filteredAgents.setPredicate(row -> true);
        } else {
            String q = queue.trim();
            filteredAgents.setPredicate(row -> row.getQueues() != null && row.getQueues().contains(q));
        }
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
        List<String> items = queues.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        queueFilter.getItems().setAll(items);
        queueFilter.getItems().add(0, "Todas las colas");
        if (prev != null && queueFilter.getItems().contains(prev)) {
            queueFilter.setValue(prev);
        } else {
            queueFilter.setValue("Todas las colas");
        }
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

    public void shutdown() {
        polling = false;
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        worker.shutdownNow();
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
