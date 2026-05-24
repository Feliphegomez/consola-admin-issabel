package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.db.FailedShortCallsDao.CallDirectionFilter;
import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.CallRecordingFileRow;
import dn.demedallo.admin.model.CallWithRecordingsRow;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceDetail;
import dn.demedallo.admin.model.ReadableTraceStepRow;
import dn.demedallo.admin.service.CallProblemDiagnosisService;
import dn.demedallo.admin.service.CallRecordingService;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browse call recordings ({@code call_recording}) with full trace, play, download and delete.
 */
public final class CallRecordingsPane extends BorderPane implements AutoCloseable {

    private final CallRecordingService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "call-recordings");
        t.setDaemon(true);
        return t;
    });

    private final TextField phoneField = new TextField();
    private final DatePicker dateFrom = new DatePicker(LocalDate.now().minusDays(30));
    private final DatePicker dateTo = new DatePicker(LocalDate.now());
    private final ComboBox<String> directionFilter = new ComboBox<>();
    private final ComboBox<String> sourceFilter = new ComboBox<>();
    private final Label status = new Label();
    private final Label callDetail = new Label();
    private final Label diagnosisLabel = new Label();
    private final TextArea logLines = new TextArea();

    private final TableView<CallWithRecordingsRow> callsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<CallRecordingFileRow> filesTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<ReadableTraceStepRow> traceTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TraceMermaidPane mermaidPane = new TraceMermaidPane();

    private final Button playBtn = actionBtn("Escuchar");
    private final Button downloadBtn = actionBtn("Descargar");
    private final Button deleteBtn = actionBtn("Eliminar");

    private volatile int searchGeneration;
    private volatile int traceGeneration;
    private volatile PhoneTraceCallRow selectedCall;

    public CallRecordingsPane(AdminDbSettings dbSettings, String eccpHost) {
        this.service = new CallRecordingService(dbSettings, eccpHost);
        getStyleClass().add("call-recordings-pane");
        setPadding(new Insets(8));

        phoneField.setPromptText("Opcional — ej. 6045451116");
        phoneField.setPrefWidth(160);
        directionFilter.getItems().addAll("Todas", "Entrantes", "Salientes");
        directionFilter.setValue("Todas");
        sourceFilter.getItems().addAll(
                "CDR Issabel (admin web)",
                "Call center (campañas)",
                "Ambas fuentes");
        sourceFilter.setValue("CDR Issabel (admin web)");

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
                new Label("Fuente:"), sourceFilter,
                search);
        filters.setAlignment(Pos.CENTER_LEFT);

        buildCallsColumns();
        buildFilesColumns();
        buildTraceColumns();
        TableViewUtil.prepare(callsTable);
        TableViewUtil.prepare(filesTable);
        TableViewUtil.prepare(traceTable);

        callsTable.setPlaceholder(new Label("Busque llamadas con grabación en el rango indicado"));
        filesTable.setPlaceholder(new Label("Seleccione una llamada para ver sus archivos"));
        traceTable.setPlaceholder(new Label("Trazabilidad completa de la llamada seleccionada"));

        callsTable.getSelectionModel().selectedItemProperty().addListener((o, prev, row) -> {
            if (row != null) {
                selectedCall = row.call();
                showCallDetail(row);
                loadRecordings(row.call());
                loadTrace(row.call());
            } else {
                selectedCall = null;
                callDetail.setText("Seleccione una llamada.");
                filesTable.getItems().clear();
                traceTable.getItems().clear();
                mermaidPane.clear();
                diagnosisLabel.setText("");
                logLines.clear();
            }
            updateFileActions(null);
        });

        filesTable.getSelectionModel().selectedItemProperty().addListener((o, prev, file) ->
                updateFileActions(file));

        playBtn.setOnAction(e -> runFileAction("play"));
        downloadBtn.setOnAction(e -> runFileAction("download"));
        deleteBtn.getStyleClass().add("pbx-delete-btn");
        deleteBtn.setOnAction(e -> runFileAction("delete"));

        HBox fileActions = new HBox(8, playBtn, downloadBtn, deleteBtn);
        fileActions.setAlignment(Pos.CENTER_LEFT);
        fileActions.setPadding(new Insets(4, 0, 0, 0));

        VBox filesBox = new VBox(6,
                sectionTitle("Archivos de grabación"),
                fileActions,
                TableViewUtil.wrapInScrollPane(filesTable, "grabaciones-archivos"));
        VBox.setVgrow(filesTable, Priority.ALWAYS);

        VBox traceTableBox = new VBox(6,
                sectionTitle("Trazabilidad (paso a paso)"),
                TableViewUtil.wrapInScrollPane(traceTable, "grabaciones-trazabilidad"));
        VBox.setVgrow(traceTable, Priority.ALWAYS);

        SplitPane traceSplit = new SplitPane(traceTableBox, mermaidPane);
        traceSplit.setDividerPositions(0.55);
        SplitPane.setResizableWithParent(mermaidPane, true);

        SplitPane bottomSplit = new SplitPane(filesBox, traceSplit);
        bottomSplit.setDividerPositions(0.32);

        VBox callsBox = new VBox(6,
                sectionTitle("Llamadas con grabación"),
                TableViewUtil.wrapInScrollPane(callsTable, "grabaciones-llamadas"));
        VBox.setVgrow(callsTable, Priority.ALWAYS);

        SplitPane mainSplit = new SplitPane(callsBox, bottomSplit);
        mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.38);

        Label hint = new Label(
                "CDR Issabel = mismo listado que el admin web (BD asteriskcdrdb, tabla cdr). "
                        + "Call center = grabaciones de campañas (call_recording). "
                        + "SSH en Logs Issabel para escuchar/descargar WAV.");
        hint.setWrapText(true);
        hint.getStyleClass().add("panel-hint");

        callDetail.setWrapText(true);
        callDetail.getStyleClass().add("report-desc");
        diagnosisLabel.setWrapText(true);
        diagnosisLabel.getStyleClass().addAll("report-desc", "phone-trace-diagnosis");

        logLines.setEditable(false);
        logLines.setWrapText(true);
        logLines.setPrefRowCount(3);
        logLines.getStyleClass().addAll("login-log", "phone-trace-log-lines");

        status.getStyleClass().add("monitor-status");
        if (!service.isDbEnabled()) {
            status.setText("MySQL no configurado — active call_center en el login.");
        } else if (!service.isSshReady()) {
            status.setText("SSH no activo — configure en Logs Issabel para audio y eliminar archivos.");
        }

        VBox center = new VBox(8, hint, callDetail, diagnosisLabel, logLines, mainSplit);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        setTop(new VBox(8, filters, status));
        setCenter(center);

        updateFileActions(null);
    }

    private static Button actionBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("monitor-btn-small");
        return b;
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-table-title");
        return l;
    }

    private void buildCallsColumns() {
        addCol(callsTable, "Tipo", 72, r -> r.call().directionLabel());
        addCol(callsTable, "ID", 52, r -> String.valueOf(r.call().callId));
        addCol(callsTable, "Teléfono", 110, r -> r.call().phone);
        addCol(callsTable, "Campaña", 140, r -> r.call().campaignName);
        addCol(callsTable, "Estado", 100, r -> r.call().statusLabel);
        addCol(callsTable, "Fecha/hora", 140, r -> r.call().callDatetime);
        addCol(callsTable, "Duración", 72, r -> r.call().duration);
        addCol(callsTable, "Grab.", 52, r -> String.valueOf(r.recordingCount()));
        addCol(callsTable, "Agente", 70, r -> r.call().agent);
        addCol(callsTable, "Uniqueid", 120, r -> r.call().uniqueid);
    }

    private void buildFilesColumns() {
        addCol(filesTable, "ID", 52, r -> String.valueOf(r.id));
        addCol(filesTable, "Fecha", 140, r -> r.datetimeEntry);
        addCol(filesTable, "Archivo", 220, r -> r.displayName);
        addCol(filesTable, "Canal", 140, r -> r.channel);
        addCol(filesTable, "Uniqueid", 120, r -> r.uniqueid);
    }

    private void buildTraceColumns() {
        addCol(traceTable, "Paso", 44, r -> String.valueOf(r.step));
        TableColumn<ReadableTraceStepRow, String> summary = addCol(traceTable, "Qué ocurrió", 280,
                r -> r.summary);
        summary.setPrefWidth(280);
        addCol(traceTable, "Origen", 58, r -> CallProblemDiagnosisService.stepOriginLabel(
                selectedCall, r));
        addCol(traceTable, "Dónde", 140, r -> CallProblemDiagnosisService.stepWhereLabel(
                selectedCall, r));
        addCol(traceTable, "Fecha/hora", 130, r -> r.datetime);
        addCol(traceTable, "Estado", 96, r -> r.statusLabel);
        addCol(traceTable, "Agente", 70, r -> r.agent);
        addCol(traceTable, "Troncal", 90, r -> r.trunk);
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
            status.setText("MySQL no configurado.");
            return;
        }
        String digits = service.normalizePhone(phoneField.getText());
        if (!phoneField.getText().isBlank() && digits.length() < 4) {
            status.setText("Si filtra por teléfono, use al menos 4 dígitos.");
            return;
        }
        ShiftDatetimeRange range = ShiftDatetimeRange.ofDates(dateFrom.getValue(), dateTo.getValue());
        CallDirectionFilter filter = mapDirection(directionFilter.getValue());
        int gen = ++searchGeneration;
        traceGeneration = gen;
        status.setText("Buscando grabaciones…");
        worker.execute(() -> {
            try {
                List<CallWithRecordingsRow> rows = service.search(
                        phoneField.getText(), range, filter, mapSource(sourceFilter.getValue()));
                Platform.runLater(() -> {
                    if (gen != searchGeneration) {
                        return;
                    }
                    callsTable.getItems().setAll(rows);
                    filesTable.getItems().clear();
                    traceTable.getItems().clear();
                    mermaidPane.clear();
                    status.setText(rows.size() + " llamada(s) con grabación · " + range.indicatorText
                            + (service.isSshReady() ? "" : " · SSH inactivo (solo consulta BD)"));
                    if (!rows.isEmpty()) {
                        callsTable.getSelectionModel().selectFirst();
                    } else {
                        callDetail.setText("Sin resultados. Pruebe fuente «CDR Issabel» y fechas como en el admin web. "
                                + "Verifique Base CDR = asteriskcdrdb en login.");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void loadRecordings(PhoneTraceCallRow call) {
        worker.execute(() -> {
            try {
                List<CallRecordingFileRow> files = service.listFiles(call);
                Platform.runLater(() -> {
                    filesTable.getItems().setAll(files);
                    if (!files.isEmpty()) {
                        filesTable.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error cargando archivos: " + ex.getMessage()));
            }
        });
    }

    private void loadTrace(PhoneTraceCallRow row) {
        int gen = ++traceGeneration;
        traceTable.getItems().clear();
        mermaidPane.clear();
        diagnosisLabel.setText("Cargando trazabilidad…");
        logLines.clear();
        worker.execute(() -> {
            try {
                PhoneTraceDetail detail = service.loadTrace(row);
                Platform.runLater(() -> {
                    if (gen != traceGeneration) {
                        return;
                    }
                    traceTable.getItems().setAll(detail.steps);
                    applyDiagnosis(detail.diagnosis);
                    if (detail.logInvestigation.hasConclusion()) {
                        logLines.setText(detail.logInvestigation.sourceLabel + ": "
                                + detail.logInvestigation.conclusion);
                    } else {
                        logLines.clear();
                    }
                    mermaidPane.render(row, detail.steps, detail.displayFailureReason, detail.diagnosis);
                    traceTable.refresh();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (gen == traceGeneration) {
                        status.setText("Error trazabilidad: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void applyDiagnosis(CallProblemDiagnosis d) {
        if (d == null || !d.isProblem()) {
            diagnosisLabel.setText("");
            diagnosisLabel.getStyleClass().removeAll(
                    "phone-trace-diagnosis-internal", "phone-trace-diagnosis-external",
                    "phone-trace-diagnosis-mixed");
            return;
        }
        diagnosisLabel.setText(d.formattedBlock());
        diagnosisLabel.getStyleClass().removeAll(
                "phone-trace-diagnosis-internal", "phone-trace-diagnosis-external",
                "phone-trace-diagnosis-mixed");
        switch (d.origin) {
            case INTERNAL -> diagnosisLabel.getStyleClass().add("phone-trace-diagnosis-internal");
            case EXTERNAL -> diagnosisLabel.getStyleClass().add("phone-trace-diagnosis-external");
            default -> diagnosisLabel.getStyleClass().add("phone-trace-diagnosis-mixed");
        }
    }

    private void showCallDetail(CallWithRecordingsRow row) {
        PhoneTraceCallRow c = row.call();
        StringBuilder sb = new StringBuilder();
        sb.append(c.directionLabel()).append(" · ID ").append(c.callId)
                .append(" · ").append(c.phone)
                .append(" · ").append(c.statusLabel)
                .append(" · ").append(row.recordingCount()).append(" grabación(es)");
        if (!"-".equals(c.campaignName)) {
            sb.append(" · ").append(c.campaignName);
        }
        sb.append(" · ").append(c.callDatetime);
        if (c.failureReason != null && !c.failureReason.isBlank()) {
            sb.append("\nMotivo: ").append(c.failureReason);
        }
        callDetail.setText(sb.toString());
    }

    private void updateFileActions(CallRecordingFileRow file) {
        boolean ok = file != null && service.isSshReady();
        playBtn.setDisable(!ok);
        downloadBtn.setDisable(!ok);
        deleteBtn.setDisable(file == null);
    }

    private void runFileAction(String action) {
        CallRecordingFileRow file = filesTable.getSelectionModel().getSelectedItem();
        if (file == null) {
            return;
        }
        if (("play".equals(action) || "download".equals(action)) && !service.isSshReady()) {
            status.setText("Configure SSH en Logs Issabel para esta acción.");
            return;
        }
        if ("delete".equals(action)) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setHeaderText("Eliminar grabación");
            a.setContentText("¿Eliminar «" + file.displayName + "»?\n"
                    + "Se borrará el archivo en el servidor (si existe) y el registro en call_recording.");
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        if ("download".equals(action)) {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar grabación");
            fc.setInitialFileName(file.displayName);
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio", "*.wav", "*.WAV", "*.gsm", "*.mp3"));
            File target = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
            if (target == null) {
                return;
            }
            runAsync("Descargando…", () -> {
                service.downloadTo(file, target.toPath());
                return target.getAbsolutePath();
            }, path -> status.setText("Guardado en " + path));
            return;
        }
        runAsync(switch (action) {
            case "play" -> "Descargando para reproducir…";
            case "delete" -> "Eliminando…";
            default -> "Procesando…";
        }, () -> {
            if ("play".equals(action)) {
                Path local = service.downloadToTemp(file);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(local.toFile());
                } else {
                    throw new IllegalStateException("Desktop no disponible para abrir el audio");
                }
                return local.toString();
            }
            service.deleteRecording(selectedCall, file);
            return file.displayName;
        }, result -> {
            if ("delete".equals(action)) {
                status.setText("Grabación eliminada: " + result);
                CallWithRecordingsRow callRow = callsTable.getSelectionModel().getSelectedItem();
                if (callRow != null) {
                    loadRecordings(callRow.call());
                    runSearch();
                }
            } else if ("play".equals(action)) {
                status.setText("Reproduciendo (reproductor del sistema): " + file.displayName);
            }
        });
    }

    private interface AsyncResult {
        void onSuccess(String result);
    }

    private void runAsync(String busyMsg, ThrowingSupplier supplier, AsyncResult onDone) {
        status.setText(busyMsg);
        worker.execute(() -> {
            try {
                String result = supplier.get();
                Platform.runLater(() -> onDone.onSuccess(result));
            } catch (Exception ex) {
                AppLogFile.appendLine("[recordings] " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }

    private static CallRecordingService.RecordingSource mapSource(String value) {
        if (value != null && value.startsWith("Call center")) {
            return CallRecordingService.RecordingSource.CALL_CENTER;
        }
        if (value != null && value.startsWith("Ambas")) {
            return CallRecordingService.RecordingSource.BOTH;
        }
        return CallRecordingService.RecordingSource.CDR;
    }

    private static CallDirectionFilter mapDirection(String value) {
        if ("Entrantes".equals(value)) {
            return CallDirectionFilter.INCOMING;
        }
        if ("Salientes".equals(value)) {
            return CallDirectionFilter.OUTGOING;
        }
        return CallDirectionFilter.ALL;
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
