package dn.demedallo.admin.ui;

import dn.demedallo.admin.service.LocalLogTailService;
import dn.demedallo.admin.service.RemoteLogTailService;
import dn.demedallo.admin.util.AdminLogSettings;
import dn.demedallo.admin.util.AppLogFile;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tab to tail Issabel dialer and Asterisk logs on the ECCP server (SSH) plus local app log.
 */
public final class IssabelLogsPane extends BorderPane implements AutoCloseable {

    private final String serverHost;
    private final AdminLogSettings settings;
    private final RemoteLogTailService remoteTail = new RemoteLogTailService();
    private final LocalLogTailService localTail = new LocalLogTailService();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "issabel-logs");
        t.setDaemon(true);
        return t;
    });

    private final TextField sshHost = new TextField();
    private final Spinner<Integer> sshPort = new Spinner<>(1, 65535, 22);
    private final TextField sshUser = new TextField();
    private final PasswordField sshPass = new PasswordField();
    private final CheckBox sshEnabled = new CheckBox("SSH activo (logs en servidor Issabel)");
    private final TextField dialerPath = new TextField(AdminLogSettings.DEFAULT_DIALER_LOG);
    private final TextField asteriskFullPath = new TextField(AdminLogSettings.DEFAULT_ASTERISK_FULL);
    private final TextField asteriskMessagesPath = new TextField(AdminLogSettings.DEFAULT_ASTERISK_MESSAGES);
    private final Spinner<Integer> tailLines = new Spinner<>(50, 5000, 800);
    private final Spinner<Integer> refreshSec = new Spinner<>(2, 60, 4);
    private final TextField grepFilter = new TextField();
    private final CheckBox autoRefresh = new CheckBox("Auto-actualizar");
    private final Label status = new Label();

    private final TextArea dialerLog = logArea();
    private final TextArea asteriskFullLog = logArea();
    private final TextArea asteriskMessagesLog = logArea();
    private final TextArea localAppLog = logArea();

    private Timeline pollTimeline;
    private final AtomicReference<String> activeSource = new AtomicReference<>("dialer");

    public IssabelLogsPane(String serverHost) {
        this.serverHost = serverHost == null ? "" : serverHost.trim();
        this.settings = AdminLogSettings.load();
        getStyleClass().add("logs-pane");
        setPadding(new Insets(10));
        loadSettingsIntoForm();

        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refreshActive());

        Button saveCfg = new Button("Guardar configuración");
        saveCfg.getStyleClass().add("monitor-btn-small");
        saveCfg.setOnAction(e -> saveSettingsFromForm());

        grepFilter.setPromptText("Filtro (regex, opcional)");
        grepFilter.setPrefWidth(200);
        tailLines.setEditable(true);
        refreshSec.setEditable(true);
        sshHost.setPromptText("IP del servidor Issabel (ECCP)");
        sshHost.setText(this.serverHost);

        HBox actions = new HBox(10, refresh, saveCfg, autoRefresh,
                new Label("Cada (s):"), refreshSec,
                new Label("Líneas:"), tailLines,
                grepFilter);
        actions.setAlignment(Pos.CENTER_LEFT);

        GridPane sshForm = new GridPane();
        sshForm.setHgap(8);
        sshForm.setVgap(6);
        int r = 0;
        sshForm.add(sshEnabled, 0, r++, 2, 1);
        sshForm.add(new Label("Host SSH"), 0, r);
        sshForm.add(sshHost, 1, r++);
        sshForm.add(new Label("Puerto"), 0, r);
        sshForm.add(sshPort, 1, r++);
        sshForm.add(new Label("Usuario"), 0, r);
        sshForm.add(sshUser, 1, r++);
        sshForm.add(new Label("Clave SSH"), 0, r);
        sshForm.add(sshPass, 1, r++);
        sshForm.add(new Label("Dialer"), 0, r);
        sshForm.add(dialerPath, 1, r++);
        sshForm.add(new Label("Asterisk full"), 0, r);
        sshForm.add(asteriskFullPath, 1, r++);
        sshForm.add(new Label("Asterisk messages"), 0, r);
        sshForm.add(asteriskMessagesPath, 1, r++);
        GridPane.setHgrow(sshHost, Priority.ALWAYS);
        GridPane.setHgrow(dialerPath, Priority.ALWAYS);
        GridPane.setHgrow(asteriskFullPath, Priority.ALWAYS);
        GridPane.setHgrow(asteriskMessagesPath, Priority.ALWAYS);
        sshForm.getStyleClass().add("logs-config");

        TabPane logTabs = new TabPane();
        logTabs.getStyleClass().add("monitor-tabs");
        Tab tDialer = new Tab("Dialer (dialerd.log)", wrapLog(dialerLog));
        Tab tFull = new Tab("Asterisk (full)", wrapLog(asteriskFullLog));
        Tab tMsg = new Tab("Asterisk (messages)", wrapLog(asteriskMessagesLog));
        Tab tLocal = new Tab("Consola admin (local)", wrapLog(localAppLog));
        logTabs.getTabs().addAll(tDialer, tFull, tMsg, tLocal);
        logTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        logTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldT, tab) -> {
            if (tab == tDialer) {
                activeSource.set("dialer");
            } else if (tab == tFull) {
                activeSource.set("asterisk-full");
            } else if (tab == tMsg) {
                activeSource.set("asterisk-messages");
            } else {
                activeSource.set("local");
            }
            refreshActive();
        });

        status.getStyleClass().add("monitor-status");
        status.setWrapText(true);

        VBox top = new VBox(8, actions, sshForm, status);
        setTop(top);
        setCenter(logTabs);

        configurePolling();
        refreshActive();
    }

    private static BorderPane wrapLog(TextArea area) {
        BorderPane p = new BorderPane(area);
        BorderPane.setMargin(area, Insets.EMPTY);
        return p;
    }

    private static TextArea logArea() {
        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.getStyleClass().addAll("login-log", "log-viewer");
        return ta;
    }

    private void loadSettingsIntoForm() {
        sshEnabled.setSelected(settings.sshEnabled);
        sshPort.getValueFactory().setValue(settings.sshPort);
        sshUser.setText(settings.sshUser);
        sshPass.setText(settings.sshPassword);
        dialerPath.setText(settings.dialerLogPath);
        asteriskFullPath.setText(settings.asteriskFullPath);
        asteriskMessagesPath.setText(settings.asteriskMessagesPath);
        tailLines.getValueFactory().setValue(settings.tailLines);
        refreshSec.getValueFactory().setValue(settings.refreshSeconds);
        grepFilter.setText(settings.grepFilter);
        autoRefresh.setSelected(settings.autoRefresh);
    }

    private void saveSettingsFromForm() {
        settings.sshEnabled = sshEnabled.isSelected();
        settings.sshPort = sshPort.getValue();
        settings.sshUser = sshUser.getText().trim();
        if (!sshPass.getText().isEmpty()) {
            settings.sshPassword = sshPass.getText();
        }
        settings.dialerLogPath = dialerPath.getText().trim();
        settings.asteriskFullPath = asteriskFullPath.getText().trim();
        settings.asteriskMessagesPath = asteriskMessagesPath.getText().trim();
        settings.tailLines = tailLines.getValue();
        settings.refreshSeconds = refreshSec.getValue();
        settings.grepFilter = grepFilter.getText().trim();
        settings.autoRefresh = autoRefresh.isSelected();
        settings.save();
        status.setText("Configuración guardada.");
        configurePolling();
    }

    private void configurePolling() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        if (!autoRefresh.isSelected()) {
            return;
        }
        int sec = Math.max(2, refreshSec.getValue());
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(sec), e -> refreshActive()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    private void refreshActive() {
        String src = activeSource.get();
        status.setText("Cargando " + src + "…");
        int lines = tailLines.getValue();
        String grep = grepFilter.getText().trim();
        worker.execute(() -> {
            try {
                String text = loadSource(src, lines);
                text = RemoteLogTailService.applyGrepFilter(text, grep);
                String finalText = text.isEmpty() ? "(sin líneas)" : text;
                Platform.runLater(() -> {
                    TextArea target = areaForSource(src);
                    target.setText(finalText);
                    target.positionCaret(finalText.length());
                    target.setScrollTop(Double.MAX_VALUE);
                    status.setText("OK · " + src + " · " + countLines(finalText) + " líneas · "
                            + java.time.LocalTime.now().withNano(0));
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[logs] " + src + ": " + ex.getMessage());
                Platform.runLater(() -> status.setText("Error (" + src + "): " + ex.getMessage()));
            }
        });
    }

    private String loadSource(String src, int lines) throws Exception {
        if ("local".equals(src)) {
            Path latest = LocalLogTailService.latestAdminLog(AppLogFile.directory());
            return localTail.tail(latest, lines);
        }
        if (!sshEnabled.isSelected()) {
            throw new IllegalStateException("Active SSH y guarde usuario/clave para leer logs del servidor.");
        }
        String host = sshHost.getText().trim();
        String user = sshUser.getText().trim();
        String pass = sshPass.getText();
        if (pass.isEmpty()) {
            pass = settings.sshPassword;
        }
        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            throw new IllegalStateException("Complete host, usuario y clave SSH.");
        }
        int port = sshPort.getValue();
        String path = switch (src) {
            case "dialer" -> dialerPath.getText().trim();
            case "asterisk-full" -> asteriskFullPath.getText().trim();
            case "asterisk-messages" -> asteriskMessagesPath.getText().trim();
            default -> dialerPath.getText().trim();
        };
        return remoteTail.tail(host, port, user, pass, path, lines);
    }

    private TextArea areaForSource(String src) {
        return switch (src) {
            case "asterisk-full" -> asteriskFullLog;
            case "asterisk-messages" -> asteriskMessagesLog;
            case "local" -> localAppLog;
            default -> dialerLog;
        };
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n", -1).length;
    }

    public void shutdown() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        worker.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
