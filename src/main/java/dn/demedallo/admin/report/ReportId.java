package dn.demedallo.admin.report;

/**
 * Issabel web report modules mapped to native admin console views.
 */
public enum ReportId {

    REP_AGENTS_MONITORING("rep_agents_monitoring", "Monitoreo de agentes", ReportKind.LIVE_ECCP,
            "Tiempo real — pestaña Monitoreo > Agentes"),
    REP_INCOMING_CALLS_MONITORING("rep_incoming_calls_monitoring", "Monitoreo llamadas entrantes", ReportKind.LIVE_ECCP,
            "Colas entrantes hoy (ECCP)"),
    REP_OUTGOING_CALLS_MONITORING("rep_outgoing_calls_monitoring", "Monitoreo llamadas salientes", ReportKind.LIVE_ECCP,
            "Campañas salientes activas hoy (ECCP)"),
    REP_INCOMING_CAMPAIGNS_PANEL("rep_incoming_campaigns_panel", "Panel campañas entrantes", ReportKind.LIVE_ECCP,
            "Campañas entrantes activas"),
    REP_OUTGOING_CAMPAIGNS_PANEL("rep_outgoing_campaigns_panel", "Panel campañas salientes", ReportKind.LIVE_ECCP,
            "Campañas salientes activas"),
    CAMPAIGN_MONITORING("campaign_monitoring", "Monitoreo de campaña", ReportKind.LIVE_ECCP,
            "Detalle de una campaña/cola"),
    REP_AGENT_INFORMATION("rep_agent_information", "Información por agente", ReportKind.HISTORICAL_DB,
            "Resumen agente/cola (requiere MySQL)"),
    REP_TRUNKS_USED_PER_HOUR("rep_trunks_used_per_hour", "Troncales por hora", ReportKind.HISTORICAL_DB,
            "Uso de troncales por hora"),
    REPORTS_BREAK("reports_break", "Reporte de breaks", ReportKind.HISTORICAL_DB,
            "Pausas por agente"),
    CALLS_PER_HOUR("calls_per_hour", "Llamadas por hora", ReportKind.HISTORICAL_DB,
            "Histograma horario entrante/saliente"),
    GRAPHIC_CALLS("graphic_calls", "Llamadas por hora (gráfico)", ReportKind.HISTORICAL_DB,
            "Mismos datos que Llamadas por hora (tabla)"),
    CALLS_PER_AGENT("calls_per_agent", "Llamadas por agente", ReportKind.HISTORICAL_DB,
            "Contestadas por agente y cola"),
    CALLS_DETAIL("calls_detail", "Detalle de llamadas", ReportKind.HISTORICAL_DB,
            "Listado CDR call_center (máx. 2000 filas)"),
    HOLD_TIME("hold_time", "Tiempo en espera", ReportKind.HISTORICAL_DB,
            "Histograma duration_wait"),
    INGOINGS_CALLS_SUCCESS("ingoings_calls_success", "Éxito llamadas entrantes", ReportKind.HISTORICAL_DB,
            "Terminadas vs abandonadas por cola"),
    LOGIN_LOGOUT("login_logout", "Login / Logout", ReportKind.HISTORICAL_DB,
            "Sesiones de agentes"),
    AGENT_DETAIL_REPORT("agent_detail_report", "Detalle de agentes", ReportKind.HISTORICAL_DB,
            "Resumen por agente: sesiones, pausas, llamadas entrantes y salientes"),
    FORM_DATA_VIEWER("form_data_viewer", "Datos de formularios", ReportKind.HISTORICAL_DB,
            "Valores capturados en formularios (entrantes y salientes)");

    public enum ReportKind {
        LIVE_ECCP,
        HISTORICAL_DB
    }

    public final String moduleName;
    public final String title;
    public final ReportKind kind;
    public final String description;

    ReportId(String moduleName, String title, ReportKind kind, String description) {
        this.moduleName = moduleName;
        this.title = title;
        this.kind = kind;
        this.description = description;
    }
}
