package dn.demedallo.admin.report;

import dn.demedallo.admin.db.CallCenterDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Historical reports from call_center DB (Issabel web module SQL).
 */
public final class DbReportService {

    private final CallCenterDb db;

    public DbReportService(CallCenterDb db) {
        this.db = db;
    }

    public ReportTableData run(ReportId id, ReportQueryParams p) throws Exception {
        return switch (id) {
            case LOGIN_LOGOUT -> loginLogout(p);
            case REPORTS_BREAK -> reportsBreak(p);
            case CALLS_PER_HOUR, GRAPHIC_CALLS -> callsPerHour(p);
            case CALLS_PER_AGENT -> callsPerAgent(p);
            case CALLS_DETAIL -> callsDetail(p);
            case HOLD_TIME -> holdTime(p);
            case INGOINGS_CALLS_SUCCESS -> ingoingsCallsSuccess(p);
            case REP_TRUNKS_USED_PER_HOUR -> trunksPerHour(p);
            case REP_AGENT_INFORMATION -> agentInformation(p);
            case AGENT_DETAIL_REPORT -> agentDetailReport(p);
            case FORM_DATA_VIEWER -> formDataViewer(p);
            default -> ReportTableData.empty("Informe no implementado en BD: " + id.moduleName);
        };
    }

    public ReportTableData listAgentsForFilter() throws Exception {
        String sql = """
                SELECT number AS Agente, name AS Nombre
                FROM agent WHERE estatus = 'A'
                ORDER BY name
                """;
        return query(sql);
    }

    private ReportTableData agentDetailReport(ReportQueryParams p) throws Exception {
        String agent = p.option("agent", "");
        StringBuilder sql = new StringBuilder("""
                SELECT agent.number AS Agente, agent.name AS Nombre,
                       (SELECT COUNT(*) FROM audit a
                         WHERE a.id_agent = agent.id AND a.id_break IS NULL
                           AND a.datetime_init BETWEEN ? AND ?) AS Sesiones_login,
                       (SELECT COALESCE(SUM(TIME_TO_SEC(IFNULL(a.duration,
                            TIMEDIFF(IFNULL(a.datetime_end, NOW()), a.datetime_init)))), 0)
                         FROM audit a
                         WHERE a.id_agent = agent.id AND a.id_break IS NULL
                           AND a.datetime_init BETWEEN ? AND ?) AS Segundos_conectado,
                       (SELECT COUNT(*) FROM audit a
                         WHERE a.id_agent = agent.id AND a.id_break IS NOT NULL
                           AND a.datetime_init BETWEEN ? AND ?) AS Pausas_registradas,
                       (SELECT COALESCE(SUM(TIME_TO_SEC(IFNULL(a.duration,
                            TIMEDIFF(IFNULL(a.datetime_end, NOW()), a.datetime_init)))), 0)
                         FROM audit a
                         WHERE a.id_agent = agent.id AND a.id_break IS NOT NULL
                           AND a.datetime_init BETWEEN ? AND ?) AS Segundos_en_pausa,
                       (SELECT COUNT(*) FROM call_entry ce
                         WHERE ce.id_agent = agent.id AND ce.status = 'terminada'
                           AND ce.datetime_init BETWEEN ? AND ?) AS Llamadas_entrantes,
                       (SELECT COALESCE(SUM(ce.duration), 0) FROM call_entry ce
                         WHERE ce.id_agent = agent.id AND ce.status = 'terminada'
                           AND ce.datetime_init BETWEEN ? AND ?) AS Seg_hablado_entrante,
                       (SELECT COUNT(*) FROM calls c
                         WHERE c.id_agent = agent.id AND c.status = 'Success'
                           AND c.start_time BETWEEN ? AND ?) AS Llamadas_salientes,
                       (SELECT COALESCE(SUM(c.duration), 0) FROM calls c
                         WHERE c.id_agent = agent.id AND c.status = 'Success'
                           AND c.start_time BETWEEN ? AND ?) AS Seg_hablado_saliente,
                       (SELECT COUNT(*) FROM call_entry ce
                         WHERE ce.id_agent = agent.id AND ce.status = 'abandonada'
                           AND ce.datetime_entry_queue BETWEEN ? AND ?) AS Abandonadas_cola
                FROM agent
                WHERE agent.estatus = 'A'
                """);
        List<Object> params = new ArrayList<>();
        String dt0 = p.fromDateTime();
        String dt1 = p.toDateTime();
        for (int i = 0; i < 10; i++) {
            params.add(dt0);
            params.add(dt1);
        }
        if (!agent.isBlank()) {
            sql.append(" AND agent.number = ? ");
            params.add(agent);
        }
        sql.append(" ORDER BY agent.name ");
        return query(sql.toString(), params.toArray());
    }

