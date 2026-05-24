package dn.demedallo.admin.service;

import dn.demedallo.admin.db.AsteriskDb;
import dn.demedallo.admin.db.PbxAdminDao;
import dn.demedallo.admin.db.PbxAdminWriteDao;
import dn.demedallo.admin.model.pbx.PbxForms;
import dn.demedallo.admin.model.pbx.PbxRows;
import dn.demedallo.admin.util.AdminDbSettings;

import java.util.ArrayList;
import java.util.List;

public final class PbxAdminService {

    public static final String RELOAD_HINT =
            " Ejecute en el servidor Issabel: fwconsole reload (o recargue desde la GUI).";

    public static final String MOH_ISSABEL_HINT =
            " Issabel no usa la tabla «music»; las clases son carpetas en /var/lib/asterisk/moh/."
                    + " Tipo/formato/aleatorio se configuran en la GUI Issabel; use «Recargar Asterisk» tras cambios.";

    private final AdminDbSettings settings;
    private final PbxAdminDao dao;
    private final PbxAdminWriteDao writeDao;
    private final PbxMohService mohService;

    public PbxAdminService(AdminDbSettings settings) {
        this(settings, "");
    }

    public PbxAdminService(AdminDbSettings settings, String eccpHost) {
        AsteriskDb asteriskDb = new AsteriskDb(settings);
        this.settings = settings;
        this.dao = new PbxAdminDao(asteriskDb);
        this.writeDao = new PbxAdminWriteDao(asteriskDb);
        this.mohService = new PbxMohService(eccpHost);
    }

    public boolean isAvailable() {
        return settings.isConfigured();
    }

    public String pbxDatabaseLabel() {
        String name = settings.pbxDbName;
        return name == null || name.isBlank() ? "asterisk" : name.trim();
    }

    public List<PbxRows.TimeGroupRow> listTimeGroups() throws Exception {
        return dao.listTimeGroups();
    }

    public List<PbxRows.TimeGroupDetailRow> listTimeGroupDetails(int timeGroupId) throws Exception {
        return dao.listTimeGroupDetails(timeGroupId);
    }

    public List<PbxRows.TimeConditionRow> listTimeConditions() throws Exception {
        return dao.listTimeConditions();
    }

    public List<PbxRows.QueueRow> listQueues() throws Exception {
        return dao.listQueues();
    }

    public List<PbxRows.ExtensionRow> listExtensions() throws Exception {
        return dao.listExtensions();
    }

    public List<PbxRows.TrunkRow> listTrunks() throws Exception {
        return dao.listTrunks();
    }

    public List<PbxRows.InboundRouteRow> listInboundRoutes() throws Exception {
        return dao.listInboundRoutes();
    }

    public List<PbxRows.OutboundRouteRow> listOutboundRoutes() throws Exception {
        return dao.listOutboundRoutes();
    }

    public List<PbxRows.IvrDetailRow> listIvrs() throws Exception {
        return dao.listIvrs();
    }

    public List<PbxRows.IvrEntryRow> listIvrEntries(int ivrId) throws Exception {
        return dao.listIvrEntries(ivrId);
    }

    public List<PbxRows.MohRow> listMoh() throws Exception {
        List<String> fromDb = dao.listMohClassNamesFromDb();
        List<String> fromServer = List.of();
        if (mohService.isSshReady()) {
            try {
                fromServer = mohService.listMohDirectoriesOnServer();
            } catch (Exception ignored) {
                // still show DB / default classes
            }
        }
        List<String> merged = PbxMohService.mergeClassNames(fromDb, fromServer);
        List<PbxRows.MohRow> rows = new ArrayList<>(merged.size());
        for (String category : merged) {
            rows.add(new PbxRows.MohRow(category, "files", 0, "", ""));
        }
        return rows;
    }

    public List<PbxRows.AnnouncementRow> listAnnouncements() throws Exception {
        return dao.listAnnouncements();
    }

    public List<PbxRows.RecordingRow> listRecordings() throws Exception {
        return dao.listRecordings();
    }

    public int saveTimeGroup(PbxForms.TimeGroupForm form) throws Exception {
        requireNonBlank(form.description(), "Descripción");
        return writeDao.saveTimeGroup(form);
    }

