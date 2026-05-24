package dn.demedallo.admin.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and builds FreePBX/Issabel time group rules ({@code HH:mm-HH:mm|weekday|day|month}).
 */
public final class FreePbxTimeRuleCodec {

    private static final Pattern RULE = Pattern.compile(
            "^\\s*(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})\\|([^|]+)\\|([^|]+)\\|([^|]+)\\s*$");

    public static final String ANY = "*";
    public static final String ANY_DAY = "-";

    private static final Map<String, String> WEEKDAY_LABELS = linkedMap(
            "sun", "Domingo",
            "mon", "Lunes",
            "tue", "Martes",
            "wed", "Miércoles",
            "thu", "Jueves",
            "fri", "Viernes",
            "sat", "Sábado");

    private static final Map<String, String> MONTH_LABELS = linkedMap(
            "jan", "Enero",
            "feb", "Febrero",
            "mar", "Marzo",
            "apr", "Abril",
            "may", "Mayo",
            "jun", "Junio",
            "jul", "Julio",
            "aug", "Agosto",
            "sep", "Septiembre",
            "oct", "Octubre",
            "nov", "Noviembre",
            "dec", "Diciembre");

    private FreePbxTimeRuleCodec() {
    }

    public static Map<String, String> weekdayLabels() {
        return WEEKDAY_LABELS;
    }

    public static Map<String, String> monthLabels() {
        return MONTH_LABELS;
    }

    public record TimeRuleParts(
            String startHour,
            String startMinute,
            String endHour,
            String endMinute,
            String weekdayStart,
            String weekdayEnd,
            String monthDayStart,
            String monthDayEnd,
            String monthStart,
            String monthEnd
    ) {
        public static TimeRuleParts defaults() {
            return new TimeRuleParts("09", "00", "17", "00", "mon", "fri", ANY_DAY, ANY_DAY, ANY_DAY, ANY_DAY);
        }

        public String preview(String name) {
            String label = name == null || name.isBlank() ? "Franja" : name.trim();
            return label + " — " + FreePbxTimeRuleCodec.build(this);
        }
    }

    public static TimeRuleParts parse(String rule) {
        if (rule == null || rule.isBlank()) {
            return TimeRuleParts.defaults();
        }
        Matcher m = RULE.matcher(rule.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Regla horaria no reconocida. Formato: HH:mm-HH:mm|día|día-mes|mes "
                            + "(ej. 09:00-17:00|mon-fri|*|*)");
        }
        String[] start = splitTime(m.group(1));
        String[] end = splitTime(m.group(2));
        String[] weekdays = splitRange(m.group(3));
        String[] monthDays = splitRange(toAnyDay(m.group(4)));
        String[] months = splitRange(toAnyDay(m.group(5)));
        return new TimeRuleParts(
                start[0], start[1],
                end[0], end[1],
                weekdays[0], weekdays[1],
                monthDays[0], monthDays[1],
                months[0], months[1]);
    }

    public static String build(TimeRuleParts parts) {
        validate(parts);
        String time = padTime(parts.startHour(), parts.startMinute())
                + "-"
                + padTime(parts.endHour(), parts.endMinute());
        return time + "|"
                + buildSegment(parts.weekdayStart(), parts.weekdayEnd(), false)
                + "|"
                + buildSegment(parts.monthDayStart(), parts.monthDayEnd(), true)
                + "|"
                + buildSegment(parts.monthStart(), parts.monthEnd(), false);
    }

    public static void validate(TimeRuleParts parts) {
        if (parts == null) {
            throw new IllegalArgumentException("Complete la franja horaria.");
        }
        int start = toMinutes(parts.startHour(), parts.startMinute(), "Hora de inicio");
        int end = toMinutes(parts.endHour(), parts.endMinute(), "Hora de fin");
        if (start >= end) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        requireWeekday(parts.weekdayStart(), "Día de la semana de inicio");
        requireWeekday(parts.weekdayEnd(), "Día de la semana de fin");
        requireMonthDay(parts.monthDayStart(), "Día del mes de inicio");
        requireMonthDay(parts.monthDayEnd(), "Día del mes de fin");
        requireMonth(parts.monthStart(), "Mes de inicio");
        requireMonth(parts.monthEnd(), "Mes de fin");
    }


    private static String[] splitTime(String hhmm) {
        String[] p = hhmm.split(":");
        return new String[]{pad2(p[0]), pad2(p[1])};
    }

    private static String[] splitRange(String segment) {
        if (segment == null || segment.isBlank() || ANY.equals(segment.trim())) {
            return new String[]{ANY, ANY};
        }
        if (ANY_DAY.equals(segment.trim())) {
            return new String[]{ANY_DAY, ANY_DAY};
        }
        int dash = segment.indexOf('-');
        if (dash < 0) {
            return new String[]{segment.trim().toLowerCase(Locale.ROOT), segment.trim().toLowerCase(Locale.ROOT)};
        }
        return new String[]{
                segment.substring(0, dash).trim().toLowerCase(Locale.ROOT),
                segment.substring(dash + 1).trim().toLowerCase(Locale.ROOT)
        };
    }

    private static String toAnyDay(String segment) {
        if (ANY.equals(segment == null ? "" : segment.trim())) {
            return ANY_DAY;
        }
        return segment;
    }

    private static String buildSegment(String start, String end, boolean numericDay) {
        String s = normalizeAny(start);
        String e = normalizeAny(end);
        if (ANY.equals(s) || ANY.equals(e) || ANY_DAY.equals(s) || ANY_DAY.equals(e)) {
            return ANY;
        }
        if (s.equals(e)) {
            return s;
        }
        return s + "-" + e;
    }

    private static String normalizeAny(String value) {
        if (value == null || value.isBlank() || ANY_DAY.equals(value.trim())) {
            return ANY;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String padTime(String hour, String minute) {
        return pad2(hour) + ":" + pad2(minute);
    }

    private static String pad2(String value) {
        if (value == null || value.isBlank()) {
            return "00";
        }
        int n = Integer.parseInt(value.trim());
        if (n < 0 || n > 59 && value.length() <= 2) {
            // hour path validated separately
        }
        return String.format(Locale.ROOT, "%02d", n);
    }

    private static int toMinutes(String hour, String minute, String label) {
        try {
            int h = Integer.parseInt(hour.trim());
            int m = Integer.parseInt(minute.trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                throw new IllegalArgumentException(label + " inválida.");
            }
            return h * 60 + m;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " inválida.");
        }
    }

    private static void requireWeekday(String code, String label) {
        if (ANY.equals(code) || WEEKDAY_LABELS.containsKey(code)) {
            return;
        }
        throw new IllegalArgumentException(label + " inválido.");
    }

    private static void requireMonthDay(String value, String label) {
        if (ANY.equals(value) || ANY_DAY.equals(value)) {
            return;
        }
        try {
            int day = Integer.parseInt(value);
            if (day < 1 || day > 31) {
                throw new IllegalArgumentException(label + " debe estar entre 1 y 31.");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " inválido.");
        }
    }

    private static void requireMonth(String code, String label) {
        if (ANY.equals(code) || ANY_DAY.equals(code) || MONTH_LABELS.containsKey(code)) {
            return;
        }
        throw new IllegalArgumentException(label + " inválido.");
    }

    private static Map<String, String> linkedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
