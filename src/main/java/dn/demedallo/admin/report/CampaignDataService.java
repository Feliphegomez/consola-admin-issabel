package dn.demedallo.admin.report;

import dn.demedallo.admin.db.CallCenterDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Campaign call + form data browser (Issabel campaign_out / campaign_in export SQL).
 */
public final class CampaignDataService {

    public record CampaignOption(int id, String name, String queue, String status, String type) {
        @Override
        public String toString() {
            return "(" + type + ") " + name + " · cola " + queue + " · " + status;
        }
    }

    private final CallCenterDb db;

    public CampaignDataService(CallCenterDb db) {
        this.db = db;
    }

    public List<CampaignOption> listCampaigns(String type) throws Exception {
        if ("incoming".equalsIgnoreCase(type)) {
            String sql = """
                    SELECT ce.id AS id, ce.name AS nombre, q.queue AS cola, ce.estatus AS estado
                    FROM campaign_entry ce
                    INNER JOIN queue_call_entry q ON ce.id_queue_call_entry = q.id
                    ORDER BY ce.name
                    """;
            return mapCampaignOptions(query(sql), "incoming");
        }
        String sql = """
                SELECT id, name AS nombre, queue AS cola, estatus AS estado
                FROM campaign
                ORDER BY name
                """;
        return mapCampaignOptions(query(sql), "outgoing");
    }

    public ReportTableData campaignCalls(String type, int campaignId, String fromDt, String toDt, String phone)
            throws Exception {
        String phoneLike = phone == null || phone.isBlank() ? "" : "%" + phone.trim() + "%";
        if ("incoming".equalsIgnoreCase(type)) {
            String sql = """
                    SELECT CASE WHEN EXISTS (
                               SELECT 1 FROM form_data_recolected_entry fdre
                               INNER JOIN form_field ff ON fdre.id_form_field = ff.id AND ff.tipo <> 'LABEL'
                               WHERE fdre.id_call_entry = ce.id
                                 AND TRIM(IFNULL(fdre.value, '')) <> ''
                           ) THEN '✓' ELSE '' END AS Datos,
                           ce.id AS ID_llamada,
                           IFNULL(ce.callerid, '') AS Telefono,
                           (SELECT COUNT(*) FROM call_entry ce2
                             WHERE ce2.id_campaign = ce.id_campaign
                               AND ce2.callerid = ce.callerid
                               AND ce2.datetime_init BETWEEN ? AND ?) AS Llamadas_al_numero,
                           camp.name AS Campana,
                           q.queue AS Cola,
                           ce.status AS Estado,
                           IFNULL(agent.number, '') AS Agente,
                           IFNULL(agent.name, '') AS Nombre_agente,
                           ce.datetime_init AS Inicio,
                           ce.datetime_end AS Fin,
                           IFNULL(ce.duration, 0) AS Duracion_seg,
                           IFNULL(ce.duration_wait, 0) AS Espera_seg,
                           IFNULL(ce.trunk, '') AS Troncal,
                           IFNULL(ce.uniqueid, '') AS Uniqueid
                    FROM call_entry ce
                    INNER JOIN campaign_entry camp ON ce.id_campaign = camp.id
                    INNER JOIN queue_call_entry q ON camp.id_queue_call_entry = q.id
                    LEFT JOIN agent ON ce.id_agent = agent.id
                    WHERE ce.id_campaign = ?
                      AND ce.datetime_init BETWEEN ? AND ?
                      AND (? = '' OR ce.callerid LIKE ?)
                    ORDER BY ce.datetime_init DESC
                    LIMIT 3000
                    """;
            return query(sql, fromDt, toDt, campaignId, fromDt, toDt, phoneLike, phoneLike);
        }
        String sql = """
                SELECT CASE WHEN EXISTS (
                           SELECT 1 FROM form_data_recolected fdr
                           INNER JOIN form_field ff ON fdr.id_form_field = ff.id AND ff.tipo <> 'LABEL'
                           WHERE fdr.id_calls = c.id
                             AND TRIM(IFNULL(fdr.value, '')) <> ''
                       ) THEN '✓' ELSE '' END AS Datos,
                       c.id AS ID_llamada,
                       IFNULL(c.phone, '') AS Telefono,
                       (SELECT COUNT(*) FROM calls c2
                         WHERE c2.id_campaign = c.id_campaign
                           AND c2.phone = c.phone
                           AND c2.start_time BETWEEN ? AND ?) AS Llamadas_al_numero,
                       camp.name AS Campana,
                       camp.queue AS Cola,
                       c.status AS Estado,
                       IFNULL(agent.number, '') AS Agente,
                       IFNULL(agent.name, '') AS Nombre_agente,
                       c.start_time AS Inicio,
                       c.end_time AS Fin,
                       IFNULL(c.duration, 0) AS Duracion_seg,
                       IFNULL(c.duration_wait, 0) AS Espera_seg,
                       IFNULL(c.trunk, '') AS Troncal,
                       IFNULL(c.uniqueid, '') AS Uniqueid,
                       IFNULL(c.failure_cause_txt, '') AS Causa_fallo
                FROM calls c
                INNER JOIN campaign camp ON c.id_campaign = camp.id
                LEFT JOIN agent ON c.id_agent = agent.id
                WHERE c.id_campaign = ?
                  AND c.start_time BETWEEN ? AND ?
                  AND (? = '' OR c.phone LIKE ?)
                ORDER BY c.start_time DESC
                LIMIT 3000
                """;
        return query(sql, fromDt, toDt, campaignId, fromDt, toDt, phoneLike, phoneLike);
    }

