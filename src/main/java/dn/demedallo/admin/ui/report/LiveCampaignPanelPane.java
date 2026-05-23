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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Incoming or outgoing campaign panel (rep_*_campaigns_panel). */
public final class LiveCampaignPanelPane extends BorderPane implements AutoCloseable {

    private final AdminEccpClient client;
    private final String campaignType;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-campaign-panel");
        t.setDaemon(true);
        return t;
    });

    private final Label status = new Label();
    private final TableView<CampaignRow> table = new TableView<>(FXCollections.observableArrayList());

    public LiveCampaignPanelPane(AdminEccpClient client, String campaignType, String title) {
        this.client = client;
        this.campaignType = campaignType;
        getStyleClass().add("report-pane");
        setPadding(new Insets(12));

        Label desc = new Label(title + " — campañas activas (ECCP getcampaignstatus).");
        desc.setWrapText(true);

        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refresh());

        status.getStyleClass().add("monitor-status");
        buildColumns();
        TableViewUtil.prepare(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        setTop(new VBox(8, desc, new HBox(10, refresh), status));
        setCenter(TableViewUtil.wrapInScrollPane(table));
        refresh();
    }

    private void buildColumns() {
        addCol("ID", r -> r.id);
        addCol("Nombre", r -> r.name);
        addCol("Cola", r -> r.queue);
        addCol("Estado", r -> r.status);
        addCol("Llamadas hoy", r -> r.callsToday);
        addCol("En espera", r -> r.waiting);
        addCol("Agentes", r -> r.agents);
        addCol("Activas", r -> r.activeCalls);
    }

    private void addCol(String title, java.util.function.Function<CampaignRow, String> fn) {
        TableColumn<CampaignRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, title);
        table.getColumns().add(c);
    }

    private void refresh() {
        status.setText("Cargando…");
        worker.execute(() -> {
            try {
                List<CampaignRow> rows = load();
                Platform.runLater(() -> {
                    table.getItems().setAll(rows);
                    status.setText(rows.size() + " campaña(s)");
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[report-panel] " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private List<CampaignRow> load() throws Exception {
        String today = LocalDate.now().toString();
        List<CampaignRef> list = CampaignListParser.parse(client.getCampaignList(null));
        List<CampaignRow> rows = new ArrayList<>();
        for (CampaignRef c : list) {
            if (!campaignType.equalsIgnoreCase(c.type)) {
                continue;
            }
            if (!"active".equalsIgnoreCase(c.status)) {
                continue;
            }
            QueueStatusSnapshot snap = QueueStatusParser.parse(
                    client.getCampaignStatus(c.type, c.id, today));
            CampaignRow row = new CampaignRow();
            row.id = String.valueOf(c.id);
            row.name = c.name == null ? "" : c.name;
            row.queue = snap.activeCalls.stream()
                    .map(r -> r.queueProperty().get())
                    .filter(q -> q != null && !q.isBlank() && !"—".equals(q))
                    .findFirst().orElse(c.name);
            row.status = AdminLabels.campaignStatusLabel(c.status);
            row.callsToday = String.valueOf(snap.totalCalls);
            row.waiting = String.valueOf(snap.activeCalls.size() + snap.onQueue);
            row.agents = QueueStatusParser.formatAgentSummary(snap.agentStatusCounts);
            row.activeCalls = String.valueOf(snap.activeCalls == null ? 0 : snap.activeCalls.size());
            rows.add(row);
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
        String id, name, queue, status, callsToday, waiting, agents, activeCalls;
    }
}
