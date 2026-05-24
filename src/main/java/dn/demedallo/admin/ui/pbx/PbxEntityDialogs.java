package dn.demedallo.admin.ui.pbx;

import dn.demedallo.admin.model.pbx.PbxForms;
import dn.demedallo.admin.model.pbx.PbxRows;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.List;
import java.util.Optional;

/** Modal forms for PBX create/update (no delete). */
public final class PbxEntityDialogs {

    private PbxEntityDialogs() {
    }

    public static Optional<PbxForms.TimeGroupForm> timeGroup(PbxRows.TimeGroupRow existing) {
        TextField desc = field("Descripción", existing == null ? "" : existing.description());
        return show("Grupo de horario", grid(row("Descripción *", desc)), () ->
                new PbxForms.TimeGroupForm(existing == null ? null : existing.id(), desc.getText()));
    }

    public static Optional<PbxForms.TimeGroupDetailForm> timeGroupDetail(
            int groupId, PbxRows.TimeGroupDetailRow existing) {
        TimeGroupDetailFormPane form = new TimeGroupDetailFormPane(
                existing == null ? "" : existing.name(),
                existing == null ? "" : existing.timeRule());
        GridPane g = grid(full(form));
        return showWide("Franja horaria", g, () -> new PbxForms.TimeGroupDetailForm(
                existing == null ? null : existing.id(),
                groupId,
                form.buildTimeRule(),
                form.nameValue()));
    }

    public static Optional<PbxForms.TimeConditionForm> timeCondition(
            List<PbxRows.TimeGroupRow> groups, PbxRows.TimeConditionRow existing) {
        TextField name = field("Nombre", existing == null ? "" : existing.displayName());
        ComboBox<PbxRows.TimeGroupRow> groupCombo = groupCombo(groups, existing);
        TextField trueGoto = field("Si coincide (destino)", existing == null ? "" : existing.trueGoto());
        TextField falseGoto = field("Si no coincide", existing == null ? "" : existing.falseGoto());
        TextField dept = field("Departamento", existing == null ? "" : existing.deptName());
        return show("Condición horaria", grid(
                row("Nombre *", name),
                row("Grupo horario *", groupCombo),
                row("Si coincide", trueGoto),
                row("Si no coincide", falseGoto),
                row("Departamento", dept)), () -> {
            PbxRows.TimeGroupRow g = groupCombo.getSelectionModel().getSelectedItem();
            if (g == null) {
                throw new IllegalArgumentException("Seleccione un grupo horario.");
            }
            return new PbxForms.TimeConditionForm(
                    existing == null ? null : existing.id(),
                    name.getText(),
                    g.id(),
                    trueGoto.getText(),
                    falseGoto.getText(),
                    dept.getText());
        });
    }

    public static Optional<PbxForms.QueueForm> queue(PbxRows.QueueRow existing) {
        boolean create = existing == null;
        TextField ext = field("Extensión cola", create ? "" : existing.extension());
        ext.setEditable(create);
        TextField descr = field("Descripción", existing == null ? "" : existing.description());
        TextField dest = field("Destino si falla", existing == null ? "" : existing.destination());
        TextField maxWait = field("Espera máx.", existing == null ? "" : existing.maxWait());
        TextField cb = field("Callback ID", existing == null ? "" : existing.callbackId());
        return show(create ? "Nueva cola" : "Editar cola", grid(
                row("Extensión *", ext),
                row("Descripción *", descr),
                row("Destino fallo", dest),
                row("Espera máx.", maxWait),
                row("Callback", cb)), () -> new PbxForms.QueueForm(
                create, ext.getText(), descr.getText(), dest.getText(), maxWait.getText(), cb.getText()));
    }

    public static Optional<PbxForms.ExtensionForm> extension(PbxRows.ExtensionRow existing) {
        boolean create = existing == null;
        TextField ext = field("Extensión", create ? "" : existing.extension());
        ext.setEditable(create);
        TextField name = field("Nombre", existing == null ? "" : existing.name());
        TextField tech = field("Tecnología", existing == null ? "pjsip" : existing.tech());
        TextField dial = field("Marcado", existing == null ? "" : existing.dial());
        TextField vm = field("Buzón", existing == null ? "" : existing.voicemail());
        Label hint = new Label("Solo actualiza users/devices. Parámetros SIP avanzados: GUI Issabel.");
        hint.setWrapText(true);
        return show(create ? "Nueva extensión" : "Editar extensión", grid(
                row("Extensión *", ext),
                row("Nombre *", name),
                row("Tecnología", tech),
                row("Marcado", dial),
                row("Buzón", vm),
                full(hint)), () -> new PbxForms.ExtensionForm(
                create, ext.getText(), name.getText(), tech.getText(), dial.getText(), vm.getText()));
    }

