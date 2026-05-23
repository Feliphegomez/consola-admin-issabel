package dn.demedallo.admin.ui;



import dn.demedallo.admin.branding.BrandingSupport;

import dn.demedallo.admin.protocol.AdminEccpClient;

import dn.demedallo.admin.update.UpdatePrompt;

import dn.demedallo.admin.util.AdminDbSettings;

import dn.demedallo.admin.util.AdminLoginPreferences;

import dn.demedallo.admin.util.AdminMonitorSettings;

import dn.demedallo.admin.util.AppLogFile;

import dn.demedallo.admin.util.ApplicationBuildInfo;

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

import javafx.scene.image.Image;

import javafx.scene.layout.BorderPane;

import javafx.scene.Node;

import javafx.scene.layout.ColumnConstraints;

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



    private AdminWorkspacePane workspacePane;

    private AdminEccpClient client;

    private Stage mainStage;



    @Override

    public void start(Stage stage) {

        this.mainStage = stage;

        var brand = BrandingSupport.get();

        stage.setTitle(brand.title().isEmpty() ? "Consola administración Issabel — ECCP" : brand.title() + " — ECCP");

        applyStageIcon(stage);

        showLogin(stage);

        stage.setMinWidth(960);

        stage.setMinHeight(600);

        stage.show();

        AppLogFile.appendLine("[app] version=" + ApplicationBuildInfo.version());

        UpdatePrompt.checkOnStartup(stage, getHostServices());

    }



    private static void applyStageIcon(Stage stage) {

        try (var iconIn = AdminConsoleApp.class.getResourceAsStream("/dn/demedallo/admin/app-icon.png")) {

            if (iconIn != null) {

                stage.getIcons().add(new Image(iconIn));

            }

        } catch (Exception ignored) {

        }

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

        CheckBox dbEnabled = new CheckBox("Informes históricos vía MySQL (call_center)");

        TextField dbHost = new TextField();

        TextField dbPort = new TextField();

        TextField dbName = new TextField();

        TextField dbUser = new TextField();

        PasswordField dbPass = new PasswordField();

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

        AdminDbSettings dbSettings = AdminDbSettings.load();

        dbEnabled.setSelected(dbSettings.dbEnabled);

        dbHost.setText(dbSettings.dbHost);

        dbPort.setText(String.valueOf(dbSettings.dbPort));

        dbName.setText(dbSettings.dbName);

        dbUser.setText(dbSettings.dbUser);

        dbPass.setText(dbSettings.dbPassword);

        dbHost.getStyleClass().add("login-field");

        dbPort.getStyleClass().add("login-field");

        dbName.getStyleClass().add("login-field");

        dbUser.getStyleClass().add("login-field");

        dbPass.getStyleClass().add("login-field");

        dbPort.setPromptText("3306");

        dbName.setPromptText("call_center");



        Label status = new Label("Listo.");

        status.getStyleClass().add("login-status");



        Button connect = new Button("Conectar");

        connect.getStyleClass().add("login-primary");



        GridPane eccpForm = newLoginFieldGrid();
        int eccpRow = 0;
        eccpRow = addLoginField(eccpForm, eccpRow, "Host ECCP", host);
        eccpRow = addLoginField(eccpForm, eccpRow, "Puerto", port);
        eccpRow = addLoginField(eccpForm, eccpRow, "Usuario ECCP", eccpUser);
        eccpRow = addLoginField(eccpForm, eccpRow, "Clave ECCP", eccpPass);
        eccpRow = addLoginFullWidth(eccpForm, eccpRow, remember);
        eccpRow = addLoginField(eccpForm, eccpRow, "Su extensión", supervisorExt);
        eccpRow = addLoginFullWidth(eccpForm, eccpRow, amiEnabled);
        eccpRow = addLoginField(eccpForm, eccpRow, "Puerto AMI", amiPort);
        eccpRow = addLoginField(eccpForm, eccpRow, "Usuario AMI", amiUser);
        eccpRow = addLoginField(eccpForm, eccpRow, "Clave AMI", amiSecret);
        eccpRow = addLoginField(eccpForm, eccpRow, "Tecnología canal", channelTech);
        addLoginField(eccpForm, eccpRow, "Código escucha", spyPrefix);

        GridPane dbForm = newLoginFieldGrid();
        int dbRow = 0;
        dbRow = addLoginFullWidth(dbForm, dbRow, dbEnabled);
        dbRow = addLoginField(dbForm, dbRow, "Host MySQL", dbHost);
        dbRow = addLoginField(dbForm, dbRow, "Puerto MySQL", dbPort);
        dbRow = addLoginField(dbForm, dbRow, "Base de datos", dbName);
        dbRow = addLoginField(dbForm, dbRow, "Usuario MySQL", dbUser);
        addLoginField(dbForm, dbRow, "Clave MySQL", dbPass);

        VBox leftCol = loginFormColumn("ECCP y escucha", eccpForm);
        VBox rightCol = loginFormColumn("MySQL (informes)", dbForm);
        HBox formColumns = new HBox(28, leftCol, rightCol);
        formColumns.getStyleClass().add("login-form-columns");
        HBox.setHgrow(leftCol, Priority.ALWAYS);
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        leftCol.setMaxWidth(Double.MAX_VALUE);
        rightCol.setMaxWidth(Double.MAX_VALUE);



        Label title = new Label("Monitoreo de agentes");

        title.getStyleClass().add("login-title");

        Label sub = new Label("ECCP supervisor + informes Issabel. Históricos: BD call_center (mismo DSN que módulos web).");

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

                    AdminDbSettings db = AdminDbSettings.load();

                    db.dbEnabled = dbEnabled.isSelected();

                    db.dbHost = dbHost.getText().trim().isEmpty() ? host.getText().trim() : dbHost.getText().trim();

                    try {

                        db.dbPort = Integer.parseInt(dbPort.getText().trim());

                    } catch (NumberFormatException ignored) {

                        db.dbPort = 3306;

                    }

                    db.dbName = dbName.getText().trim();

                    db.dbUser = dbUser.getText().trim();

                    if (!dbPass.getText().isEmpty()) {

                        db.dbPassword = dbPass.getText();

                    }

                    db.save();

                    Platform.runLater(() -> {
                        try {
                            appendLog(log, "Abriendo consola…");
                            openMonitor(stage, c, host.getText().trim(), ms, db);
                        } catch (Throwable openEx) {
                            String err = openEx.getMessage() != null ? openEx.getMessage() : openEx.toString();
                            AppLogFile.appendLine("[login] openMonitor | EN: " + openEx);
                            status.setText("Error: " + err);
                            appendLog(log, "ERROR abriendo consola: " + err);
                            connect.setDisable(false);
                        }
                    });

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



        VBox card = new VBox(10,

                BrandingSupport.createHeaderBar(),

                title,

                sub,

                formColumns,

                connect,

                status,

                log);

        card.getStyleClass().add("login-card");

        card.setPadding(new Insets(24));

        card.setMaxWidth(900);



        BorderPane root = new BorderPane(card);

        root.getStyleClass().add("login-root");

        BorderPane.setAlignment(card, Pos.CENTER);



        Scene scene = new Scene(root, 920, 680);

        var css = getClass().getResource("/css/issabel-admin.css");

        if (css != null) {

            scene.getStylesheets().add(css.toExternalForm());

        }

        AboutSupport.registerF1(scene, stage, getHostServices());

        stage.setScene(scene);

        stage.setOnCloseRequest(ev -> {

            if (client != null) {

                client.close();

            }

        });

        appendLog(log, "Versión instalada: " + ApplicationBuildInfo.version());

        appendLog(log, "Registro en disco: " + AppLogFile.directory().toAbsolutePath());

    }



    private void openMonitor(Stage stage, AdminEccpClient c, String hostLabel,

                             AdminMonitorSettings settings, AdminDbSettings dbSettings) {

        this.client = c;

        workspacePane = new AdminWorkspacePane(c, settings, dbSettings, hostLabel, msg -> Platform.runLater(() -> {

            if (workspacePane != null) {

                workspacePane.shutdown();

            }

            client = null;

            showLogin(stage);

        }));



        BorderPane root = new BorderPane(workspacePane);

        root.getStyleClass().add("app-root");

        var brand = BrandingSupport.get();

        String headerText = brand.company().isEmpty()

                ? "Consola administración — " + hostLabel

                : brand.company() + " — " + hostLabel;

        Label header = new Label(headerText);

        header.getStyleClass().add("app-header");

        root.setTop(header);

        BorderPane.setMargin(header, new Insets(8, 12, 0, 12));



        Scene scene = new Scene(root, 1100, 700);

        var css = getClass().getResource("/css/issabel-admin.css");

        if (css != null) {

            scene.getStylesheets().add(css.toExternalForm());

        }

        AboutSupport.registerF1(scene, stage, getHostServices());

        stage.setScene(scene);

        var b = BrandingSupport.get();

        stage.setTitle(b.title().isEmpty() ? "Consola administración Issabel" : b.title() + " — " + hostLabel);

    }



    private static GridPane newLoginFieldGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("login-form");
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(128);
        labels.setPrefWidth(128);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(180);
        grid.getColumnConstraints().addAll(labels, fields);
        return grid;
    }

    private static int addLoginField(GridPane grid, int row, String label, Node field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        return row + 1;
    }

    private static int addLoginFullWidth(GridPane grid, int row, Node node) {
        grid.add(node, 0, row, 2, 1);
        return row + 1;
    }

    private static VBox loginFormColumn(String sectionTitle, GridPane fields) {
        Label title = new Label(sectionTitle);
        title.getStyleClass().add("login-section-title");
        VBox box = new VBox(8, title, fields);
        box.getStyleClass().add("login-form-column");
        VBox.setVgrow(fields, Priority.NEVER);
        return box;
    }

    private static void appendLog(TextArea log, String line) {

        AppLogFile.appendLine(line);

        Platform.runLater(() -> log.appendText(line + "\n"));

    }



    public static void main(String[] args) {

        AppLogFile.appendLine("[app] start version=" + ApplicationBuildInfo.version());

        launch(args);

    }

}

