package dn.demedallo.admin.util;

import java.util.ArrayList;
import java.util.List;

/** Resolves Issabel/FreePBX MixMonitor paths (same rules as calls_detail module). */
public final class CallRecordingPathUtil {

    public static final String DEFAULT_MONITOR_DIR = "/var/spool/asterisk/monitor";

    private CallRecordingPathUtil() {
    }

    public static String resolveFullPath(String recordingFile) {
        if (recordingFile == null || recordingFile.isBlank()) {
            return "";
        }
        String t = recordingFile.trim();
        if (t.startsWith("/")) {
            return t;
        }
        return DEFAULT_MONITOR_DIR + "/" + t;
    }

    /** Candidate remote paths (handles .wav49 → .WAV fallback). */
    public static List<String> candidatePaths(String recordingFile) {
        List<String> out = new ArrayList<>();
        String full = resolveFullPath(recordingFile);
        if (full.isBlank()) {
            return out;
        }
        out.add(full);
        if (full.endsWith(".wav49")) {
            out.add(full.substring(0, full.length() - 6) + ".WAV");
        }
        return out;
    }

    public static String shellQuote(String path) {
        if (path == null) {
            return "''";
        }
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
