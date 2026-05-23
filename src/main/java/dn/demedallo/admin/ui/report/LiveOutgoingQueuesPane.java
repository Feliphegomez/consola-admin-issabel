package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.i18n.AdminLabels;
import dn.demedallo.admin.model.CampaignListParser;
import dn.demedallo.admin.model.CampaignRef;
import dn.demedallo.admin.model.QueueStatusParser;
import dn.demedallo.admin.model.QueueStatusSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AppLogFile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Outgoing campaigns monitor (parallel to {@link LiveIncomingQueuesPane}) — ECCP getcampaignstatus.
 */
public final class LiveOutgoingQueuesPane extends BorderPane implements AutoCloseable {

    private static final int MAX_CAMPAIGNS = 40;

    private final AdminEccpClient client;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-outgoing-queues");
        t.setDaemon(true);
        return t;
    });

    private final Label status = new Label();
    private final TableView<CampaignRow> table = new TableView<>(FXCollections.observableArrayList());

    public LiveOutgoingQueuesPane(AdminEccpClient client) {
        this.client = client;
        getStyleClass().add("report-pane");
        setPadding(new Insets(12));

        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refresh());

        buildColumns();
        TableViewUtil.prepare(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        status.getStyleClass().add("monitor-status");
        setTop(new VBox(8,
                new Label("Monitoreo de campañas salientes (hoy) — ECCP getcampaignlist + getcampaignstatus."),
                new HBox(10, refresh), status));
        setCenter(TableViewUtil.wrapInScrollPane(table));
        refresh();
    }

    private void buildColumns() {
        addCol("Campaña", r -> r.name, 160);
        addCol("Cola", r -> r.queue, 80);
        addCol("Estado", r -> r.status, 90);
        addCol("Llamadas hoy", r -> r.callsToday, 95);
        addCol("En espera", r -> r.waiting, 85);
        addCol("Activas", r -> r.active, 75);
        addCol("Agentes", r -> r.agents, 140);
    }

    private void addCol(String title, java.util.function.Function<CampaignRow, String> fn, double width) {
        TableColumn<CampaignRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, width);
        table.getColumns().add(c);
    }

    private void refresh() {
        status.setText("Cargando…");
        worker.execute(() -> {
            try {
                List<CampaignRow> rows = load();
                Platform.runLater(() -> {
                    table.getItems().setAll(rows);
                    status.setText(rows.size() + " campaña(s) saliente(s)");
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[report-outgoing] " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private List<CampaignRow> load() throws Exception {
        String today = LocalDate.now().toString();
        List<CampaignRef> campaigns = CampaignListParser.parse(client.getCampaignList(null));
        List<CampaignRef> outgoing = new ArrayList<>();
        for (CampaignRef c : campaigns) {
            if ("outgoing".equalsIgnoreCase(c.type) && "active".equalsIgnoreCase(c.status)) {
                outgoing.add(c);
            }
        }
        outgoing.sort(Comparator.comparing(c -> c.name == null ? "" : c.name, String.CASE_INSENSITIVE_ORDER));

        List<CampaignRow> rows = new ArrayList<>();
        int polls = 0;
        for (CampaignRef c : outgoing) {
            if (polls >= MAX_CAMPAIGNS) {
                break;
            }
            try {
                QueueStatusSnapshot snap = QueueStatusParser.parse(
                        client.getCampaignStatus("outgoing", c.id, today));
                CampaignRow row = new CampaignRow();
                row.id = String.valueOf(c.id);
                row.name = c.name == null ? "" : c.name;
                row.queue = snap.activeCalls.stream()
                        .map(r -> r.queueProperty().get())
                        .filter(q -> q != null && !q.isBlank() && !"—".equals(q))
                        .findFirst()
                        .orElse("—");
                row.status = AdminLabels.campaignStatusLabel(c.status);
                row.callsToday = String.valueOf(snap.totalCalls);
                row.waiting = String.valueOf(snap.activeCalls.size() + snap.onQueue);
                row.active = String.valueOf(snap.activeCalls == null ? 0 : snap.activeCalls.size());
                row.agents = QueueStatusParser.formatAgentSummary(snap.agentStatusCounts);
                rows.add(row);
                polls++;
            } catch (Exception ex) {
                AppLogFile.appendLine("[report-outgoing] campaign " + c.id + ": " + ex.getMessage());
            }
        }
        return rows;
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }

    private static final class CampaignRow {
        String id, name, queue, status, callsToday, waiting, active, agents;
    }
}
