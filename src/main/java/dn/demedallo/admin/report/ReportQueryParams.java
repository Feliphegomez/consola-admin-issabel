package dn.demedallo.admin.report;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class ReportQueryParams {

    private final LocalDate from;
    private final LocalDate to;
    private final Map<String, String> options = new HashMap<>();

    public ReportQueryParams(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    public void setOption(String key, String value) {
        if (value != null) {
            options.put(key, value.trim());
        }
    }

    public String option(String key, String def) {
        return options.getOrDefault(key, def);
    }

    public String fromDate() {
        return from.toString();
    }

    public String toDate() {
        return to.toString();
    }

    public String fromDateTime() {
        return fromDate() + " 00:00:00";
    }

    public String toDateTime() {
        return toDate() + " 23:59:59";
    }
}
