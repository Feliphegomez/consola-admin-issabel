package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.ChannelUsageBucket;
import dn.demedallo.admin.model.ChannelUsageDayPeak;
import dn.demedallo.admin.service.ChannelUsageService;
import dn.demedallo.admin.service.ChannelUsageService.PeakMetric;
import dn.demedallo.admin.ui.util.DateRangeFilterPane;
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
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Channel usage report with filters — concurrent channels estimated from {@code asteriskcdrdb.cdr}.
 */
public final class ChannelUsagePane extends BorderPane implements AutoCloseable {

    private static final String[] INTERVAL_LABELS = {"15 min", "30 min", "60 min"};
    private static final int[] INTERVAL_MINUTES = {15, 30, 60};
    private static final String[] PEAK_METRIC_LABELS = {
            "Total", "SIP / PJSIP", "DAHDI", "IAX", "Local", "H323"
    };
    private static final PeakMetric[] PEAK_METRICS = {
            PeakMetric.TOTAL, PeakMetric.SIP, PeakMetric.DAHDI,
            PeakMetric.IAX, PeakMetric.LOCAL, PeakMetric.H323
    };

    private final ChannelUsageService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "channel-usage");
        t.setDaemon(true);
        return t;
    });

    private final DateRangeFilterPane dateFilter = new DateRangeFilterPane();
    private final Spinner<Integer> hourFrom = hourSpinner(0);
    private final Spinner<Integer> hourTo = hourSpinner(23);
    private final ComboBox<String> intervalCombo = new ComboBox<>();
    private final Spinner<Integer> thresholdSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 20));
    private final ComboBox<String> peakMetricCombo = new ComboBox<>();
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

    private final TableView<ChannelUsageBucket> intervalTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<ChannelUsageDayPeak> dayTable =
            new TableView<>(FXCollections.observableArrayList());
    private final StackPane tableStack = new StackPane();

    private volatile int searchGeneration;
    private volatile boolean daySearchMode;

    public ChannelUsagePane(AdminDbSettings dbSettings) {
        this.service = new ChannelUsageService(dbSettings);
        getStyleClass().add("channel-usage-pane");
        setPadding(new Insets(8));

        intervalCombo.getItems().addAll(INTERVAL_LABELS);
        intervalCombo.setValue("30 min");
        peakMetricCombo.getItems().addAll(PEAK_METRIC_LABELS);
        peakMetricCombo.setValue("Total");
        thresholdSpinner.setEditable(true);
        thresholdSpinner.setPrefWidth(72);

        Button search = new Button("Generar gráfico");
        search.getStyleClass().add("monitor-btn");
        search.setDefaultButton(true);
        search.setOnAction(e -> runSearch());

        Button searchDays = new Button("Buscar días ≥ umbral");
        searchDays.getStyleClass().add("monitor-btn");
        searchDays.setOnAction(e -> runDayThresholdSearch());

        HBox dates = new HBox(8,
                dateFilter,
                new Label("Hora ini."), hourFrom,
                new Label("Hora fin"), hourTo);
        dates.setAlignment(Pos.CENTER_LEFT);

        HBox techFilters = new HBox(10, showTotal, showSip, showDahdi, showIax, showLocal, showH323);
        techFilters.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(8,
                search, searchDays,
                new Label("Intervalo"), intervalCombo,
                new Label("Umbral ≥"), thresholdSpinner,
                new Label("Métrica"), peakMetricCombo);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(
                "Estimación de canales activos desde CDR (cada llamada cuenta canal y destino). "
                        + "«Generar gráfico» muestra intervalos del rango; «Buscar días ≥ umbral» lista cada día "
                        + "cuyo pico cumple el filtro (máx. 90 días). Doble clic en un día abre su gráfico horario.");
        hint.setWrapText(true);
        hint.getStyleClass().add("channel-usage-hint");

        status.setWrapText(true);
        status.getStyleClass().add("monitor-status");

        VBox filters = new VBox(6, dates, techFilters, actions, hint, status);
        filters.setPadding(new Insets(0, 0, 8, 0));

        configureIntervalTable();
        configureDayTable();
        configureChartSeries();
        showTotal.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showSip.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showDahdi.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showIax.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showLocal.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());
        showH323.selectedProperty().addListener((o, a, b) -> refreshChartVisibility());

        javafx.scene.Parent intervalPanel =
                TableViewUtil.wrapInScrollPane(intervalTable, "uso-canales-intervalos", true);
        javafx.scene.Parent dayPanel =
                TableViewUtil.wrapInScrollPane(dayTable, "uso-canales-dias", true);
        dayTable.setVisible(false);
        dayTable.setManaged(false);
        tableStack.getChildren().addAll(intervalPanel, dayPanel);

        dayTable.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                ChannelUsageDayPeak row = dayTable.getSelectionModel().getSelectedItem();
                if (row != null) {
                    openDayChart(row.date());
                }
            }
        });

        SplitPane split = new SplitPane();
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.getItems().addAll(chart, tableStack);
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

    private void configureIntervalTable() {
        intervalTable.setPlaceholder(new Label("Seleccione rango y pulse «Generar gráfico»."));
        intervalTable.getStyleClass().add("monitor-table");

        TableColumn<ChannelUsageBucket, String> timeCol = new TableColumn<>("Hora");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().timeLabel()));
        timeCol.setPrefWidth(80);

        intervalTable.getColumns().addAll(timeCol,
                bucketCol("Total", ChannelUsageBucket::total),
                bucketCol("SIP", ChannelUsageBucket::sip),
                bucketCol("DAHDI", ChannelUsageBucket::dahdi),
                bucketCol("IAX", ChannelUsageBucket::iax),
                bucketCol("Local", ChannelUsageBucket::local),
                bucketCol("H323", ChannelUsageBucket::h323));
        TableViewUtil.applyStandardColumns(intervalTable, true);
    }

    private void configureDayTable() {
        dayTable.setPlaceholder(new Label("Pulse «Buscar días ≥ umbral» para listar fechas que cumplen el filtro."));
        dayTable.getStyleClass().add("monitor-table");

        TableColumn<ChannelUsageDayPeak, String> dateCol = new TableColumn<>("Día");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().dateLabel()));
        dateCol.setPrefWidth(100);

        TableColumn<ChannelUsageDayPeak, Number> peakCol = new TableColumn<>("Pico filtro");
        peakCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().peakFiltered()));
        peakCol.setPrefWidth(80);

        TableColumn<ChannelUsageDayPeak, String> timeCol = new TableColumn<>("Hora pico");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().peakTimeLabel()));
        timeCol.setPrefWidth(80);

        dayTable.getColumns().addAll(dateCol, peakCol, timeCol,
                dayCol("Total", ChannelUsageDayPeak::totalPeak),
                dayCol("SIP", ChannelUsageDayPeak::sipPeak),
                dayCol("DAHDI", ChannelUsageDayPeak::dahdiPeak),
                dayCol("IAX", ChannelUsageDayPeak::iaxPeak),
                dayCol("Local", ChannelUsageDayPeak::localPeak),
                dayCol("H323", ChannelUsageDayPeak::h323Peak));
        TableViewUtil.applyStandardColumns(dayTable, true);
    }

    private static TableColumn<ChannelUsageBucket, Number> bucketCol(String title,
            java.util.function.ToIntFunction<ChannelUsageBucket> getter) {
        TableColumn<ChannelUsageBucket, Number> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new SimpleIntegerProperty(getter.applyAsInt(c.getValue())));
        col.setPrefWidth(72);
        return col;
    }

    private static TableColumn<ChannelUsageDayPeak, Number> dayCol(String title,
            java.util.function.ToIntFunction<ChannelUsageDayPeak> getter) {
        TableColumn<ChannelUsageDayPeak, Number> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new SimpleIntegerProperty(getter.applyAsInt(c.getValue())));
        col.setPrefWidth(68);
        return col;
    }

    private void showIntervalTableMode() {
        daySearchMode = false;
        intervalTable.setVisible(true);
        intervalTable.setManaged(true);
        dayTable.setVisible(false);
        dayTable.setManaged(false);
        chart.setTitle("Uso de canales (estimado desde CDR)");
        CategoryAxis xAxis = (CategoryAxis) chart.getXAxis();
        xAxis.setLabel("Hora (fin de intervalo)");
    }

    private void showDayTableMode() {
        daySearchMode = true;
        intervalTable.setVisible(false);
        intervalTable.setManaged(false);
        dayTable.setVisible(true);
        dayTable.setManaged(true);
        chart.setTitle("Días que cumplen el umbral (pico por día)");
        CategoryAxis xAxis = (CategoryAxis) chart.getXAxis();
        xAxis.setLabel("Día");
    }

    private void openDayChart(LocalDate day) {
        dateFilter.setCustomRange(day, day);
        runSearch();
    }

    private void runSearch() {
        String dateErr = dateFilter.validate();
        if (dateErr != null) {
            status.setText(dateErr);
            return;
        }
        LocalDate from = dateFilter.getFrom();
        LocalDate to = dateFilter.getTo();
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
        intervalTable.setPlaceholder(new Label("Calculando…"));
        showIntervalTableMode();

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
                    intervalTable.setPlaceholder(new Label("Error al cargar datos."));
                });
            }
        });
    }

    private void runDayThresholdSearch() {
        String dateErr = dateFilter.validate();
        if (dateErr != null) {
            status.setText(dateErr);
            return;
        }
        LocalDate from = dateFilter.getFrom();
        LocalDate to = dateFilter.getTo();
        if (to.isBefore(from)) {
            status.setText("«Hasta» no puede ser anterior a «Desde».");
            return;
        }
        long dayCount = ChronoUnit.DAYS.between(from, to) + 1;
        if (dayCount > 90) {
            status.setText("La búsqueda de días admite como máximo 90 días.");
            return;
        }
        int hFrom = hourFrom.getValue() == null ? 0 : hourFrom.getValue();
        int hTo = hourTo.getValue() == null ? 23 : hourTo.getValue();
        if (hFrom < 0 || hFrom > 23 || hTo < 0 || hTo > 23) {
            status.setText("Horas deben estar entre 0 y 23.");
            return;
        }
        int threshold = thresholdSpinner.getValue() == null ? 20 : thresholdSpinner.getValue();
        PeakMetric metric = peakMetric();
        int intervalMin = intervalMinutes();
        int gen = ++searchGeneration;
        status.setText("Buscando días con pico " + metricLabel(metric) + " ≥ " + threshold + "…");
        dayTable.setPlaceholder(new Label("Analizando " + dayCount + " día(s)…"));
        showDayTableMode();

        worker.execute(() -> {
            try {
                List<ChannelUsageDayPeak> matchingDays = service.findDaysMeetingThreshold(
                        from, to, hFrom, hTo, intervalMin, threshold, metric);
                Platform.runLater(() -> {
                    if (gen != searchGeneration) {
                        return;
                    }
                    applyDayResult(matchingDays, from, to, threshold, metric, intervalMin);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (gen != searchGeneration) {
                        return;
                    }
                    status.setText("Error: " + ex.getMessage());
                    dayTable.setPlaceholder(new Label("Error en búsqueda de días."));
                });
            }
        });
    }

    private PeakMetric peakMetric() {
        String sel = peakMetricCombo.getValue();
        for (int i = 0; i < PEAK_METRIC_LABELS.length; i++) {
            if (PEAK_METRIC_LABELS[i].equals(sel)) {
                return PEAK_METRICS[i];
            }
        }
        return PeakMetric.TOTAL;
    }

    private static String metricLabel(PeakMetric metric) {
        return switch (metric) {
            case SIP -> "SIP";
            case DAHDI -> "DAHDI";
            case IAX -> "IAX";
            case LOCAL -> "Local";
            case H323 -> "H323";
            case TOTAL -> "Total";
        };
    }

    private void applyDayResult(List<ChannelUsageDayPeak> days, LocalDate from, LocalDate to,
            int threshold, PeakMetric metric, int intervalMin) {
        dayTable.getItems().setAll(days);
        seriesTotal.getData().clear();
        seriesSip.getData().clear();
        seriesDahdi.getData().clear();
        seriesIax.getData().clear();
        seriesLocal.getData().clear();
        seriesH323.getData().clear();

        XYChart.Series<String, Number> peakSeries = seriesForMetric(metric);
        for (ChannelUsageDayPeak d : days) {
            peakSeries.getData().add(new XYChart.Data<>(d.dateLabel(), d.peakFiltered()));
        }

        long scanned = ChronoUnit.DAYS.between(from, to) + 1;
        if (days.isEmpty()) {
            status.setText("Ningún día con pico " + metricLabel(metric) + " ≥ " + threshold
                    + " entre " + from + " y " + to + " (" + scanned + " días analizados, ventana "
                    + intervalMin + " min).");
            dayTable.setPlaceholder(new Label("Ningún día cumple el umbral."));
        } else {
            status.setText(days.size() + " día(s) con pico " + metricLabel(metric) + " ≥ " + threshold
                    + " · " + scanned + " analizados · doble clic abre el gráfico del día.");
            dayTable.setPlaceholder(new Label("Sin coincidencias."));
        }
        chart.getData().clear();
        chart.getData().add(seriesForMetric(metric));
    }

    private XYChart.Series<String, Number> seriesForMetric(PeakMetric metric) {
        return switch (metric) {
            case SIP -> seriesSip;
            case DAHDI -> seriesDahdi;
            case IAX -> seriesIax;
            case LOCAL -> seriesLocal;
            case H323 -> seriesH323;
            case TOTAL -> seriesTotal;
        };
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
        if (daySearchMode) {
            showIntervalTableMode();
        }
        intervalTable.getItems().setAll(buckets);
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
            intervalTable.setPlaceholder(new Label("Sin datos en el rango seleccionado."));
        } else {
            status.setText(buckets.size() + " intervalos · pico Total " + maxTotal
                    + " canales · " + rangeLabel + " · intervalo " + intervalMin + " min.");
            intervalTable.setPlaceholder(new Label("Sin datos."));
        }
        refreshChartVisibility();
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
