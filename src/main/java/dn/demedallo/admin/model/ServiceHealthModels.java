package dn.demedallo.admin.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Health probe results for Issabel infrastructure monitoring. */
public final class ServiceHealthModels {

    public enum Level {
        OK,
        WARN,
        ERROR,
        UNKNOWN;

        public static Level fromServiceState(String state) {
            if (state == null) {
                return UNKNOWN;
            }
            String s = state.trim().toLowerCase();
            if ("active".equals(s) || "running".equals(s)) {
                return OK;
            }
            if ("inactive".equals(s) || "dead".equals(s) || "failed".equals(s)) {
                return ERROR;
            }
            if ("activating".equals(s) || "deactivating".equals(s)) {
                return WARN;
            }
            return UNKNOWN;
        }
    }

    public record ServiceCheck(
            String id,
            String category,
            String name,
            Level level,
            String summary,
            String cause,
            String actionHint,
            Map<String, String> metrics
    ) {
        public ServiceCheck {
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }
    }

    public record DiskVolume(
            String mount,
            long totalBytes,
            long usedBytes,
            long availBytes,
            int usePercent
    ) {
        public double usedRatio() {
            return totalBytes <= 0 ? 0 : (double) usedBytes / totalBytes;
        }
    }

    public record ServerMetrics(
            String hostname,
            String serverTime,
            double load1,
            double load5,
            double load15,
            long memTotalMb,
            long memUsedMb,
            long memFreeMb,
            int cpuCount
    ) {
        public double memUsedPercent() {
            return memTotalMb <= 0 ? 0 : 100.0 * memUsedMb / memTotalMb;
        }

        public double loadPerCpu() {
            return cpuCount <= 0 ? load1 : load1 / cpuCount;
        }
    }

    public record ServiceHealthSnapshot(
            Instant collectedAt,
            boolean sshUsed,
            String sshError,
            ServerMetrics server,
            List<DiskVolume> disks,
            List<ServiceCheck> checks,
            List<String> dialerErrors,
            List<String> asteriskErrors,
            List<String> issabelStatusLines
    ) {
        public ServiceHealthSnapshot {
            disks = disks == null ? List.of() : List.copyOf(disks);
            checks = checks == null ? List.of() : List.copyOf(checks);
            dialerErrors = dialerErrors == null ? List.of() : List.copyOf(dialerErrors);
            asteriskErrors = asteriskErrors == null ? List.of() : List.copyOf(asteriskErrors);
            issabelStatusLines = issabelStatusLines == null ? List.of() : List.copyOf(issabelStatusLines);
        }

        public long errorCount() {
            return checks.stream().filter(c -> c.level() == Level.ERROR).count();
        }

        public long warnCount() {
            return checks.stream().filter(c -> c.level() == Level.WARN).count();
        }
    }

    private ServiceHealthModels() {
    }
}
