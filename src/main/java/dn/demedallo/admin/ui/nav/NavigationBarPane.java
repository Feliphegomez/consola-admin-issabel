package dn.demedallo.admin.ui.nav;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * Top bar showing navigation trail: Inicio › Llamadas › Grabaciones
 */
public final class NavigationBarPane extends HBox {

  private final Label hint = new Label("Ubicación:");

  public NavigationBarPane(WorkspaceNavigation navigation) {
    getStyleClass().add("workspace-nav-bar");
    setAlignment(Pos.CENTER_LEFT);
    setPadding(new Insets(8, 12, 8, 12));
    hint.getStyleClass().add("workspace-nav-hint");
    getChildren().add(hint);
    navigation.addListener(this::render);
    render(navigation.trail());
  }

  private void render(List<WorkspaceNavigation.Crumb> trail) {
    getChildren().removeIf(n -> n != hint);
    if (trail == null || trail.isEmpty()) {
      Label empty = new Label("—");
      empty.getStyleClass().add("workspace-nav-current");
      getChildren().add(empty);
      return;
    }
    for (int i = 0; i < trail.size(); i++) {
      if (i > 0) {
        Label sep = new Label("›");
        sep.getStyleClass().add("workspace-nav-sep");
        getChildren().add(sep);
      }
      WorkspaceNavigation.Crumb crumb = trail.get(i);
      boolean last = i == trail.size() - 1;
      if (!last && crumb.clickable()) {
        Hyperlink link = new Hyperlink(crumb.label());
        link.getStyleClass().add("workspace-nav-link");
        link.setOnAction(e -> {
          if (crumb.onNavigate() != null) {
            crumb.onNavigate().run();
          }
        });
        getChildren().add(link);
      } else if (!last) {
        Label lbl = new Label(crumb.label());
        lbl.getStyleClass().add("workspace-nav-segment");
        getChildren().add(lbl);
      } else {
        Label current = new Label(crumb.label());
        current.getStyleClass().add("workspace-nav-current");
        getChildren().add(current);
      }
    }
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    getChildren().add(spacer);
  }
}
