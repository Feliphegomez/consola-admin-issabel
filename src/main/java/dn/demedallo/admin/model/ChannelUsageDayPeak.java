package dn.demedallo.admin.model;

import java.time.LocalDate;

/**
 * Peak concurrent channel usage for one calendar day (within configured hour window).
 */
public record ChannelUsageDayPeak(
        LocalDate date,
        String peakTimeLabel,
        int peakFiltered,
        int totalPeak,
        int sipPeak,
        int dahdiPeak,
        int iaxPeak,
        int localPeak,
        int h323Peak) {

    public String dateLabel() {
        return date.toString();
    }
}