    public static Optional<PbxForms.TrunkForm> trunk(PbxRows.TrunkRow existing) {
        boolean create = existing == null;
        TextField trunkId = field("ID troncal", create ? "" : String.valueOf(existing.trunkId()));
        trunkId.setEditable(create);
        TextField name = field("Nombre", existing == null ? "" : existing.name());
        TextField tech = field("Tecnología", existing == null ? "pjsip" : existing.tech());
        tech.setEditable(create);
        TextField channel = field("Canal", existing == null ? "" : existing.channelId());
        channel.setEditable(create);
        TextField outCid = field("CID salida", existing == null ? "" : existing.outCid());
        TextField disabled = field("Deshabilitado (on/off)", existing == null ? "off" : existing.disabled());
        TextField provider = field("Proveedor", existing == null ? "" : existing.provider());
        return show(create ? "Nueva troncal" : "Editar troncal", grid(
                row("ID troncal", trunkId),
                row("Nombre *", name),
                row("Tecnología *", tech),
                row("Canal *", channel),
                row("CID salida", outCid),
                row("Deshabilitado", disabled),
                row("Proveedor", provider)), () -> {
            int id = create ? parseIntOrZero(trunkId.getText()) : existing.trunkId();
            return new PbxForms.TrunkForm(create, id, name.getText(), tech.getText(), channel.getText(),
                    outCid.getText(), disabled.getText(), provider.getText());
        });
    }

    public static Optional<PbxForms.InboundRouteForm> inbound(PbxRows.InboundRouteRow existing) {
        boolean create = existing == null;
        TextField did = field("DID / extensión", create ? "" : existing.did());
        did.setEditable(create);
        TextField cid = field("CID", existing == null ? "" : existing.cidNum());
        TextField dest = field("Destino", existing == null ? "" : existing.destination());
        TextField descr = field("Descripción", existing == null ? "" : existing.description());
        TextField moh = field("MOH class", existing == null ? "default" : existing.mohClass());
        return show(create ? "Nueva ruta entrante" : "Editar ruta entrante", grid(
                row("DID / ext *", did),
                row("CID", cid),
                row("Destino", dest),
                row("Descripción", descr),
                row("MOH", moh)), () -> new PbxForms.InboundRouteForm(
                create, did.getText(), cid.getText(), dest.getText(), descr.getText(), moh.getText()));
    }

    public static Optional<PbxForms.OutboundRouteForm> outbound(
            List<PbxRows.TimeGroupRow> groups, PbxRows.OutboundRouteRow existing) {
        boolean create = existing == null;
        TextField routeId = field("ID ruta", create ? "" : String.valueOf(existing.routeId()));
        routeId.setEditable(false);
        TextField name = field("Nombre", existing == null ? "" : existing.name());
        TextField outCid = field("CID salida", existing == null ? "" : existing.outCid());
        TextField emergency = field("Ruta emergencia", existing == null ? "" : existing.emergencyRoute());
        TextField timeGroupId = field("ID grupo horario (0=ninguno)",
                existing == null || existing.timeGroupId() <= 0 ? "0" : String.valueOf(existing.timeGroupId()));
        TextArea patterns = area("Patrones nuevos (uno por línea: prefijo|coincidencia)",
                create ? "" : "");
        TextArea trunks = area("Troncales nuevas (IDs separados por coma)",
                create ? "" : "");
        Label hint = new Label("En edición solo se añaden patrones/troncales nuevos (no se eliminan existentes).");
        hint.setWrapText(true);
        return show(create ? "Nueva ruta saliente" : "Editar ruta saliente", grid(
                row("ID", routeId),
                row("Nombre *", name),
                row("CID salida", outCid),
                row("Emergencia", emergency),
                row("Grupo horario ID", timeGroupId),
                row("Patrones nuevos", patterns),
                row("Troncales nuevas", trunks),
                full(hint)), () -> {
            Integer rid = create ? null : existing.routeId();
            int tgId = parseIntOrZero(timeGroupId.getText());
            Integer tg = tgId <= 0 ? null : tgId;
            return new PbxForms.OutboundRouteForm(
                    create, rid, name.getText(), outCid.getText(), emergency.getText(), tg,
                    patterns.getText(), trunks.getText());
        });
    }

