package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.i18n.AdminLabels;
import dn.demedallo.admin.model.CampaignListParser;
import dn.demedallo.admin.model.IncomingQueueRef;
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
import org.w3c.dom.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** rep_incoming_calls_monitoring — today per incoming queue. */
public final class LiveIncomingQueuesPane extends BorderPane implements AutoCloseable {

    private final AdminEccpClient client;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-incoming-queues");
        t.setDaemon(true);
        return t;
    });

    private final Label status = new Label();
    private final TableView<QueueRow> table = new TableView<>(FXCollections.observableArrayList());

    public LiveIncomingQueuesPane(AdminEccpClient client) {
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
                new Label("Monitoreo de colas entrantes (hoy) — ECCP getincomingqueuestatus."),
                new HBox(10, refresh), status));
        setCenter(TableViewUtil.wrapInScrollPane(table));
        refresh();
    }

    private void buildColumns() {
        addCol("Cola", r -> r.queue);
        addCol("Estado", r -> r.status);
        addCol("Llamadas hoy", r -> r.callsToday);
        addCol("En espera", r -> r.waiting);
        addCol("Activas", r -> r.active);
    }

    private void addCol(String title, java.util.function.Function<QueueRow, String> fn) {
        TableColumn<QueueRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, title);
        table.getColumns().add(c);
    }

    private void refresh() {
        status.setText("Cargando…");
        worker.execute(() -> {
            try {
                List<QueueRow> rows = load();
                Platform.runLater(() -> {
                    table.getItems().setAll(rows);
                    status.setText(rows.size() + " cola(s)");
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[report-incoming] " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private List<QueueRow> load() throws Exception {
        String today = LocalDate.now().toString();
        Document doc = client.getIncomingQueueList();
        List<IncomingQueueRef> queues = CampaignListParser.parseIncomingQueues(doc);
        List<QueueRow> rows = new ArrayList<>();
        for (IncomingQueueRef q : queues) {
            QueueStatusSnapshot snap = QueueStatusParser.parse(
                    client.getIncomingQueueStatus(q.queue, today));
            QueueRow row = new QueueRow();
            row.queue = q.queue;
            row.status = AdminLabels.campaignStatusLabel(q.status);
            row.callsToday = String.valueOf(snap.totalCalls);
            row.waiting = String.valueOf(snap.activeCalls.size() + snap.onQueue);
            row.active = String.valueOf(snap.activeCalls == null ? 0 : snap.activeCalls.size());
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

    private static final class QueueRow {
        String queue, status, callsToday, waiting, active;
    }
}
