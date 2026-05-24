package dn.demedallo.admin.service;

import dn.demedallo.admin.db.AsteriskDb;

import dn.demedallo.admin.db.CallCenterDb;

import dn.demedallo.admin.db.CallRecordingDao;

import dn.demedallo.admin.db.CdrRecordingDao;

import dn.demedallo.admin.db.FailedShortCallsDao.CallDirectionFilter;

import dn.demedallo.admin.db.PhoneTraceSearchDao;

import dn.demedallo.admin.model.CallLogInvestigationResult;
import dn.demedallo.admin.model.CallProblemDiagnosis;
import dn.demedallo.admin.model.CallRecordingFileRow;

import dn.demedallo.admin.model.CallWithRecordingsRow;

import dn.demedallo.admin.model.PhoneTraceCallRow;

import dn.demedallo.admin.model.PhoneTraceDetail;

import dn.demedallo.admin.util.AdminDbSettings;

import dn.demedallo.admin.util.AdminLogSettings;

import dn.demedallo.admin.util.CallRecordingPathUtil;

import dn.demedallo.admin.util.ShiftDatetimeRange;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Optional;

import java.util.Set;

/**

 * Search call recordings from Issabel CDR and/or call_center {@code call_recording}.

 */

public final class CallRecordingService {

    public enum RecordingSource {

        /** Same data as Issabel web admin CDR (asteriskcdrdb.cdr). */

        CDR,

        /** Campaign dialer recordings linked in call_center. */

        CALL_CENTER,

        BOTH

    }

    private final boolean dbEnabled;

    private final CallRecordingDao recordingDao;

    private final CdrRecordingDao cdrDao;

    private final PhoneTraceSearchService traceService;

    private final CdrTraceService cdrTraceService;

    private final RemoteLogTailService ssh = new RemoteLogTailService();

    private final AdminLogSettings logSettings;

    private final String sshHost;

    public CallRecordingService(AdminDbSettings dbSettings, String eccpHost) {

        this.logSettings = AdminLogSettings.load();

        this.sshHost = eccpHost == null || eccpHost.isBlank() ? "127.0.0.1" : eccpHost.trim();

        this.dbEnabled = dbSettings != null && dbSettings.dbEnabled && dbSettings.isConfigured();

        if (dbEnabled) {

            CallCenterDb db = new CallCenterDb(dbSettings);

            AsteriskDb asteriskDb = new AsteriskDb(dbSettings);

            this.recordingDao = new CallRecordingDao(db);

            this.cdrDao = new CdrRecordingDao(asteriskDb);

        } else {

            this.recordingDao = null;

            this.cdrDao = null;

        }

        this.traceService = new PhoneTraceSearchService(dbSettings, eccpHost);
        this.cdrTraceService = dbSettings != null ? new CdrTraceService(dbSettings) : null;

    }

    public boolean isDbEnabled() {

        return dbEnabled;

    }

    public boolean isSshReady() {

        return logSettings.isSshReady();

    }

    public String normalizePhone(String input) {

        return PhoneTraceSearchDao.normalizePhoneQuery(input);

    }

    public List<CallWithRecordingsRow> search(String phoneInput, ShiftDatetimeRange range,

            CallDirectionFilter filter, RecordingSource source) throws Exception {

        if (!dbEnabled) {

            return List.of();

        }

        String digits = normalizePhone(phoneInput);

        String phoneLike = digits.isEmpty() ? null : "%" + digits + "%";

        RecordingSource src = source == null ? RecordingSource.CDR : source;

        List<CallWithRecordingsRow> out = new ArrayList<>();

        Set<String> seen = new LinkedHashSet<>();

        if (src == RecordingSource.CDR || src == RecordingSource.BOTH) {

            for (CallWithRecordingsRow row : cdrDao.searchWithRecordings(phoneLike, range.start, range.end, filter)) {

                String key = row.call().uniqueid;

                if (key.isBlank() || key.equals("-")) {

                    key = "cdr-" + row.call().callDatetime + "-" + row.call().phone;

                }

                if (seen.add(key)) {

                    out.add(row);

                }

            }

        }

        if (src == RecordingSource.CALL_CENTER || src == RecordingSource.BOTH) {

            for (CallWithRecordingsRow row : recordingDao.searchCallsWithRecordings(

                    phoneLike, range.start, range.end, filter)) {

                String key = row.call().uniqueid;

                if (key.isBlank() || key.equals("-")) {

                    key = "cc-" + row.call().callId;

                }

                if (seen.add(key)) {

                    out.add(row);

                }

            }

        }

        out.sort(Comparator.comparing((CallWithRecordingsRow r) -> r.call().callDatetime).reversed());

        return out;

    }

