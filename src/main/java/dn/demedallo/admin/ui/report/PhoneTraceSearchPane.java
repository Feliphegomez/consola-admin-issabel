package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.db.FailedShortCallsDao.CallDirectionFilter;
import dn.demedallo.admin.model.CallLogInvestigationResult;
import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceRowSummary;
import dn.demedallo.admin.model.PhoneTraceDetail;
import dn.demedallo.admin.model.ReadableTraceStepRow;
import dn.demedallo.admin.service.CallProblemDiagnosisService;
import dn.demedallo.admin.service.PhoneTraceSearchService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ShiftDatetimeRange;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search all inbound/outbound calls for a phone number and show readable trace steps.
 */
public final class PhoneTraceSearchPane extends BorderPane implements AutoCloseable {

    private final PhoneTraceSearchService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "phone-trace-search");
        t.setDaemon(true);
        return t;
    });

    private final TextField phoneField = new TextField();
    private final DatePicker dateFrom = new DatePicker(LocalDate.now().minusDays(30));
    private final DatePicker dateTo = new DatePicker(LocalDate.now());
    private final ComboBox<String> directionFilter = new ComboBox<>();
    private final Label status = new Label();
    private final Label callDetail = new Label();
    private final Label diagnosisLabel = new Label();
    private final Label logConclusion = new Label();
    private final TextArea logLines = new TextArea();

    private final TableView<PhoneTraceCallRow> callsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<ReadableTraceStepRow> traceTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TraceMermaidPane mermaidPane = new TraceMermaidPane();

    private volatile int searchGeneration;
    private volatile int traceGeneration;
    private volatile int enrichGeneration;
    private volatile String lastSearchStatus = "";
    private volatile String lastDisplayFailureReason = "";
    private volatile CallProblemDiagnosis lastDiagnosis;
    private volatile PhoneTraceCallRow selectedTraceCall;
    private final ConcurrentHashMap<String, PhoneTraceRowSummary> rowSummaryCache = new ConcurrentHashMap<>();

    public PhoneTraceSearchPane(AdminDbSettings dbSettings, String eccpHost) {
        this.service = new PhoneTraceSearchService(dbSettings, eccpHost);
        getStyleClass().add("phone-trace-search-pane");
        setPadding(new Insets(8));

        phoneField.setPromptText("Ej: 6045451116");
        phoneField.setPrefWidth(180);
        directionFilter.getItems().addAll("Todas", "Entrantes", "Salientes");
        directionFilter.setValue("Todas");
        directionFilter.setMaxWidth(120);

        Button search = new Button("Buscar");
        search.getStyleClass().add("monitor-btn");
        search.setDefaultButton(true);
        search.setOnAction(e -> runSearch());
        phoneField.setOnAction(e -> runSearch());

        HBox filters = new HBox(10,
                new Label("Teléfono:"), phoneField,
                new Label("Desde:"), dateFrom,
                new Label("Hasta:"), dateTo,
                new Label("Tipo:"), directionFilter,
                search);
        filters.setAlignment(Pos.CENTER_LEFT);

        buildCallsColumns();
        buildTraceColumns();
        TableViewUtil.prepare(callsTable);
        TableViewUtil.prepare(traceTable);
        callsTable.setPlaceholder(new Label("Ingrese un número y pulse Buscar"));
        traceTable.setPlaceholder(new Label("Seleccione una llamada para ver la trazabilidad paso a paso"));

        callsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, row) -> {
            if (row != null) {
                showCallDetail(row);
                loadTrace(row);
            } else {
                callDetail.setText("Seleccione una llamada de la lista.");
                traceTable.getItems().clear();
                mermaidPane.clear();
                clearLogPanel();
            }
        });

        VBox traceTableBox = new VBox(6,
                sectionTitle("Trazabilidad (paso a paso)"),
                TableViewUtil.wrapInScrollPane(traceTable, "trazabilidad-telefono"));
        VBox.setVgrow(traceTable, Priority.ALWAYS);

        SplitPane traceSplit = new SplitPane(traceTableBox, mermaidPane);
        traceSplit.setDividerPositions(0.58);
        SplitPane.setResizableWithParent(mermaidPane, true);
        VBox traceBox = new VBox(traceSplit);
        VBox.setVgrow(traceSplit, Priority.ALWAYS);

        VBox callsBox = new VBox(6,
                sectionTitle("Llamadas encontradas"),
                TableViewUtil.wrapInScrollPane(callsTable, "buscar-telefono-llamadas"));
        VBox.setVgrow(callsTable, Priority.ALWAYS);

        SplitPane split = new SplitPane(callsBox, traceBox);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.48);

        Label hint = new Label(
                "Busca en call_center llamadas entrantes y salientes por número (mín. 4 dígitos). "
                        + "En fallos se indica si el origen es interno (Issabel/dialer/cola) o externo "
                        + "(cliente/troncal/destino) y dónde revisar.");
        hint.setWrapText(true);
        hint.getStyleClass().add("panel-hint");

        callDetail.setWrapText(true);
        callDetail.getStyleClass().add("report-desc");
        callDetail.setText("Seleccione una llamada para ver el detalle y la trazabilidad.");

        diagnosisLabel.setWrapText(true);
        diagnosisLabel.getStyleClass().addAll("report-desc", "phone-trace-diagnosis");
        diagnosisLabel.setText("");

        logConclusion.setWrapText(true);
        logConclusion.getStyleClass().addAll("report-desc", "phone-trace-log-conclusion");
        logConclusion.setText("");

        logLines.setEditable(false);
        logLines.setWrapText(true);
        logLines.setPrefRowCount(4);
        logLines.getStyleClass().addAll("login-log", "phone-trace-log-lines");
        logLines.setPromptText("Líneas del log (dialer/Asterisk) al investigar fallos sin código SIP…");

        status.getStyleClass().add("monitor-status");
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado — active la base de datos call_center en el login.");
        }

        VBox center = new VBox(8, hint, callDetail, diagnosisLabel, logConclusion, logLines, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox.setVgrow(logLines, Priority.NEVER);

        setTop(new VBox(8, filters, status));
        setCenter(center);
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-table-title");
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER_LEFT);
        return l;
    }

    private void buildCallsColumns() {
        callsTable.getColumns().add(rescheduleColumn());
        addCol(callsTable, "Tipo", 72, PhoneTraceCallRow::directionLabel);
        addCol(callsTable, "ID", 52, r -> String.valueOf(r.callId));
        addCol(callsTable, "Teléfono", 110, r -> r.phone);
        addCol(callsTable, "Campaña / cola", 140, r -> r.campaignName);
        addCol(callsTable, "Estado", 100, r -> r.statusLabel);
        addCol(callsTable, "Diagnóstico", 200, this::diagnosisForRow);
        TableColumn<PhoneTraceCallRow, String> motive = addCol(callsTable, "Motivo", 200,
                r -> motiveForRow(r));
        motive.setPrefWidth(200);
        addCol(callsTable, "Fecha/hora", 140, r -> r.callDatetime);
        addCol(callsTable, "Duración", 72, r -> r.duration);
        addCol(callsTable, "Reint.", 56, PhoneTraceCallRow::retriesLabel);
        addCol(callsTable, "Agente", 70, r -> r.agent);
        addCol(callsTable, "Troncal", 90, r -> r.trunk);
    }

    private TableColumn<PhoneTraceCallRow, Void> rescheduleColumn() {
        TableColumn<PhoneTraceCallRow, Void> c = new TableColumn<>("Acción");
        c.setPrefWidth(118);
        c.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Reagendar");

            {
                btn.getStyleClass().add("monitor-btn-small");
                btn.setOnAction(e -> {
                    PhoneTraceCallRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) {
                        confirmAndReschedule(row);
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
                PhoneTraceCallRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || !row.canReschedule() || !service.isDbEnabled()) {
                    setGraphic(null);
                    return;
                }
                btn.setDisable(false);
                btn.setText(row.retriesExhausted() ? "Reagendar *" : "Reagendar");
                setGraphic(btn);
            }
        });
        return c;
    }

    private void buildTraceColumns() {
        addCol(traceTable, "Paso", 44, r -> String.valueOf(r.step));
        TableColumn<ReadableTraceStepRow, String> summary = addCol(traceTable, "Qué ocurrió", 300, r -> r.summary);
        summary.setPrefWidth(300);
        addCol(traceTable, "Origen", 58, r -> CallProblemDiagnosisService.stepOriginLabel(
                selectedTraceCall, r));
        addCol(traceTable, "Dónde", 160, r -> CallProblemDiagnosisService.stepWhereLabel(
                selectedTraceCall, r));
        addCol(traceTable, "Fecha/hora", 130, r -> r.datetime);
        addCol(traceTable, "Estado", 96, r -> r.statusLabel);
        addCol(traceTable, "Reint.", 48, r -> r.retry);
        addCol(traceTable, "Agente", 70, r -> r.agent);
        addCol(traceTable, "Troncal", 90, r -> r.trunk);
        addCol(traceTable, "Duración", 72, r -> r.duration);
    }

    private static <T> TableColumn<T, String> addCol(TableView<T> table, String title, double width,
            java.util.function.Function<T, String> fn) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                fn.apply(cd.getValue())));
        TableViewUtil.styleColumn(c, width);
        table.getColumns().add(c);
        return c;
    }

    private void runSearch() {
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado — active la base de datos en el login.");
            return;
        }
        String digits = service.normalizePhone(phoneField.getText());
        if (digits.length() < 4) {
            status.setText("Ingrese al menos 4 dígitos del teléfono (ej. 6045451116).");
            return;
        }
        ShiftDatetimeRange range = ShiftDatetimeRange.ofDates(
                dateFrom.getValue(), dateTo.getValue());
        CallDirectionFilter filter = mapDirection(directionFilter.getValue());
        int generation = ++searchGeneration;
        enrichGeneration = generation;
        rowSummaryCache.clear();
        traceTable.getItems().clear();
        mermaidPane.clear();
        clearLogPanel();
        lastDiagnosis = null;
        selectedTraceCall = null;
        callDetail.setText("Buscando…");
        status.setText("Buscando llamadas que contengan «" + digits + "»…");
        worker.execute(() -> {
            try {
                List<PhoneTraceCallRow> rows = service.search(phoneField.getText(), range, filter);
                Platform.runLater(() -> {
                    if (generation != searchGeneration) {
                        return;
                    }
                    callsTable.getItems().setAll(rows);
                    int in = 0;
                    int out = 0;
                    for (PhoneTraceCallRow r : rows) {
                        if (r.direction == PhoneTraceCallRow.CallDirection.INCOMING) {
                            in++;
                        } else {
                            out++;
                        }
                    }
                    lastSearchStatus = rows.size() + " llamadas · " + in + " entrantes · " + out
                            + " salientes · " + range.indicatorText;
                    status.setText(lastSearchStatus);
                    if (!rows.isEmpty()) {
                        callsTable.getSelectionModel().selectFirst();
                        enrichAllRows(rows, generation);
                    } else {
                        callDetail.setText("No se encontraron llamadas para «" + digits
                                + "» en el rango indicado.");
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[phone-trace] search | EN: " + ex.getMessage());
                Platform.runLater(() -> {
                    if (generation == searchGeneration) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void showCallDetail(PhoneTraceCallRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.directionLabel()).append(" · ID ").append(row.callId)
                .append(" · ").append(row.phone)
                .append(" · ").append(row.statusLabel);
        if (!"-".equals(row.campaignName)) {
            sb.append(" · ").append(row.campaignName);
        }
        sb.append(" · ").append(row.callDatetime);
        if (!"-".equals(row.agent)) {
            sb.append(" · agente ").append(row.agent);
        }
        String motive = displayMotiveFor(row);
        if (motive != null && !motive.isBlank()) {
            sb.append("\nMotivo: ").append(motive);
        }
        CallProblemDiagnosis diagnosis = summaryFor(row).map(s -> s.diagnosis()).orElse(lastDiagnosis);
        if (diagnosis != null && diagnosis.isProblem()
                && callsTable.getSelectionModel().getSelectedItem() == row) {
            sb.append("\n\n").append(diagnosis.formattedBlock());
        }
        if (row.canReschedule()) {
            sb.append("\nPuede usar Reagendar para volver a encolar en la campaña «")
                    .append(row.campaignName).append("».");
        }
        callDetail.setText(sb.toString());
    }

    private void confirmAndReschedule(PhoneTraceCallRow row) {
        String warn = row.retriesExhausted()
                ? "Esta llamada agotó los reintentos de campaña (" + row.retriesLabel()
                + "). Se forzará un intento adicional.\n\n"
                : "";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reagendar en campaña");
        alert.setHeaderText("Reagendar " + row.phone + " — " + row.campaignName);
        alert.setContentText(warn + "ID llamada: " + row.callId + "\n"
                + "Motivo actual: " + displayMotiveFor(row) + "\n\n"
                + "El dialer volverá a marcar cuando haya canal disponible.\n\n"
                + "¿Continuar?");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                runReschedule(row);
            }
        });
    }

    private void runReschedule(PhoneTraceCallRow row) {
        status.setText("Reagendando llamada " + row.callId + " en campaña…");
        worker.execute(() -> {
            try {
                service.rescheduleInCampaign(row);
                AppLogFile.appendLine("[phone-trace] reagendada call_id=" + row.callId
                        + " phone=" + row.phone + " campaign=" + row.campaignName);
                Platform.runLater(() -> {
                    status.setText("Llamada " + row.callId + " reagendada — el dialer reintentará "
                            + row.phone);
                    runSearch();
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[phone-trace] reagendar call_id=" + row.callId + ": "
                        + ex.getMessage());
                Platform.runLater(() -> status.setText("Error al reagendar: " + ex.getMessage()));
            }
        });
    }

    private void loadTrace(PhoneTraceCallRow row) {
        int generation = ++traceGeneration;
        selectedTraceCall = row;
        traceTable.getItems().clear();
        mermaidPane.clear();
        diagnosisLabel.setText("Analizando origen del problema…");
        styleDiagnosis(null);
        logConclusion.setText("Buscando en logs del servidor…");
        logLines.clear();
        worker.execute(() -> {
            try {
                PhoneTraceDetail detail = service.loadTraceDetail(row);
                Platform.runLater(() -> {
                    if (generation != traceGeneration) {
                        return;
                    }
                    lastDisplayFailureReason = detail.displayFailureReason;
                    lastDiagnosis = detail.diagnosis;
                    rowSummaryCache.put(rowKey(row), new PhoneTraceRowSummary(
                            detail.displayFailureReason, detail.diagnosis));
                    traceTable.getItems().setAll(detail.steps);
                    applyDiagnosis(detail.diagnosis);
                    applyLogInvestigation(detail.logInvestigation, detail.displayFailureReason);
                    mermaidPane.render(row, detail.steps, detail.displayFailureReason,
                            detail.diagnosis);
                    callsTable.refresh();
                    showCallDetail(row);
                    if (detail.steps.isEmpty()) {
                        status.setText(row.directionLabel() + " #" + row.callId
                                + " — sin eventos en call_progress_log");
                    } else if (detail.diagnosis.isProblem()) {
                        status.setText(row.directionLabel() + " #" + row.callId
                                + " — " + detail.diagnosis.originLabel
                                + " · " + detail.diagnosis.locationLabel);
                    } else if (detail.logInvestigation.hasConclusion()) {
                        status.setText(row.directionLabel() + " #" + row.callId
                                + " — trazabilidad + conclusión de log");
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[phone-trace] trace | EN: " + ex.getMessage());
                Platform.runLater(() -> status.setText("Trazabilidad: " + ex.getMessage()));
            }
        });
    }

    private void applyLogInvestigation(CallLogInvestigationResult log, String displayReason) {
        if (log == null) {
            clearLogPanel();
            return;
        }
        if (log.hasConclusion()) {
            logConclusion.setText("Conclusión del log (" + log.sourceLabel + "): " + displayReason);
        } else if (log.searched) {
            logConclusion.setText("Log: " + log.statusMessage);
        } else {
            logConclusion.setText(log.statusMessage.isBlank() ? "" : "Log: " + log.statusMessage);
        }
        if (!log.matchedLines.isEmpty()) {
            logLines.setText(String.join("\n", log.matchedLines));
        } else {
            logLines.clear();
        }
    }

    private void clearLogPanel() {
        lastDisplayFailureReason = "";
        lastDiagnosis = null;
        diagnosisLabel.setText("");
        styleDiagnosis(null);
        logConclusion.setText("");
        logLines.clear();
    }

    private void applyDiagnosis(CallProblemDiagnosis diagnosis) {
        if (diagnosis == null || !diagnosis.isProblem()) {
            if (diagnosis != null && !diagnosis.summary.isBlank()) {
                diagnosisLabel.setText(diagnosis.originLabel + "\n" + diagnosis.summary);
            } else {
                diagnosisLabel.setText("");
            }
            styleDiagnosis(diagnosis);
            return;
        }
        diagnosisLabel.setText("Diagnóstico\n" + diagnosis.formattedBlock());
        styleDiagnosis(diagnosis);
    }

    private void styleDiagnosis(CallProblemDiagnosis diagnosis) {
        diagnosisLabel.getStyleClass().removeAll(
                "phone-trace-diagnosis-internal",
                "phone-trace-diagnosis-external",
                "phone-trace-diagnosis-mixed");
        if (diagnosis == null) {
            return;
        }
        switch (diagnosis.origin) {
            case INTERNAL -> diagnosisLabel.getStyleClass().add("phone-trace-diagnosis-internal");
            case EXTERNAL -> diagnosisLabel.getStyleClass().add("phone-trace-diagnosis-external");
            case MIXED -> diagnosisLabel.getStyleClass().add("phone-trace-diagnosis-mixed");
            default -> { }
        }
    }

    private void enrichAllRows(List<PhoneTraceCallRow> rows, int generation) {
        if (rows.isEmpty()) {
            return;
        }
        worker.execute(() -> {
            int total = rows.size();
            int done = 0;
            for (PhoneTraceCallRow row : rows) {
                if (generation != enrichGeneration) {
                    return;
                }
                try {
                    PhoneTraceRowSummary summary = service.loadRowSummary(row);
                    int completed = ++done;
                    Platform.runLater(() -> {
                        if (generation != enrichGeneration) {
                            return;
                        }
                        rowSummaryCache.put(rowKey(row), summary);
                        callsTable.refresh();
                        PhoneTraceCallRow selected = callsTable.getSelectionModel().getSelectedItem();
                        if (selected != null && rowKey(selected).equals(rowKey(row))) {
                            lastDisplayFailureReason = summary.displayFailureReason();
                            lastDiagnosis = summary.diagnosis();
                            showCallDetail(row);
                            if (traceTable.getItems().isEmpty()) {
                                applyDiagnosis(summary.diagnosis());
                            }
                        }
                        if (completed < total) {
                            status.setText(lastSearchStatus + " · motivos " + completed + "/" + total);
                        } else {
                            status.setText(lastSearchStatus);
                        }
                    });
                } catch (Exception ex) {
                    AppLogFile.appendLine("[phone-trace] enrich row " + row.callId + " | EN: "
                            + ex.getMessage());
                }
            }
        });
    }

    private static String rowKey(PhoneTraceCallRow row) {
        return row.direction.name() + ":" + row.callId;
    }

    private java.util.Optional<PhoneTraceRowSummary> summaryFor(PhoneTraceCallRow row) {
        if (row == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(rowSummaryCache.get(rowKey(row)));
    }

    private String displayMotiveFor(PhoneTraceCallRow row) {
        if (row == null) {
            return "";
        }
        return summaryFor(row)
                .map(PhoneTraceRowSummary::displayFailureReason)
                .filter(s -> !s.isBlank())
                .orElse(row.failureReason == null ? "" : row.failureReason);
    }

    private String diagnosisForRow(PhoneTraceCallRow r) {
        if (r == null) {
            return "";
        }
        return summaryFor(r).map(summary -> formatDiagnosisLabel(summary.diagnosis()))
                .orElseGet(() -> CallProblemDiagnosisService.quickHint(r));
    }

    private static String formatDiagnosisLabel(CallProblemDiagnosis diagnosis) {
        if (diagnosis == null) {
            return "";
        }
        if (diagnosis.isProblem()) {
            return diagnosis.originLabel.replace("Problema ", "")
                    + " · " + diagnosis.locationLabel;
        }
        return diagnosis.originLabel;
    }

    private String motiveForRow(PhoneTraceCallRow r) {
        return displayMotiveFor(r);
    }

    private static CallDirectionFilter mapDirection(String value) {
        if ("Salientes".equals(value)) {
            return CallDirectionFilter.OUTGOING;
        }
        if ("Entrantes".equals(value)) {
            return CallDirectionFilter.INCOMING;
        }
        return CallDirectionFilter.ALL;
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