    public int saveTimeGroupDetail(PbxForms.TimeGroupDetailForm form) throws Exception {
        if (form.timeGroupId() <= 0) {
            throw new IllegalArgumentException("Seleccione un grupo de horario.");
        }
        requireNonBlank(form.timeRule(), "Regla horaria");
        return writeDao.saveTimeGroupDetail(form);
    }

    public int saveTimeCondition(PbxForms.TimeConditionForm form) throws Exception {
        requireNonBlank(form.displayName(), "Nombre");
        if (form.timeGroupId() <= 0) {
            throw new IllegalArgumentException("ID de grupo horario inválido.");
        }
        return writeDao.saveTimeCondition(form);
    }

    public void saveQueue(PbxForms.QueueForm form) throws Exception {
        requireNonBlank(form.extension(), "Extensión de cola");
        requireNonBlank(form.description(), "Descripción");
        writeDao.saveQueue(form);
    }

    public void saveExtension(PbxForms.ExtensionForm form) throws Exception {
        requireNonBlank(form.extension(), "Extensión");
        requireNonBlank(form.name(), "Nombre");
        writeDao.saveExtension(form);
    }

    public void saveTrunk(PbxForms.TrunkForm form) throws Exception {
        requireNonBlank(form.name(), "Nombre");
        requireNonBlank(form.tech(), "Tecnología");
        requireNonBlank(form.channelId(), "Canal");
        if (form.create() && form.trunkId() <= 0) {
            // auto-assign in DAO
        }
        writeDao.saveTrunk(form);
    }

    public void saveInboundRoute(PbxForms.InboundRouteForm form) throws Exception {
        requireNonBlank(form.did(), "DID / extensión");
        writeDao.saveInboundRoute(form);
    }

    public int saveOutboundRoute(PbxForms.OutboundRouteForm form) throws Exception {
        requireNonBlank(form.name(), "Nombre");
        if (!form.create() && (form.routeId() == null || form.routeId() <= 0)) {
            throw new IllegalArgumentException("ID de ruta inválido.");
        }
        return writeDao.saveOutboundRoute(form);
    }

    public int saveIvr(PbxForms.IvrDetailForm form) throws Exception {
        requireNonBlank(form.name(), "Nombre IVR");
        return writeDao.saveIvr(form);
    }

    public void saveIvrEntry(PbxForms.IvrEntryForm form) throws Exception {
        if (form.ivrId() <= 0) {
            throw new IllegalArgumentException("Seleccione un IVR.");
        }
        requireNonBlank(form.selection(), "Tecla / selección");
        requireNonBlank(form.dest(), "Destino");
        writeDao.saveIvrEntry(form);
    }

    public void saveMoh(PbxForms.MohForm form) throws Exception {
        requireNonBlank(form.category(), "Nombre de clase MOH");
        PbxMohService.validateClassName(form.category());
        if (form.create()) {
            mohService.createMohDirectory(form.category());
        } else {
            throw new IllegalStateException(
                    "La edición de tipo/formato MOH no está en MySQL." + MOH_ISSABEL_HINT);
        }
    }

    public void deleteMoh(String category) throws Exception {
        requireNonBlank(category, "Clase MOH");
        PbxMohService.validateClassName(category);
        int refs = dao.countMohReferences(category);
        if (refs > 0) {
            throw new IllegalStateException(
                    "La clase «" + category.trim() + "» está en uso en "
                            + refs + " registro(s) PBX (extensiones, rutas, etc.). Cambie esas referencias antes de eliminar.");
        }
        mohService.deleteMohDirectory(category);
    }

    public int saveAnnouncement(PbxForms.AnnouncementForm form) throws Exception {
        requireNonBlank(form.description(), "Descripción");
        return writeDao.saveAnnouncement(form);
    }

    public void deleteAnnouncement(int announcementId) throws Exception {
        if (announcementId <= 0) {
            throw new IllegalArgumentException("ID de anuncio inválido.");
        }
        writeDao.deleteAnnouncement(announcementId);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio.");
        }
    }
}
