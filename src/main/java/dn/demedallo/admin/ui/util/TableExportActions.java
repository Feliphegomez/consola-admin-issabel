package dn.demedallo.admin.ui.util;

import dn.demedallo.admin.export.TableExportData;
import dn.demedallo.admin.export.TableExportService;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Export toolbar (CSV / XLSX / PDF) for JavaFX tables.
 */
public final class TableExportActions {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TableExportActions() {
    }

    public static HBox createExportBar(TableView<?> table, String exportBaseName) {
        String stem = sanitizeFileStem(exportBaseName);
        Button csv = exportButton("CSV", table, stem, TableExportService.Format.CSV);
        Button xlsx = exportButton("XLSX", table, stem, TableExportService.Format.XLSX);
        Button pdf = exportButton("PDF", table, stem, TableExportService.Format.PDF);
        HBox bar = new HBox(8, csv, xlsx, pdf);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("table-export-bar");
        return bar;
    }

    private static Button exportButton(String label, TableView<?> table, String stem,
            TableExportService.Format format) {
        Button btn = new Button(label);
        btn.getStyleClass().add("monitor-btn-small");
        btn.setOnAction(e -> exportTable(table, stem, format, btn));
        return btn;
    }

    private static void exportTable(TableView<?> table, String stem, TableExportService.Format format,
            Button source) {
        TableExportData data = TableExportService.snapshot(table, stem);
        if (data.isEmpty()) {
            showInfo(source, "No hay filas para exportar.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar " + format.name());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(format.name(), "*" + format.extension));
        chooser.setInitialFileName(stem + "_" + STAMP.format(LocalDateTime.now()) + format.extension);
        Window window = source.getScene() == null ? null : source.getScene().getWindow();
        java.io.File chosen = chooser.showSaveDialog(window);
        if (chosen == null) {
            return;
        }
        Path path = chosen.toPath();
        if (!path.toString().toLowerCase().endsWith(format.extension.toLowerCase())) {
            path = Path.of(path.toString() + format.extension);
        }
        try {
            TableExportService.write(path, data, format);
            showInfo(source, "Exportado: " + path.getFileName());
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Exportar");
            alert.setHeaderText("No se pudo guardar el archivo");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private static void showInfo(Button source, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Exportar");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static String sanitizeFileStem(String name) {
        if (name == null || name.isBlank()) {
            return "tabla";
        }
        String s = name.trim().toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-");
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        return s.isBlank() ? "tabla" : s;
    }
}