    private ReportTableData formDataViewer(ReportQueryParams p) throws Exception {
        String tipo = p.option("tipo", "all");
        String campaign = p.option("campaign", "");
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        if (!"E".equalsIgnoreCase(tipo)) {
            sql.append("""
                    SELECT 'Saliente' AS Tipo, c.id AS ID_llamada, IFNULL(c.phone, '') AS Telefono,
                           c.start_time AS Fecha, IFNULL(agent.number, '') AS Agente,
                           IFNULL(agent.name, '') AS Nombre_agente,
                           IFNULL(camp.name, '') AS Campana_cola,
                           IFNULL(form.nombre, '') AS Formulario,
                           REPLACE(REPLACE(IFNULL(ff.etiqueta, ''), CHAR(10), ' '), CHAR(13), ' ') AS Campo,
                           IFNULL(fdr.value, '') AS Valor
                    FROM form_data_recolected fdr
                    INNER JOIN form_field ff ON fdr.id_form_field = ff.id AND ff.tipo <> 'LABEL'
                    INNER JOIN form ON ff.id_form = form.id
                    INNER JOIN calls c ON fdr.id_calls = c.id
                    LEFT JOIN agent ON c.id_agent = agent.id
                    LEFT JOIN campaign camp ON c.id_campaign = camp.id
                    WHERE c.start_time BETWEEN ? AND ?
                    """);
            params.add(p.fromDateTime());
            params.add(p.toDateTime());
            if (!campaign.isBlank()) {
                sql.append(" AND camp.name LIKE ? ");
                params.add("%" + campaign + "%");
            }
        }
        if ("all".equalsIgnoreCase(tipo)) {
            if (!sql.isEmpty()) {
                sql.append(" UNION ALL ");
            }
        }
        if (!"S".equalsIgnoreCase(tipo)) {
            sql.append("""
                    SELECT 'Entrante' AS Tipo, ce.id AS ID_llamada, IFNULL(ce.callerid, '') AS Telefono,
                           ce.datetime_init AS Fecha, IFNULL(agent.number, '') AS Agente,
                           IFNULL(agent.name, '') AS Nombre_agente,
                           IFNULL(camp.name, '') AS Campana_cola,
                           IFNULL(form.nombre, '') AS Formulario,
                           REPLACE(REPLACE(IFNULL(ff.etiqueta, ''), CHAR(10), ' '), CHAR(13), ' ') AS Campo,
                           IFNULL(fdre.value, '') AS Valor
                    FROM form_data_recolected_entry fdre
                    INNER JOIN form_field ff ON fdre.id_form_field = ff.id AND ff.tipo <> 'LABEL'
                    INNER JOIN form ON ff.id_form = form.id
                    INNER JOIN call_entry ce ON fdre.id_call_entry = ce.id
                    LEFT JOIN agent ON ce.id_agent = agent.id
                    LEFT JOIN campaign_entry camp ON ce.id_campaign = camp.id
                    WHERE ce.datetime_init BETWEEN ? AND ?
                    """);
            params.add(p.fromDateTime());
            params.add(p.toDateTime());
            if (!campaign.isBlank()) {
                sql.append(" AND camp.name LIKE ? ");
                params.add("%" + campaign + "%");
            }
        }
        sql.append(" ORDER BY Fecha DESC, ID_llamada, Formulario, Campo LIMIT 5000 ");
        if (sql.isEmpty()) {
            return ReportTableData.empty("Seleccione tipo Entrante, Saliente o Ambos.");
        }
        return query(sql.toString(), params.toArray());
    }

    private ReportTableData loginLogout(ReportQueryParams p) throws Exception {
        String sql = """
                SELECT agent.number AS Agente, agent.name AS Nombre,
                       audit.datetime_init AS Inicio, audit.datetime_end AS Fin,
                       TIME_TO_SEC(IF(audit.duration IS NULL, TIMEDIFF(NOW(), audit.datetime_init), audit.duration)) AS Segundos,
                       (SELECT COALESCE(SUM(duration),0) FROM call_entry
                         WHERE call_entry.id_agent = agent.id
                           AND call_entry.datetime_init BETWEEN audit.datetime_init
                           AND IF(audit.datetime_end IS NULL, NOW(), audit.datetime_end)) AS Entrante_sec,
                       (SELECT COALESCE(SUM(duration),0) FROM calls
                         WHERE calls.id_agent = agent.id
                           AND calls.start_time BETWEEN audit.datetime_init
                           AND IF(audit.datetime_end IS NULL, NOW(), audit.datetime_end)) AS Saliente_sec,
                       IF(audit.datetime_end IS NULL, 'ONLINE', '') AS Estado
                FROM audit, agent
                WHERE audit.datetime_init BETWEEN ? AND ?
                  AND audit.id_agent = agent.id AND audit.id_break IS NULL
                ORDER BY agent.name, audit.datetime_init
                """;
        return query(sql, p.fromDateTime(), p.toDateTime());
    }

