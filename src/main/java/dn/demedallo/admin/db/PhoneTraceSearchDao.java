package dn.demedallo.admin.db;

import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceCallRow.CallDirection;
import dn.demedallo.admin.model.ReadableTraceStepRow;
import dn.demedallo.admin.util.CallStatusLabels;
import dn.demedallo.admin.util.FailureCauseLabels;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Search inbound/outbound calls by phone and load {@code call_progress_log} trace.
 */
public final class PhoneTraceSearchDao {

    private static final int MAX_CALL_ROWS = 500;

    private final CallCenterDb db;

    public PhoneTraceSearchDao(CallCenterDb db) {
        this.db = db;
    }

    public List<PhoneTraceCallRow> searchByPhone(String phoneDigits, String datetimeStart, String datetimeEnd,
            FailedShortCallsDao.CallDirectionFilter directionFilter) throws Exception {
        String like = "%" + phoneDigits + "%";
        List<PhoneTraceCallRow> out = new ArrayList<>();
        if (directionFilter == FailedShortCallsDao.CallDirectionFilter.OUTGOING
                || directionFilter == FailedShortCallsDao.CallDirectionFilter.ALL) {
            out.addAll(loadOutgoing(like, datetimeStart, datetimeEnd));
        }
        if (directionFilter == FailedShortCallsDao.CallDirectionFilter.INCOMING
                || directionFilter == FailedShortCallsDao.CallDirectionFilter.ALL) {
            out.addAll(loadIncoming(like, datetimeStart, datetimeEnd));
        }
        out.sort((a, b) -> b.callDatetime.compareToIgnoreCase(a.callDatetime));
        if (out.size() > MAX_CALL_ROWS) {
            return new ArrayList<>(out.subList(0, MAX_CALL_ROWS));
        }
        return out;
    }

    public Optional<PhoneTraceCallRow> findByUniqueid(String uniqueid) throws Exception {
        if (uniqueid == null || uniqueid.isBlank() || "-".equals(uniqueid.trim())) {
            return Optional.empty();
        }
        String uid = uniqueid.trim();
        Optional<PhoneTraceCallRow> out = findOutgoingByUniqueid(uid);
        if (out.isPresent()) {
            return out;
        }
        return findIncomingByUniqueid(uid);
    }

