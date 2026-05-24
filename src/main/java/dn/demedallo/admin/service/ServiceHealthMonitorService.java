package dn.demedallo.admin.service;

import dn.demedallo.admin.model.ServiceHealthModels.DiskVolume;
import dn.demedallo.admin.model.ServiceHealthModels.Level;
import dn.demedallo.admin.model.ServiceHealthModels.ServerMetrics;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceCheck;
import dn.demedallo.admin.model.ServiceHealthModels.ServiceHealthSnapshot;
import dn.demedallo.admin.protocol.AdminEccpClient;
import dn.demedallo.admin.util.AdminDbSettings;
import dn.demedallo.admin.util.AdminLogSettings;
import dn.demedallo.admin.util.AppLogFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects dialer, PBX, Issabel, server and disk health via SSH and local JDBC probes.
 */
public final class ServiceHealthMonitorService {

    private static final String DIAG_SCRIPT = """
            echo '=== META ==='
            hostname
            date -Iseconds 2>/dev/null || date
            echo '=== UPTIME ==='
            uptime
            echo '=== MEM ==='
            free -m 2>/dev/null | head -3
            echo '=== CPU ==='
            nproc 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1
            echo '=== DISK ==='
            df -P -B1 / /var /opt 2>/dev/null | grep -v Filesystem || df -P -B1 / 2>/dev/null | grep -v Filesystem
            echo '=== SVC_DIALER ==='
            systemctl is-active issabeldialer 2>/dev/null || echo unknown
            echo '=== DIALER_PROC ==='
            pgrep -afc dialerd 2>/dev/null || echo 0
            echo '=== SVC_ASTERISK ==='
            systemctl is-active asterisk 2>/dev/null || echo unknown
            echo '=== AST_CHANNELS ==='
            asterisk -rx 'core show channels count' 2>/dev/null | head -5
            echo '=== SVC_HTTPD ==='
            (systemctl is-active httpd 2>/dev/null || systemctl is-active apache2 2>/dev/null || echo unknown)
            echo '=== SVC_MARIADB ==='
            (systemctl is-active mariadb 2>/dev/null || systemctl is-active mysqld 2>/dev/null || echo unknown)
            echo '=== ISSABEL ==='
            fwconsole status 2>/dev/null | head -20
            echo '=== DIALER_ERR ==='
            tail -n 250 /opt/issabel/dialer/dialerd.log 2>/dev/null | grep -E 'ERR|ERROR|FATAL|CRITICAL' | tail -n 10
            echo '=== AST_ERR ==='
            tail -n 250 /var/log/asterisk/messages 2>/dev/null | grep -iE 'error|warning|failed|unable' | tail -n 10
            """;

    private final RemoteLogTailService ssh = new RemoteLogTailService();
    private final AdminLogSettings logSettings;
    private final AdminDbSettings dbSettings;
    private final String eccpHost;
    private final AdminEccpClient eccpClient;

    public ServiceHealthMonitorService(AdminEccpClient eccpClient, AdminDbSettings dbSettings, String eccpHost) {
        this.eccpClient = eccpClient;
        this.dbSettings = dbSettings;
        this.eccpHost = eccpHost == null ? "" : eccpHost.trim();
        this.logSettings = AdminLogSettings.load();
    }