    public ReportTableData formDataForCall(String type, int callId) throws Exception {
        if ("incoming".equalsIgnoreCase(type)) {
            String sql = """
                    SELECT IFNULL(form.nombre, '') AS Formulario,
                           REPLACE(REPLACE(IFNULL(ff.etiqueta, ''), CHAR(10), ' '), CHAR(13), ' ') AS Campo,
                           IFNULL(fdre.value, '') AS Valor
                    FROM form_data_recolected_entry fdre
                    INNER JOIN form_field ff ON fdre.id_form_field = ff.id AND ff.tipo <> 'LABEL'
                    INNER JOIN form ON ff.id_form = form.id
                    WHERE fdre.id_call_entry = ?
                    ORDER BY form.nombre, ff.orden
                    """;
            return query(sql, callId);
        }
        String sql = """
                SELECT IFNULL(form.nombre, '') AS Formulario,
                       REPLACE(REPLACE(IFNULL(ff.etiqueta, ''), CHAR(10), ' '), CHAR(13), ' ') AS Campo,
                       IFNULL(fdr.value, '') AS Valor
                FROM form_data_recolected fdr
                INNER JOIN form_field ff ON fdr.id_form_field = ff.id AND ff.tipo <> 'LABEL'
                INNER JOIN form ON ff.id_form = form.id
                WHERE fdr.id_calls = ?
                ORDER BY form.nombre, ff.orden
                """;
        return query(sql, callId);
    }

    private static List<CampaignOption> mapCampaignOptions(ReportTableData data, String type) {
        List<CampaignOption> out = new ArrayList<>();
        for (List<String> row : data.getRows()) {
            if (row.size() < 4) {
                continue;
            }
            int id = parseInt(row.get(0));
            String name = row.get(1);
            String queue = row.get(2);
            String status = mapStatus(row.get(3));
            out.add(new CampaignOption(id, name, queue, status, type));
        }
        return out;
    }

    private static String mapStatus(String estatus) {
        if (estatus == null) {
            return "";
        }
        return switch (estatus.toUpperCase()) {
            case "A" -> "active";
            case "I" -> "inactive";
            case "T" -> "finished";
            default -> estatus;
        };
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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
