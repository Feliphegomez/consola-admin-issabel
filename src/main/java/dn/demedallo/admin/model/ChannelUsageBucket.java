package dn.demedallo.admin.model;

/**
 * Active channel count at one time bucket (end of interval).
 */
public record ChannelUsageBucket(
        String timeLabel,
        long bucketEndEpochSec,
        int total,
        int sip,
        int dahdi,
        int iax,
        int local,
        int h323) {
}
