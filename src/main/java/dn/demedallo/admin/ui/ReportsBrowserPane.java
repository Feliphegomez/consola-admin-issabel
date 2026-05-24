package dn.demedallo.admin.ui;

import dn.demedallo.admin.report.ReportContext;
import dn.demedallo.admin.report.ReportId;
import dn.demedallo.admin.ui.report.AgentDetailReportPane;
import dn.demedallo.admin.ui.report.FormDataViewerPane;
import dn.demedallo.admin.ui.report.HistoricalReportPane;
import dn.demedallo.admin.ui.report.LiveCampaignMonitorPane;
import dn.demedallo.admin.ui.report.LiveCampaignPanelPane;
import dn.demedallo.admin.ui.report.LiveIncomingQueuesPane;
import dn.demedallo.admin.ui.report.LiveOutgoingQueuesPane;
import dn.demedallo.admin.ui.report.LiveMonitorHintPane;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class ReportsBrowserPane extends BorderPane {

    private static final double NAV_MIN_WIDTH = 240;
    private static final double NAV_PREF_WIDTH = 280;
    private static final double DEFAULT_DIVIDER = 0.28;

    private final ReportContext ctx;
    private final StackPane content = new StackPane();
    private final List<AutoCloseable> openPanes = new ArrayList<>();
    private final ListView<ReportId> reportList;
    private final BorderPane navPane;
    private final BorderPane contentShell;
    private final Button toggleNavBtn;
    private final SplitPane split;

    public ReportsBrowserPane(ReportContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("monitor-root");
        setPadding(new Insets(8));

        reportList = new ListView<>(FXCollections.observableArrayList(ReportId.values()));
        reportList.getStyleClass().add("report-list");
        reportList.setMinWidth(NAV_MIN_WIDTH);
        reportList.setPrefWidth(NAV_PREF_WIDTH);
        reportList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ReportId item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String kind = item.kind == ReportId.ReportKind.LIVE_ECCP ? "[vivo]" : "[BD]";
                setText(kind + " " + item.title);
            }
        });
        reportList.getSelectionModel().selectedItemProperty()
                .addListener((o, old, selected) -> showReport(selected));

        Label navTitle = new Label("Informes");
        navTitle.getStyleClass().add("report-nav-title");
        navPane = new BorderPane();
        navPane.setTop(navTitle);
        navPane.setCenter(reportList);
        navPane.getStyleClass().add("report-nav-pane");
        navPane.setMinWidth(NAV_MIN_WIDTH);
        navPane.setPrefWidth(NAV_PREF_WIDTH);
        BorderPane.setAlignment(navTitle, Pos.CENTER_LEFT);
        BorderPane.setMargin(navTitle, new Insets(0, 4, 6, 4));
        BorderPane.setMargin(reportList, new Insets(0, 0, 0, 0));

        Label placeholder = new Label("Seleccione un informe de la lista.");
        placeholder.getStyleClass().add("report-placeholder");
        content.getChildren().add(placeholder);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);

        toggleNavBtn = new Button("Ocultar lista");
        toggleNavBtn.getStyleClass().addAll("monitor-btn-small", "report-nav-toggle");
        toggleNavBtn.setOnAction(e -> toggleNavPanel());

        contentShell = new BorderPane();
        contentShell.setCenter(content);
        HBox contentTop = new HBox(8, toggleNavBtn);
        contentTop.setAlignment(Pos.CENTER_LEFT);
        contentTop.setPadding(new Insets(0, 0, 6, 0));
        contentShell.setTop(contentTop);
        contentShell.setMinWidth(0);

        split = new SplitPane(navPane, contentShell);
        split.getStyleClass().add("report-split");
        split.setDividerPositions(DEFAULT_DIVIDER);
        SplitPane.setResizableWithParent(navPane, false);
        installDividerGuard(split);

        setCenter(split);
        reportList.getSelectionModel().select(ReportId.REP_AGENTS_MONITORING);
    }

    private void installDividerGuard(SplitPane pane) {
        Runnable enforceMin = () -> {
            if (pane.getWidth() <= 0 || !navPane.isManaged()) {
                return;
            }
            double minPos = Math.min(0.45, NAV_MIN_WIDTH / pane.getWidth());
            double[] pos = pane.getDividerPositions();
            if (pos.length > 0 && pos[0] < minPos) {
                pane.setDividerPositions(minPos);
            }
        };
        pane.widthProperty().addListener((obs, o, n) -> enforceMin.run());
        if (!pane.getDividers().isEmpty()) {
            pane.getDividers().getFirst().positionProperty().addListener((obs, o, n) -> enforceMin.run());
        }
    }

    private void toggleNavPanel() {
        boolean show = !navPane.isManaged();
        setNavVisible(show);
    }

    private void setNavVisible(boolean visible) {
        navPane.setManaged(visible);
        navPane.setVisible(visible);
        if (visible) {
            if (!split.getItems().contains(navPane)) {
                split.getItems().addFirst(navPane);
            }
            split.setDividerPositions(DEFAULT_DIVIDER);
            toggleNavBtn.setText("Ocultar lista");
        } else {
            split.getItems().remove(navPane);
            toggleNavBtn.setText("Mostrar lista de informes");
        }
    }

    private void showReport(ReportId id) {
        if (id == null) {
            return;
        }
        closeOpenPanes();
        content.getChildren().clear();
        javafx.scene.Node pane = buildPane(id);
        BorderPane.setAlignment(pane, Pos.TOP_LEFT);
        content.getChildren().add(pane);
    }

    private javafx.scene.Node buildPane(ReportId id) {
        return switch (id) {
            case REP_AGENTS_MONITORING -> new LiveMonitorHintPane();
            case REP_INCOMING_CALLS_MONITORING -> track(new LiveIncomingQueuesPane(ctx.eccp));
            case REP_OUTGOING_CALLS_MONITORING -> track(new LiveOutgoingQueuesPane(ctx.eccp));
            case REP_INCOMING_CAMPAIGNS_PANEL -> track(new LiveCampaignPanelPane(ctx.eccp, "incoming",
                    "Panel campañas entrantes"));
            case REP_OUTGOING_CAMPAIGNS_PANEL -> track(new LiveCampaignPanelPane(ctx.eccp, "outgoing",
                    "Panel campañas salientes"));
            case CAMPAIGN_MONITORING -> track(new LiveCampaignMonitorPane(ctx.eccp));
            case AGENT_DETAIL_REPORT -> track(new AgentDetailReportPane(ctx.dbReports, ctx.dbSettings.isConfigured()));
            case FORM_DATA_VIEWER -> track(new FormDataViewerPane(ctx.dbReports, ctx.dbSettings.isConfigured()));
            default -> track(new HistoricalReportPane(id, ctx.dbReports, ctx.dbSettings.isConfigured()));
        };
    }

    private <T extends javafx.scene.Node> T track(T node) {
        if (node instanceof AutoCloseable ac) {
            openPanes.add(ac);
        }
        return node;
    }

    private void closeOpenPanes() {
        for (AutoCloseable ac : openPanes) {
            try {
                ac.close();
            } catch (Exception ignored) {
            }
        }
        openPanes.clear();
    }

    public void shutdown() {
        closeOpenPanes();
    }
}
