package dn.demedallo.admin.ui.report;

import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.ReadableTraceStepRow;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders call trace as a Mermaid flowchart in a {@link WebView} (read-only, bundled JS).
 */
public final class TraceMermaidPane extends BorderPane {

    private final WebView webView = new WebView();
    private final AtomicBoolean pageReady = new AtomicBoolean(false);
    private volatile String pendingDiagram;

    public TraceMermaidPane() {
        getStyleClass().add("trace-mermaid-pane");
        setMinWidth(260);
        setPrefWidth(340);

        Label title = new Label("Diagrama (Mermaid)");
        title.getStyleClass().add("panel-table-title");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(title, new Insets(0, 0, 4, 0));

        webView.setContextMenuEnabled(false);
        webView.setMaxWidth(Double.MAX_VALUE);
        webView.setMaxHeight(Double.MAX_VALUE);
        ScrollPane scroll = new ScrollPane(webView);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("trace-mermaid-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox box = new VBox(4, title, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        setCenter(box);

        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                pageReady.set(true);
                String pending = pendingDiagram;
                if (pending != null) {
                    executeRender(pending);
                }
            }
        });

        URL page = TraceMermaidPane.class.getResource("/admin/trace/mermaid-viewer.html");
        if (page != null) {
            engine.load(page.toExternalForm());
        }
    }

    public void clear() {
        render(null, List.of(), "", null);
    }

    public void render(PhoneTraceCallRow call, List<ReadableTraceStepRow> steps,
            String displayFailureReason, CallProblemDiagnosis diagnosis) {
        String diagram = call == null ? "" : TraceMermaidBuilder.build(
                call, steps, displayFailureReason, diagnosis);
        pendingDiagram = diagram;
        if (pageReady.get()) {
            executeRender(diagram);
        }
    }

    private void executeRender(String diagram) {
        Platform.runLater(() -> {
            if (!pageReady.get()) {
                return;
            }
            String js = "renderDiagram(" + toJsString(diagram) + ")";
            try {
                webView.getEngine().executeScript(js);
            } catch (Exception ignored) {
                // WebView not ready yet
            }
        });
    }

    private static String toJsString(String value) {
        if (value == null) {
            return "''";
        }
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        sb.append("'");
        return sb.toString();
    }
}
