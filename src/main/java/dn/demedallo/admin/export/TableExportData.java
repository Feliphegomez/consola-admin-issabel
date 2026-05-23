package dn.demedallo.admin.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabular snapshot for export (headers + cell text per row).
 */
public final class TableExportData {

    public final String title;
    public final List<String> headers;
    public final List<List<String>> rows;

    public TableExportData(String title, List<String> headers, List<List<String>> rows) {
        this.title = title == null || title.isBlank() ? "tabla" : title.trim();
        this.headers = List.copyOf(headers);
        this.rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return headers.isEmpty() && rows.isEmpty();
    }

    public static TableExportData empty(String title) {
        return new TableExportData(title, List.of(), List.of());
    }

    public static TableExportData of(String title, List<String> headers, List<List<String>> rows) {
        List<List<String>> normalized = new ArrayList<>();
        int cols = headers.size();
        for (List<String> row : rows) {
            List<String> line = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                line.add(c < row.size() && row.get(c) != null ? row.get(c) : "");
            }
            normalized.add(line);
        }
        return new TableExportData(title, headers, normalized);
    }
}
