package dn.demedallo.admin.ui;

import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminLoginPreferences;
import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.util.AppLogFile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Issabel admin / supervisor console: ECCP login and agent monitoring dashboard.
 */
public class AdminConsoleApp extends Application {

    private static final int CONNECT_MS = 8000;
    private static final int READ_MS = 90000;

    private AdminMonitorPane monitorPane;
    private AdminEccpClient client;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Consola administración Issabel — ECCP");
        showLogin(stage);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.show();
    }

    private void showLogin(Stage stage) {
        TextField host = new TextField();
        TextField port = new TextField();
        TextField eccpUser = new TextField();
        PasswordField eccpPass = new PasswordField();
        CheckBox remember = new CheckBox("Recordar contraseña ECCP en este equipo");
        TextField supervisorExt = new TextField();
        CheckBox amiEnabled = new CheckBox("Escucha automática vía AMI (marca su extensión al pulsar Escuchar)");
        TextField amiPort = new TextField();
        TextField amiUser = new TextField();
        PasswordField amiSecret = new PasswordField();
        TextField channelTech = new TextField();
        TextField spyPrefix = new TextField();
        TextArea log = new TextArea();
        log.setEditable(false);
        log.setWrapText(true);
        VBox.setVgrow(log, Priority.ALWAYS);

        host.getStyleClass().add("login-field");
        port.getStyleClass().add("login-field");
        eccpUser.getStyleClass().add("login-field");
        eccpPass.getStyleClass().add("login-field");
        supervisorExt.getStyleClass().add("login-field");
        amiPort.getStyleClass().add("login-field");
        amiUser.getStyleClass().add("login-field");
        amiSecret.getStyleClass().add("login-field");

        AdminLoginPreferences.loadInto(host, port, eccpUser, eccpPass, remember);
        AdminMonitorSettings monitorSettings = AdminMonitorSettings.load();
        supervisorExt.setText(monitorSettings.supervisorExtension);
        amiEnabled.setSelected(monitorSettings.amiEnabled);
        amiPort.setText(String.valueOf(monitorSettings.amiPort));
        amiUser.setText(monitorSettings.amiUser);
        amiSecret.setText(monitorSettings.amiSecret);
        channelTech.setText(monitorSettings.channelTech);
        spyPrefix.setText(monitorSettings.spyPrefix);
        amiPort.setPromptText("5038");
        supervisorExt.setPromptText("Ej: 8003 — su extensión física o softphone");
        amiSecret.setPromptText("AMI secret (manager.conf)");
        channelTech.setPromptText("PJSIP o SIP (si no se detecta del canal)");
        spyPrefix.setPromptText("555 — código escucha + extensión agente (sin *)");
        spyPrefix.getStyleClass().add("login-field");
        if (monitorSettings.isListenConfigured()) {
            amiEnabled.setSelected(true);
        }

        Label status = new Label("Listo.");
        status.getStyleClass().add("login-status");

        Button connect = new Button("Conectar");
        connect.getStyleClass().add("login-primary");

        GridPane form = new GridPane();
        form.getStyleClass().add("login-form");
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.add(new Label("Host ECCP"), 0, row);
        form.add(host, 1, row++);
        form.add(new Label("Puerto"), 0, row);
        form.add(port, 1, row++);
        form.add(new Label("Usuario ECCP"), 0, row);
        form.add(eccpUser, 1, row++);
        form.add(new Label("Clave ECCP"), 0, row);
        form.add(eccpPass, 1, row++);
        form.add(remember, 0, row++, 2, 1);
        form.add(new Label("Su extensión"), 0, row);
        form.add(supervisorExt, 1, row++);
        form.add(amiEnabled, 0, row++, 2, 1);
        form.add(new Label("Puerto AMI"), 0, row);
        form.add(amiPort, 1, row++);
        form.add(new Label("Usuario AMI"), 0, row);
        form.add(amiUser, 1, row++);
        form.add(new Label("Clave AMI"), 0, row);
        form.add(amiSecret, 1, row++);
        form.add(new Label("Tecnología canal"), 0, row);
        form.add(channelTech, 1, row++);
        form.add(new Label("Código escucha"), 0, row);
        form.add(spyPrefix, 1, row++);
        GridPane.setHgrow(host, Priority.ALWAYS);
        GridPane.setHgrow(port, Priority.ALWAYS);
        GridPane.setHgrow(eccpUser, Priority.ALWAYS);
        GridPane.setHgrow(eccpPass, Priority.ALWAYS);

        Label title = new Label("Monitoreo de agentes");
        title.getStyleClass().add("login-title");
        Label sub = new Label("ECCP de supervisor. Para escuchar llamadas: su extensión + AMI (Issabel manager.conf).");
        sub.getStyleClass().add("login-sub");
        sub.setWrapText(true);

        connect.setOnAction(e -> {
            connect.setDisable(true);
            status.setText("Conectando…");
            log.clear();
            appendLog(log, "Conectando a " + host.getText().trim() + "…");

            Thread t = new Thread(() -> {
                try {
                    int p = Integer.parseInt(port.getText().trim());
                    AdminEccpClient c = new AdminEccpClient();
                    c.connect(host.getText().trim(), p, CONNECT_MS, READ_MS);
                    appendLog(log, "TCP OK. Login ECCP…");
                    c.login(eccpUser.getText().trim(), eccpPass.getText());
                    appendLog(log, "Login ECCP OK.");
                    AdminLoginPreferences.saveFrom(host, port, eccpUser, eccpPass, remember);
                    AdminMonitorSettings ms = AdminMonitorSettings.load();
                    ms.supervisorExtension = supervisorExt.getText().trim();
                    ms.amiEnabled = amiEnabled.isSelected();
                    ms.amiHost = host.getText().trim();
                    try {
                        ms.amiPort = Integer.parseInt(amiPort.getText().trim());
                    } catch (NumberFormatException ignored) {
                        ms.amiPort = 5038;
                    }
                    ms.amiUser = amiUser.getText().trim();
                    if (!amiSecret.getText().isEmpty()) {
                        ms.amiSecret = amiSecret.getText();
                    }
                    String tech = channelTech.getText().trim();
                    ms.channelTech = tech.isEmpty() ? "PJSIP" : tech;
                    ms.spyPrefix = spyPrefix.getText().trim();
                    ms.save();
                    Platform.runLater(() -> openMonitor(stage, c, host.getText().trim(), ms));
                } catch (Exception ex) {
                    AppLogFile.appendLine("[login] " + ex.getMessage());
                    Platform.runLater(() -> {
                        status.setText("Error: " + ex.getMessage());
                        appendLog(log, "ERROR: " + ex.getMessage());
                        connect.setDisable(false);
                    });
                }
            }, "admin-login");
            t.setDaemon(true);
            t.start();
        });

        VBox card = new VBox(10, title, sub, form, connect, status, log);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(24));
        card.setMaxWidth(560);

        BorderPane root = new BorderPane(card);
        root.getStyleClass().add("login-root");
        BorderPane.setAlignment(card, Pos.CENTER);

        Scene scene = new Scene(root, 640, 620);
        var css = getClass().getResource("/css/issabel-admin.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(ev -> {
            if (client != null) {
                client.close();
            }
        });
    }

    private void openMonitor(Stage stage, AdminEccpClient c, String hostLabel, AdminMonitorSettings settings) {
        this.client = c;
        monitorPane = new AdminMonitorPane(c, settings, msg -> Platform.runLater(() -> {
            if (monitorPane != null) {
                monitorPane.shutdown();
            }
            client = null;
            showLogin(stage);
        }));

        BorderPane root = new BorderPane(monitorPane);
        root.getStyleClass().add("app-root");
        Label header = new Label("Monitoreo de agentes — " + hostLabel);
        header.getStyleClass().add("app-header");
        root.setTop(header);
        BorderPane.setMargin(header, new Insets(8, 12, 0, 12));

        Scene scene = new Scene(root, 1100, 700);
        var css = getClass().getResource("/css/issabel-admin.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
    }

    private static void appendLog(TextArea log, String line) {
        Platform.runLater(() -> log.appendText(line + "\n"));
    }

    public static void main(String[] args) {
        AppLogFile.appendLine("[app] start");
        launch(args);
    }
}
