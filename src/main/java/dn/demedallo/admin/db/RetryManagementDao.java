package dn.demedallo.admin.db;

import dn.demedallo.admin.model.BulkFailureRetryPreview.CampaignCount;
import dn.demedallo.admin.model.RetryCallRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Supervisor retry queue for failed outgoing calls ({@code calls}).
 */
public final class RetryManagementDao {

    private static final int MAX_ROWS = 2000;
    private static final int MAX_BULK_RETRY = 500;

    private final CallCenterDb db;

    public RetryManagementDao(CallCenterDb db) {
        this.db = db;
    }

    public List<RetryCallRow> loadRetryCandidates(String datetimeStart, String datetimeEnd) throws Exception {
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
                WHERE c.status IN ('Failure', 'ShortCall', 'NoAnswer')
                  AND c.dnc = 0
                  AND camp.estatus = 'A'
                  AND c.fecha_llamada >= ?
                  AND c.fecha_llamada <= ?
                ORDER BY c.fecha_llamada DESC
                LIMIT %d
                """.formatted(MAX_ROWS);

        List<RetryCallRow> out = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, datetimeStart);
            ps.setString(2, datetimeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    out.add(new RetryCallRow(
                            rs.getInt("call_id"),
                            rs.getInt("campaign_id"),
                            dash(rs.getString("campaign_name")),
                            dash(rs.getString("phone")),
                            status,
                            statusLabel(status),
                            formatTs(rs.getTimestamp("call_datetime")),
                            formatDuration(rs.getInt("duration_sec")),
                            rs.getInt("retries"),
                            rs.getInt("max_retries"),
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

    private static final String BULK_FAILURE_UNKNOWN_CORE = """
            c.status = 'Failure'
              AND c.dnc = 0
              AND camp.estatus = 'A'
              AND (c.uniqueid IS NULL OR TRIM(c.uniqueid) = ''
                   OR LOWER(TRIM(c.uniqueid)) IN ('unknown', '<unknown>'))
              AND c.fecha_llamada >= ?
              AND c.fecha_llamada <= ?
            """;

    /**
     * Excludes: last outbound to phone was Success; another pending row for dialer;
     * dont_call list or any calls.dnc=1 for that phone.
     */
    private static final String BULK_FAILURE_UNKNOWN_ELIGIBLE_EXTRA = """
              AND NOT EXISTS (
                SELECT 1 FROM dont_call d
                WHERE d.caller_id = c.phone AND d.status = 'A'
              )
              AND NOT EXISTS (
                SELECT 1 FROM calls c_dnc
                WHERE c_dnc.phone = c.phone AND c_dnc.dnc = 1
              )
              AND NOT EXISTS (
                SELECT 1 FROM (
                  SELECT phone, status AS last_status,
                         ROW_NUMBER() OVER (
                           PARTITION BY phone
                           ORDER BY COALESCE(fecha_llamada, '1970-01-01 00:00:00') DESC, id DESC
                         ) AS rn
                  FROM calls
                ) last
                WHERE last.phone = c.phone AND last.rn = 1 AND last.last_status = 'Success'
              )
              AND NOT EXISTS (
                SELECT 1 FROM calls c_pend
                WHERE c_pend.phone = c.phone
                  AND c_pend.id <> c.id
                  AND c_pend.status IS NULL
                  AND c_pend.dnc = 0
                  AND ? BETWEEN c_pend.date_init AND c_pend.date_end
              )
            """;

    public int countFailureUnknownRaw(String datetimeStart, String datetimeEnd) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                WHERE
                """ + BULK_FAILURE_UNKNOWN_CORE;
        return countQuery(sql, datetimeStart, datetimeEnd, null);
    }

