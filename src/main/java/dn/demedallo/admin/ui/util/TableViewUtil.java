package dn.demedallo.admin.ui.util;

import dn.demedallo.admin.report.ReportTableData;
import dn.demedallo.admin.report.ReportTableFormatters;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Fixes JavaFX TableView column squashing when using CONSTRAINED_RESIZE_POLICY with dynamic columns.
 */
public final class TableViewUtil {

    private static final double MIN_COL = 72;
    private static final double MAX_COL = 480;
    private static final double CHAR_PX = 7.2;

    private TableViewUtil() {
    }

    public static void prepare(TableView<?> table) {
        if (!table.getStyleClass().contains("monitor-table")) {
            table.getStyleClass().add("monitor-table");
        }
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(32);
    }

    /**
     * Table inside scroll pane plus CSV / XLSX / PDF export buttons.
     */
    public static Parent wrapInScrollPane(TableView<?> table) {
        return wrapInScrollPane(table, "tabla");
    }

    /**
     * Wide tables use {@link TableView#UNCONSTRAINED_RESIZE_POLICY}; horizontal scroll must stay on the
     * TableView only — wrapping in ScrollPane causes duplicate horizontal scrollbars.
     */
    public static Parent wrapInScrollPane(TableView<?> table, String exportBaseName) {
        ScrollPane scroll = createScrollPane(table);
        VBox box = new VBox(6, TableExportActions.createExportBar(table, exportBaseName), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setMaxHeight(Double.MAX_VALUE);
        return box;
    }

    private static ScrollPane createScrollPane(TableView<?> table) {
        prepare(table);
        table.setMaxHeight(Double.MAX_VALUE);
        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(false);
        scroll.setMinViewportWidth(0);
        scroll.setMaxWidth(Double.MAX_VALUE);
        scroll.setMaxHeight(Double.MAX_VALUE);
        return scroll;
    }

    public static void styleColumn(TableColumn<?, ?> column, double prefWidth) {
        double w = Math.max(MIN_COL, prefWidth);
        column.setMinWidth(w);
        column.setPrefWidth(w);
        column.setMaxWidth(MAX_COL);
        column.setResizable(true);
        column.setStyle("-fx-alignment: CENTER-LEFT;");
    }

    public static void styleColumn(TableColumn<?, ?> column, String header) {
        styleColumn(column, widthForText(header));
    }

    public static void applyStringColumns(TableView<List<String>> table, ReportTableData data) {
        table.getColumns().clear();
        table.getItems().clear();
        ReportTableData display = ReportTableFormatters.forDisplay(data);
        List<String> headers = display.getColumns();
        List<List<String>> rows = display.getRows();
        double totalWidth = 24;
        for (int i = 0; i < headers.size(); i++) {
            final int idx = i;
            String title = headers.get(i);
            TableColumn<List<String>, String> col = new TableColumn<>(title);
            col.setCellValueFactory(cd -> {
                List<String> row = cd.getValue();
                if (row == null || idx >= row.size()) {
                    return new SimpleStringProperty("");
                }
                return new SimpleStringProperty(nullToEmpty(row.get(idx)));
            });
            double w = columnWidth(title, rows, idx);
            styleColumn(col, w);
            table.getColumns().add(col);
            totalWidth += w;
        }
        table.setItems(FXCollections.observableArrayList(rows));
        double w = Math.min(totalWidth, 4000);
        table.setPrefWidth(w);
        // Do not set minWidth — wide tables scroll inside ScrollPane without collapsing the nav split.
        table.setMinWidth(0);
    }

    private static double columnWidth(String header, List<List<String>> rows, int colIndex) {
        int maxChars = header == null ? 8 : header.length();
        int sample = Math.min(rows.size(), 80);
        for (int r = 0; r < sample; r++) {
            List<String> row = rows.get(r);
            if (row != null && colIndex < row.size()) {
                maxChars = Math.max(maxChars, nullToEmpty(row.get(colIndex)).length());
            }
        }
        return widthForText(maxChars);
    }

    private static double widthForText(String text) {
        int len = text == null ? 8 : text.length();
        return widthForText(len);
    }

    private static double widthForText(int charCount) {
        return Math.min(MAX_COL, Math.max(MIN_COL, charCount * CHAR_PX + 28));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