    private Optional<PhoneTraceCallRow> findOutgoingByUniqueid(String uniqueid) throws Exception {
        String sql = """
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
                       IFNULL(a.number, '') AS agent_number
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                LEFT JOIN agent a ON c.id_agent = a.id
                WHERE c.uniqueid = ?
                ORDER BY c.fecha_llamada DESC
                LIMIT 1
                """;
        List<PhoneTraceCallRow> rows = queryCallRowsByUniqueid(sql, uniqueid, CallDirection.OUTGOING);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<PhoneTraceCallRow> findIncomingByUniqueid(String uniqueid) throws Exception {
        String sql = """
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
                       IFNULL(a.number, '') AS agent_number
                FROM call_entry ce
                LEFT JOIN campaign_entry camp ON ce.id_campaign = camp.id
                LEFT JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                LEFT JOIN agent a ON ce.id_agent = a.id
                WHERE ce.uniqueid = ?
                ORDER BY call_datetime DESC
                LIMIT 1
                """;
        List<PhoneTraceCallRow> rows = queryCallRowsByUniqueid(sql, uniqueid, CallDirection.INCOMING);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private List<PhoneTraceCallRow> queryCallRowsByUniqueid(String sql, String uniqueid,
            CallDirection direction) throws Exception {
        List<PhoneTraceCallRow> out = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uniqueid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    Object failureCode = rs.getObject("failure_code");
                    String failureText = rs.getString("failure_text");
                    String failureReason = FailureCauseLabels.formatReason(failureCode, failureText, status);
                    out.add(new PhoneTraceCallRow(
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
                            dash(rs.getString("agent_number"))));
                }
            }
        }
        return out;
    }

    public List<ReadableTraceStepRow> loadReadableTrace(PhoneTraceCallRow call) throws Exception {
        List<ReadableTraceStepRow> out = new ArrayList<>();
        if (call == null) {
            return out;
        }
        if (call.direction == CallDirection.OUTGOING) {
            loadReadableProgress(outgoingProgressSql(), call.callId, out);
        } else {
            loadReadableProgress(incomingProgressSql(), call.callId, out);
        }
        appendFailureNoteIfNeeded(call, out);
        return out;
    }

    private static void appendFailureNoteIfNeeded(PhoneTraceCallRow call, List<ReadableTraceStepRow> steps) {
        if (call.failureReason == null || call.failureReason.isBlank()
                || "Success".equals(call.status)) {
            return;
        }
        boolean hasFailureStep = steps.stream()
                .anyMatch(s -> "Fallo".equals(s.statusLabel) || s.summary.contains("fallo"));
        if (!hasFailureStep && ("Failure".equals(call.status) || "ShortCall".equals(call.status)
                || "NoAnswer".equals(call.status) || "Abandoned".equals(call.status))) {
            int step = steps.size() + 1;
            steps.add(new ReadableTraceStepRow(
                    step,
                    call.callDatetime,
                    "Motivo registrado: " + call.failureReason,
                    call.statusLabel,
                    call.retriesLabel(),
                    call.trunk,
                    call.agent,
                    call.duration));
        }
    }

    /** Digits-only phone fragment for LIKE (empty if invalid). */
    public static String normalizePhoneQuery(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.length() >= 4 ? digits.toString() : "";
    }

    private List<PhoneTraceCallRow> loadOutgoing(String phoneLike, String datetimeStart, String datetimeEnd)
            throws Exception {
        String sql = """
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
                       IFNULL(a.number, '') AS agent_number
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                LEFT JOIN agent a ON c.id_agent = a.id
                WHERE c.phone LIKE ?
                  AND c.fecha_llamada >= ?
                  AND c.fecha_llamada <= ?
                ORDER BY c.fecha_llamada DESC
                LIMIT %d
                """.formatted(MAX_CALL_ROWS);
        return queryCallRows(sql, phoneLike, datetimeStart, datetimeEnd, CallDirection.OUTGOING);
    }

    private List<PhoneTraceCallRow> loadIncoming(String phoneLike, String datetimeStart, String datetimeEnd)
            throws Exception {
        String sql = """
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
                       IFNULL(a.number, '') AS agent_number
                FROM call_entry ce
                LEFT JOIN campaign_entry camp ON ce.id_campaign = camp.id
                LEFT JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                LEFT JOIN agent a ON ce.id_agent = a.id
                WHERE ce.callerid LIKE ?
                  AND COALESCE(ce.datetime_init, ce.datetime_entry_queue) >= ?
                  AND COALESCE(ce.datetime_init, ce.datetime_entry_queue) <= ?
                ORDER BY call_datetime DESC
                LIMIT %d
                """.formatted(MAX_CALL_ROWS);
        return queryCallRows(sql, phoneLike, datetimeStart, datetimeEnd, CallDirection.INCOMING);
    }

    private List<PhoneTraceCallRow> queryCallRows(String sql, String phoneLike, String datetimeStart,
            String datetimeEnd, CallDirection direction) throws Exception {
        List<PhoneTraceCallRow> out = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneLike);
            ps.setString(2, datetimeStart);
            ps.setString(3, datetimeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    Object failureCode = rs.getObject("failure_code");
                    String failureText = rs.getString("failure_text");
                    String failureReason = FailureCauseLabels.formatReason(failureCode, failureText, status);
                    out.add(new PhoneTraceCallRow(
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
                            dash(rs.getString("agent_number"))));
                }
            }
        }
        return out;
    }

    private void loadReadableProgress(String sql, int callId, List<ReadableTraceStepRow> out) throws Exception {
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, callId);
            int step = 0;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    step++;
                    String rawStatus = rs.getString("new_status");
                    int retry = rs.getInt("retry");
                    String trunk = dash(rs.getString("trunk"));
                    String agent = dash(rs.getString("agent_number"));
                    String duration = formatDuration(rs.getInt("duration_sec"));
                    String statusLabel = CallStatusLabels.statusLabel(rawStatus);
                    String summary = CallStatusLabels.traceSummary(rawStatus, retry, trunk, agent, duration);
                    out.add(new ReadableTraceStepRow(
                            step,
                            formatTs(rs.getTimestamp("datetime_entry")),
                            summary,
                            statusLabel,
                            String.valueOf(retry),
                            trunk,
                            agent,
                            duration));
                }
            }
        }
    }

    private static String outgoingProgressSql() {
        return """
                SELECT l.datetime_entry, l.new_status, IFNULL(l.retry, 0) AS retry,
                       IFNULL(l.uniqueid, '') AS uniqueid, IFNULL(l.trunk, '') AS trunk,
                       IFNULL(l.duration, 0) AS duration_sec,
                       IFNULL(a.number, '') AS agent_number
                FROM call_progress_log l
                LEFT JOIN agent a ON l.id_agent = a.id
                WHERE l.id_call_outgoing = ?
                ORDER BY l.datetime_entry ASC, l.id ASC
                """;
    }

    private static String incomingProgressSql() {
        return """
                SELECT l.datetime_entry, l.new_status, IFNULL(l.retry, 0) AS retry,
                       IFNULL(l.uniqueid, '') AS uniqueid, IFNULL(l.trunk, '') AS trunk,
                       IFNULL(l.duration, 0) AS duration_sec,
                       IFNULL(a.number, '') AS agent_number
                FROM call_progress_log l
                LEFT JOIN agent a ON l.id_agent = a.id
                WHERE l.id_call_incoming = ?
                ORDER BY l.datetime_entry ASC, l.id ASC
                """;
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
