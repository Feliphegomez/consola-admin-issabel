package dn.demedallo.admin.model;

/** Call row that has at least one recording in {@code call_recording}. */
public record CallWithRecordingsRow(PhoneTraceCallRow call, int recordingCount) {
}
