package dn.demedallo.admin.service;

import dn.demedallo.admin.db.AsteriskDb;
import dn.demedallo.admin.db.ChannelUsageDao;
import dn.demedallo.admin.model.CdrChannelLeg;
import dn.demedallo.admin.model.ChannelUsageBucket;
import dn.demedallo.admin.util.AdminDbSettings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimates concurrent Asterisk channel usage from CDR legs (similar shape to Issabel «Uso de Canales»).
 */
public final class ChannelUsageService {

    public enum ChannelTech {
        SIP, DAHDI, IAX, LOCAL, H323, UNKNOWN
    }

    private static final DateTimeFormatter BUCKET_LABEL =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final ChannelUsageDao dao;

    public ChannelUsageService(AdminDbSettings settings) {
        this.dao = new ChannelUsageDao(new AsteriskDb(settings));
    }

    public List<ChannelUsageBucket> loadUsage(String datetimeStart, String datetimeEnd,
            int intervalMinutes) throws Exception {
        if (intervalMinutes < 5 || intervalMinutes > 120) {
            throw new IllegalArgumentException("Intervalo inválido (use 5–120 minutos).");
        }
        long rangeStart = parseSqlDatetime(datetimeStart);
        long rangeEnd = parseSqlDatetime(datetimeEnd);
        if (rangeEnd <= rangeStart) {
            throw new IllegalArgumentException("Rango de fechas inválido.");
        }
        long maxSpanSec = 14L * 24 * 3600;
        if (rangeEnd - rangeStart > maxSpanSec) {
            throw new IllegalArgumentException("Máximo 14 días por consulta.");
        }

        List<CdrChannelLeg> legs = dao.loadChannelLegs(datetimeStart, datetimeEnd);
        return aggregate(legs, rangeStart, rangeEnd, intervalMinutes);
    }

    public static ChannelTech classifyChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return ChannelTech.UNKNOWN;
        }
        String upper = channel.toUpperCase();
        if (upper.startsWith("PJSIP/") || upper.startsWith("SIP/")) {
            return ChannelTech.SIP;
        }
        if (upper.startsWith("DAHDI/")) {
            return ChannelTech.DAHDI;
        }
        if (upper.startsWith("IAX2/") || upper.startsWith("IAX/")) {
            return ChannelTech.IAX;
        }
        if (upper.startsWith("LOCAL/")) {
            return ChannelTech.LOCAL;
        }
        if (upper.startsWith("H323/")) {
            return ChannelTech.H323;
        }
        return ChannelTech.UNKNOWN;
    }

    static List<ChannelUsageBucket> aggregate(List<CdrChannelLeg> legs,
            long rangeStartSec, long rangeEndSec, int intervalMinutes) {
        long stepSec = intervalMinutes * 60L;
        List<ChannelUsageBucket> buckets = new ArrayList<>();
        for (long bucketEnd = alignBucketEnd(rangeStartSec, stepSec);
             bucketEnd <= rangeEndSec;
             bucketEnd += stepSec) {
            int total = 0;
            int sip = 0;
            int dahdi = 0;
            int iax = 0;
            int local = 0;
            int h323 = 0;
            for (CdrChannelLeg leg : legs) {
                if (leg.startEpochSec() <= bucketEnd && bucketEnd < leg.endEpochSec()) {
                    total++;
                    switch (leg.tech()) {
                        case SIP -> sip++;
                        case DAHDI -> dahdi++;
                        case IAX -> iax++;
                        case LOCAL -> local++;
                        case H323 -> h323++;
                        default -> { }
                    }
                }
            }
            String label = BUCKET_LABEL.format(Instant.ofEpochSecond(bucketEnd));
            buckets.add(new ChannelUsageBucket(label, bucketEnd, total, sip, dahdi, iax, local, h323));
        }
        return buckets;
    }

    private static long alignBucketEnd(long rangeStartSec, long stepSec) {
        long aligned = ((rangeStartSec / stepSec) + 1) * stepSec;
        return aligned;
    }

    private static long parseSqlDatetime(String value) {
        LocalDateTime ldt = LocalDateTime.parse(value.trim(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

}
