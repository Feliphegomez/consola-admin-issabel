package dn.demedallo.admin.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports {@link TableExportData} to CSV, XLSX and PDF.
 */
public final class TableExportService {

    public enum Format {
        CSV(".csv"),
        XLSX(".xlsx"),
        PDF(".pdf");

        public final String extension;

        Format(String extension) {
            this.extension = extension;
        }
    }

    private TableExportService() {
    }

    public static TableExportData snapshot(TableView<?> table, String title) {
        if (table == null) {
            return TableExportData.empty(title);
        }
        List<TableColumn<?, ?>> columns = new ArrayList<>(table.getVisibleLeafColumns());
        if (columns.isEmpty()) {
            columns.addAll(table.getColumns());
        }
        List<String> headers = new ArrayList<>();
        for (TableColumn<?, ?> col : columns) {
            String text = col.getText();
            headers.add(text == null || text.isBlank() ? "Col" + (headers.size() + 1) : text);
        }
        List<List<String>> rows = new ArrayList<>();
        for (Object item : table.getItems()) {
            List<String> line = new ArrayList<>();
            for (TableColumn<?, ?> col : columns) {
                line.add(cellText(col, item));
            }
            rows.add(line);
        }
        return TableExportData.of(title, headers, rows);
    }

    private static String cellText(TableColumn<?, ?> column, Object row) {
        if (row == null || column == null) {
            return "";
        }
        @SuppressWarnings("unchecked")
        TableColumn<Object, ?> col = (TableColumn<Object, ?>) column;
        Object value = col.getCellData(row);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static void write(Path file, TableExportData data, Format format) throws IOException {
        switch (format) {
            case CSV -> writeCsv(file, data);
            case XLSX -> writeXlsx(file, data);
            case PDF -> writePdf(file, data);
        }
    }

    private static void writeCsv(Path file, TableExportData data) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('\ufeff');
            w.write(formatCsvLine(data.headers));
            w.newLine();
            for (List<String> row : data.rows) {
                w.write(formatCsvLine(row));
                w.newLine();
            }
        }
    }

    private static String formatCsvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(escapeCsv(cells.get(i)));
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        String s = value == null ? "" : value;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static void writeXlsx(Path file, TableExportData data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            String sheetName = sanitizeSheetName(data.title);
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < data.headers.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(data.headers.get(c));
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (List<String> rowData : data.rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < data.headers.size(); c++) {
                    row.createCell(c).setCellValue(c < rowData.size() ? rowData.get(c) : "");
                }
            }
            for (int c = 0; c < data.headers.size(); c++) {
                sheet.autoSizeColumn(c);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }
    }

    private static String sanitizeSheetName(String name) {
        String n = name.replaceAll("[\\\\/*?\\[\\]:]", "_");
        if (n.length() > 31) {
            n = n.substring(0, 31);
        }
        return n.isBlank() ? "Datos" : n;
    }

    private static void writePdf(Path file, TableExportData data) throws IOException {
        if (data.headers.isEmpty()) {
            try (Document doc = new Document(PageSize.A4)) {
                PdfWriter.getInstance(doc, Files.newOutputStream(file));
                doc.open();
                doc.add(new Paragraph("Sin datos para exportar."));
                doc.close();
            } catch (DocumentException e) {
                throw new IOException(e);
            }
            return;
        }
        int cols = data.headers.size();
        boolean landscape = cols > 5;
        try (Document doc = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 48, 36)) {
            PdfWriter.getInstance(doc, Files.newOutputStream(file));
            doc.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            doc.add(new Paragraph(data.title, titleFont));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(cols);
            table.setWidthPercentage(100f);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 7);
            for (String h : data.headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                cell.setPadding(4f);
                table.addCell(cell);
            }
            for (List<String> row : data.rows) {
                for (int c = 0; c < cols; c++) {
                    String text = c < row.size() ? row.get(c) : "";
                    PdfPCell cell = new PdfPCell(new Phrase(text, cellFont));
                    cell.setPadding(3f);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            doc.close();
        } catch (DocumentException e) {
            throw new IOException(e);
        }
    }
}
