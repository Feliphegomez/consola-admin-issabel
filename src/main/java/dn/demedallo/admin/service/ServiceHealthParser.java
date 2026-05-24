package dn.demedallo.admin.service;

import dn.demedallo.admin.model.ServiceHealthModels.DiskVolume;
import dn.demedallo.admin.model.ServiceHealthModels.Level;
import dn.demedallo.admin.model.ServiceHealthModels.ServerMetrics;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceCheck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses SSH diagnostic script output for service health monitoring. */
final class ServiceHealthParser {

    private static final Pattern LOAD = Pattern.compile(
            "load average:\\s*([0-9.,]+),\\s*([0-9.,]+),\\s*([0-9.,]+)");
    private static final Pattern MEM = Pattern.compile(
            "Mem:\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private ServiceHealthParser() {
    }

    static Map<String, String> splitSections(String raw) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return sections;
        }
        String current = "";
        StringBuilder body = new StringBuilder();
        for (String line : raw.split("\n")) {
            if (line.startsWith("=== ") && line.endsWith(" ===")) {
                if (!current.isEmpty()) {
                    sections.put(current, body.toString().trim());
                }
                current = line.substring(4, line.length() - 4).trim();
                body = new StringBuilder();
            } else {
                if (!body.isEmpty()) {
                    body.append('\n');
                }
                body.append(line);
            }
        }
        if (!current.isEmpty()) {
            sections.put(current, body.toString().trim());
        }
        return sections;
    }

    static ServerMetrics parseServer(Map<String, String> s) {
        String meta = s.getOrDefault("META", "");
        String hostname = firstLine(meta);
        String time = "";
        if (meta.contains("\n")) {
            time = meta.substring(meta.indexOf('\n') + 1).trim();
        }
        String uptime = s.getOrDefault("UPTIME", "");
        double l1 = 0;
        double l5 = 0;
        double l15 = 0;
        Matcher lm = LOAD.matcher(uptime);
        if (lm.find()) {
            l1 = parseDouble(lm.group(1));
            l5 = parseDouble(lm.group(2));
            l15 = parseDouble(lm.group(3));
        }
        long total = 0;
        long used = 0;
        long free = 0;
        Matcher mm = MEM.matcher(s.getOrDefault("MEM", ""));
        if (mm.find()) {
            total = Long.parseLong(mm.group(1));
            used = Long.parseLong(mm.group(2));
            free = Long.parseLong(mm.group(3));
        }
        int cpus = 1;
        String cpuLine = s.getOrDefault("CPU", "");
        if (!cpuLine.isBlank()) {
            try {
                cpus = Math.max(1, Integer.parseInt(cpuLine.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return new ServerMetrics(hostname, time, l1, l5, l15, total, used, free, cpus);
    }

    static List<DiskVolume> parseDisks(String block) {
        List<DiskVolume> out = new ArrayList<>();
        if (block == null || block.isBlank()) {
            return out;
        }
        for (String line : block.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            String[] p = t.split("\\s+");
            if (p.length < 6) {
                continue;
            }
            try {
                long total = Long.parseLong(p[1]);
                long used = Long.parseLong(p[2]);
                long avail = Long.parseLong(p[3]);
                int pct = Integer.parseInt(p[4].replace("%", ""));
                out.add(new DiskVolume(p[p.length - 1], total, used, avail, pct));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static ServiceCheck serviceCheck(String id, String category, String name, String stateLine,
            String okSummary, String failCause, String failAction) {
        String state = firstLine(stateLine);
        Level level = Level.fromServiceState(state);
        String extra = "";
        if (stateLine != null && stateLine.contains("\n")) {
            extra = stateLine.substring(stateLine.indexOf('\n') + 1).trim();
        }
        if (level == Level.OK) {
            return new ServiceCheck(id, category, name, level, okSummary,
                    extra.isBlank() ? "Servicio activo (" + state + ")." : extra,
                    "Ninguna acción requerida.", Map.of("state", state));
        }
        if (level == Level.UNKNOWN && (state.isBlank() || "unknown".equalsIgnoreCase(state))) {
            return new ServiceCheck(id, category, name, Level.WARN,
                    "Estado no determinado",
                    "No se pudo leer systemctl (¿permisos root? ¿systemd?). Salida: "
                            + truncate(stateLine, 200),
                    "Ejecute en el servidor: systemctl status " + id,
                    Map.of("raw", truncate(stateLine, 120)));
        }
        return new ServiceCheck(id, category, name, Level.ERROR,
                "Servicio no activo",
                failCause + " Estado reportado: «" + state + "». "
                        + (extra.isBlank() ? "" : extra + " "),
                failAction,
                Map.of("state", state));
    }

    static List<String> lines(String block) {
        if (block == null || block.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : block.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    static ServiceCheck dialerProcessCheck(String block) {
        String count = firstLine(block);
        int procs = 0;
        try {
            procs = Integer.parseInt(count.trim());
        } catch (NumberFormatException ignored) {
        }
        if (procs > 0) {
            return new ServiceCheck("dialerd-proc", "Dialer", "Proceso dialerd",
                    Level.OK, procs + " proceso(s) dialerd",
                    "El demonio parece estar en ejecución.",
                    "Revise dialerd.log si hay campañas sin marcar.",
                    Map.of("processes", String.valueOf(procs)));
        }
        return new ServiceCheck("dialerd-proc", "Dialer", "Proceso dialerd",
                Level.ERROR, "Sin procesos dialerd",
                "No hay procesos dialerd en ejecución. Las campañas salientes no marcarán.",
                "systemctl start issabeldialer — revise /opt/issabel/dialer/dialerd.log",
                Map.of("processes", "0"));
    }

    static ServiceCheck asteriskChannelsCheck(String block) {
        if (block == null || block.isBlank()) {
            return new ServiceCheck("asterisk-ch", "PBX", "Canales Asterisk",
                    Level.WARN, "Sin datos de canales",
                    "asterisk -rx no respondió (¿CLI bloqueada? ¿servicio caído?).",
                    "asterisk -rvvv — core show channels count",
                    Map.of());
        }
        return new ServiceCheck("asterisk-ch", "PBX", "Canales Asterisk",
                Level.OK, truncate(firstLine(block), 80),
                block.trim(),
                "Monitoree si el número de canales crece sin liberarse.",
                Map.of("detail", truncate(block, 150)));
    }

    static List<ServiceCheck> issabelFwChecks(String block) {
        List<ServiceCheck> out = new ArrayList<>();
        if (block == null || block.isBlank()) {
            out.add(new ServiceCheck("issabel-fw", "Issabel", "fwconsole status",
                    Level.WARN, "Sin salida fwconsole",
                    "fwconsole status no devolvió datos (¿ruta Issabel? ¿permisos?).",
                    "Ejecute como root: fwconsole status",
                    Map.of()));
            return out;
        }
        for (String line : block.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            Level level = Level.OK;
            String cause = t;
            String lower = t.toLowerCase(Locale.ROOT);
            if (lower.contains("not running") || lower.contains("stopped")
                    || lower.contains("disabled") || lower.contains("fail")) {
                level = Level.ERROR;
                cause = "Componente Issabel detenido o fallido: " + t;
            } else if (lower.contains("unknown") || lower.contains("warning")) {
                level = Level.WARN;
                cause = "Advertencia Issabel: " + t;
            }
            out.add(new ServiceCheck("issabel-" + out.size(), "Issabel", truncate(t, 40),
                    level, truncate(t, 60), cause,
                    level == Level.OK ? "—" : "fwconsole restart — revise /var/log/issabel/",
                    Map.of("line", t)));
        }
        return out;
    }

    private static String firstLine(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s.trim() : s.substring(0, i).trim();
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