    private ReportTableData reportsBreak(ReportQueryParams p) throws Exception {
        String sql = """
                SELECT agent.number AS Agente, agent.name AS Nombre,
                       break.name AS Pausa, COUNT(audit.id) AS Veces,
                       SUM(UNIX_TIMESTAMP(IFNULL(audit.datetime_end, NOW())) - UNIX_TIMESTAMP(audit.datetime_init)) AS Segundos
                FROM agent
                LEFT JOIN (audit, break) ON agent.id = audit.id_agent AND break.id = audit.id_break
                    AND audit.datetime_init >= ? AND audit.datetime_init <= ?
                WHERE agent.estatus = 'A'
                GROUP BY agent.id, audit.id_break
                ORDER BY agent.name, break.name
                """;
        return query(sql, p.fromDate() + " 00:00:00", p.toDate() + " 23:59:59");
    }

    private ReportTableData callsPerHour(ReportQueryParams p) throws Exception {
        String tipo = p.option("tipo", "E");
        String estado = p.option("estado", "T");
        String sql;
        List<Object> params = new ArrayList<>();
        if ("S".equalsIgnoreCase(tipo)) {
            String estadoSql = switch (estado.toUpperCase()) {
                case "E" -> " AND status = 'Success' ";
                case "N" -> " AND (status = 'NoAnswer' OR status = 'ShortCall') ";
                case "A" -> " AND status = 'Abandoned' ";
                default -> " ";
            };
            sql = "SELECT camp.queue AS Cola, HOUR(c.start_time) AS Hora, COUNT(*) AS N "
                    + "FROM calls c, campaign camp "
                    + "WHERE start_time >= ? AND end_time <= ? AND c.id_campaign = camp.id AND c.status IS NOT NULL"
                    + estadoSql + " GROUP BY queue, hora ORDER BY queue, hora";
            params.add(p.fromDate() + " 00:00:00");
            params.add(p.toDate() + " 23:59:59");
        } else {
            String estadoSql = switch (estado.toUpperCase()) {
                case "E" -> " AND status = 'terminada' ";
                case "A" -> " AND status = 'abandonada' ";
                default -> " ";
            };
            sql = "SELECT queue_ce.queue AS Cola, "
                    + "HOUR(IF(status = 'abandonada', datetime_entry_queue, datetime_init)) AS Hora, "
                    + "COUNT(*) AS N FROM call_entry call_e, queue_call_entry queue_ce "
                    + "WHERE call_e.id_queue_call_entry = queue_ce.id "
                    + "AND ((status = 'abandonada' AND datetime_entry_queue >= ?) "
                    + "OR (status <> 'abandonada' AND datetime_init >= ?)) AND datetime_end <= ? "
                    + estadoSql + " GROUP BY queue, hora ORDER BY queue, hora";
            params.add(p.fromDate() + " 00:00:00");
            params.add(p.fromDate() + " 00:00:00");
            params.add(p.toDate() + " 23:59:59");
        }
        return query(sql, params.toArray());
    }

    private ReportTableData callsPerAgent(ReportQueryParams p) throws Exception {
        String sql = """
                SELECT agent.number AS Agente, agent.name AS Nombre, q.queue AS Cola,
                       'Entrante' AS Tipo, COUNT(*) AS Llamadas
                FROM call_entry ce
                JOIN agent ON ce.id_agent = agent.id
                JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                WHERE ce.status = 'terminada' AND ce.datetime_init BETWEEN ? AND ?
                GROUP BY agent.id, q.queue
                UNION ALL
                SELECT agent.number, agent.name, camp.queue, 'Saliente', COUNT(*)
                FROM calls c
                JOIN agent ON c.id_agent = agent.id
                JOIN campaign camp ON c.id_campaign = camp.id
                WHERE c.status = 'Success' AND c.start_time BETWEEN ? AND ?
                GROUP BY agent.id, camp.queue
                ORDER BY Nombre, Cola, Tipo
                """;
        return query(sql, p.fromDateTime(), p.toDateTime(), p.fromDateTime(), p.toDateTime());
    }

    private ReportTableData callsDetail(ReportQueryParams p) throws Exception {
        String sql = """
                SELECT 'Entrante' AS Tipo, ce.id AS ID, q.queue AS Cola, agent.number AS Agente,
                       IFNULL(ce.callerid,'') AS Telefono, ce.status AS Estado,
                       ce.datetime_init AS Inicio, ce.datetime_end AS Fin, ce.duration AS Duracion
                FROM call_entry ce
                LEFT JOIN agent ON ce.id_agent = agent.id
                LEFT JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                WHERE ce.datetime_init BETWEEN ? AND ?
                UNION ALL
                SELECT 'Saliente', c.id, camp.queue, agent.number, c.phone, c.status,
                       c.start_time, c.end_time, c.duration
                FROM calls c
                LEFT JOIN agent ON c.id_agent = agent.id
                LEFT JOIN campaign camp ON c.id_campaign = camp.id
                WHERE c.start_time BETWEEN ? AND ?
                ORDER BY Inicio DESC
                LIMIT 2000
                """;
        return query(sql, p.fromDateTime(), p.toDateTime(), p.fromDateTime(), p.toDateTime());
    }

