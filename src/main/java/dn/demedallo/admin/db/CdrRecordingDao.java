package dn.demedallo.admin.db;

import dn.demedallo.admin.model.CdrCallDetail;
import dn.demedallo.admin.model.CelEventRow;
import dn.demedallo.admin.model.CallWithRecordingsRow;
import dn.demedallo.admin.model.PhoneTraceCallRow;
import dn.demedallo.admin.model.PhoneTraceCallRow.CallDirection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Issabel/FreePBX CDR ({@code asteriskcdrdb.cdr}) — same source as the web admin CDR report.
 */
public final class CdrRecordingDao {

    private static final int MAX_ROWS = 500;

    private final AsteriskDb db;

    public CdrRecordingDao(AsteriskDb db) {
        this.db = db;
    }

    public CdrCallDetail loadByUniqueid(String uniqueid) throws SQLException {
        if (uniqueid == null || uniqueid.isBlank()) {
            return null;
        }
        String sql = """
                SELECT uniqueid, calldate, clid, src, dst, dcontext, channel, dstchannel,
                       lastapp, lastdata, duration, billsec, disposition, recordingfile
                FROM cdr
                WHERE uniqueid = ?
                ORDER BY calldate DESC
                LIMIT 1
                """;
        try (Connection c = db.openCdr();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uniqueid.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapDetail(rs);
            }
        }
    }

    public List<CelEventRow> loadCelByUniqueid(String uniqueid) throws SQLException {
        if (uniqueid == null || uniqueid.isBlank()) {
            return List.of();
        }
        String sql = """
                SELECT eventtype, eventtime, channame, appname, appdata, extra
                FROM cel
                WHERE uniqueid = ?
                ORDER BY eventtime
                LIMIT 500
                """;
        try (Connection c = db.openCdr();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uniqueid.trim());
            try (ResultSet rs = ps.executeQuery()) {
                List<CelEventRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CelEventRow(
                            str(rs, "eventtype"),
                            formatTs(rs.getTimestamp("eventtime")),
                            str(rs, "channame"),
                            str(rs, "appname"),
                            str(rs, "appdata"),
                            str(rs, "extra")));
                }
                return rows;
            }
        } catch (SQLException ex) {
            if (isMissingTable(ex)) {
                return List.of();
            }
            throw ex;
        }
    }

    public List<CallWithRecordingsRow> searchWithRecordings(String phoneLikeOrNull,
            String datetimeStart, String datetimeEnd,
            FailedShortCallsDao.CallDirectionFilter directionFilter) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT uniqueid, calldate, src, dst, billsec, duration,
                       recordingfile, channel, disposition
                FROM cdr
                WHERE calldate >= ? AND calldate <= ?
                  AND recordingfile IS NOT NULL AND TRIM(recordingfile) <> ''
                """);
        if (phoneLikeOrNull != null && !phoneLikeOrNull.isBlank()) {
            sql.append(" AND (src LIKE ? OR dst LIKE ?) ");
        }
        appendDirectionFilter(sql, directionFilter);
        sql.append(" ORDER BY calldate DESC LIMIT ").append(MAX_ROWS);

        try (Connection c = db.openCdr();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setString(i++, datetimeStart);
            ps.setString(i++, datetimeEnd);
            if (phoneLikeOrNull != null && !phoneLikeOrNull.isBlank()) {
                ps.setString(i++, phoneLikeOrNull);
                ps.setString(i++, phoneLikeOrNull);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<CallWithRecordingsRow> rows = new ArrayList<>();
                Set<String> seenUniqueids = new LinkedHashSet<>();
                while (rs.next()) {
                    String uniqueid = str(rs, "uniqueid");
                    if (!seenUniqueids.add(uniqueid)) {
                        continue;
                    }
                    rows.add(mapRow(rs));
                }
                return rows;
            }
        } catch (SQLException ex) {
            if (isMissingTable(ex)) {
                throw new SQLException(
                        "Tabla cdr no encontrada. Revise «Base CDR» en login (por defecto asteriskcdrdb).", ex);
            }
            throw ex;
        }
    }

    private static void appendDirectionFilter(StringBuilder sql,
            FailedShortCallsDao.CallDirectionFilter directionFilter) {
        if (directionFilter == FailedShortCallsDao.CallDirectionFilter.OUTGOING) {
            sql.append("""
                     AND CHAR_LENGTH(dst) > CHAR_LENGTH(src)
                     AND dst REGEXP '^[0-9]{6,}$'
                    """);
        } else if (directionFilter == FailedShortCallsDao.CallDirectionFilter.INCOMING) {
            sql.append("""
                     AND CHAR_LENGTH(src) > CHAR_LENGTH(dst)
                     AND src REGEXP '^[0-9]{6,}$'
                    """);
        }
    }

    private static CdrCallDetail mapDetail(ResultSet rs) throws SQLException {
        return new CdrCallDetail(
                str(rs, "uniqueid"),
                formatTs(rs.getTimestamp("calldate")),
                str(rs, "clid"),
                str(rs, "src"),
                str(rs, "dst"),
                str(rs, "dcontext"),
                str(rs, "channel"),
                str(rs, "dstchannel"),
                str(rs, "lastapp"),
                str(rs, "lastdata"),
                rs.getInt("duration"),
                rs.getInt("billsec"),
                str(rs, "disposition"),
                str(rs, "recordingfile"));
    }

    private static CallWithRecordingsRow mapRow(ResultSet rs) throws SQLException {
        String src = str(rs, "src");
        String dst = str(rs, "dst");
        boolean outgoing = dst.length() >= src.length() && dst.matches(".*\\d{6,}.*");
        CallDirection direction = outgoing ? CallDirection.OUTGOING : CallDirection.INCOMING;
        String phone = outgoing ? dst : src;
        int billsec = rs.getInt("billsec");
        String disposition = str(rs, "disposition");
        String recording = str(rs, "recordingfile");
        String channel = str(rs, "channel");
        PhoneTraceCallRow call = new PhoneTraceCallRow(
                direction,
                0,
                0,
                "CDR Issabel",
                dash(phone),
                disposition,
                disposition.isBlank() ? "-" : disposition,
                formatTs(rs.getTimestamp("calldate")),
                formatDuration(billsec > 0 ? billsec : rs.getInt("duration")),
                0,
                0,
                "-",
                "",
                "",
                dash(str(rs, "uniqueid")),
                "",
                "");
        call.cdrRecordingFile = recording;
        call.cdrChannel = channel;
        return new CallWithRecordingsRow(call, 1);
    }

    private static boolean isMissingTable(SQLException ex) {
        String msg = ex.getMessage();
        return msg != null && (msg.contains("doesn't exist") || msg.contains("Unknown table"));
    }

    private static String str(ResultSet rs, String column) throws SQLException {
        String v = rs.getString(column);
        return v == null ? "" : v.trim();
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