    public ServiceHealthSnapshot collect() {
        Instant now = Instant.now();
        List<ServiceCheck> checks = new ArrayList<>();
        checks.add(checkEccp());
        checks.addAll(checkJdbc());

        if (!logSettings.isSshReady()) {
            checks.add(new ServiceCheck("ssh", "Servidor", "SSH remoto", Level.ERROR,
                    "SSH no configurado",
                    "Active SSH y credenciales en la pestaña «Logs Issabel» para monitorear dialer, PBX y disco.",
                    "Logs Issabel → marque «SSH activo», usuario root y contraseña → Guardar.",
                    Map.of("host", eccpHost)));
            return new ServiceHealthSnapshot(now, false,
                    "SSH desactivado o sin credenciales (Logs Issabel).",
                    null, List.of(), checks, List.of(), List.of(), List.of());
        }

        String host = eccpHost.isBlank() ? "127.0.0.1" : eccpHost;
        try {
            String raw = ssh.exec(host, logSettings.sshPort, logSettings.sshUser,
                    logSettings.sshPassword, DIAG_SCRIPT);
            Map<String, String> sections = ServiceHealthParser.splitSections(raw);
            ServerMetrics server = ServiceHealthParser.parseServer(sections);
            List<DiskVolume> disks = ServiceHealthParser.parseDisks(sections.get("DISK"));

            checks.add(ServiceHealthParser.serviceCheck("issabeldialer", "Dialer", "Servicio issabeldialer",
                    sections.get("SVC_DIALER"),
                    "Dialer activo (systemd)",
                    "El servicio issabeldialer no está activo. No se ejecutará el marcador predictivo.",
                    "systemctl start issabeldialer && tail -50 /opt/issabel/dialer/dialerd.log"));
            checks.add(ServiceHealthParser.dialerProcessCheck(sections.get("DIALER_PROC")));

            checks.add(ServiceHealthParser.serviceCheck("asterisk", "PBX", "Servicio Asterisk",
                    sections.get("SVC_ASTERISK"),
                    "Asterisk activo",
                    "Asterisk no está activo: no habrá llamadas SIP ni colas.",
                    "systemctl start asterisk && asterisk -rvvv"));
            checks.add(ServiceHealthParser.asteriskChannelsCheck(sections.get("AST_CHANNELS")));

            checks.add(ServiceHealthParser.serviceCheck("httpd", "Issabel", "Apache (httpd)",
                    sections.get("SVC_HTTPD"),
                    "Interfaz web activa",
                    "Apache detenido: consola Issabel y módulos web inaccesibles.",
                    "systemctl start httpd"));
            checks.add(ServiceHealthParser.serviceCheck("mariadb", "Issabel", "MariaDB/MySQL",
                    sections.get("SVC_MARIADB"),
                    "Base de datos activa (systemd)",
                    "MariaDB detenido: fallará call center, CDR y FreePBX.",
                    "systemctl start mariadb && journalctl -u mariadb -n 50"));

            checks.addAll(ServiceHealthParser.issabelFwChecks(sections.get("ISSABEL")));
            checks.addAll(diskChecks(disks));
            checks.addAll(serverLoadChecks(server));

            List<String> dialerErr = ServiceHealthParser.lines(sections.get("DIALER_ERR"));
            List<String> astErr = ServiceHealthParser.lines(sections.get("AST_ERR"));
            if (!dialerErr.isEmpty()) {
                checks.add(new ServiceCheck("dialer-log-err", "Dialer", "Errores recientes en log",
                        Level.WARN, dialerErr.size() + " línea(s) ERR en dialerd.log",
                        String.join("\n", dialerErr),
                        "grep ERR /opt/issabel/dialer/dialerd.log | tail -50",
                        Map.of("count", String.valueOf(dialerErr.size()))));
            }
            if (!astErr.isEmpty()) {
                checks.add(new ServiceCheck("asterisk-log-err", "PBX", "Alertas recientes Asterisk",
                        Level.WARN, astErr.size() + " línea(s) en messages",
                        String.join("\n", astErr),
                        "tail -100 /var/log/asterisk/messages",
                        Map.of("count", String.valueOf(astErr.size()))));
            }

            return new ServiceHealthSnapshot(now, true, "", server, disks, checks,
                    dialerErr, astErr, ServiceHealthParser.lines(sections.get("ISSABEL")));
        } catch (Exception ex) {
            AppLogFile.appendLine("[service-health] SSH | EN: " + ex.getMessage());
            checks.add(new ServiceCheck("ssh-exec", "Servidor", "Conexión SSH", Level.ERROR,
                    "Fallo al ejecutar diagnóstico remoto",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                    "Verifique host, puerto 22, firewall y credenciales en Logs Issabel.",
                    Map.of("host", host)));
            return new ServiceHealthSnapshot(now, false, ex.getMessage(), null, List.of(), checks,
                    List.of(), List.of(), List.of());
        }
    }

    private ServiceCheck checkEccp() {
        String cookie = eccpClient == null ? "" : eccpClient.getAppCookie();
        if (cookie != null && !cookie.isBlank()) {
            return new ServiceCheck("eccp", "Consola", "ECCP (consola admin)", Level.OK,
                    "Conectado al dialer",
                    "Sesión ECCP activa con el servidor Issabel.",
                    "—", Map.of("host", eccpHost));
        }
        return new ServiceCheck("eccp", "Consola", "ECCP (consola admin)", Level.ERROR,
                "Sin conexión ECCP",
                "La consola no tiene sesión ECCP activa; no se recibirán eventos en tiempo real.",
                "Cierre sesión y vuelva a iniciar sesión en la consola.",
                Map.of("host", eccpHost));
    }

    private List<ServiceCheck> checkJdbc() {
        List<ServiceCheck> out = new ArrayList<>();
        if (dbSettings == null || !dbSettings.isConfigured()) {
            out.add(new ServiceCheck("mysql-cc", "Issabel", "MySQL call_center (JDBC)", Level.WARN,
                    "MySQL no configurado en login",
                    "No hay credenciales JDBC: informes y paneles de campaña limitados.",
                    "Login → active «Base de datos call_center» con host, usuario y clave.",
                    Map.of()));
            return out;
        }
        out.add(probeJdbc("mysql-cc", "Issabel", "MySQL call_center",
                dbSettings.jdbcUrl(), dbSettings.dbUser, dbSettings.dbPassword));
        out.add(probeJdbc("mysql-pbx", "PBX", "MySQL asterisk (FreePBX)",
                dbSettings.jdbcUrlPbx(), dbSettings.dbUser, dbSettings.dbPassword));
        return out;
    }

