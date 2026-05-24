package dn.demedallo.admin.ui;

import dn.demedallo.admin.model.ServiceHealthModels.DiskVolume;
import dn.demedallo.admin.model.ServiceHealthModels.Level;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceCheck;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceHealthSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.service.ServiceHealthMonitorService;
import dn.demedallo.admin.util.AdminDbSettings;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Infrastructure health tab: dialer, PBX, Issabel, server metrics and disk with charts.
 */
public final class ServiceMonitoringPane extends BorderPane implements AutoCloseable {

    private static final int MAX_HISTORY = 48;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ServiceHealthMonitorService monitorService;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "service-health");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    private final Label statusLabel = new Label("Sin datos");
    private final Label okBadge = badge("OK", "health-badge-ok");
    private final Label warnBadge = badge("WARN 0", "health-badge-warn");
    private final Label errBadge = badge("ERROR 0", "health-badge-err");
    private final Label hostLabel = new Label();
    private final CheckBox autoRefresh = new CheckBox("Auto-actualizar");
    private final Spinner<Integer> refreshSec = new Spinner<>(10, 300, 30);
    private Timeline pollTimeline;

    private final ObservableList<ServiceCheck> checks = FXCollections.observableArrayList();
    private final TableView<ServiceCheck> table = new TableView<>(checks);

    private final TextArea detailArea = new TextArea();
    private final TextArea errorsArea = new TextArea();

    private final PieChart diskChart = new PieChart();
    private final LineChart<String, Number> loadChart = buildLineChart("Carga CPU (load 1m)");
    private final LineChart<String, Number> memChart = buildLineChart("Memoria RAM (%)");
    private final PieChart statusChart = new PieChart();

    private final ObservableList<XYChart.Series<String, Number>> loadSeriesList = loadChart.getData();
    private final ObservableList<XYChart.Series<String, Number>> memSeriesList = memChart.getData();
    private final XYChart.Series<String, Number> loadSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> memSeries = new XYChart.Series<>();

    public ServiceMonitoringPane(AdminEccpClient client, AdminDbSettings dbSettings, String eccpHost) {
        this.monitorService = new ServiceHealthMonitorService(client, dbSettings, eccpHost);
        getStyleClass().add("service-health-pane");
        setPadding(new Insets(10));

        hostLabel.setText("Servidor: " + (eccpHost == null || eccpHost.isBlank() ? "(login ECCP)" : eccpHost));
        hostLabel.getStyleClass().add("health-host-label");

        autoRefresh.setSelected(true);
        refreshSec.setEditable(true);

        Button refreshBtn = new Button("Actualizar ahora");
        refreshBtn.getStyleClass().add("monitor-btn");
        refreshBtn.setOnAction(e -> refreshNow());

        HBox top = new HBox(12,
                refreshBtn, autoRefresh, new Label("Cada (s):"), refreshSec,
                hostLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox badges = new HBox(10, okBadge, warnBadge, errBadge, statusLabel);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.setPadding(new Insets(6, 0, 8, 0));

        configureTable();
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setPrefRowCount(8);
        detailArea.getStyleClass().add("health-detail");

        errorsArea.setEditable(false);
        errorsArea.setWrapText(true);
        errorsArea.setPrefRowCount(6);
        errorsArea.setPromptText("Errores recientes dialerd / Asterisk…");
        errorsArea.getStyleClass().add("health-errors");

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, row) -> showDetail(row));

        VBox chartsBox = buildChartsPanel();
        SplitPane centerSplit = new SplitPane(chartsBox, table);
        centerSplit.setDividerPositions(0.38);
        VBox.setVgrow(centerSplit, Priority.ALWAYS);

        VBox detailBox = new VBox(4,
                new Label("Detalle / causa / acción sugerida"), detailArea,
                new Label("Errores recientes en logs"), errorsArea);
        detailBox.setPadding(new Insets(8, 0, 0, 0));

        VBox rootBox = new VBox(4, top, badges, centerSplit, detailBox);
        VBox.setVgrow(centerSplit, Priority.ALWAYS);
        setCenter(rootBox);

        loadSeries.setName("Load 1m");
        memSeries.setName("RAM %");
        loadSeriesList.add(loadSeries);
        memSeriesList.add(memSeries);

        setupAutoRefresh();
        refreshNow();
    }

    private static Label badge(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().addAll("health-badge", style);
        return l;
    }

    private static LineChart<String, Number> buildLineChart(String title) {
        CategoryAxis x = new CategoryAxis();
        x.setLabel("Hora");
        NumberAxis y = new NumberAxis();
        y.setLabel("");
        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(180);
        chart.getStyleClass().add("health-line-chart");
        return chart;
    }

    private VBox buildChartsPanel() {
        diskChart.setTitle("Disco (/)");
        diskChart.setLabelsVisible(true);
        diskChart.setLegendSide(Side.BOTTOM);
        diskChart.setPrefHeight(180);
        diskChart.getStyleClass().add("health-pie-chart");

        statusChart.setTitle("Estado de comprobaciones");
        statusChart.setLabelsVisible(true);
        statusChart.setLegendSide(Side.BOTTOM);
        statusChart.setPrefHeight(180);
        statusChart.getStyleClass().add("health-pie-chart");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(diskChart, 0, 0);
        grid.add(statusChart, 1, 0);
        grid.add(loadChart, 0, 1);
        grid.add(memChart, 1, 1);
        GridPane.setHgrow(diskChart, Priority.ALWAYS);
        GridPane.setHgrow(statusChart, Priority.ALWAYS);
        GridPane.setHgrow(loadChart, Priority.ALWAYS);
        GridPane.setHgrow(memChart, Priority.ALWAYS);

        VBox box = new VBox(grid);
        box.getStyleClass().add("health-charts");
        return box;
    }

    private void configureTable() {
        table.setPlaceholder(new Label("Recopilando estado de servicios…"));
        table.getStyleClass().addAll("health-table", "monitor-table");
        // #region agent log
        table.setRowFactory(tv -> {
            TableRow<ServiceCheck> row = new TableRow<>();
            row.hoverProperty().addListener((obs, was, hover) -> {
                if (!hover || row.isEmpty()) {
                    return;
                }
                agentDebugLog("H1", "health row hover",
                        "{\"hasMonitorTable\":" + table.getStyleClass().contains("monitor-table")
                                + ",\"hasHealthTable\":" + table.getStyleClass().contains("health-table")
                                + ",\"columnCount\":" + table.getColumns().size() + "}");
            });
            return row;
        });
        // #endregion

        TableColumn<ServiceCheck, String> catCol = new TableColumn<>("Área");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category()));
        catCol.setPrefWidth(90);

        TableColumn<ServiceCheck, String> nameCol = new TableColumn<>("Servicio");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(160);

        TableColumn<ServiceCheck, String> levelCol = new TableColumn<>("Estado");
        levelCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().level().name()));
        levelCol.setPrefWidth(70);
        levelCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                getStyleClass().removeAll("health-cell-ok", "health-cell-warn", "health-cell-err");
                switch (item) {
                    case "OK" -> getStyleClass().add("health-cell-ok");
                    case "WARN" -> getStyleClass().add("health-cell-warn");
                    case "ERROR" -> getStyleClass().add("health-cell-err");
                    default -> {
                    }
                }
            }
        });

        TableColumn<ServiceCheck, String> sumCol = new TableColumn<>("Resumen");
        sumCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().summary()));
        sumCol.setPrefWidth(200);

        TableColumn<ServiceCheck, String> causeCol = new TableColumn<>("Causa / detalle");
        causeCol.setCellValueFactory(c -> new SimpleStringProperty(truncate(c.getValue().cause(), 120)));
        causeCol.setPrefWidth(280);

        table.getColumns().addAll(catCol, nameCol, levelCol, sumCol, causeCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void setupAutoRefresh() {
        autoRefresh.selectedProperty().addListener((obs, o, on) -> {
            if (on) {
                startTimeline();
            } else {
                stopTimeline();
            }
        });
        refreshSec.valueProperty().addListener((obs, o, n) -> {
            if (autoRefresh.isSelected()) {
                startTimeline();
            }
        });
        startTimeline();
    }

    private void startTimeline() {
        stopTimeline();
        int sec = Math.max(10, refreshSec.getValue());
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(sec), e -> refreshNow()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    private void stopTimeline() {
        if (pollTimeline != null) {
            pollTimeline.stop();
            pollTimeline = null;
        }
    }

    private void refreshNow() {
        if (!refreshRunning.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> statusLabel.setText("Actualizando…"));
        worker.submit(() -> {
            try {
                ServiceHealthSnapshot snap = monitorService.collect();
                Platform.runLater(() -> applySnapshot(snap));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            } finally {
                refreshRunning.set(false);
            }
        });
    }

    private void applySnapshot(ServiceHealthSnapshot snap) {
        checks.setAll(snap.checks());
        long ok = snap.checks().stream().filter(c -> c.level() == Level.OK).count();
        long warn = snap.warnCount();
        long err = snap.errorCount();
        okBadge.setText("OK " + ok);
        warnBadge.setText("WARN " + warn);
        errBadge.setText("ERROR " + err);

        String when = TIME_FMT.format(snap.collectedAt());
        if (!snap.sshUsed() && snap.sshError() != null && !snap.sshError().isBlank()) {
            statusLabel.setText("Última: " + when + " — " + snap.sshError());
        } else if (snap.server() != null) {
            statusLabel.setText("Última: " + when + " — " + snap.server().hostname()
                    + " · load " + snap.server().load1());
        } else {
            statusLabel.setText("Última actualización: " + when);
        }

        updateStatusChart(ok, warn, err);
        updateDiskChart(snap.disks());
        appendHistory(snap);

        List<String> errLines = new ArrayList<>();
        for (String line : snap.dialerErrors()) {
            errLines.add("[dialerd] " + line);
        }
        for (String line : snap.asteriskErrors()) {
            errLines.add("[asterisk] " + line);
        }
        if (errLines.isEmpty()) {
            errorsArea.setText("Sin líneas ERR recientes en dialerd.log ni alertas en asterisk/messages.");
        } else {
            errorsArea.setText(String.join("\n", errLines));
        }

        if (!checks.isEmpty() && table.getSelectionModel().getSelectedItem() == null) {
            table.getSelectionModel().selectFirst();
        } else {
            showDetail(table.getSelectionModel().getSelectedItem());
        }
    }

    private void updateStatusChart(long ok, long warn, long err) {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        if (ok > 0) {
            data.add(new PieChart.Data("OK (" + ok + ")", ok));
        }
        if (warn > 0) {
            data.add(new PieChart.Data("WARN (" + warn + ")", warn));
        }
        if (err > 0) {
            data.add(new PieChart.Data("ERROR (" + err + ")", err));
        }
        if (data.isEmpty()) {
            data.add(new PieChart.Data("Sin datos", 1));
        }
        statusChart.setData(data);
    }

    private void updateDiskChart(List<DiskVolume> disks) {
        DiskVolume root = disks.stream()
                .filter(d -> "/".equals(d.mount()))
                .findFirst()
                .orElse(disks.isEmpty() ? null : disks.get(0));
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        if (root != null) {
            diskChart.setTitle("Disco " + root.mount() + " (" + root.usePercent() + "%)");
            long free = Math.max(0, root.availBytes());
            long used = Math.max(0, root.usedBytes());
            data.add(new PieChart.Data("Usado " + root.usePercent() + "%", used));
            data.add(new PieChart.Data("Libre", free));
        } else {
            diskChart.setTitle("Disco");
            data.add(new PieChart.Data("Sin datos SSH", 1));
        }
        diskChart.setData(data);
    }

    private void appendHistory(ServiceHealthSnapshot snap) {
        if (snap.server() == null) {
            return;
        }
        String label = TIME_FMT.format(snap.collectedAt());
        loadSeries.getData().add(new XYChart.Data<>(label, snap.server().load1()));
        memSeries.getData().add(new XYChart.Data<>(label, snap.server().memUsedPercent()));
        trimSeries(loadSeries);
        trimSeries(memSeries);
    }

    private static void trimSeries(XYChart.Series<String, Number> series) {
        while (series.getData().size() > MAX_HISTORY) {
            series.getData().remove(0);
        }
    }

    private void showDetail(ServiceCheck row) {
        if (row == null) {
            detailArea.clear();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Servicio: ").append(row.name()).append(" (").append(row.category()).append(")\n");
        sb.append("Estado: ").append(row.level()).append("\n\n");
        sb.append("Resumen:\n").append(row.summary()).append("\n\n");
        sb.append("Causa / motivo:\n").append(row.cause()).append("\n\n");
        sb.append("Acción sugerida:\n").append(row.actionHint()).append("\n");
        if (!row.metrics().isEmpty()) {
            sb.append("\nMétricas:\n");
            for (Map.Entry<String, String> e : row.metrics().entrySet()) {
                sb.append("  · ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        detailArea.setText(sb.toString());
    }

    // #region agent log
    private static void agentDebugLog(String hypothesisId, String message, String dataJson) {
        try {
            Path log = Path.of(System.getProperty("user.dir"), "debug-38d6ea.log");
            String line = "{\"sessionId\":\"38d6ea\",\"hypothesisId\":\"" + hypothesisId
                    + "\",\"location\":\"ServiceMonitoringPane\",\"message\":\"" + message
                    + "\",\"data\":" + dataJson + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Files.writeString(log, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
    // #endregion

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    public void shutdown() {
        stopTimeline();
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
