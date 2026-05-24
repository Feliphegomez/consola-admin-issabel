package dn.demedallo.admin.db;

import dn.demedallo.admin.model.pbx.PbxRows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only queries against FreePBX/Issabel {@code asterisk} tables. */
public final class PbxAdminDao {

    private final AsteriskDb db;

    public PbxAdminDao(AsteriskDb db) {
        this.db = db;
    }

    public List<PbxRows.TimeGroupRow> listTimeGroups() throws SQLException {
        String sql = """
                SELECT g.id, g.description, COUNT(d.id) AS slot_count
                FROM timegroups_groups g
                LEFT JOIN timegroups_details d ON d.timegroupid = g.id
                GROUP BY g.id, g.description
                ORDER BY g.description
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.TimeGroupRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.TimeGroupRow(
                        rs.getInt("id"),
                        str(rs, "description"),
                        rs.getInt("slot_count")));
            }
            return rows;
        }
    }

    public List<PbxRows.TimeGroupDetailRow> listTimeGroupDetails(int timeGroupId) throws SQLException {
        String sql = """
                SELECT id, timegroupid, time, name
                FROM timegroups_details
                WHERE timegroupid = ?
                ORDER BY id
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, timeGroupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PbxRows.TimeGroupDetailRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PbxRows.TimeGroupDetailRow(
                            rs.getInt("id"),
                            rs.getInt("timegroupid"),
                            str(rs, "time"),
                            str(rs, "name")));
                }
                return rows;
            }
        }
    }

    public List<PbxRows.TimeConditionRow> listTimeConditions() throws SQLException {
        String sql = """
                SELECT tc.timeconditions_id, tc.displayname,
                       COALESCE(tc.time, 0) AS time_group_id,
                       COALESCE(tg.description, '') AS time_group_label,
                       tc.truegoto, tc.falsegoto, COALESCE(tc.deptname, '') AS deptname
                FROM timeconditions tc
                LEFT JOIN timegroups_groups tg ON tg.id = tc.time
                ORDER BY tc.displayname
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.TimeConditionRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.TimeConditionRow(
                        rs.getInt("timeconditions_id"),
                        str(rs, "displayname"),
                        rs.getInt("time_group_id"),
                        str(rs, "time_group_label"),
                        str(rs, "truegoto"),
                        str(rs, "falsegoto"),
                        str(rs, "deptname")));
            }
            return rows;
        }
    }

    public List<PbxRows.QueueRow> listQueues() throws SQLException {
        String sql = """
                SELECT extension, descr, dest, maxwait, callback_id
                FROM queues_config
                ORDER BY extension
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.QueueRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.QueueRow(
                        str(rs, "extension"),
                        str(rs, "descr"),
                        str(rs, "dest"),
                        str(rs, "maxwait"),
                        str(rs, "callback_id")));
            }
            return rows;
        }
    }

    public List<PbxRows.ExtensionRow> listExtensions() throws SQLException {
        String sql = """
                SELECT u.extension, COALESCE(u.name, '') AS uname,
                       COALESCE(d.tech, '') AS tech,
                       COALESCE(d.dial, '') AS dial,
                       COALESCE(u.voicemail, '') AS voicemail
                FROM users u
                LEFT JOIN devices d ON d.id = u.extension
                ORDER BY u.extension
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.ExtensionRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.ExtensionRow(
                        str(rs, "extension"),
                        str(rs, "uname"),
                        str(rs, "tech"),
                        str(rs, "dial"),
                        str(rs, "voicemail")));
            }
            return rows;
        }
    }

    public List<PbxRows.TrunkRow> listTrunks() throws SQLException {
        String sql = """
                SELECT trunkid, name, tech, channelid, outcid,
                       COALESCE(disabled, '') AS disabled,
                       COALESCE(provider, '') AS provider
                FROM trunks
                ORDER BY name, tech, channelid
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.TrunkRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.TrunkRow(
                        rs.getInt("trunkid"),
                        str(rs, "name"),
                        str(rs, "tech"),
                        str(rs, "channelid"),
                        str(rs, "outcid"),
                        str(rs, "disabled"),
                        str(rs, "provider")));
            }
            return rows;
        }
    }

    public List<PbxRows.InboundRouteRow> listInboundRoutes() throws SQLException {
        String sql = """
                SELECT extension, COALESCE(cidnum, '') AS cidnum,
                       COALESCE(destination, '') AS destination,
                       COALESCE(description, '') AS description,
                       mohclass
                FROM incoming
                ORDER BY extension
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.InboundRouteRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.InboundRouteRow(
                        str(rs, "extension"),
                        str(rs, "cidnum"),
                        str(rs, "destination"),
                        str(rs, "description"),
                        str(rs, "mohclass")));
            }
            return rows;
        }
    }

    public List<PbxRows.OutboundRouteRow> listOutboundRoutes() throws SQLException {
        String sql = """
                SELECT r.route_id, COALESCE(r.name, '') AS rname,
                       COALESCE(r.outcid, '') AS outcid,
                       COALESCE(r.emergency_route, '') AS emergency_route,
                       COALESCE(r.time_group_id, 0) AS time_group_id,
                       COALESCE(tg.description, '') AS time_group_label,
                       COALESCE((
                           SELECT GROUP_CONCAT(
                               CONCAT(IFNULL(p.match_pattern_prefix, ''),
                                      IFNULL(p.match_pattern_pass, ''))
                               ORDER BY p.match_pattern_prefix
                               SEPARATOR ' | ')
                           FROM outbound_route_patterns p
                           WHERE p.route_id = r.route_id
                       ), '') AS patterns,
                       COALESCE((
                           SELECT GROUP_CONCAT(
                               CONCAT(COALESCE(t.name, ''), ' #', ort.trunk_id)
                               ORDER BY ort.seq
                               SEPARATOR ' → ')
                           FROM outbound_route_trunks ort
                           LEFT JOIN trunks t ON t.trunkid = ort.trunk_id
                           WHERE ort.route_id = r.route_id
                       ), '') AS trunks
                FROM outbound_routes r
                LEFT JOIN timegroups_groups tg ON tg.id = r.time_group_id
                ORDER BY (
                    SELECT MIN(seq) FROM outbound_route_sequence s WHERE s.route_id = r.route_id
                ), r.route_id
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.OutboundRouteRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.OutboundRouteRow(
                        rs.getInt("route_id"),
                        str(rs, "rname"),
                        str(rs, "outcid"),
                        str(rs, "emergency_route"),
                        rs.getInt("time_group_id"),
                        str(rs, "time_group_label"),
                        str(rs, "patterns"),
                        str(rs, "trunks")));
            }
            return rows;
        }
    }

    public List<PbxRows.IvrDetailRow> listIvrs() throws SQLException {
        String sql = """
                SELECT d.id, COALESCE(d.name, '') AS ivr_name, COALESCE(d.description, '') AS descr,
                       COALESCE(d.announcement, 0) AS ann,
                       COALESCE(d.directdial, '') AS directdial,
                       COALESCE(d.timeout_time, 0) AS timeout_time,
                       COALESCE(d.timeout_destination, '') AS timeout_dest,
                       COALESCE(d.invalid_destination, '') AS invalid_dest,
                       COUNT(e.ivr_id) AS entry_count
                FROM ivr_details d
                LEFT JOIN ivr_entries e ON e.ivr_id = d.id
                GROUP BY d.id, d.name, d.description, d.announcement, d.directdial,
                         d.timeout_time, d.timeout_destination, d.invalid_destination
                ORDER BY d.name, d.id
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.IvrDetailRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.IvrDetailRow(
                        rs.getInt("id"),
                        str(rs, "ivr_name"),
                        str(rs, "descr"),
                        rs.getInt("ann"),
                        str(rs, "directdial"),
                        rs.getInt("timeout_time"),
                        str(rs, "timeout_dest"),
                        str(rs, "invalid_dest"),
                        rs.getInt("entry_count")));
            }
            return rows;
        }
    }

    public List<PbxRows.IvrEntryRow> listIvrEntries(int ivrId) throws SQLException {
        String sql = """
                SELECT ivr_id, COALESCE(selection, '') AS selection,
                       COALESCE(dest, '') AS dest, COALESCE(ivr_ret, 0) AS ivr_ret
                FROM ivr_entries
                WHERE ivr_id = ?
                ORDER BY selection
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ivrId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PbxRows.IvrEntryRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PbxRows.IvrEntryRow(
                            rs.getInt("ivr_id"),
                            str(rs, "selection"),
                            str(rs, "dest"),
                            rs.getInt("ivr_ret")));
                }
                return rows;
            }
        }
    }

    /**
     * MOH class names referenced in Issabel DB (no {@code music} table in modern Issabel).
     */
    public List<String> listMohClassNamesFromDb() throws SQLException {
        String sql = """
                SELECT DISTINCT category FROM (
                    SELECT TRIM(mohclass) AS category FROM users
                    WHERE mohclass IS NOT NULL AND TRIM(mohclass) <> ''
                    UNION
                    SELECT TRIM(mohclass) FROM incoming
                    WHERE mohclass IS NOT NULL AND TRIM(mohclass) <> ''
                    UNION
                    SELECT TRIM(mohclass) FROM outbound_routes
                    WHERE mohclass IS NOT NULL AND TRIM(mohclass) <> ''
                    UNION
                    SELECT TRIM(parkedmusicclass) FROM parkplus
                    WHERE parkedmusicclass IS NOT NULL AND TRIM(parkedmusicclass) <> ''
                    UNION
                    SELECT TRIM(music) FROM meetme
                    WHERE music IS NOT NULL AND TRIM(music) <> ''
                ) refs
                ORDER BY category
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                String category = str(rs, "category");
                if (!category.isEmpty()) {
                    names.add(category);
                }
            }
            return names;
        }
    }

    public int countMohReferences(String category) throws SQLException {
        String name = category == null ? "" : category.trim();
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM users WHERE TRIM(mohclass) = ?) +
                  (SELECT COUNT(*) FROM incoming WHERE TRIM(mohclass) = ?) +
                  (SELECT COUNT(*) FROM outbound_routes WHERE TRIM(mohclass) = ?) +
                  (SELECT COUNT(*) FROM parkplus WHERE TRIM(parkedmusicclass) = ?) +
                  (SELECT COUNT(*) FROM meetme WHERE TRIM(music) = ?) AS refs
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, name);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("refs") : 0;
            }
        }
    }

    public List<PbxRows.AnnouncementRow> listAnnouncements() throws SQLException {
        String sql = """
                SELECT a.announcement_id, COALESCE(a.description, '') AS descr,
                       COALESCE(a.recording_id, 0) AS rec_id,
                       COALESCE(r.displayname, '') AS rec_label,
                       COALESCE(a.post_dest, '') AS post_dest,
                       COALESCE(a.repeat_msg, '') AS repeat_msg,
                       COALESCE(a.return_ivr, 0) AS return_ivr
                FROM announcement a
                LEFT JOIN recordings r ON r.id = a.recording_id
                ORDER BY a.description, a.announcement_id
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.AnnouncementRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.AnnouncementRow(
                        rs.getInt("announcement_id"),
                        str(rs, "descr"),
                        rs.getInt("rec_id"),
                        str(rs, "rec_label"),
                        str(rs, "post_dest"),
                        str(rs, "repeat_msg"),
                        rs.getInt("return_ivr")));
            }
            return rows;
        }
    }

    public List<PbxRows.RecordingRow> listRecordings() throws SQLException {
        String sql = """
                SELECT id, COALESCE(displayname, '') AS dname, COALESCE(filename, '') AS fname
                FROM recordings
                ORDER BY displayname, id
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PbxRows.RecordingRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new PbxRows.RecordingRow(
                        rs.getInt("id"),
                        str(rs, "dname"),
                        str(rs, "fname")));
            }
            return rows;
        }
    }

    private static String str(ResultSet rs, String column) throws SQLException {
        String v = rs.getString(column);
        return v == null ? "" : v;
    }
}
