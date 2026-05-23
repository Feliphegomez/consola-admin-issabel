package dn.demedallo.admin.db;

import dn.demedallo.admin.model.IncomingPanelSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Historical call stats for incoming campaigns (call_entry), same SQL as Issabel web panel.
 */
public final class IncomingCampaignStatsDao {

    private final CallCenterDb db;

    public IncomingCampaignStatsDao(CallCenterDb db) {
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
                SELECT status, COUNT(*) AS cnt,
                       SUM(IFNULL(duration, 0)) AS total_duration,
                       MAX(IFNULL(duration, 0)) AS max_duration
                FROM call_entry
                WHERE id_campaign IN (%s)
                  AND datetime_entry_queue >= ?
                  AND datetime_entry_queue <= ?
                GROUP BY status
                """.formatted(placeholders);

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : campaignIds) {
                ps.setInt(i++, id);
            }
            ps.setString(i++, datetimeStart);
            ps.setString(i, datetimeEnd);
            long totalSec = 0;
            long maxDur = 0;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt("cnt");
                    int dur = rs.getInt("total_duration");
                    int max = rs.getInt("max_duration");
                    snap.statusCount.total += count;
                    totalSec += dur;
                    maxDur = Math.max(maxDur, max);
                    if (status == null) {
                        continue;
                    }
                    switch (status) {
                        case "terminada" -> {
                            snap.statusCount.success += count;
                            snap.statusCount.finished += count;
                        }
                        case "abandonada" -> snap.statusCount.abandoned += count;
                        case "fin-monitoreo" -> snap.statusCount.lostTrack += count;
                        case "activa" -> snap.statusCount.success += count;
                        case "en-cola" -> snap.statusCount.onQueue += count;
                        default -> {
                        }
                    }
                }
            }
            snap.stats.totalSec = totalSec;
            snap.stats.maxDurationSec = maxDur;
        }
    }

    public record IncomingCampaignRef(int id, String name, String queue) {
    }

    public java.util.Map<Integer, String> loadQueueByCampaignId() throws Exception {
        java.util.Map<Integer, String> out = new java.util.LinkedHashMap<>();
        String sql = """
                SELECT ce.id, qce.queue
                FROM campaign_entry ce
                INNER JOIN queue_call_entry qce ON ce.id_queue_call_entry = qce.id
                WHERE ce.estatus = 'A'
                  AND ce.datetime_init <= CURDATE()
                  AND ce.datetime_end >= CURDATE()
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

    public List<IncomingCampaignRef> loadActiveIncomingCampaigns() throws Exception {
        String sql = """
                SELECT ce.id, ce.name, qce.queue
                FROM campaign_entry ce
                INNER JOIN queue_call_entry qce ON ce.id_queue_call_entry = qce.id
                WHERE ce.estatus = 'A'
                  AND ce.datetime_init <= CURDATE()
                  AND ce.datetime_end >= CURDATE()
                ORDER BY ce.name
                """;
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<IncomingCampaignRef> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new IncomingCampaignRef(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("queue")));
            }
            return out;
        }
    }
}
