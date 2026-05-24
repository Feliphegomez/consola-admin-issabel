package dn.demedallo.admin.db;

import dn.demedallo.admin.model.CdrChannelLeg;
import dn.demedallo.admin.service.ChannelUsageService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads CDR rows to estimate concurrent channel usage from {@code asteriskcdrdb.cdr}.
 */
public final class ChannelUsageDao {

    private static final int MAX_CDR_ROWS = 80_000;

    private final AsteriskDb db;

    public ChannelUsageDao(AsteriskDb db) {
        this.db = db;
    }

    public List<CdrChannelLeg> loadChannelLegs(String datetimeStart, String datetimeEnd) throws SQLException {
        String sql = """
                SELECT calldate, duration, billsec, channel, dstchannel
                FROM cdr
                WHERE calldate >= ? AND calldate <= ?
                ORDER BY calldate
                LIMIT
                """ + MAX_CDR_ROWS;
        List<CdrChannelLeg> legs = new ArrayList<>();
        try (Connection c = db.openCdr();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, datetimeStart);
            ps.setString(2, datetimeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long start = toEpochSec(rs.getTimestamp("calldate"));
                    int duration = Math.max(1, Math.max(rs.getInt("duration"), rs.getInt("billsec")));
                    long end = start + duration;
                    addLeg(legs, start, end, rs.getString("channel"));
                    addLeg(legs, start, end, rs.getString("dstchannel"));
                }
            }
        } catch (SQLException ex) {
            if (isMissingTable(ex)) {
                throw new SQLException(
                        "Tabla cdr no encontrada. Revise «Base CDR» en login (por defecto asteriskcdrdb).", ex);
            }
            throw ex;
        }
        return legs;
    }

    private static void addLeg(List<CdrChannelLeg> legs, long start, long end, String channel) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        ChannelUsageService.ChannelTech tech = ChannelUsageService.classifyChannel(channel.trim());
        if (tech != ChannelUsageService.ChannelTech.UNKNOWN) {
            legs.add(new CdrChannelLeg(start, end, tech));
        }
    }

    private static long toEpochSec(Timestamp ts) {
        if (ts == null) {
            return 0L;
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private static boolean isMissingTable(SQLException ex) {
        String msg = ex.getMessage();
        return msg != null && (msg.contains("doesn't exist") || msg.contains("Unknown table"));
    }
}