    public List<CallRecordingFileRow> listFiles(PhoneTraceCallRow call) throws Exception {

        if (call == null) {

            return List.of();

        }

        if (call.isCdrOnly()) {

            return List.of(new CallRecordingFileRow(

                    0,

                    call.callDatetime,

                    call.uniqueid,

                    call.cdrChannel,

                    call.cdrRecordingFile));

        }

        if (!dbEnabled || recordingDao == null) {

            return List.of();

        }

        return recordingDao.listRecordingFiles(call);

    }

    public PhoneTraceDetail loadTrace(PhoneTraceCallRow call) throws Exception {
        if (call == null) {
            return traceService.loadTraceDetail(null);
        }
        if (!call.isCdrOnly()) {
            return traceService.loadTraceDetail(call);
        }
        if (dbEnabled && call.uniqueid != null && !call.uniqueid.isBlank() && !"-".equals(call.uniqueid)) {
            Optional<PhoneTraceCallRow> linked = traceService.findCallByUniqueid(call.uniqueid);
            if (linked.isPresent() && linked.get().callId > 0) {
                return traceService.loadTraceDetail(linked.get());
            }
        }
        if (cdrTraceService != null) {
            return cdrTraceService.loadCdrTraceDetail(call);
        }
        return new PhoneTraceDetail(call, List.of(),
                CallLogInvestigationResult.skipped("CDR no disponible."),
                call.statusLabel,
                new CallProblemDiagnosis(CallProblemDiagnosis.Origin.UNKNOWN, "CDR", "", List.of()));
    }

    public String resolveRemotePath(CallRecordingFileRow row) throws Exception {

        requireSsh();

        for (String candidate : CallRecordingPathUtil.candidatePaths(row.recordingFile)) {

            if (ssh.remoteFileExists(sshHost, logSettings.sshPort, logSettings.sshUser,

                    logSettings.sshPassword, candidate)) {

                return candidate;

            }

        }

        throw new java.io.FileNotFoundException(

                "Archivo no encontrado en servidor: " + row.recordingFile

                        + " (revise /var/spool/asterisk/monitor/)");

    }

    public Path downloadToTemp(CallRecordingFileRow row) throws Exception {

        String remote = resolveRemotePath(row);

        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "demedallo-admin-recordings");

        Files.createDirectories(dir);

        String safeName = row.displayName.isBlank() ? "recording-" + row.id + ".wav" : row.displayName;

        safeName = safeName.replaceAll("[\\\\/:*?\"<>|]", "_");

        Path local = dir.resolve(safeName);

        ssh.downloadFile(sshHost, logSettings.sshPort, logSettings.sshUser,

                logSettings.sshPassword, remote, local);

        return local;

    }

    public void downloadTo(CallRecordingFileRow row, Path target) throws Exception {

        String remote = resolveRemotePath(row);

        ssh.downloadFile(sshHost, logSettings.sshPort, logSettings.sshUser,

                logSettings.sshPassword, remote, target);

    }

    public void deleteRecording(PhoneTraceCallRow call, CallRecordingFileRow row) throws Exception {

        requireSsh();

        if (row != null && isSshReady()) {

            for (String candidate : CallRecordingPathUtil.candidatePaths(row.recordingFile)) {

                if (ssh.remoteFileExists(sshHost, logSettings.sshPort, logSettings.sshUser,

                        logSettings.sshPassword, candidate)) {

                    ssh.deleteRemoteFile(sshHost, logSettings.sshPort, logSettings.sshUser,

                            logSettings.sshPassword, candidate);

                    break;

                }

            }

        }

        if (call != null && call.isCdrOnly()) {

            return;

        }

        if (!dbEnabled || recordingDao == null || row == null || row.id <= 0) {

            return;

        }

        recordingDao.deleteRecordingRow(row.id);

    }

    private void requireSsh() {

        if (!isSshReady()) {

            throw new IllegalStateException(

                    "SSH no configurado. Active SSH en la pestaña «Logs Issabel» para descargar o escuchar.");

        }

    }

}
