package dn.demedallo.admin.db;

import dn.demedallo.admin.model.CallProgressStepRow;
import dn.demedallo.admin.model.FailedShortCallRow;
import dn.demedallo.admin.model.FailedShortCallRow.CallDirection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Failed and short calls with progress log traceability (Issabel {@code call_progress_log}).
 */
public final class FailedShortCallsDao {

    private static final int MAX_CALL_ROWS = 2000;

    private final CallCenterDb db;

    public FailedShortCallsDao(CallCenterDb db) {
        this.db = db;
    }

    public List<FailedShortCallRow> loadFailedShortCalls(String datetimeStart, String datetimeEnd,
            CallDirectionFilter directionFilter) throws Exception {
        List<FailedShortCallRow> out = new ArrayList<>();
        if (directionFilter == CallDirectionFilter.OUTGOING || directionFilter == CallDirectionFilter.ALL) {
            out.addAll(loadOutgoing(datetimeStart, datetimeEnd));
        }
        if (directionFilter == CallDirectionFilter.INCOMING || directionFilter == CallDirectionFilter.ALL) {
            out.addAll(loadIncoming(datetimeStart, datetimeEnd));
        }
        out.sort((a, b) -> b.callDatetime.compareToIgnoreCase(a.callDatetime));
        if (out.size() > MAX_CALL_ROWS) {
            return new ArrayList<>(out.subList(0, MAX_CALL_ROWS));
        }
        return out;
    }

    public List<CallProgressStepRow> loadProgressLog(FailedShortCallRow call) throws Exception {
        if (call.direction == CallDirection.OUTGOING) {
            return loadOutgoingProgress(call.callId);
        }
        return loadIncomingProgress(call.callId);
    }

    private List<FailedShortCallRow> loadOutgoing(String datetimeStart, String datetimeEnd) throws Exception {
        String sql = """
                SELECT c.id AS call_id,
                       IFNULL(camp.name, '') AS campaign_name,
                       IFNULL(c.phone, '') AS phone,
                       IFNULL(c.status, '') AS status,
                       c.fecha_llamada AS call_datetime,
                       IFNULL(c.duration, 0) AS duration_sec,
                       IFNULL(c.retries, 0) AS retries,
                       c.failure_cause AS failure_code,
                       IFNULL(c.failure_cause_txt, '') AS failure_text,
                       IFNULL(c.uniqueid, '') AS uniqueid,
                       IFNULL(c.trunk, '') AS trunk,
                       IFNULL(a.number, '') AS agent_number
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                LEFT JOIN agent a ON c.id_agent = a.id
                WHERE c.status IN ('Failure', 'ShortCall')
                  AND c.fecha_llamada >= ?
                  AND c.fecha_llamada <= ?
                ORDER BY c.fecha_llamada DESC
                LIMIT %d
                """.formatted(MAX_CALL_ROWS);
        return queryCallRows(sql, datetimeStart, datetimeEnd, CallDirection.OUTGOING);
    }

    private List<FailedShortCallRow> loadIncoming(String datetimeStart, String datetimeEnd) throws Exception {
        String sql = """
                SELECT ce.id AS call_id,
                       IFNULL(camp.name, '') AS campaign_name,
                       IFNULL(ce.callerid, '') AS phone,
                       IFNULL(ce.status, '') AS status,
                       COALESCE(ce.datetime_init, ce.datetime_entry_queue) AS call_datetime,
                       IFNULL(ce.duration, 0) AS duration_sec,
                       0 AS retries,
                       NULL AS failure_code,
                       '' AS failure_text,
                       IFNULL(ce.uniqueid, '') AS uniqueid,
                       IFNULL(ce.trunk, '') AS trunk,
                       IFNULL(a.number, '') AS agent_number
                FROM call_entry ce
                LEFT JOIN campaign_entry camp ON ce.id_campaign = camp.id
                LEFT JOIN agent a ON ce.id_agent = a.id
                WHERE ce.status IN ('Failure', 'ShortCall')
                  AND COALESCE(ce.datetime_init, ce.datetime_entry_queue) >= ?
                  AND COALESCE(ce.datetime_init, ce.datetime_entry_queue) <= ?
                ORDER BY call_datetime DESC
                LIMIT %d
                """.formatted(MAX_CALL_ROWS);
        return queryCallRows(sql, datetimeStart, datetimeEnd, CallDirection.INCOMING);
    }