    private ReportTableData holdTime(ReportQueryParams p) throws Exception {
        String tipo = p.option("call_type", "incoming");
        String sql;
        if ("outgoing".equalsIgnoreCase(tipo)) {
            sql = """
                    SELECT camp.queue AS Cola, FLOOR(c.duration_wait / 10) * 10 AS Intervalo_seg,
                           COUNT(*) AS N
                    FROM calls c, campaign camp
                    WHERE c.id_campaign = camp.id AND c.duration_wait IS NOT NULL
                      AND c.start_time BETWEEN ? AND ?
                    GROUP BY camp.queue, Intervalo_seg ORDER BY camp.queue, Intervalo_seg
                    """;
        } else {
            sql = """
                    SELECT q.queue AS Cola, FLOOR(ce.duration_wait / 10) * 10 AS Intervalo_seg,
                           COUNT(*) AS N
                    FROM call_entry ce, queue_call_entry q
                    WHERE ce.id_queue_call_entry = q.id AND ce.duration_wait IS NOT NULL
                      AND ce.datetime_entry_queue BETWEEN ? AND ?
                    GROUP BY q.queue, Intervalo_seg ORDER BY q.queue, Intervalo_seg
                    """;
        }
        return query(sql, p.fromDateTime(), p.toDateTime());
    }

    private ReportTableData ingoingsCallsSuccess(ReportQueryParams p) throws Exception {
        String sql = """
                SELECT queue_call_entry.queue AS Cola, call_entry.status AS Estado,
                       COUNT(call_entry.id) AS N, SUM(call_entry.duration_wait) AS Espera_total_seg
                FROM queue_call_entry, call_entry
                WHERE queue_call_entry.id = call_entry.id_queue_call_entry
                  AND call_entry.status IN ('terminada', 'abandonada')
                  AND call_entry.datetime_entry_queue BETWEEN ? AND ?
                GROUP BY queue_call_entry.queue, call_entry.status
                ORDER BY Cola, Estado
                """;
        return query(sql, p.fromDateTime(), p.toDateTime());
    }

    private ReportTableData trunksPerHour(ReportQueryParams p) throws Exception {
        String sql = """
                SELECT trunk AS Troncal, HOUR(datetime_entry_queue) AS Hora, status AS Estado,
                       COUNT(*) AS N
                FROM call_entry
                WHERE datetime_entry_queue BETWEEN ? AND ?
                  AND trunk IS NOT NULL AND trunk <> ''
                GROUP BY trunk, Hora, status ORDER BY trunk, Hora
                """;
        return query(sql, p.fromDateTime(), p.toDateTime());
    }

    private ReportTableData agentInformation(ReportQueryParams p) throws Exception {
        String agent = p.option("agent", "");
        String queue = p.option("queue", "");
        StringBuilder sql = new StringBuilder("""
                SELECT agent.number AS Agente, agent.name AS Nombre,
                       COUNT(DISTINCT audit.id) AS Logins,
                       (SELECT COUNT(*) FROM call_entry ce
                         JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                         WHERE ce.id_agent = agent.id
                           AND ce.datetime_init BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(p.fromDateTime());
        params.add(p.toDateTime());
        if (!queue.isBlank()) {
            sql.append(" AND q.queue = ? ");
            params.add(queue);
        }
        sql.append(") AS Llamadas_entrantes, (SELECT COUNT(*) FROM calls c WHERE c.id_agent = agent.id ");
        sql.append(" AND c.start_time BETWEEN ? AND ?) AS Llamadas_salientes ");
        params.add(p.fromDateTime());
        params.add(p.toDateTime());
        sql.append(" FROM agent LEFT JOIN audit ON audit.id_agent = agent.id AND audit.id_break IS NULL ");
        sql.append(" AND audit.datetime_init BETWEEN ? AND ? ");
        params.add(p.fromDateTime());
        params.add(p.toDateTime());
        if (!agent.isBlank()) {
            sql.append(" WHERE agent.number = ? ");
            params.add(agent);
        }
        sql.append(" GROUP BY agent.id ORDER BY agent.name ");
        return query(sql.toString(), params.toArray());
    }

    private ReportTableData query(String sql, Object... params) throws Exception {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return ReportTableData.fromResult(rs);
            }
        }
    }
}
