package dn.demedallo.admin.ui;

import dn.demedallo.admin.ui.nav.WorkspaceNavigation;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sidebar navigation with a single content area (same pattern as Informes).
 */
public class NavSectionPane extends BorderPane {

    private static final double NAV_MIN_WIDTH = 200;
    private static final double NAV_PREF_WIDTH = 220;
    private static final double DEFAULT_DIVIDER = 0.22;

    public record NavItem(String id, String label, String hint) {
    }

    private final StackPane content = new StackPane();
    private final ListView<NavItem> navList;
    private final BorderPane navPane;
    private final SplitPane split;
    private final Button toggleNavBtn;
    private final Map<String, Supplier<Node>> factories = new LinkedHashMap<>();
    private final Map<String, Node> cache = new LinkedHashMap<>();
    private final List<AutoCloseable> openClosables = new ArrayList<>();
    private String activeId;
    private WorkspaceNavigation navigation;
    private String mainTabLabel;
    private Runnable selectMainTab;
    private Consumer<NavItem> onItemShown;

    public NavSectionPane(String sectionTitle, List<NavItem> items) {
        getStyleClass().add("nav-section-pane");
        setPadding(new Insets(8));

        navList = new ListView<>(FXCollections.observableArrayList(items));
        navList.getStyleClass().add("section-nav-list");
        navList.setMinWidth(NAV_MIN_WIDTH);
        navList.setPrefWidth(NAV_PREF_WIDTH);
        navList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(NavItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item.label());
                if (item.hint() != null && !item.hint().isBlank()) {
                    setTooltip(new Tooltip(item.hint()));
                }
            }
        });
        navList.getSelectionModel().selectedItemProperty()
                .addListener((o, old, selected) -> showItem(selected));

        Label navTitle = new Label(sectionTitle);
        navTitle.getStyleClass().add("section-nav-title");
        navPane = new BorderPane();
        navPane.setTop(navTitle);
        navPane.setCenter(navList);
        navPane.getStyleClass().add("section-nav-pane");
        navPane.setMinWidth(NAV_MIN_WIDTH);
        navPane.setPrefWidth(NAV_PREF_WIDTH);
        BorderPane.setAlignment(navTitle, Pos.CENTER_LEFT);
        BorderPane.setMargin(navTitle, new Insets(0, 4, 6, 4));

        Label placeholder = new Label("Seleccione una opción del menú.");
        placeholder.getStyleClass().add("report-placeholder");
        content.getChildren().add(placeholder);

        toggleNavBtn = new Button("Ocultar menú");
        toggleNavBtn.getStyleClass().addAll("monitor-btn-small", "report-nav-toggle");
        toggleNavBtn.setOnAction(e -> toggleNav());

        BorderPane contentShell = new BorderPane();
        contentShell.setCenter(content);
        HBox top = new HBox(8, toggleNavBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 6, 0));
        contentShell.setTop(top);

        split = new SplitPane(navPane, contentShell);
        split.getStyleClass().add("report-split");
        split.setDividerPositions(DEFAULT_DIVIDER);
        SplitPane.setResizableWithParent(navPane, false);
        installDividerGuard(split);

        setCenter(split);
        if (!items.isEmpty()) {
            navList.getSelectionModel().selectFirst();
        }
    }

    public void register(String id, Supplier<Node> factory) {
        factories.put(id, factory);
    }

    /** Binds top navigation bar to this section (main tab + sidebar item). */
    public void bindNavigation(WorkspaceNavigation nav, String mainTabLabel, Runnable selectMainTab) {
        this.navigation = nav;
        this.mainTabLabel = mainTabLabel;
        this.selectMainTab = selectMainTab;
        navList.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> publishTrail(item));
        publishTrail(navList.getSelectionModel().getSelectedItem());
    }

    public void setOnItemShown(Consumer<NavItem> handler) {
        this.onItemShown = handler;
    }

    public void selectItemById(String id) {
        for (NavItem item : navList.getItems()) {
            if (item.id().equals(id)) {
                navList.getSelectionModel().select(item);
                return;
            }
        }
    }

    public NavItem getSelectedItem() {
        return navList.getSelectionModel().getSelectedItem();
    }

    public void publishTrailForSelection() {
        publishTrail(navList.getSelectionModel().getSelectedItem());
    }

    private void publishTrail(NavItem item) {
        if (navigation == null || mainTabLabel == null || item == null) {
            return;
        }
        navigation.setMainAndSub(mainTabLabel, selectMainTab, item.label());
    }

    private void showItem(NavItem item) {
        if (item == null) {
            return;
        }
        Supplier<Node> factory = factories.get(item.id());
        if (factory == null) {
            return;
        }
        if (item.id().equals(activeId)) {
            return;
        }
        content.getChildren().clear();
        Node pane = cache.computeIfAbsent(item.id(), k -> {
            Node n = factory.get();
            if (n instanceof AutoCloseable ac) {
                openClosables.add(ac);
            }
            return n;
        });
        content.getChildren().add(pane);
        activeId = item.id();
        publishTrail(item);
        if (onItemShown != null) {
            onItemShown.accept(item);
        }
    }

    private void toggleNav() {
        boolean show = !navPane.isManaged();
        navPane.setManaged(show);
        navPane.setVisible(show);
        if (show) {
            if (!split.getItems().contains(navPane)) {
                split.getItems().addFirst(navPane);
            }
            split.setDividerPositions(DEFAULT_DIVIDER);
            toggleNavBtn.setText("Ocultar menú");
        } else {
            split.getItems().remove(navPane);
            toggleNavBtn.setText("Mostrar menú");
        }
    }

    private static void installDividerGuard(SplitPane pane) {
        Runnable enforceMin = () -> {
            if (pane.getWidth() <= 0 || pane.getItems().isEmpty()) {
                return;
            }
            double minPos = Math.min(0.4, NAV_MIN_WIDTH / pane.getWidth());
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

    public void shutdown() {
        closeClosables();
        cache.clear();
    }

    private void closeClosables() {
        for (AutoCloseable ac : openClosables) {
            try {
                ac.close();
            } catch (Exception ignored) {
            }
        }
        openClosables.clear();
    }
}
