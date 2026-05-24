package dn.demedallo.admin.service;

import dn.demedallo.admin.db.AsteriskDb;
import dn.demedallo.admin.db.ChannelUsageDao;
import dn.demedallo.admin.model.CdrChannelLeg;
import dn.demedallo.admin.model.ChannelUsageBucket;
import dn.demedallo.admin.model.ChannelUsageDayPeak;
import dn.demedallo.admin.util.AdminDbSettings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimates concurrent Asterisk channel usage from CDR legs (similar shape to Issabel «Uso de Canales»).
 */
public final class ChannelUsageService {

    public enum ChannelTech {
        SIP, DAHDI, IAX, LOCAL, H323, UNKNOWN
    }

    /** Metric used when scanning days against a channel threshold. */
    public enum PeakMetric {
        TOTAL, SIP, DAHDI, IAX, LOCAL, H323
    }

    private static final int MAX_DAY_SCAN = 90;

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

    /**
     * Lists days in {@code [dateFrom, dateTo]} whose peak concurrent channels (in the hour window)
     * meet or exceed {@code minChannels} for {@code metric}.
     */
    public List<ChannelUsageDayPeak> findDaysMeetingThreshold(LocalDate dateFrom, LocalDate dateTo,
            int hourFrom, int hourTo, int intervalMinutes, int minChannels, PeakMetric metric)
            throws Exception {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("Rango de fechas inválido.");
        }
        long days = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (days > MAX_DAY_SCAN) {
            throw new IllegalArgumentException("Máximo " + MAX_DAY_SCAN + " días por búsqueda de picos.");
        }
        if (intervalMinutes < 5 || intervalMinutes > 120) {
            throw new IllegalArgumentException("Intervalo inválido (use 5–120 minutos).");
        }
        if (minChannels < 1) {
            throw new IllegalArgumentException("El umbral debe ser al menos 1 canal.");
        }

        List<ChannelUsageDayPeak> matches = new ArrayList<>();
        for (LocalDate day = dateFrom; !day.isAfter(dateTo); day = day.plusDays(1)) {
            String start = day + String.format(" %02d:00:00", hourFrom);
            String end = day + String.format(" %02d:59:59", hourTo);
            long rangeStart = parseSqlDatetime(start);
            long rangeEnd = parseSqlDatetime(end);
            if (rangeEnd <= rangeStart) {
                continue;
            }
            List<CdrChannelLeg> legs = dao.loadChannelLegs(start, end);
            List<ChannelUsageBucket> buckets = aggregate(legs, rangeStart, rangeEnd, intervalMinutes);
            ChannelUsageDayPeak peak = peakForDay(day, buckets, metric);
            if (peak != null && peak.peakFiltered() >= minChannels) {
                matches.add(peak);
            }
        }
        return matches;
    }

    private static ChannelUsageDayPeak peakForDay(LocalDate day, List<ChannelUsageBucket> buckets,
            PeakMetric metric) {
        if (buckets.isEmpty()) {
            return null;
        }
        int maxTotal = 0;
        int maxSip = 0;
        int maxDahdi = 0;
        int maxIax = 0;
        int maxLocal = 0;
        int maxH323 = 0;
        String peakTime = buckets.get(0).timeLabel();
        int peakFiltered = 0;
        for (ChannelUsageBucket b : buckets) {
            maxTotal = Math.max(maxTotal, b.total());
            maxSip = Math.max(maxSip, b.sip());
            maxDahdi = Math.max(maxDahdi, b.dahdi());
            maxIax = Math.max(maxIax, b.iax());
            maxLocal = Math.max(maxLocal, b.local());
            maxH323 = Math.max(maxH323, b.h323());
            int v = metricValue(b, metric);
            if (v >= peakFiltered) {
                peakFiltered = v;
                peakTime = b.timeLabel();
            }
        }
        return new ChannelUsageDayPeak(day, peakTime, peakFiltered,
                maxTotal, maxSip, maxDahdi, maxIax, maxLocal, maxH323);
    }

    static int metricValue(ChannelUsageBucket b, PeakMetric metric) {
        return switch (metric) {
            case SIP -> b.sip();
            case DAHDI -> b.dahdi();
            case IAX -> b.iax();
            case LOCAL -> b.local();
            case H323 -> b.h323();
            case TOTAL -> b.total();
        };
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
