package dn.demedallo.admin.report;

import dn.demedallo.admin.i18n.AdminLabels;

import java.util.ArrayList;
import java.util.List;

/** Post-process SQL report rows for display (seconds → days H:mm:ss, etc.). */
public final class ReportTableFormatters {

    private ReportTableFormatters() {
    }

    public static ReportTableData forDisplay(ReportTableData raw) {
        if (raw == null) {
            return ReportTableData.empty("");
        }
        List<String> headers = raw.getColumns();
        List<Integer> secondColumnIndexes = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            if (isSecondsColumn(headers.get(i))) {
                secondColumnIndexes.add(i);
            }
        }
        if (secondColumnIndexes.isEmpty()) {
            return raw;
        }
        List<List<String>> formattedRows = new ArrayList<>();
        for (List<String> row : raw.getRows()) {
            List<String> copy = new ArrayList<>(row);
            for (int col : secondColumnIndexes) {
                if (col < copy.size()) {
                    copy.set(col, formatSecondsValue(copy.get(col)));
                }
            }
            formattedRows.add(copy);
        }
        return new ReportTableData(headers, formattedRows);
    }

    private static boolean isSecondsColumn(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String h = header.toLowerCase();
        return h.contains("segundo")
                || h.contains("_seg")
                || h.endsWith("_sec")
                || h.contains("duracion_seg")
                || h.contains("espera_seg")
                || h.contains("intervalo_seg")
                || h.equals("segundos")
                || h.contains("entrante_sec")
                || h.contains("saliente_sec");
    }

    private static String formatSecondsValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "0 00:00:00";
        }
        try {
            long sec = (long) Double.parseDouble(raw.trim().replace(',', '.'));
            return AdminLabels.formatDaysHms(sec);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
