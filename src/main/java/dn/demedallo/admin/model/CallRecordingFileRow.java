package dn.demedallo.admin.model;

/** One row from {@code call_recording} (MixMonitor file metadata). */
public final class CallRecordingFileRow {

    public final int id;
    public final String datetimeEntry;
    public final String uniqueid;
    public final String channel;
    public final String recordingFile;
    public final String displayName;

    public CallRecordingFileRow(int id, String datetimeEntry, String uniqueid, String channel,
            String recordingFile) {
        this.id = id;
        this.datetimeEntry = datetimeEntry == null ? "" : datetimeEntry;
        this.uniqueid = uniqueid == null ? "" : uniqueid;
        this.channel = channel == null ? "" : channel;
        this.recordingFile = recordingFile == null ? "" : recordingFile;
        this.displayName = basename(recordingFile);
    }

    private static String basename(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