    public static Optional<PbxForms.IvrDetailForm> ivr(PbxRows.IvrDetailRow existing) {
        TextField name = field("Nombre IVR", existing == null ? "" : existing.name());
        TextField descr = field("Descripción", existing == null ? "" : existing.description());
        TextField ann = field("ID grabación anuncio (0=ninguno)",
                existing == null || existing.announcement() <= 0 ? "0" : String.valueOf(existing.announcement()));
        TextField directDial = field("Marcado directo extensión",
                existing == null ? "" : existing.directDial());
        TextField timeout = field("Timeout (s)",
                existing == null || existing.timeoutTime() <= 0 ? "10" : String.valueOf(existing.timeoutTime()));
        TextField timeoutDest = field("Destino timeout",
                existing == null ? "" : existing.timeoutDestination());
        TextField invalidDest = field("Destino inválido",
                existing == null ? "" : existing.invalidDestination());
        Label hint = new Label("Destino FreePBX: ext-queues,8001,1 | from-did-direct,101,1 | app-blackhole,hangup,1");
        hint.setWrapText(true);
        return show(existing == null ? "Nuevo IVR" : "Editar IVR", grid(
                row("Nombre *", name),
                row("Descripción", descr),
                row("Grabación ID", ann),
                row("Marcado directo", directDial),
                row("Timeout (s)", timeout),
                row("Destino timeout", timeoutDest),
                row("Destino inválido", invalidDest),
                full(hint)), () -> new PbxForms.IvrDetailForm(
                existing == null ? null : existing.id(),
                name.getText(),
                descr.getText(),
                parseIntOrZero(ann.getText()),
                directDial.getText(),
                parseIntOrZero(timeout.getText()),
                timeoutDest.getText(),
                invalidDest.getText()));
    }

    public static Optional<PbxForms.IvrEntryForm> ivrEntry(int ivrId, PbxRows.IvrEntryRow existing) {
        boolean create = existing == null;
        TextField selection = field("Tecla / valor (0-9, *, #, i, t…)",
                create ? "" : existing.selection());
        selection.setEditable(create);
        TextField dest = field("Destino FreePBX", create ? "" : existing.dest());
        TextField ivrRet = field("Volver al IVR (0/1)", create ? "0" : String.valueOf(existing.ivrRet()));
        Label hint = new Label("Ej.: ext-queues,2001,1 — ivr_ret=1 vuelve al menú tras el destino.");
        hint.setWrapText(true);
        return show(create ? "Nueva opción IVR" : "Editar opción IVR", grid(
                row("Selección *", selection),
                row("Destino *", dest),
                row("Volver IVR", ivrRet),
                full(hint)), () -> new PbxForms.IvrEntryForm(
                create,
                ivrId,
                selection.getText(),
                create ? null : existing.selection(),
                dest.getText(),
                parseIntOrZero(ivrRet.getText())));
    }

    public static Optional<PbxForms.MohForm> moh(PbxRows.MohRow existing) {
        if (existing != null) {
            return Optional.empty();
        }
        TextField category = field("Clase MOH (nombre)", "");
        Label hint = new Label(
                "Issabel guarda las clases como carpetas en /var/lib/asterisk/moh/ (no hay tabla «music»)."
                        + " Se crea la carpeta vía SSH; suba los WAV/gsm en la GUI Issabel y pulse «Recargar Asterisk».");
        hint.setWrapText(true);
        return show("Nueva clase MOH", grid(
                row("Clase *", category),
                full(hint)), () -> new PbxForms.MohForm(
                true,
                category.getText(),
                "files",
                0,
                "",
                ""));
    }

    public static Optional<PbxForms.AnnouncementForm> announcement(
            List<PbxRows.RecordingRow> recordings, PbxRows.AnnouncementRow existing) {
        TextField descr = field("Descripción", existing == null ? "" : existing.description());
        ComboBox<PbxRows.RecordingRow> recCombo = recordingCombo(recordings, existing);
        TextField postDest = field("Destino posterior",
                existing == null ? "" : existing.postDest());
        TextField repeatMsg = field("Repetir mensaje", existing == null ? "" : existing.repeatMsg());
        TextField returnIvr = field("Volver a IVR (0/1)",
                existing == null ? "0" : String.valueOf(existing.returnIvr()));
        Label hint = new Label("El audio proviene de Grabaciones del sistema (tabla recordings).");
        hint.setWrapText(true);
        return show(existing == null ? "Nuevo anuncio" : "Editar anuncio", grid(
                row("Descripción *", descr),
                row("Grabación", recCombo),
                row("Destino posterior", postDest),
                row("Repetir msg", repeatMsg),
                row("Volver IVR", returnIvr),
                full(hint)), () -> {
            PbxRows.RecordingRow rec = recCombo.getSelectionModel().getSelectedItem();
            Integer recId = rec == null || rec.id() <= 0 ? null : rec.id();
            return new PbxForms.AnnouncementForm(
                    existing == null ? null : existing.id(),
                    descr.getText(),
                    recId,
                    postDest.getText(),
                    repeatMsg.getText(),
                    parseIntOrZero(returnIvr.getText()));
        });
    }

