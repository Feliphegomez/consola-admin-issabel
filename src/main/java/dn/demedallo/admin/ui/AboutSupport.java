package dn.demedallo.admin.ui;

import dn.demedallo.admin.branding.BrandingSupport;
import dn.demedallo.admin.update.UpdatePrompt;
import dn.demedallo.admin.util.ApplicationBuildInfo;
import javafx.application.HostServices;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * F1 About dialog and update check (same flow as native-agent-console).
 */
public final class AboutSupport {

    private AboutSupport() {
    }

    public static void registerF1(Scene scene, Stage owner, HostServices hostServices) {
        if (scene == null) {
            return;
        }
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F1),
                () -> showAbout(owner, hostServices));
    }

    public static void showAbout(Stage owner, HostServices hostServices) {
        var brand = BrandingSupport.get();
        String appName = brand.title().isEmpty() ? "Consola administración Issabel" : brand.title();
        String ver = ApplicationBuildInfo.version();
        String body = """
                Nombre de la aplicacion: %s
                Version instalada: %s
                Empresa: %s
                Creador: Andres Felipe Gomez Maya
                Origen: deMedallo.com
                Email / Contacto: fg@demedallo.com - 573170307812

                Accesos rapidos de teclado:
                - F1: Acerca de y comprobar actualizaciones
                """.formatted(appName, ver,
                brand.company().isEmpty() ? "deMedallo.com" : brand.company());
        Dialog<Void> d = new Dialog<>();
        if (owner != null) {
            d.initOwner(owner);
        }
        d.setTitle("Acerca de");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextArea area = new TextArea(body);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(14);
        area.setMaxWidth(Double.MAX_VALUE);
        Button btnCheck = new Button("Comprobar actualizaciones");
        btnCheck.setOnAction(e -> UpdatePrompt.checkManual(owner, hostServices));
        HBox row = new HBox(8, btnCheck);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox vb = new VBox(10, area, row);
        vb.setPrefWidth(520);
        d.getDialogPane().setContent(vb);
        d.getDialogPane().setHeaderText(appName);
        d.showAndWait();
    }
}
