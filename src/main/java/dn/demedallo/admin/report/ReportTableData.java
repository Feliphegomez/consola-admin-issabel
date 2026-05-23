package dn.demedallo.admin.report;

import java.util.ArrayList;
import java.util.List;

public final class ReportTableData {

    private final List<String> columns;
    private final List<List<String>> rows;

    public ReportTableData(List<String> columns, List<List<String>> rows) {
        this.columns = List.copyOf(columns);
        this.rows = List.copyOf(rows);
    }

    public static ReportTableData empty(String message) {
        return new ReportTableData(List.of("Mensaje"), List.of(List.of(message)));
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public static ReportTableData fromResult(java.sql.ResultSet rs) throws java.sql.SQLException {
        var meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<String> headers = new ArrayList<>();
        for (int c = 1; c <= cols; c++) {
            headers.add(meta.getColumnLabel(c));
        }
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int c = 1; c <= cols; c++) {
                Object v = rs.getObject(c);
                row.add(v == null ? "" : String.valueOf(v));
            }
            rows.add(row);
        }
        return new ReportTableData(headers, rows);
    }
}