    private static ComboBox<PbxRows.RecordingRow> recordingCombo(
            List<PbxRows.RecordingRow> recordings, PbxRows.AnnouncementRow existing) {
        ComboBox<PbxRows.RecordingRow> combo = new ComboBox<>();
        PbxRows.RecordingRow none = new PbxRows.RecordingRow(0, "(ninguna)", "");
        combo.getItems().add(none);
        combo.getItems().addAll(recordings);
        if (existing != null && existing.recordingId() > 0) {
            recordings.stream().filter(r -> r.id() == existing.recordingId()).findFirst()
                    .ifPresent(r -> combo.getSelectionModel().select(r));
        } else {
            combo.getSelectionModel().selectFirst();
        }
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private static ComboBox<PbxRows.TimeGroupRow> groupCombo(
            List<PbxRows.TimeGroupRow> groups, PbxRows.TimeConditionRow existing) {
        ComboBox<PbxRows.TimeGroupRow> combo = new ComboBox<>();
        combo.getItems().addAll(groups);
        if (existing != null) {
            groups.stream().filter(g -> g.id() == existing.timeGroupId()).findFirst()
                    .ifPresent(g -> combo.getSelectionModel().select(g));
        } else if (!groups.isEmpty()) {
            combo.getSelectionModel().selectFirst();
        }
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private static int parseIntOrZero(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private interface FormSupplier<T> {
        T get() throws Exception;
    }

    private static <T> Optional<T> show(String title, GridPane grid, FormSupplier<T> onSave) {
        Dialog<T> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(title);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        grid.setPadding(new Insets(12));
        grid.setHgap(10);
        grid.setVgap(8);
        d.getDialogPane().setContent(grid);
        d.getDialogPane().setMinWidth(480);
        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            try {
                return onSave.get();
            } catch (Exception ex) {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING);
                a.setHeaderText("Revise el formulario");
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.toString());
                a.showAndWait();
                return null;
            }
        });
        return d.showAndWait();
    }

    private static <T> Optional<T> showWide(String title, GridPane grid, FormSupplier<T> onSave) {
        Dialog<T> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(null);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        grid.setPadding(new Insets(12));
        grid.setHgap(10);
        grid.setVgap(8);
        d.getDialogPane().setContent(grid);
        d.getDialogPane().setMinWidth(560);
        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            try {
                return onSave.get();
            } catch (Exception ex) {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING);
                a.setHeaderText("Revise el formulario");
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.toString());
                a.showAndWait();
                return null;
            }
        });
        return d.showAndWait();
    }

    private static TextField field(String prompt, String value) {
        TextField f = new TextField(value == null ? "" : value);
        f.setPromptText(prompt);
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private static TextArea area(String prompt, String value) {
        TextArea a = new TextArea(value == null ? "" : value);
        a.setPromptText(prompt);
        a.setPrefRowCount(3);
        a.setWrapText(true);
        a.setMaxWidth(Double.MAX_VALUE);
        return a;
    }

    private static GridPane grid(GridRow... rows) {
        GridPane g = new GridPane();
        int r = 0;
        for (GridRow row : rows) {
            if (row.fullWidth()) {
                g.add(row.node(), 0, r, 2, 1);
                GridPane.setHgrow(row.node(), Priority.ALWAYS);
            } else {
                g.add(new Label(row.label()), 0, r);
                g.add(row.node(), 1, r);
                GridPane.setHgrow(row.node(), Priority.ALWAYS);
            }
            r++;
        }
        return g;
    }

    private static GridRow row(String label, javafx.scene.Node node) {
        return new GridRow(label, node, false);
    }

    private static GridRow full(javafx.scene.Node node) {
        return new GridRow("", node, true);
    }

    private record GridRow(String label, javafx.scene.Node node, boolean fullWidth) {
    }
}
