package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.i18n.AdminLabels;
import dn.demedallo.admin.model.ActiveCallRow;
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
import javafx.scene.control.ComboBox;
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

/** campaign_monitoring — single campaign status + active calls. */
public final class LiveCampaignMonitorPane extends BorderPane implements AutoCloseable {

    private final AdminEccpClient client;
    private final ComboBox<CampaignRef> campaignCombo = new ComboBox<>();
    private final Label summary = new Label();
    private final TableView<ActiveCallRow> callsTable = new TableView<>(FXCollections.observableArrayList());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-campaign-monitor");
        t.setDaemon(true);
        return t;
    });

    public LiveCampaignMonitorPane(AdminEccpClient client) {
        this.client = client;
        getStyleClass().add("report-pane");
        setPadding(new Insets(12));

        campaignCombo.setPromptText("Seleccione campaña");
        campaignCombo.setMaxWidth(400);
        Button refreshList = new Button("Recargar lista");
        refreshList.getStyleClass().add("monitor-btn");
        refreshList.setOnAction(e -> loadCampaignList());
        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> loadSelected());

        buildCallColumns();
        TableViewUtil.prepare(callsTable);
        VBox.setVgrow(callsTable, Priority.ALWAYS);

        summary.setWrapText(true);
        summary.getStyleClass().add("monitor-status");

        setTop(new VBox(8,
                new Label("Monitoreo de campaña — ECCP getcampaignstatus / getincomingqueuestatus."),
                new HBox(10, new Label("Campaña:"), campaignCombo, refreshList, refresh),
                summary));
        setCenter(TableViewUtil.wrapInScrollPane(callsTable));
        loadCampaignList();
    }

    private void buildCallColumns() {
        addCol("Teléfono", r -> r.numberProperty().get());
        addCol("Estado", r -> r.statusProperty().get());
        addCol("Tipo", r -> r.typeProperty().get());
        addCol("ID", r -> r.callIdProperty().get());
        addCol("Cola", r -> r.queueProperty().get());
    }

    private void addCol(String title, java.util.function.Function<ActiveCallRow, String> fn) {
        TableColumn<ActiveCallRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, title);
        callsTable.getColumns().add(c);
    }

    private void loadCampaignList() {
        summary.setText("Cargando lista de campañas…");
        worker.execute(() -> {
            try {
                List<CampaignRef> all = new ArrayList<>();
                // Issabel web campaign_monitoring lists all campaigns (no status filter).
                all.addAll(CampaignListParser.parse(client.getCampaignList(null)));
                for (var q : CampaignListParser.parseIncomingQueues(client.getIncomingQueueList())) {
                    CampaignRef pseudo = new CampaignRef();
                    pseudo.type = "incomingqueue";
                    pseudo.id = q.id;
                    pseudo.name = "Cola " + q.queue;
                    pseudo.status = q.status;
                    all.add(pseudo);
                }
                all.sort(LiveCampaignMonitorPane::compareCampaigns);
                Platform.runLater(() -> {
                    campaignCombo.setItems(FXCollections.observableArrayList(all));
                    if (all.isEmpty()) {
                        summary.setText("No hay campañas ni colas en el servidor.");
                        callsTable.getItems().clear();
                        return;
                    }
                    campaignCombo.getSelectionModel().select(0);
                    summary.setText(all.size() + " campaña(s) / cola(s) disponibles.");
                    loadSelected();
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[campaign-monitor] list: " + ex.getMessage());
                Platform.runLater(() -> summary.setText("Error listando campañas: " + ex.getMessage()));
            }
        });
    }

    /** Same order idea as Issabel campaign_monitoring: active first, then by id descending. */
    private static int compareCampaigns(CampaignRef a, CampaignRef b) {
        int sa = statusRank(a.status);
        int sb = statusRank(b.status);
        if (sa != sb) {
            return Integer.compare(sa, sb);
        }
        if ("incomingqueue".equals(a.type) != "incomingqueue".equals(b.type)) {
            return "incomingqueue".equals(a.type) ? 1 : -1;
        }
        return Integer.compare(b.id, a.id);
    }

    private static int statusRank(String status) {
        if (status == null) {
            return 2;
        }
        return switch (status.toLowerCase()) {
            case "active" -> 0;
            case "inactive" -> 1;
            default -> 2;
        };
    }

    private void loadSelected() {
        CampaignRef c = campaignCombo.getSelectionModel().getSelectedItem();
        if (c == null) {
            return;
        }
        summary.setText("Cargando…");
        worker.execute(() -> {
            try {
                String today = LocalDate.now().toString();
                String queueNum = c.name.startsWith("Cola ") ? c.name.substring(5).trim() : "";
                var doc = "incomingqueue".equals(c.type)
                        ? client.getIncomingQueueStatus(queueNum, today)
                        : client.getCampaignStatus(c.type, c.id, today);
                QueueStatusSnapshot snap = QueueStatusParser.parse(doc);
                String info = AdminLabels.campaignTypeLabel(c.type) + " · " + c.name
                        + " · Llamadas hoy: " + snap.totalCalls
                        + " · " + QueueStatusParser.formatCallStates(snap)
                        + " · " + QueueStatusParser.formatAgentSummary(snap.agentStatusCounts);
                Platform.runLater(() -> {
                    summary.setText(info);
                    callsTable.getItems().setAll(snap.activeCalls);
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[campaign-monitor] " + ex.getMessage());
                Platform.runLater(() -> summary.setText("Error: " + ex.getMessage()));
            }
        });
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
