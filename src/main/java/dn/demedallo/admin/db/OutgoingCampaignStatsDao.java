package dn.demedallo.admin.db;

import dn.demedallo.admin.model.IncomingPanelSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Historical call stats for outgoing campaigns ({@code calls} table), same logic as Issabel web panel.
 */
public final class OutgoingCampaignStatsDao {

    private final CallCenterDb db;

    public OutgoingCampaignStatsDao(CallCenterDb db) {
        this.db = db;
    }

    public void fillDbStats(IncomingPanelSnapshot snap, List<Integer> campaignIds,
            String datetimeStart, String datetimeEnd) throws Exception {
        snap.statusCount.total = 0;
        snap.statusCount.onQueue = 0;
        snap.statusCount.success = 0;
        snap.statusCount.abandoned = 0;
        snap.statusCount.finished = 0;
        snap.statusCount.lostTrack = 0;
        snap.stats.totalSec = 0;
        snap.stats.maxDurationSec = 0;
        if (campaignIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", campaignIds.stream().map(id -> "?").toList());

        String sql = """
                SELECT c.status,
                       COUNT(*) AS cnt,
                       SUM(CASE WHEN cc.id_call IS NULL THEN 1 ELSE 0 END) AS completed_cnt,
                       SUM(IFNULL(c.retries, 0)) AS total_retries,
                       SUM(IFNULL(c.duration, 0)) AS total_duration,
                       MAX(IFNULL(c.duration, 0)) AS max_duration
                FROM calls c
                LEFT JOIN current_calls cc ON cc.id_call = c.id
                WHERE c.id_campaign IN (%s)
                  AND c.fecha_llamada >= ?
                  AND c.fecha_llamada <= ?
                GROUP BY c.status
                """.formatted(placeholders);

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : campaignIds) {
                ps.setInt(i++, id);
            }
            ps.setString(i++, datetimeStart);
            ps.setString(i, datetimeEnd);

            int totalRetries = 0;
            long totalSec = 0;
            long maxDur = 0;

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt("cnt");
                    int completedCount = rs.getInt("completed_cnt");
                    int retriesCount = rs.getInt("total_retries");
                    totalRetries += retriesCount;
                    totalSec += rs.getLong("total_duration");
                    maxDur = Math.max(maxDur, rs.getLong("max_duration"));
                    if (status == null) {
                        continue;
                    }
                    switch (status) {
                        case "Success" -> {
                            snap.statusCount.success += count;
                            snap.statusCount.finished += completedCount;
                            snap.statusCount.abandoned += Math.max(0, retriesCount - count);
                        }
                        case "Abandoned", "ShortCall" -> snap.statusCount.abandoned += retriesCount;
                        case "NoAnswer", "Failure" -> {
                            snap.statusCount.lostTrack += completedCount;
                            snap.statusCount.abandoned += Math.max(0, retriesCount - count);
                        }
                        case "OnQueue" -> snap.statusCount.abandoned += Math.max(0, retriesCount - count);
                        default -> {
                        }
                    }
                }
            }
            snap.statusCount.total = totalRetries;
            snap.stats.totalSec = totalSec;
            snap.stats.maxDurationSec = maxDur;
        }

        String sqlQueued = """
                SELECT COUNT(*) AS cnt FROM calls
                WHERE id_campaign IN (%s)
                  AND fecha_llamada >= ?
                  AND fecha_llamada <= ?
                  AND status = 'OnQueue'
                """.formatted(placeholders);
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sqlQueued)) {
            int i = 1;
            for (Integer id : campaignIds) {
                ps.setInt(i++, id);
            }
            ps.setString(i++, datetimeStart);
            ps.setString(i, datetimeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    snap.statusCount.onQueue = rs.getInt("cnt");
                }
            }
        }
    }

    public record OutgoingCampaignRef(int id, String name, String queue, String status) {
    }

    public java.util.Map<Integer, String> loadQueueByCampaignId() throws Exception {
        java.util.Map<Integer, String> out = new java.util.LinkedHashMap<>();
        String sql = """
                SELECT id, queue FROM campaign
                WHERE estatus <> 'I'
                  AND datetime_init <= CURDATE()
                  AND datetime_end >= CURDATE()
                """;
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String q = rs.getString("queue");
                out.put(rs.getInt("id"), q == null ? "" : q.trim());
            }
        }
        return out;
    }

    public List<IncomingPanelSnapshot.PanelPendingCallRow> loadPendingOutgoingCalls(
            List<Integer> campaignIds, int limit)
            throws Exception {
        List<IncomingPanelSnapshot.PanelPendingCallRow> out = new ArrayList<>();
        if (campaignIds.isEmpty()) {
            return out;
        }
        int maxRows = Math.max(1, Math.min(limit, 2000));
        String placeholders = String.join(",", campaignIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT c.id AS call_id,
                       camp.name AS campaign_name,
                       IFNULL(c.phone, '') AS phone,
                       IFNULL(c.retries, 0) AS retries,
                       IFNULL(c.agent, '') AS agent,
                       c.date_init,
                       c.date_end,
                       c.time_init,
                       c.time_end
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                WHERE c.id_campaign IN (%s)
                  AND c.dnc = 0
                  AND c.status IS NULL
                  AND CURDATE() BETWEEN c.date_init AND c.date_end
                ORDER BY c.retries DESC, c.date_end, c.time_end, c.id
                LIMIT %d
                """.formatted(placeholders, maxRows);
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : campaignIds) {
                ps.setInt(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    IncomingPanelSnapshot.PanelPendingCallRow row =
                            new IncomingPanelSnapshot.PanelPendingCallRow();
                    row.callId = rs.getInt("call_id");
                    row.campaignName = nullToDash(rs.getString("campaign_name"));
                    row.phone = nullToDash(rs.getString("phone"));
                    row.retries = String.valueOf(rs.getInt("retries"));
                    row.agent = formatAgent(rs.getString("agent"));
                    row.schedule = formatCallSchedule(
                            rs.getString("date_init"),
                            rs.getString("date_end"),
                            rs.getString("time_init"),
                            rs.getString("time_end"));
                    out.add(row);
                }
            }
        }
        return out;
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }

    private static String formatAgent(String agent) {
        if (agent == null || agent.isBlank()) {
            return "-";
        }
        return agent.trim();
    }

    private static String formatCallSchedule(String dateInit, String dateEnd,
            String timeInit, String timeEnd) {
        String d1 = dateInit == null ? "" : dateInit.trim();
        String d2 = dateEnd == null ? "" : dateEnd.trim();
        String t1 = timeInit == null ? "" : timeInit.trim();
        String t2 = timeEnd == null ? "" : timeEnd.trim();
        if (d1.isEmpty() && d2.isEmpty()) {
            return "-";
        }
        if (t1.isEmpty() && t2.isEmpty()) {
            return d1.equals(d2) ? d1 : d1 + " … " + d2;
        }
        String window = (t1.isEmpty() ? "00:00:00" : t1) + " - " + (t2.isEmpty() ? "23:59:59" : t2);
        return (d1.equals(d2) ? d1 : d1 + " … " + d2) + " " + window;
    }

    public List<OutgoingCampaignRef> loadActiveOutgoingCampaigns() throws Exception {
        String sql = """
                SELECT id, name, queue, estatus
                FROM campaign
                WHERE estatus <> 'I'
                  AND datetime_init <= CURDATE()
                  AND datetime_end >= CURDATE()
                ORDER BY name
                """;
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<OutgoingCampaignRef> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new OutgoingCampaignRef(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("queue") == null ? "" : rs.getString("queue").trim(),
                        rs.getString("estatus") == null ? "" : rs.getString("estatus").trim()));
            }
            return out;
        }
    }
}