    private static ServiceCheck probeJdbc(String id, String cat, String name,
            String url, String user, String pass) {
        try (Connection c = DriverManager.getConnection(url, user, pass);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            if (rs.next()) {
                return new ServiceCheck(id, cat, name, Level.OK,
                        "Consulta OK",
                        "Conexión JDBC exitosa a " + maskUrl(url) + ".",
                        "—", Map.of("url", maskUrl(url)));
            }
            return new ServiceCheck(id, cat, name, Level.WARN,
                    "Sin respuesta SELECT 1",
                    "La conexión abrió pero la consulta de prueba no devolvió filas.",
                    "Revise permisos del usuario MySQL.", Map.of("url", maskUrl(url)));
        } catch (Exception ex) {
            return new ServiceCheck(id, cat, name, Level.ERROR,
                    "Conexión JDBC fallida",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                    "Verifique host/puerto 3306, usuario, clave y que MariaDB acepte conexiones remotas.",
                    Map.of("url", maskUrl(url)));
        }
    }

    private static String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("password=[^&]*", "password=***");
    }

    private static List<ServiceCheck> diskChecks(List<DiskVolume> disks) {
        List<ServiceCheck> out = new ArrayList<>();
        for (DiskVolume d : disks) {
            Level level = Level.OK;
            String cause = "Uso " + d.usePercent() + "% en " + d.mount();
            String action = "—";
            if (d.usePercent() >= 95) {
                level = Level.ERROR;
                cause = "Disco crítico en " + d.mount() + ": " + d.usePercent()
                        + "% usado. Riesgo de caída de logs, grabaciones y MySQL.";
                action = "Libere espacio en " + d.mount() + " (logs, grabaciones, /tmp). Comando: du -sh /*";
            } else if (d.usePercent() >= 85) {
                level = Level.WARN;
                cause = "Disco alto en " + d.mount() + ": " + d.usePercent() + "% usado.";
                action = "Planifique limpieza de logs Asterisk, dialerd y grabaciones.";
            }
            out.add(new ServiceCheck("disk-" + d.mount(), "Servidor", "Disco " + d.mount(),
                    level, d.usePercent() + "% usado",
                    cause, action,
                    Map.of("usedPct", String.valueOf(d.usePercent()),
                            "availGb", String.format("%.1f", d.availBytes() / 1_000_000_000.0))));
        }
        return out;
    }

    private static List<ServiceCheck> serverLoadChecks(ServerMetrics s) {
        List<ServiceCheck> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        double perCpu = s.loadPerCpu();
        Level level = Level.OK;
        String cause = "Load average 1m=" + s.load1() + " (" + s.cpuCount() + " CPU)";
        String action = "—";
        if (perCpu >= 2.0) {
            level = Level.ERROR;
            cause = "Carga CPU muy alta: load " + s.load1() + " con " + s.cpuCount()
                    + " CPU(s) (>2 por núcleo). Issabel puede degradar llamadas.";
            action = "top -c — identifique procesos (asterisk, dialerd, mysql).";
        } else if (perCpu >= 1.0) {
            level = Level.WARN;
            cause = "Carga CPU elevada: load " + s.load1() + " / " + s.cpuCount() + " CPU.";
            action = "Monitoree si coincide con horario punta de campañas.";
        }
        out.add(new ServiceCheck("cpu-load", "Servidor", "Carga CPU", level,
                "Load " + s.load1(), cause, action,
                Map.of("load1", String.valueOf(s.load1()), "cpus", String.valueOf(s.cpuCount()))));

        if (s.memUsedPercent() >= 95) {
            out.add(new ServiceCheck("mem", "Servidor", "Memoria RAM", Level.ERROR,
                    String.format("%.0f%% RAM usada", s.memUsedPercent()),
                    "Memoria casi agotada: " + s.memUsedMb() + " MB / " + s.memTotalMb()
                            + " MB. Riesgo de OOM y caída de Asterisk/MySQL.",
                    "free -m — reinicie servicios o aumente RAM.",
                    Map.of("usedMb", String.valueOf(s.memUsedMb()))));
        } else if (s.memUsedPercent() >= 85) {
            out.add(new ServiceCheck("mem", "Servidor", "Memoria RAM", Level.WARN,
                    String.format("%.0f%% RAM usada", s.memUsedPercent()),
                    "Uso de memoria alto: " + s.memUsedMb() + " / " + s.memTotalMb() + " MB.",
                    "Revise consumo de Asterisk y MySQL.",
                    Map.of("usedMb", String.valueOf(s.memUsedMb()))));
        } else {
            out.add(new ServiceCheck("mem", "Servidor", "Memoria RAM", Level.OK,
                    String.format("%.0f%% RAM usada", s.memUsedPercent()),
                    s.memUsedMb() + " MB usados de " + s.memTotalMb() + " MB.",
                    "—", Map.of("usedMb", String.valueOf(s.memUsedMb()))));
        }
        return out;
    }
}