    private List<FailedShortCallRow> queryCallRows(String sql, String datetimeStart, String datetimeEnd,
            CallDirection direction) throws Exception {
        List<FailedShortCallRow> out = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, datetimeStart);
            ps.setString(2, datetimeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    out.add(new FailedShortCallRow(
                            direction,
                            rs.getInt("call_id"),
                            dash(rs.getString("campaign_name")),
                            dash(rs.getString("phone")),
                            status,
                            statusLabel(status),
                            formatTs(rs.getTimestamp("call_datetime")),
                            formatDuration(rs.getInt("duration_sec")),
                            String.valueOf(rs.getInt("retries")),
                            formatFailureCode(rs.getObject("failure_code")),
                            dash(rs.getString("failure_text")),
                            dash(rs.getString("uniqueid")),
                            dash(rs.getString("trunk")),
                            dash(rs.getString("agent_number"))));
                }
            }
        }
        return out;
    }

    private List<CallProgressStepRow> loadOutgoingProgress(int callId) throws Exception {
        String sql = """
                SELECT l.datetime_entry, l.new_status, IFNULL(l.retry, 0) AS retry,
                       IFNULL(l.uniqueid, '') AS uniqueid, IFNULL(l.trunk, '') AS trunk,
                       IFNULL(l.duration, 0) AS duration_sec,
                       IFNULL(a.number, '') AS agent_number
                FROM call_progress_log l
                LEFT JOIN agent a ON l.id_agent = a.id
                WHERE l.id_call_outgoing = ?
                ORDER BY l.datetime_entry ASC, l.id ASC
                """;
        return queryProgress(sql, callId);
    }

    private List<CallProgressStepRow> loadIncomingProgress(int callId) throws Exception {
        String sql = """
                SELECT l.datetime_entry, l.new_status, IFNULL(l.retry, 0) AS retry,
                       IFNULL(l.uniqueid, '') AS uniqueid, IFNULL(l.trunk, '') AS trunk,
                       IFNULL(l.duration, 0) AS duration_sec,
                       IFNULL(a.number, '') AS agent_number
                FROM call_progress_log l
                LEFT JOIN agent a ON l.id_agent = a.id
                WHERE l.id_call_incoming = ?
                ORDER BY l.datetime_entry ASC, l.id ASC
                """;
        return queryProgress(sql, callId);
    }

    private List<CallProgressStepRow> queryProgress(String sql, int callId) throws Exception {
        List<CallProgressStepRow> out = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, callId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CallProgressStepRow(
                            formatTs(rs.getTimestamp("datetime_entry")),
                            statusLabel(rs.getString("new_status")),
                            String.valueOf(rs.getInt("retry")),
                            dash(rs.getString("uniqueid")),
                            dash(rs.getString("trunk")),
                            formatDuration(rs.getInt("duration_sec")),
                            dash(rs.getString("agent_number"))));
                }
            }
        }
        return out;
    }

    private static String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status) {
            case "Failure" -> "Fallo";
            case "ShortCall" -> "Llamada corta";
            case "Success" -> "Éxito";
            case "NoAnswer" -> "No contesta";
            case "Abandoned" -> "Abandonada";
            case "Placing" -> "Marcando";
            case "Ringing" -> "Timbrando";
            case "OnQueue" -> "En cola";
            case "OnHold" -> "En espera";
            case "Hangup" -> "Colgado";
            default -> status;
        };
    }

    private static String formatFailureCode(Object code) {
        if (code == null) {
            return "-";
        }
        return String.valueOf(code);
    }

    private static String formatTs(Timestamp ts) {
        if (ts == null) {
            return "-";
        }
        return ts.toLocalDateTime().withNano(0).toString().replace('T', ' ');
    }

    private static String formatDuration(int sec) {
        if (sec <= 0) {
            return "00:00:00";
        }
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }

    public enum CallDirectionFilter {
        ALL,
        OUTGOING,
        INCOMING
    }
}