    public int countFailureUnknownEligible(String datetimeStart, String datetimeEnd, LocalDate today)
            throws SQLException {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                WHERE
                """ + BULK_FAILURE_UNKNOWN_CORE + BULK_FAILURE_UNKNOWN_ELIGIBLE_EXTRA;
        return countQuery(sql, datetimeStart, datetimeEnd, today);
    }

    /**
     * Outgoing {@code Failure} without uniqueid, eligible for bulk re-queue.
     */
    public List<Integer> loadFailureUnknownEligibleCallIds(String datetimeStart, String datetimeEnd,
            LocalDate today) throws SQLException {
        String sql = """
                SELECT c.id AS call_id
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                WHERE
                """ + BULK_FAILURE_UNKNOWN_CORE + BULK_FAILURE_UNKNOWN_ELIGIBLE_EXTRA + """
                ORDER BY c.fecha_llamada ASC
                LIMIT %d
                """.formatted(MAX_BULK_RETRY);

        List<Integer> ids = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindBulkFailureParams(ps, datetimeStart, datetimeEnd, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("call_id"));
                }
            }
        }
        return ids;
    }

    public List<CampaignCount> countFailureUnknownEligibleByCampaign(String datetimeStart,
            String datetimeEnd, LocalDate today) throws SQLException {
        String sql = """
                SELECT IFNULL(camp.name, CONCAT('Campaña ', camp.id)) AS campaign_name,
                       COUNT(*) AS cnt
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                WHERE
                """ + BULK_FAILURE_UNKNOWN_CORE + BULK_FAILURE_UNKNOWN_ELIGIBLE_EXTRA + """
                GROUP BY camp.id, camp.name
                ORDER BY cnt DESC, campaign_name
                """;
        List<CampaignCount> out = new ArrayList<>();
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindBulkFailureParams(ps, datetimeStart, datetimeEnd, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CampaignCount(
                            rs.getString("campaign_name"),
                            rs.getInt("cnt")));
                }
            }
        }
        return out;
    }

    private static void bindBulkFailureParams(PreparedStatement ps, String datetimeStart,
            String datetimeEnd, LocalDate today) throws SQLException {
        ps.setString(1, datetimeStart);
        ps.setString(2, datetimeEnd);
        if (today != null) {
            ps.setDate(3, java.sql.Date.valueOf(today));
        }
    }

    private int countQuery(String sql, String datetimeStart, String datetimeEnd, LocalDate today)
            throws SQLException {
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindBulkFailureParams(ps, datetimeStart, datetimeEnd, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cnt") : 0;
            }
        }
    }

    /**
     * Resets call state and sets an immediate schedule window so the dialer places another attempt.
     *
     * @return rows updated (0 or 1)
     */
    /**
     * Opens the schedule window to now so the dialer can pick a pending row (status IS NULL).
     *
     * @return rows updated (0 or 1)
     */
    public int queuePendingForImmediateDial(int callId, LocalDate dateInit, LocalDate dateEnd,
            LocalTime timeInit, LocalTime timeEnd) throws SQLException {
        String sql = """
                UPDATE calls SET
                    date_init = ?,
                    date_end = ?,
                    time_init = ?,
                    time_end = ?,
                    scheduled = 1
                WHERE id = ?
                  AND dnc = 0
                  AND status IS NULL
                """;
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(dateInit));
            ps.setDate(2, java.sql.Date.valueOf(dateEnd));
            ps.setTime(3, Time.valueOf(timeInit));
            ps.setTime(4, Time.valueOf(timeEnd));
            ps.setInt(5, callId);
            return ps.executeUpdate();
        }
    }

    public int queueOutgoingForRetry(int callId, LocalDate dateInit, LocalDate dateEnd,
            LocalTime timeInit, LocalTime timeEnd, int retriesAfterAdjust) throws SQLException {
        String sql = """
                UPDATE calls SET
                    status = NULL,
                    uniqueid = NULL,
                    fecha_llamada = NULL,
                    start_time = NULL,
                    end_time = NULL,
                    duration = NULL,
                    id_agent = NULL,
                    transfer = NULL,
                    datetime_entry_queue = NULL,
                    duration_wait = NULL,
                    failure_cause = NULL,
                    failure_cause_txt = NULL,
                    datetime_originate = NULL,
                    trunk = NULL,
                    agent = NULL,
                    scheduled = 1,
                    date_init = ?,
                    date_end = ?,
                    time_init = ?,
                    time_end = ?,
                    retries = ?
                WHERE id = ?
                  AND dnc = 0
                  AND status IN ('Failure', 'ShortCall', 'NoAnswer')
                """;
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(dateInit));
            ps.setDate(2, java.sql.Date.valueOf(dateEnd));
            ps.setTime(3, Time.valueOf(timeInit));
            ps.setTime(4, Time.valueOf(timeEnd));
            ps.setInt(5, retriesAfterAdjust);
            ps.setInt(6, callId);
            return ps.executeUpdate();
        }
    }

    public CampaignSchedule loadCampaignSchedule(int callId) throws SQLException {
        String sql = """
                SELECT camp.datetime_init, camp.datetime_end, camp.daytime_init, camp.daytime_end,
                       camp.retries AS max_retries, IFNULL(c.retries, 0) AS call_retries,
                       IFNULL(c.status, '') AS call_status, IFNULL(c.phone, '') AS phone
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                WHERE c.id = ?
                """;
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, callId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new CampaignSchedule(
                        rs.getDate("datetime_init").toLocalDate(),
                        rs.getDate("datetime_end").toLocalDate(),
                        rs.getTime("daytime_init").toLocalTime(),
                        rs.getTime("daytime_end").toLocalTime(),
                        rs.getInt("max_retries"),
                        rs.getInt("call_retries"),
                        rs.getString("call_status"),
                        dash(rs.getString("phone")));
            }
        }
    }

    public record CampaignSchedule(
            LocalDate campaignDateStart,
            LocalDate campaignDateEnd,
            LocalTime daytimeStart,
            LocalTime daytimeEnd,
            int maxRetries,
            int callRetries,
            String callStatus,
            String phone) {
    }

    private static String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status) {
            case "Failure" -> "Fallo";
            case "ShortCall" -> "Llamada corta";
            case "NoAnswer" -> "No contesta";
            default -> status;
        };
    }

    private static String formatFailureCode(Object code) {
        return code == null ? "-" : String.valueOf(code);
    }

    private static String formatTs(java.sql.Timestamp ts) {
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
