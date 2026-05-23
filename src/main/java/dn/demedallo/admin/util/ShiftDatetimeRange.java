package dn.demedallo.admin.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shift hour filter (same logic as rep_incoming_campaigns_panel PHP).
 */
public final class ShiftDatetimeRange {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public final String start;
    public final String end;
    public final String indicatorText;

    private ShiftDatetimeRange(String start, String end, String indicatorText) {
        this.start = start;
        this.end = end;
        this.indicatorText = indicatorText;
    }

    public static ShiftDatetimeRange ofHours(int fromHour, int toHour) {
        int from = clamp(fromHour);
        int to = clamp(toHour);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime start;
        LocalDateTime end;
        String indicator;
        if (from > to) {
            start = yesterday.atTime(from, 0);
            end = today.atTime(to, 59, 59);
            indicator = "Ayer " + fmtHour(from) + ":00 - Hoy " + fmtHour(to) + ":59";
        } else {
            start = today.atTime(from, 0);
            end = today.atTime(to, 59, 59);
            indicator = "Hoy " + fmtHour(from) + ":00 - " + fmtHour(to) + ":59";
        }
        return new ShiftDatetimeRange(start.format(FMT), end.format(FMT), indicator);
    }

    private static int clamp(int h) {
        return Math.max(0, Math.min(23, h));
    }

    private static String fmtHour(int h) {
        return h < 10 ? "0" + h : String.valueOf(h);
    }
}
