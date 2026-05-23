package dn.demedallo.admin.ui.report;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Points to Monitoreo tab for rep_agents_monitoring. */
public final class LiveMonitorHintPane extends VBox {

    public LiveMonitorHintPane() {
        getStyleClass().add("report-pane");
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);
        setSpacing(12);
        Label t = new Label("Monitoreo de agentes");
        t.getStyleClass().add("login-title");
        Label body = new Label(
                "Este informe en la consola web usa ECCP en tiempo real con la misma información "
                        + "que la pestaña Monitoreo → Agentes (estado, llamada activa, pausas, métricas del día).\n\n"
                        + "Use la pestaña Monitoreo para ver y escuchar llamadas activas.");
        body.setWrapText(true);
        getChildren().addAll(t, body);
    }
}
