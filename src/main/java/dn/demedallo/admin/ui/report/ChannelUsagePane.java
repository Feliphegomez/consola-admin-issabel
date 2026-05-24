package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.ChannelUsageBucket;
import dn.demedallo.admin.service.ChannelUsageService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.ShiftDatetimeRange;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Channel usage report with filters — concurrent channels estimated from {@code asteriskcdrdb.cdr}.
 */
public final class ChannelUsagePane extends BorderPane implements AutoCloseable {

    private static final String[] INTERVAL_LABELS = {"15 min", "30 min", "60 min"};
    private static final int[] INTERVAL_MINUTES = {15, 30, 60};

    private final ChannelUsageService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "channel-usage");
        t.setDaemon(true);
        return t;
    });

    private final DatePicker dateFrom = new DatePicker(LocalDate.now());
    private final DatePicker dateTo = new DatePicker(LocalDate.now());
    private final Spinner<Integer> hourFrom = hourSpinner(0);
    private final Spinner<Integer> hourTo = hourSpinner(23);
    private final ComboBox<String> intervalCombo = new ComboBox<>();
    private final CheckBox showTotal = techCheck("Total", true);
    private final CheckBox showSip = techCheck("SIP / PJSIP", true);
    private final CheckBox showDahdi = techCheck("DAHDI", true);
    private final CheckBox showIax = techCheck("IAX", true);
    private final CheckBox showLocal = techCheck("Local", true);
    private final CheckBox showH323 = techCheck("H323", true);
    private final Label status = new Label();

    private final LineChart<String, Number> chart = buildChart();
    private final XYChart.Series<String, Number> seriesTotal = new XYChart.Series<>();
    private final XYChart.Series<String, Number> seriesSip = new XYChart.Series<>();
    private final XYChart.Series<String, Number> seriesDahdi = new XYChart.Series<>();
    private final XYChart.Series<String, Number> seriesIax = new XYChart.Series<>();
    private final XYChart.Series<String, Number> seriesLocal = new XYChart.Series<>();
    private final XYChart.Series<String, Number> seriesH323 = new XYChart.Series<>();

    private final TableView<ChannelUsageBucket> table =
            new TableView<>(FXCollections.observableArrayList());

    private volatile int searchGeneration;

    public ChannelUsagePane(AdminDbSettings dbSettings) {
        this.service = new ChannelUsageService(dbSettings);
        getStyleClass().add("channel-usage-pane");
        setPadding(new Insets(8));

        intervalCombo.getItems().addAll(INTERVAL_LABELS);
        intervalCombo.setValue("30 min");

        Button search = new Button("Generar gráfico");
        search.getStyleClass().add("monitor-btn");
        search.setDefaultButton(true);
        search.setOnAction(e -> runSearch());

        Button today = new Button("Hoy");
        today.getStyleClass().add("monitor-btn");
        today.setOnAction(e -> {
            LocalDate now = LocalDate.now();
            dateFrom.setValue(now);
            dateTo.setValue(now);
            hourFrom.getValueFactory().setValue(0);
            hourTo.getValueFactory().setValue(23);
            runSearch();
        });

        HBox dates = new HBox(8,
                new Label("Desde"), dateFrom,
                new Label("Hasta"), dateTo,
                new Label("Hora ini."), hourFrom,
                new Label("Hora fin"), hourTo);
        dates.setAlignment(Pos.CENTER_LEFT);

        HBox techFilters = new HBox(10, showTotal, showSip, showDahdi, showIax, showLocal, showH323);
        techFilters.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(8, search, today, new Label("Intervalo"), intervalCombo);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(
                "Estimación de canales activos desde CDR (cada llamada cuenta canal y destino). "
                        + "Requiere base CDR en login (asteriskcdrdb).");
        hint.setWrapText(true);
        hint.getStyleClass().add("channel-usage-hint");

        status.setWrapText(true);
        status.getStyleClass().add("monitor-status");

        VBox filters = new VBox(6, dates, techFilters, actions, hint, status);
        filters.setPadding(new Insets(0, 0, 8, 0));

        configureTable();
        configureChartSeries();
        showTotal.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showSip.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showDahdi.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showIax.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showLocal.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showH323.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());

        javafx.scene.Parent tablePanel = TableViewUtil.wrapInScrollPane(table, "uso-canales", true);

        SplitPane split = new SplitPane();
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.getItems().addAll(chart, tablePanel);
        split.setDividerPositions(0.55);
        VBox.setVgrow(split, Priority.ALWAYS);

        setTop(filters);
        setCenter(split);
    }

    private static Spinner<Integer> hourSpinner(int initial) {
        Spinner<Integer> sp = new Spinner<>(0, 23, initial);
        sp.setEditable(true);
        sp.setPrefWidth(70);
        return sp;
    }

    private static CheckBox techCheck(String label, boolean selected) {
        CheckBox cb = new CheckBox(label);
        cb.setSelected(selected);
        return cb;
    }

    private static LineChart<String, Number> buildChart() {
        CategoryAxis x = new CategoryAxis();
        x.setLabel("Hora (fin de intervalo)");
        NumberAxis y = new NumberAxis();
        y.setLabel("Canales");
        y.setMinorTickVisible(false);
        LineChart<String, Number> c = new LineChart<>(x, y);
        c.setTitle("Uso de canales (estimado desde CDR)");
        c.setLegendSide(Side.TOP);
        c.setCreateSymbols(false);
        c.setAnimated(false);
        c.getStyleClass().add("channel-usage-chart");
        return c;
    }

    private void configureChartSeries() {
        seriesTotal.setName("Total");
        seriesSip.setName("SIP");
        seriesDahdi.setName("DAHDI");
        seriesIax.setName("IAX");
        seriesLocal.setName("Local");
        seriesH323.setName("H323");
        chart.getData().setAll(
                seriesTotal, seriesSip, seriesDahdi, seriesIax, seriesLocal, seriesH323);
        refreshChartVisibility();
    }

    private void refreshChartVisibility() {
        chart.getData().clear();
        if (showTotal.isSelected()) {
            chart.getData().add(seriesTotal);
        }
        if (showSip.isSelected()) {
            chart.getData().add(seriesSip);
        }
        if (showDahdi.isSelected()) {
            chart.getData().add(seriesDahdi);
        }
        if (showIax.isSelected()) {
            chart.getData().add(seriesIax);
        }
        if (showLocal.isSelected()) {
            chart.getData().add(seriesLocal);
        }
        if (showH323.isSelected()) {
            chart.getData().add(seriesH323);
        }
    }

    private void configureTable() {
        table.setPlaceholder(new Label("Seleccione rango y pulse «Generar gráfico»."));
        table.getStyleClass().add("monitor-table");

        TableColumn<ChannelUsageBucket, String> timeCol = new TableColumn<>("Hora");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().timeLabel()));
        timeCol.setPrefWidth(80);

        TableColumn<ChannelUsageBucket, Number> totalCol = colInt("Total", ChannelUsageBucket::total);
        TableColumn<ChannelUsageBucket, Number> sipCol = colInt("SIP", ChannelUsageBucket::sip);
        TableColumn<ChannelUsageBucket, Number> dahdiCol = colInt("DAHDI", ChannelUsageBucket::dahdi);
        TableColumn<ChannelUsageBucket, Number> iaxCol = colInt("IAX", ChannelUsageBucket::iax);
        TableColumn<ChannelUsageBucket, Number> localCol = colInt("Local", ChannelUsageBucket::local);
        TableColumn<ChannelUsageBucket, Number> h323Col = colInt("H323", ChannelUsageBucket::h323);

        table.getColumns().addAll(timeCol, totalCol, sipCol, dahdiCol, iaxCol, localCol, h323Col);
        TableViewUtil.prepare(table);
    }

    private static TableColumn<ChannelUsageBucket, Number> colInt(String title,
            java.util.function.ToIntFunction<ChannelUsageBucket> getter) {
        TableColumn<ChannelUsageBucket, Number> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new SimpleIntegerProperty(getter.applyAsInt(c.getValue())));
        col.setPrefWidth(72);
        return col;
    }

    private void runSearch() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) {
            status.setText("Indique fechas válidas.");
            return;
        }
        int hFrom = hourFrom.getValue() == null ? 0 : hourFrom.getValue();
        int hTo = hourTo.getValue() == null ? 23 : hourTo.getValue();
        if (hFrom < 0 || hFrom > 23 || hTo < 0 || hTo > 23) {
            status.setText("Horas deben estar entre 0 y 23.");
            return;
        }

        ShiftDatetimeRange range = ShiftDatetimeRange.ofDates(from, to);
        String start = range.start.substring(0, 10) + String.format(" %02d:00:00", hFrom);
        String end = range.end.substring(0, 10) + String.format(" %02d:59:59", hTo);
        if (start.compareTo(end) > 0) {
            status.setText("La hora inicial no puede ser posterior a la final.");
            return;
        }

        int intervalMin = intervalMinutes();
        int gen = ++searchGeneration;
        status.setText("Calculando uso de canales…");
        table.setPlaceholder(new Label("Calculando…"));

        worker.execute(() -> {
            try {
                List<ChannelUsageBucket> buckets =
                        service.loadUsage(start, end, intervalMin);
                Platform.runLater(() -> {
                    if (gen != searchGeneration) {
                        return;
                    }
                    applyResult(buckets, range.indicatorText, intervalMin);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (gen != searchGeneration) {
                        return;
                    }
                    status.setText("Error: " + ex.getMessage());
                    table.setPlaceholder(new Label("Error al cargar datos."));
                });
            }
        });
    }

    private int intervalMinutes() {
        String sel = intervalCombo.getValue();
        for (int i = 0; i < INTERVAL_LABELS.length; i++) {
            if (INTERVAL_LABELS[i].equals(sel)) {
                return INTERVAL_MINUTES[i];
            }
        }
        return 30;
    }

    private void applyResult(List<ChannelUsageBucket> buckets, String rangeLabel, int intervalMin) {
        table.getItems().setAll(buckets);
        seriesTotal.getData().clear();
        seriesSip.getData().clear();
        seriesDahdi.getData().clear();
        seriesIax.getData().clear();
        seriesLocal.getData().clear();
        seriesH323.getData().clear();

        int maxTotal = 0;
        for (ChannelUsageBucket b : buckets) {
            seriesTotal.getData().add(new XYChart.Data<>(b.timeLabel(), b.total()));
            seriesSip.getData().add(new XYChart.Data<>(b.timeLabel(), b.sip()));
            seriesDahdi.getData().add(new XYChart.Data<>(b.timeLabel(), b.dahdi()));
            seriesIax.getData().add(new XYChart.Data<>(b.timeLabel(), b.iax()));
            seriesLocal.getData().add(new XYChart.Data<>(b.timeLabel(), b.local()));
            seriesH323.getData().add(new XYChart.Data<>(b.timeLabel(), b.h323()));
            maxTotal = Math.max(maxTotal, b.total());
        }

        if (buckets.isEmpty()) {
            status.setText("Sin datos CDR en " + rangeLabel + " (intervalo " + intervalMin + " min).");
            table.setPlaceholder(new Label("Sin datos en el rango seleccionado."));
        } else {
            status.setText(buckets.size() + " intervalos · pico Total " + maxTotal
                    + " canales · " + rangeLabel + " · intervalo " + intervalMin + " min.");
            table.setPlaceholder(new Label("Sin datos."));
        }
        refreshChartVisibility();
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
