package dn.demedallo.admin.db;

import dn.demedallo.admin.model.pbx.PbxForms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** INSERT/UPDATE for Issabel/FreePBX tables (delete is not used). */
public final class PbxAdminWriteDao {

    private final AsteriskDb db;

    public PbxAdminWriteDao(AsteriskDb db) {
        this.db = db;
    }

    public int saveTimeGroup(PbxForms.TimeGroupForm form) throws SQLException {
        if (form.id() == null || form.id() <= 0) {
            String sql = "INSERT INTO timegroups_groups (description) VALUES (?)";
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, form.description().trim());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("No se obtuvo ID del grupo horario.");
            }
        }
        String sql = "UPDATE timegroups_groups SET description = ? WHERE id = ?";
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, form.description().trim());
            ps.setInt(2, form.id());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Grupo horario no encontrado: " + form.id());
            }
            return form.id();
        }
    }

    public int saveTimeGroupDetail(PbxForms.TimeGroupDetailForm form) throws SQLException {
        if (form.id() == null || form.id() <= 0) {
            String sql = "INSERT INTO timegroups_details (timegroupid, time, name) VALUES (?, ?, ?)";
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, form.timeGroupId());
                ps.setString(2, form.timeRule().trim());
                ps.setString(3, form.name().trim());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("No se obtuvo ID de la franja horaria.");
            }
        }
        String sql = "UPDATE timegroups_details SET time = ?, name = ? WHERE id = ? AND timegroupid = ?";
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, form.timeRule().trim());
            ps.setString(2, form.name().trim());
            ps.setInt(3, form.id());
            ps.setInt(4, form.timeGroupId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Franja horaria no encontrada: " + form.id());
            }
            return form.id();
        }
    }

    public int saveTimeCondition(PbxForms.TimeConditionForm form) throws SQLException {
        if (form.id() == null || form.id() <= 0) {
            String sql = """
                    INSERT INTO timeconditions (displayname, time, truegoto, falsegoto, deptname, generate_hint)
                    VALUES (?, ?, ?, ?, ?, 0)
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindTimeCondition(ps, form);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("No se obtuvo ID de condición horaria.");
            }
        }
        String sql = """
                UPDATE timeconditions
                SET displayname = ?, time = ?, truegoto = ?, falsegoto = ?, deptname = ?
                WHERE timeconditions_id = ?
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindTimeCondition(ps, form);
            ps.setInt(6, form.id());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Condición horaria no encontrada: " + form.id());
            }
            return form.id();
        }
    }

    private static void bindTimeCondition(PreparedStatement ps, PbxForms.TimeConditionForm form)
            throws SQLException {
        ps.setString(1, form.displayName().trim());
        ps.setInt(2, form.timeGroupId());
        ps.setString(3, emptyToNull(form.trueGoto()));
        ps.setString(4, emptyToNull(form.falseGoto()));
        ps.setString(5, emptyToNull(form.deptName()));
    }

    public void saveQueue(PbxForms.QueueForm form) throws SQLException {
        if (form.create()) {
            String sql = """
                    INSERT INTO queues_config (
                        extension, descr, grppre, alertinfo, ringing, maxwait, password,
                        ivr_id, dest, destcontinue, callback_id
                    ) VALUES (?, ?, '', '', 0, ?, '', '0', ?, '', ?)
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, form.extension().trim());
                ps.setString(2, form.description().trim());
                ps.setString(3, form.maxWait().trim());
                ps.setString(4, form.destination().trim());
                ps.setString(5, form.callbackId().trim());
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    UPDATE queues_config
                    SET descr = ?, maxwait = ?, dest = ?, callback_id = ?
                    WHERE extension = ?
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, form.description().trim());
                ps.setString(2, form.maxWait().trim());
                ps.setString(3, form.destination().trim());
                ps.setString(4, form.callbackId().trim());
                ps.setString(5, form.extension().trim());
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Cola no encontrada: " + form.extension());
                }
            }
        }
    }

    public void saveExtension(PbxForms.ExtensionForm form) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                if (form.create()) {
                    insertUser(c, form);
                    insertDevice(c, form);
                } else {
                    updateUser(c, form);
                    upsertDevice(c, form);
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void insertUser(Connection c, PbxForms.ExtensionForm form) throws SQLException {
        String sql = "INSERT INTO users (extension, name, voicemail) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, form.extension().trim());
            ps.setString(2, form.name().trim());
            ps.setString(3, emptyToNull(form.voicemail()));
            ps.executeUpdate();
        }
    }

    private static void updateUser(Connection c, PbxForms.ExtensionForm form) throws SQLException {
        String sql = "UPDATE users SET name = ?, voicemail = ? WHERE extension = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, form.name().trim());
            ps.setString(2, emptyToNull(form.voicemail()));
            ps.setString(3, form.extension().trim());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Extensión no encontrada en users: " + form.extension());
            }
        }
    }

    private static void insertDevice(Connection c, PbxForms.ExtensionForm form) throws SQLException {
        String sql = """
                INSERT INTO devices (id, tech, dial, devicetype, user, description)
                VALUES (?, ?, ?, 'fixed', ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            String ext = form.extension().trim();
            String tech = form.tech().trim().isEmpty() ? "pjsip" : form.tech().trim();
            String dial = form.dial().trim().isEmpty() ? ext : form.dial().trim();
            ps.setString(1, ext);
            ps.setString(2, tech);
            ps.setString(3, dial);
            ps.setString(4, ext);
            ps.setString(5, form.name().trim());
            ps.executeUpdate();
        }
    }

    private static void upsertDevice(Connection c, PbxForms.ExtensionForm form) throws SQLException {
        String ext = form.extension().trim();
        if (deviceExists(c, ext)) {
            String sql = "UPDATE devices SET tech = ?, dial = ?, description = ? WHERE id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                String tech = form.tech().trim().isEmpty() ? "pjsip" : form.tech().trim();
                String dial = form.dial().trim().isEmpty() ? ext : form.dial().trim();
                ps.setString(1, tech);
                ps.setString(2, dial);
                ps.setString(3, form.name().trim());
                ps.setString(4, ext);
                ps.executeUpdate();
            }
        } else {
            insertDevice(c, form);
        }
    }

    private static boolean deviceExists(Connection c, String ext) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM devices WHERE id = ? LIMIT 1")) {
            ps.setString(1, ext);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void saveTrunk(PbxForms.TrunkForm form) throws SQLException {
        if (form.create()) {
            int trunkId = form.trunkId() > 0 ? form.trunkId() : nextTrunkId();
            String sql = """
                    INSERT INTO trunks (
                        trunkid, name, tech, outcid, keepcid, maxchans, failscript,
                        dialoutprefix, channelid, disabled, provider, `continue`
                    ) VALUES (?, ?, ?, ?, 'off', '', '', '', ?, ?, ?, 'off')
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, trunkId);
                ps.setString(2, form.name().trim());
                ps.setString(3, form.tech().trim());
                ps.setString(4, form.outCid().trim());
                ps.setString(5, form.channelId().trim());
                ps.setString(6, form.disabled().trim().isEmpty() ? "off" : form.disabled().trim());
                ps.setString(7, emptyToNull(form.provider()));
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    UPDATE trunks
                    SET name = ?, outcid = ?, disabled = ?, provider = ?
                    WHERE trunkid = ? AND tech = ? AND channelid = ?
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, form.name().trim());
                ps.setString(2, form.outCid().trim());
                ps.setString(3, form.disabled().trim().isEmpty() ? "off" : form.disabled().trim());
                ps.setString(4, emptyToNull(form.provider()));
                ps.setInt(5, form.trunkId());
                ps.setString(6, form.tech().trim());
                ps.setString(7, form.channelId().trim());
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Troncal no encontrada.");
                }
            }
        }
    }

    private int nextTrunkId() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(trunkid), 0) + 1 FROM trunks");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public void saveInboundRoute(PbxForms.InboundRouteForm form) throws SQLException {
        if (form.create()) {
            String sql = """
                    INSERT INTO incoming (extension, cidnum, destination, description, mohclass)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                bindInbound(ps, form);
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    UPDATE incoming
                    SET cidnum = ?, destination = ?, description = ?, mohclass = ?
                    WHERE extension = ?
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, emptyToNull(form.cidNum()));
                ps.setString(2, emptyToNull(form.destination()));
                ps.setString(3, emptyToNull(form.description()));
                ps.setString(4, form.mohClass().trim().isEmpty() ? "default" : form.mohClass().trim());
                ps.setString(5, form.did().trim());
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Ruta entrante no encontrada: " + form.did());
                }
            }
        }
    }

    private static void bindInbound(PreparedStatement ps, PbxForms.InboundRouteForm form) throws SQLException {
        ps.setString(1, form.did().trim());
        ps.setString(2, emptyToNull(form.cidNum()));
        ps.setString(3, emptyToNull(form.destination()));
        ps.setString(4, emptyToNull(form.description()));
        ps.setString(5, form.mohClass().trim().isEmpty() ? "default" : form.mohClass().trim());
    }

    public int saveOutboundRoute(PbxForms.OutboundRouteForm form) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                int routeId;
                if (form.create()) {
                    routeId = insertOutboundRoute(c, form);
                    insertOutboundSequence(c, routeId);
                } else {
                    routeId = form.routeId();
                    updateOutboundRoute(c, form);
                }
                appendOutboundPatterns(c, routeId, form.patternsText());
                appendOutboundTrunks(c, routeId, form.trunkIdsText());
                c.commit();
                return routeId;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static int insertOutboundRoute(Connection c, PbxForms.OutboundRouteForm form) throws SQLException {
        String sql = """
                INSERT INTO outbound_routes (name, outcid, emergency_route, time_group_id)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, form.name().trim());
            ps.setString(2, emptyToNull(form.outCid()));
            ps.setString(3, emptyToNull(form.emergencyRoute()));
            if (form.timeGroupId() == null || form.timeGroupId() <= 0) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, form.timeGroupId());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new SQLException("No se obtuvo ID de ruta saliente.");
        }
    }

    private static void updateOutboundRoute(Connection c, PbxForms.OutboundRouteForm form) throws SQLException {
        String sql = """
                UPDATE outbound_routes
                SET name = ?, outcid = ?, emergency_route = ?, time_group_id = ?
                WHERE route_id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, form.name().trim());
            ps.setString(2, emptyToNull(form.outCid()));
            ps.setString(3, emptyToNull(form.emergencyRoute()));
            if (form.timeGroupId() == null || form.timeGroupId() <= 0) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, form.timeGroupId());
            }
            ps.setInt(5, form.routeId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Ruta saliente no encontrada: " + form.routeId());
            }
        }
    }

    private static void insertOutboundSequence(Connection c, int routeId) throws SQLException {
        int seq;
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(seq), 0) + 1 FROM outbound_route_sequence");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            seq = rs.getInt(1);
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO outbound_route_sequence (route_id, seq) VALUES (?, ?)")) {
            ps.setInt(1, routeId);
            ps.setInt(2, seq);
            ps.executeUpdate();
        }
    }

    private static void appendOutboundPatterns(Connection c, int routeId, String patternsText)
            throws SQLException {
        if (patternsText == null || patternsText.isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO outbound_route_patterns
                (route_id, match_pattern_prefix, match_pattern_pass, match_cid, prepend_digits)
                VALUES (?, ?, ?, '', '')
                """;
        List<String[]> rows = parsePatternLines(patternsText);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String[] row : rows) {
                if (patternExists(c, routeId, row[0], row[1])) {
                    continue;
                }
                ps.setInt(1, routeId);
                ps.setString(2, row[0]);
                ps.setString(3, row[1]);
                ps.executeUpdate();
            }
        }
    }

    private static boolean patternExists(Connection c, int routeId, String prefix, String pass)
            throws SQLException {
        String sql = """
                SELECT 1 FROM outbound_route_patterns
                WHERE route_id = ? AND match_pattern_prefix = ? AND match_pattern_pass = ?
                  AND match_cid = '' AND prepend_digits = ''
                LIMIT 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, routeId);
            ps.setString(2, prefix);
            ps.setString(3, pass);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<String[]> parsePatternLines(String text) {
        List<String[]> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            String prefix;
            String pass;
            if (t.contains("|")) {
                String[] p = t.split("\\|", -1);
                prefix = p[0].trim();
                pass = p.length > 1 ? p[1].trim() : "";
            } else {
                prefix = t;
                pass = "";
            }
            out.add(new String[]{prefix, pass});
        }
        return out;
    }

    private static void appendOutboundTrunks(Connection c, int routeId, String trunkIdsText)
            throws SQLException {
        if (trunkIdsText == null || trunkIdsText.isBlank()) {
            return;
        }
        int nextSeq = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(seq), 0) FROM outbound_route_trunks WHERE route_id = ?")) {
            ps.setInt(1, routeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                nextSeq = rs.getInt(1);
            }
        }
        String sql = "INSERT INTO outbound_route_trunks (route_id, trunk_id, seq) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String part : trunkIdsText.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                int trunkId = Integer.parseInt(t);
                if (trunkLinkExists(c, routeId, trunkId)) {
                    continue;
                }
                nextSeq++;
                ps.setInt(1, routeId);
                ps.setInt(2, trunkId);
                ps.setInt(3, nextSeq);
                ps.executeUpdate();
            }
        }
    }

    private static boolean trunkLinkExists(Connection c, int routeId, int trunkId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM outbound_route_trunks WHERE route_id = ? AND trunk_id = ? LIMIT 1")) {
            ps.setInt(1, routeId);
            ps.setInt(2, trunkId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String emptyToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    public int saveIvr(PbxForms.IvrDetailForm form) throws SQLException {
        if (form.id() == null || form.id() <= 0) {
            String sql = """
                    INSERT INTO ivr_details (
                        name, description, announcement, directdial,
                        timeout_time, timeout_destination, invalid_destination,
                        invalid_append_announce, invalid_ivr_ret,
                        timeout_append_announce, timeout_ivr_ret, rvolume
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, 1, 0, '')
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindIvr(ps, form);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("No se obtuvo ID del IVR.");
            }
        }
        String sql = """
                UPDATE ivr_details
                SET name = ?, description = ?, announcement = ?, directdial = ?,
                    timeout_time = ?, timeout_destination = ?, invalid_destination = ?
                WHERE id = ?
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindIvr(ps, form);
            ps.setInt(8, form.id());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("IVR no encontrado: " + form.id());
            }
            return form.id();
        }
    }

    private static void bindIvr(PreparedStatement ps, PbxForms.IvrDetailForm form) throws SQLException {
        ps.setString(1, form.name().trim());
        ps.setString(2, emptyToNull(form.description()));
        if (form.announcement() == null || form.announcement() <= 0) {
            ps.setNull(3, Types.INTEGER);
        } else {
            ps.setInt(3, form.announcement());
        }
        ps.setString(4, form.directDial() == null ? "" : form.directDial().trim());
        int timeout = form.timeoutTime() == null ? 10 : form.timeoutTime();
        ps.setInt(5, timeout);
        ps.setString(6, emptyToNull(form.timeoutDestination()));
        ps.setString(7, emptyToNull(form.invalidDestination()));
    }

    public void saveIvrEntry(PbxForms.IvrEntryForm form) throws SQLException {
        if (form.create()) {
            String sql = "INSERT INTO ivr_entries (ivr_id, selection, dest, ivr_ret) VALUES (?, ?, ?, ?)";
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, form.ivrId());
                ps.setString(2, form.selection().trim());
                ps.setString(3, form.dest().trim());
                ps.setInt(4, form.ivrRet());
                ps.executeUpdate();
            }
            return;
        }
        String oldSel = form.originalSelection() == null ? form.selection() : form.originalSelection();
        if (!oldSel.trim().equals(form.selection().trim())) {
            try (Connection c = db.open();
                 PreparedStatement del = c.prepareStatement(
                         "DELETE FROM ivr_entries WHERE ivr_id = ? AND selection = ?")) {
                del.setInt(1, form.ivrId());
                del.setString(2, oldSel.trim());
                del.executeUpdate();
            }
            String sql = "INSERT INTO ivr_entries (ivr_id, selection, dest, ivr_ret) VALUES (?, ?, ?, ?)";
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, form.ivrId());
                ps.setString(2, form.selection().trim());
                ps.setString(3, form.dest().trim());
                ps.setInt(4, form.ivrRet());
                ps.executeUpdate();
            }
            return;
        }
        String sql = """
                UPDATE ivr_entries SET dest = ?, ivr_ret = ?
                WHERE ivr_id = ? AND selection = ?
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, form.dest().trim());
            ps.setInt(2, form.ivrRet());
            ps.setInt(3, form.ivrId());
            ps.setString(4, form.selection().trim());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Opción IVR no encontrada: " + form.selection());
            }
        }
    }

    public int saveAnnouncement(PbxForms.AnnouncementForm form) throws SQLException {
        if (form.id() == null || form.id() <= 0) {
            String sql = """
                    INSERT INTO announcement (
                        description, recording_id, post_dest, repeat_msg,
                        allow_skip, noanswer, return_ivr
                    ) VALUES (?, ?, ?, ?, 1, 0, ?)
                    """;
            try (Connection c = db.open();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindAnnouncement(ps, form);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("No se obtuvo ID del anuncio.");
            }
        }
        String sql = """
                UPDATE announcement
                SET description = ?, recording_id = ?, post_dest = ?, repeat_msg = ?, return_ivr = ?
                WHERE announcement_id = ?
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindAnnouncement(ps, form);
            ps.setInt(6, form.id());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Anuncio no encontrado: " + form.id());
            }
            return form.id();
        }
    }

    private static void bindAnnouncement(PreparedStatement ps, PbxForms.AnnouncementForm form)
            throws SQLException {
        ps.setString(1, form.description().trim());
        if (form.recordingId() == null || form.recordingId() <= 0) {
            ps.setNull(2, Types.INTEGER);
        } else {
            ps.setInt(2, form.recordingId());
        }
        ps.setString(3, emptyToNull(form.postDest()));
        ps.setString(4, emptyToNull(form.repeatMsg()));
        ps.setInt(5, form.returnIvr());
    }

    public void deleteAnnouncement(int announcementId) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM announcement WHERE announcement_id = ?")) {
            ps.setInt(1, announcementId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Anuncio no encontrado: " + announcementId);
            }
        }
    }
}
