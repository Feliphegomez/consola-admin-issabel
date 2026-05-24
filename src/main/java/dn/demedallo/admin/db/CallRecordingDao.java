package dn.demedallo.admin.db;

import dn.demedallo.admin.model.CallRecordingFileRow;
import dn.demedallo.admin.model.CallWithRecordingsRow;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceCallRow.CallDirection;
import dn.demedallo.admin.util.CallStatusLabels;
import dn.demedallo.admin.util.FailureCauseLabels;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Queries {@code call_recording} and related call rows. */
public final class CallRecordingDao {

    private static final int MAX_ROWS = 500;

    private final CallCenterDb db;

    public CallRecordingDao(CallCenterDb db) {
        this.db = db;
    }

    public List<CallWithRecordingsRow> searchCallsWithRecordings(String phoneLikeOrNull,
            String datetimeStart, String datetimeEnd,
            FailedShortCallsDao.CallDirectionFilter directionFilter) throws Exception {
        List<CallWithRecordingsRow> out = new ArrayList<>();
        if (directionFilter == FailedShortCallsDao.CallDirectionFilter.OUTGOING
                || directionFilter == FailedShortCallsDao.CallDirectionFilter.ALL) {
            out.addAll(loadOutgoingWithRecordings(phoneLikeOrNull, datetimeStart, datetimeEnd));
        }
        if (directionFilter == FailedShortCallsDao.CallDirectionFilter.INCOMING
                || directionFilter == FailedShortCallsDao.CallDirectionFilter.ALL) {
            out.addAll(loadIncomingWithRecordings(phoneLikeOrNull, datetimeStart, datetimeEnd));
        }
        out.sort((a, b) -> b.call().callDatetime.compareToIgnoreCase(a.call().callDatetime));
        if (out.size() > MAX_ROWS) {
            return new ArrayList<>(out.subList(0, MAX_ROWS));
        }
        return out;
    }

    public List<CallRecordingFileRow> listRecordingFiles(PhoneTraceCallRow call) throws Exception {
        if (call == null) {
            return List.of();
        }
        String field = call.direction == CallDirection.OUTGOING ? "id_call_outgoing" : "id_call_incoming";
        String sql = """
                SELECT id, datetime_entry, uniqueid, channel, recordingfile
                FROM call_recording
                WHERE %s = ?
                ORDER BY datetime_entry DESC
                """.formatted(field);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, call.callId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CallRecordingFileRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CallRecordingFileRow(
                            rs.getInt("id"),
                            str(rs, "datetime_entry"),
                            str(rs, "uniqueid"),
                            str(rs, "channel"),
                            str(rs, "recordingfile")));
                }
                return rows;
            }
        }
    }

    public Optional<CallRecordingFileRow> findById(int recordingId) throws Exception {
        String sql = """
                SELECT id, datetime_entry, uniqueid, channel, recordingfile
                FROM call_recording WHERE id = ?
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, recordingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CallRecordingFileRow(
                        rs.getInt("id"),
                        str(rs, "datetime_entry"),
                        str(rs, "uniqueid"),
                        str(rs, "channel"),
                        str(rs, "recordingfile")));
            }
        }
    }

    public void deleteRecordingRow(int recordingId) throws Exception {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM call_recording WHERE id = ?")) {
            ps.setInt(1, recordingId);
            if (ps.executeUpdate() == 0) {
                throw new java.sql.SQLException("Grabación no encontrada en BD: id=" + recordingId);
            }
        }
    }

    private List<CallWithRecordingsRow> loadOutgoingWithRecordings(String phoneLikeOrNull,
            String datetimeStart, String datetimeEnd) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS call_id,
                       camp.id AS campaign_id,
                       IFNULL(camp.name, '') AS campaign_name,
                       IFNULL(c.phone, '') AS phone,
                       IFNULL(c.status, '') AS status,
                       c.fecha_llamada AS call_datetime,
                       IFNULL(c.duration, 0) AS duration_sec,
                       IFNULL(c.retries, 0) AS retries,
                       IFNULL(camp.retries, 1) AS max_retries,
                       c.failure_cause AS failure_code,
                       IFNULL(c.failure_cause_txt, '') AS failure_text,
                       IFNULL(c.uniqueid, '') AS uniqueid,
                       IFNULL(c.trunk, '') AS trunk,
                       IFNULL(a.number, '') AS agent_number,
                       COUNT(cr.id) AS rec_count
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                INNER JOIN call_recording cr ON cr.id_call_outgoing = c.id
                LEFT JOIN agent a ON c.id_agent = a.id
                WHERE c.fecha_llamada >= ? AND c.fecha_llamada <= ?
                """);
        if (phoneLikeOrNull != null && !phoneLikeOrNull.isBlank()) {
            sql.append(" AND c.phone LIKE ? ");
        }
        sql.append("""
                GROUP BY c.id, camp.id, camp.name, c.phone, c.status, c.fecha_llamada,
                         c.duration, c.retries, camp.retries, c.failure_cause,
                         c.failure_cause_txt, c.uniqueid, c.trunk, a.number
                ORDER BY c.fecha_llamada DESC
                LIMIT %d
                """.formatted(MAX_ROWS));
        return queryWithRecordings(sql.toString(), phoneLikeOrNull, datetimeStart, datetimeEnd,
                CallDirection.OUTGOING);
    }

    private List<CallWithRecordingsRow> loadIncomingWithRecordings(String phoneLikeOrNull,
            String datetimeStart, String datetimeEnd) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT ce.id AS call_id,
                       IFNULL(ce.id_campaign, 0) AS campaign_id,
                       IFNULL(camp.name, IFNULL(q.queue, '')) AS campaign_name,
                       IFNULL(ce.callerid, '') AS phone,
                       IFNULL(ce.status, '') AS status,
                       COALESCE(ce.datetime_init, ce.datetime_entry_queue) AS call_datetime,
                       IFNULL(ce.duration, 0) AS duration_sec,
                       0 AS retries,
                       0 AS max_retries,
                       NULL AS failure_code,
                       '' AS failure_text,
                       IFNULL(ce.uniqueid, '') AS uniqueid,
                       IFNULL(ce.trunk, '') AS trunk,
                       IFNULL(a.number, '') AS agent_number,
                       COUNT(cr.id) AS rec_count
                FROM call_entry ce
                INNER JOIN call_recording cr ON cr.id_call_incoming = ce.id
                LEFT JOIN campaign_entry camp ON ce.id_campaign = camp.id
                LEFT JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                LEFT JOIN agent a ON ce.id_agent = a.id
                WHERE COALESCE(ce.datetime_init, ce.datetime_entry_queue) >= ?
                  AND COALESCE(ce.datetime_init, ce.datetime_entry_queue) <= ?
                """);
        if (phoneLikeOrNull != null && !phoneLikeOrNull.isBlank()) {
            sql.append(" AND ce.callerid LIKE ? ");
        }
        sql.append("""
                GROUP BY ce.id, ce.id_campaign, camp.name, q.queue, ce.callerid, ce.status,
                         ce.datetime_init, ce.datetime_entry_queue, ce.duration,
                         ce.uniqueid, ce.trunk, a.number
                ORDER BY COALESCE(ce.datetime_init, ce.datetime_entry_queue) DESC
                LIMIT %d
                """.formatted(MAX_ROWS));
        return queryWithRecordings(sql.toString(), phoneLikeOrNull, datetimeStart, datetimeEnd,
                CallDirection.INCOMING);
    }

    private List<CallWithRecordingsRow> queryWithRecordings(String sql, String phoneLikeOrNull,
            String datetimeStart, String datetimeEnd, CallDirection direction) throws Exception {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, datetimeStart);
            ps.setString(i++, datetimeEnd);
            if (phoneLikeOrNull != null && !phoneLikeOrNull.isBlank()) {
                ps.setString(i, phoneLikeOrNull);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<CallWithRecordingsRow> rows = new ArrayList<>();
                while (rs.next()) {
                    String status = rs.getString("status");
                    Object failureCode = rs.getObject("failure_code");
                    String failureText = rs.getString("failure_text");
                    String failureReason = FailureCauseLabels.formatReason(failureCode, failureText, status);
                    PhoneTraceCallRow call = new PhoneTraceCallRow(
                            direction,
                            rs.getInt("call_id"),
                            rs.getInt("campaign_id"),
                            dash(rs.getString("campaign_name")),
                            dash(rs.getString("phone")),
                            status,
                            CallStatusLabels.statusLabel(status),
                            formatTs(rs.getTimestamp("call_datetime")),
                            formatDuration(rs.getInt("duration_sec")),
                            rs.getInt("retries"),
                            rs.getInt("max_retries"),
                            formatFailureCode(failureCode),
                            dash(failureText),
                            failureReason,
                            dash(rs.getString("uniqueid")),
                            dash(rs.getString("trunk")),
                            dash(rs.getString("agent_number")));
                    rows.add(new CallWithRecordingsRow(call, rs.getInt("rec_count")));
                }
                return rows;
            }
        }
    }

    private static String str(ResultSet rs, String column) throws java.sql.SQLException {
        String v = rs.getString(column);
        return v == null ? "" : v;
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
}
