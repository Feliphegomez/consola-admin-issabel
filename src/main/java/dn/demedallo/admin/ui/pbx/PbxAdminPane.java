package dn.demedallo.admin.ui.pbx;

import dn.demedallo.admin.model.pbx.PbxForms;
import dn.demedallo.admin.model.pbx.PbxRows;
import dn.demedallo.admin.service.PbxAdminService;
import dn.demedallo.admin.service.PbxReloadService;
import dn.demedallo.admin.ui.util.TableViewUtil;
import dn.demedallo.admin.util.AdminDbSettings;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Issabel/FreePBX configuration browser with create/update (delete is not allowed).
 */
public final class PbxAdminPane extends BorderPane implements AutoCloseable {

    private final PbxAdminService service;
    private final PbxReloadService reloadService;
    private final Button reloadAsteriskBtn = new Button("Recargar Asterisk");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pbx-admin");
        t.setDaemon(true);
        return t;
    });

    private final Label status = new Label();
    private final TabPane sections = new TabPane();

    private final TableView<PbxRows.TimeGroupRow> timeGroupsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.TimeGroupDetailRow> timeGroupDetailsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.TimeConditionRow> timeConditionsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.QueueRow> queuesTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.ExtensionRow> extensionsTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.TrunkRow> trunksTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.InboundRouteRow> inboundTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.OutboundRouteRow> outboundTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.IvrDetailRow> ivrTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.IvrEntryRow> ivrEntriesTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.MohRow> mohTable =
            new TableView<>(FXCollections.observableArrayList());
    private final TableView<PbxRows.AnnouncementRow> announcementsTable =
            new TableView<>(FXCollections.observableArrayList());

    private volatile int loadGeneration;
    private volatile List<PbxRows.TimeGroupRow> cachedTimeGroups = List.of();
    private volatile List<PbxRows.RecordingRow> cachedRecordings = List.of();

    /** Single-arg ctor for older builds; prefer {@link #PbxAdminPane(AdminDbSettings, String)}. */
    public PbxAdminPane(AdminDbSettings dbSettings) {
        this(dbSettings, "");
    }

    public PbxAdminPane(AdminDbSettings dbSettings, String eccpHost) {
        this.service = new PbxAdminService(dbSettings, eccpHost);
        this.reloadService = new PbxReloadService(eccpHost);
        getStyleClass().add("pbx-admin-pane");
        setPadding(new Insets(8));

        reloadAsteriskBtn.getStyleClass().addAll("monitor-btn", "pbx-reload-btn");
        reloadAsteriskBtn.setDisable(true);
        reloadAsteriskBtn.setOnAction(e -> runReloadAsterisk());

        buildTables();

        sections.getTabs().addAll(
                tab("Grupos de horario", timeGroupsSection()),
                tab("Condiciones horarias", crudSection(timeConditionsTable, "condiciones-horarias",
                        this::newTimeCondition, this::editTimeCondition, this::refreshTimeConditions)),
                tab("Colas", crudSection(queuesTable, "colas",
                        this::newQueue, this::editQueue, this::refreshQueues)),
                tab("Extensiones", crudSection(extensionsTable, "extensiones",
                        this::newExtension, this::editExtension, this::refreshExtensions)),
                tab("Troncales", crudSection(trunksTable, "troncales",
                        this::newTrunk, this::editTrunk, this::refreshTrunks)),
                tab("Rutas entrantes", crudSection(inboundTable, "rutas-entrantes",
                        this::newInbound, this::editInbound, this::refreshInbound)),
                tab("Rutas salientes", crudSection(outboundTable, "rutas-salientes",
                        this::newOutbound, this::editOutbound, this::refreshOutbound)),
                tab("IVR", ivrSection()),
                tab("Música en espera", crudSectionDeletable(mohTable, "moh",
                        this::newMoh, null, this::refreshMoh, this::deleteMoh)),
                tab("Anuncios", crudSectionDeletable(announcementsTable, "anuncios",
                        this::newAnnouncement, this::editAnnouncement, this::refreshAnnouncements,
                        this::deleteAnnouncement)));
        sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Button refresh = new Button("Actualizar todo");
        refresh.getStyleClass().add("monitor-btn");
        refresh.setOnAction(e -> refreshAll());

        status.setWrapText(true);
        status.getStyleClass().add("monitor-status");

        HBox toolbar = new HBox(12, refresh, reloadAsteriskBtn, status);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.ALWAYS);

        setTop(toolbar);
        setCenter(sections);

        if (!service.isAvailable()) {
            status.setText("MySQL no configurado. Active la base de datos en el login.");
        } else {
            status.setText("Base PBX: " + service.pbxDatabaseLabel()
                    + ". Alta/edición en todas las entidades; eliminar solo en MOH y Anuncios.");
            refreshAll();
        }
    }

    private Tab tab(String title, Parent content) {
        return new Tab(title, content);
    }

    private Parent timeGroupsSection() {
        SplitPane split = new SplitPane();
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.45);

        VBox top = new VBox(6);
        top.getChildren().addAll(
                crudBar(this::newTimeGroup, this::editTimeGroup, this::refreshTimeGroups),
                sectionBox("Grupos", timeGroupsTable, "grupos-horario"));

        VBox bottom = new VBox(6);
        bottom.getChildren().addAll(
                crudBar(this::newTimeGroupDetail, this::editTimeGroupDetail, () -> {
                    PbxRows.TimeGroupRow g = timeGroupsTable.getSelectionModel().getSelectedItem();
                    if (g != null) {
                        refreshTimeGroupDetails(g.id());
                    }
                }),
                sectionBox("Franjas del grupo seleccionado", timeGroupDetailsTable, "franjas-horario"));

        VBox.setVgrow(top.getChildren().get(1), Priority.ALWAYS);
        VBox.setVgrow(bottom.getChildren().get(1), Priority.ALWAYS);
        split.getItems().addAll(top, bottom);

        timeGroupsTable.getSelectionModel().selectedItemProperty().addListener((o, prev, row) -> {
            if (row == null) {
                timeGroupDetailsTable.getItems().clear();
                return;
            }
            refreshTimeGroupDetails(row.id());
        });
        return split;
    }

    private Parent ivrSection() {
        SplitPane split = new SplitPane();
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.45);

        VBox top = new VBox(6);
        top.getChildren().addAll(
                crudBar(this::newIvr, this::editIvr, this::refreshIvrs),
                sectionBox("IVR", ivrTable, "ivr"));

        VBox bottom = new VBox(6);
        bottom.getChildren().addAll(
                crudBar(this::newIvrEntry, this::editIvrEntry, () -> {
                    PbxRows.IvrDetailRow ivr = ivrTable.getSelectionModel().getSelectedItem();
                    if (ivr != null) {
                        refreshIvrEntries(ivr.id());
                    }
                }),
                sectionBox("Opciones del IVR seleccionado", ivrEntriesTable, "ivr-opciones"));

        VBox.setVgrow(top.getChildren().get(1), Priority.ALWAYS);
        VBox.setVgrow(bottom.getChildren().get(1), Priority.ALWAYS);
        split.getItems().addAll(top, bottom);

        ivrTable.getSelectionModel().selectedItemProperty().addListener((o, prev, row) -> {
            if (row == null) {
                ivrEntriesTable.getItems().clear();
                return;
            }
            refreshIvrEntries(row.id());
        });
        return split;
    }

    private VBox crudSectionDeletable(TableView<?> table, String exportName,
                                      Runnable onNew, Runnable onEdit, Runnable onRefresh,
                                      Runnable onDelete) {
        VBox box = new VBox(6);
        box.getChildren().addAll(
                crudBarDeletable(onNew, onEdit, onRefresh, onDelete),
                sectionBox(null, table, exportName));
        VBox.setVgrow(box.getChildren().get(1), Priority.ALWAYS);
        return box;
    }

    private HBox crudBarDeletable(Runnable onNew, Runnable onEdit, Runnable onRefresh,
                                  Runnable onDelete) {
        Button eliminar = new Button("Eliminar");
        eliminar.getStyleClass().add("pbx-delete-btn");
        eliminar.setOnAction(e -> onDelete.run());
        HBox bar = crudBar(onNew, onEdit, onRefresh);
        bar.getChildren().add(eliminar);
        return bar;
    }

    private VBox crudSection(TableView<?> table, String exportName,
                             Runnable onNew, Runnable onEdit, Runnable onRefresh) {
        VBox box = new VBox(6);
        box.getChildren().addAll(crudBar(onNew, onEdit, onRefresh), sectionBox(null, table, exportName));
        VBox.setVgrow(box.getChildren().get(1), Priority.ALWAYS);
        return box;
    }

    private HBox crudBar(Runnable onNew, Runnable onEdit, Runnable onRefresh) {
        Button nuevo = new Button("Nuevo");
        Button actualizar = new Button("Actualizar");
        nuevo.getStyleClass().add("monitor-btn");
        actualizar.getStyleClass().add("monitor-btn");
        nuevo.setOnAction(e -> onNew.run());
        actualizar.setOnAction(e -> onRefresh.run());
        HBox bar;
        if (onEdit != null) {
            Button editar = new Button("Editar");
            editar.getStyleClass().add("monitor-btn");
            editar.setOnAction(e -> onEdit.run());
            bar = new HBox(8, nuevo, editar, actualizar);
        } else {
            bar = new HBox(8, nuevo, actualizar);
        }
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox sectionBox(String heading, TableView<?> table, String exportName) {
        VBox box = new VBox(6);
        VBox.setVgrow(box, Priority.ALWAYS);
        if (heading != null && !heading.isBlank()) {
            Label lbl = new Label(heading);
            lbl.getStyleClass().add("pbx-section-title");
            box.getChildren().add(lbl);
        }
        TableViewUtil.prepareFillWidth(table);
        Parent wrapped = TableViewUtil.wrapInScrollPane(table, exportName, true);
        VBox.setVgrow(wrapped, Priority.ALWAYS);
        box.getChildren().add(wrapped);
        return box;
    }

    private void newTimeGroup() {
        PbxEntityDialogs.timeGroup(null).ifPresent(form ->
                runWrite("Grupo horario guardado", () -> service.saveTimeGroup(form), this::refreshTimeGroups));
    }

    private void editTimeGroup() {
        PbxRows.TimeGroupRow row = timeGroupsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("grupo de horario");
            return;
        }
        PbxEntityDialogs.timeGroup(row).ifPresent(form ->
                runWrite("Grupo horario actualizado", () -> service.saveTimeGroup(form), this::refreshTimeGroups));
    }

    private void newTimeGroupDetail() {
        PbxRows.TimeGroupRow g = timeGroupsTable.getSelectionModel().getSelectedItem();
        if (g == null) {
            warnSelect("grupo de horario (arriba)");
            return;
        }
        PbxEntityDialogs.timeGroupDetail(g.id(), null).ifPresent(form ->
                runWrite("Franja guardada", () -> service.saveTimeGroupDetail(form),
                        () -> refreshTimeGroupDetails(g.id())));
    }

    private void editTimeGroupDetail() {
        PbxRows.TimeGroupRow g = timeGroupsTable.getSelectionModel().getSelectedItem();
        PbxRows.TimeGroupDetailRow row = timeGroupDetailsTable.getSelectionModel().getSelectedItem();
        if (g == null || row == null) {
            warnSelect("franja horaria");
            return;
        }
        PbxEntityDialogs.timeGroupDetail(g.id(), row).ifPresent(form ->
                runWrite("Franja actualizada", () -> service.saveTimeGroupDetail(form),
                        () -> refreshTimeGroupDetails(g.id())));
    }

    private void newTimeCondition() {
        loadTimeGroupsThen(groups ->
                PbxEntityDialogs.timeCondition(groups, null).ifPresent(form ->
                        runWrite("Condición horaria guardada", () -> service.saveTimeCondition(form),
                                this::refreshTimeConditions)));
    }

    private void editTimeCondition() {
        PbxRows.TimeConditionRow row = timeConditionsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("condición horaria");
            return;
        }
        loadTimeGroupsThen(groups ->
                PbxEntityDialogs.timeCondition(groups, row).ifPresent(form ->
                        runWrite("Condición horaria actualizada", () -> service.saveTimeCondition(form),
                                this::refreshTimeConditions)));
    }

    private void newQueue() {
        PbxEntityDialogs.queue(null).ifPresent(form ->
                runWrite("Cola creada", () -> {
                    service.saveQueue(form);
                    return null;
                }, this::refreshQueues));
    }

    private void editQueue() {
        PbxRows.QueueRow row = queuesTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("cola");
            return;
        }
        PbxEntityDialogs.queue(row).ifPresent(form ->
                runWrite("Cola actualizada", () -> {
                    service.saveQueue(form);
                    return null;
                }, this::refreshQueues));
    }

    private void newExtension() {
        PbxEntityDialogs.extension(null).ifPresent(form ->
                runWrite("Extensión creada", () -> {
                    service.saveExtension(form);
                    return null;
                }, this::refreshExtensions));
    }

    private void editExtension() {
        PbxRows.ExtensionRow row = extensionsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("extensión");
            return;
        }
        PbxEntityDialogs.extension(row).ifPresent(form ->
                runWrite("Extensión actualizada", () -> {
                    service.saveExtension(form);
                    return null;
                }, this::refreshExtensions));
    }

    private void newTrunk() {
        PbxEntityDialogs.trunk(null).ifPresent(form ->
                runWrite("Troncal creada", () -> {
                    service.saveTrunk(form);
                    return null;
                }, this::refreshTrunks));
    }

    private void editTrunk() {
        PbxRows.TrunkRow row = trunksTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("troncal");
            return;
        }
        PbxEntityDialogs.trunk(row).ifPresent(form ->
                runWrite("Troncal actualizada", () -> {
                    service.saveTrunk(form);
                    return null;
                }, this::refreshTrunks));
    }

    private void newInbound() {
        PbxEntityDialogs.inbound(null).ifPresent(form ->
                runWrite("Ruta entrante creada", () -> {
                    service.saveInboundRoute(form);
                    return null;
                }, this::refreshInbound));
    }

    private void editInbound() {
        PbxRows.InboundRouteRow row = inboundTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("ruta entrante");
            return;
        }
        PbxEntityDialogs.inbound(row).ifPresent(form ->
                runWrite("Ruta entrante actualizada", () -> {
                    service.saveInboundRoute(form);
                    return null;
                }, this::refreshInbound));
    }

    private void newOutbound() {
        loadTimeGroupsThen(groups ->
                PbxEntityDialogs.outbound(groups, null).ifPresent(form ->
                        runWrite("Ruta saliente creada", () -> service.saveOutboundRoute(form),
                                this::refreshOutbound)));
    }

    private void editOutbound() {
        PbxRows.OutboundRouteRow row = outboundTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("ruta saliente");
            return;
        }
        loadTimeGroupsThen(groups ->
                PbxEntityDialogs.outbound(groups, row).ifPresent(form ->
                        runWrite("Ruta saliente actualizada", () -> service.saveOutboundRoute(form),
                                this::refreshOutbound)));
    }

    private void newIvr() {
        PbxEntityDialogs.ivr(null).ifPresent(form ->
                runWrite("IVR creado", () -> service.saveIvr(form), this::refreshIvrs));
    }

    private void editIvr() {
        PbxRows.IvrDetailRow row = ivrTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("IVR");
            return;
        }
        PbxEntityDialogs.ivr(row).ifPresent(form ->
                runWrite("IVR actualizado", () -> service.saveIvr(form), this::refreshIvrs));
    }

    private void newIvrEntry() {
        PbxRows.IvrDetailRow ivr = ivrTable.getSelectionModel().getSelectedItem();
        if (ivr == null) {
            warnSelect("IVR (arriba)");
            return;
        }
        PbxEntityDialogs.ivrEntry(ivr.id(), null).ifPresent(form ->
                runWrite("Opción IVR guardada", () -> {
                    service.saveIvrEntry(form);
                    return null;
                }, () -> refreshIvrEntries(ivr.id())));
    }

    private void editIvrEntry() {
        PbxRows.IvrDetailRow ivr = ivrTable.getSelectionModel().getSelectedItem();
        PbxRows.IvrEntryRow row = ivrEntriesTable.getSelectionModel().getSelectedItem();
        if (ivr == null || row == null) {
            warnSelect("opción IVR");
            return;
        }
        PbxEntityDialogs.ivrEntry(ivr.id(), row).ifPresent(form ->
                runWrite("Opción IVR actualizada", () -> {
                    service.saveIvrEntry(form);
                    return null;
                }, () -> refreshIvrEntries(ivr.id())));
    }

    private void newMoh() {
        PbxEntityDialogs.moh(null).ifPresent(form ->
                runWrite("Clase MOH creada", () -> {
                    service.saveMoh(form);
                    return null;
                }, this::refreshMoh));
    }

    private void deleteMoh() {
        PbxRows.MohRow row = mohTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("clase MOH");
            return;
        }
        if (!confirmDelete("clase MOH «" + row.category() + "»")) {
            return;
        }
        runWrite("Clase MOH eliminada", () -> {
            service.deleteMoh(row.category());
            return null;
        }, this::refreshMoh);
    }

    private void newAnnouncement() {
        loadRecordingsThen(recordings ->
                PbxEntityDialogs.announcement(recordings, null).ifPresent(form ->
                        runWrite("Anuncio creado", () -> service.saveAnnouncement(form),
                                this::refreshAnnouncements)));
    }

    private void editAnnouncement() {
        PbxRows.AnnouncementRow row = announcementsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("anuncio");
            return;
        }
        loadRecordingsThen(recordings ->
                PbxEntityDialogs.announcement(recordings, row).ifPresent(form ->
                        runWrite("Anuncio actualizado", () -> service.saveAnnouncement(form),
                                this::refreshAnnouncements)));
    }

    private void deleteAnnouncement() {
        PbxRows.AnnouncementRow row = announcementsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            warnSelect("anuncio");
            return;
        }
        if (!confirmDelete("anuncio «" + row.description() + "» (ID " + row.id() + ")")) {
            return;
        }
        runWrite("Anuncio eliminado", () -> {
            service.deleteAnnouncement(row.id());
            return null;
        }, this::refreshAnnouncements);
    }

    private boolean confirmDelete(String label) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setHeaderText("Confirmar eliminación");
        a.setContentText("¿Eliminar " + label + "?\nEsta acción no se puede deshacer.");
        return a.showAndWait().filter(r -> r == javafx.scene.control.ButtonType.OK).isPresent();
    }

    private void loadRecordingsThen(java.util.function.Consumer<List<PbxRows.RecordingRow>> action) {
        if (!cachedRecordings.isEmpty()) {
            action.accept(cachedRecordings);
            return;
        }
        worker.execute(() -> {
            try {
                List<PbxRows.RecordingRow> list = service.listRecordings();
                Platform.runLater(() -> {
                    cachedRecordings = list;
                    action.accept(list);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error cargando grabaciones: " + ex.getMessage()));
            }
        });
    }

    private void loadTimeGroupsThen(java.util.function.Consumer<List<PbxRows.TimeGroupRow>> action) {
        if (!cachedTimeGroups.isEmpty()) {
            action.accept(cachedTimeGroups);
            return;
        }
        worker.execute(() -> {
            try {
                List<PbxRows.TimeGroupRow> groups = service.listTimeGroups();
                Platform.runLater(() -> {
                    cachedTimeGroups = groups;
                    action.accept(groups);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error cargando grupos: " + ex.getMessage()));
            }
        });
    }

    private void runWrite(String successPrefix, WriteAction action, Runnable refresh) {
        worker.execute(() -> {
            try {
                action.run();
                Platform.runLater(() -> {
                    markReloadPending();
                    status.setText(successPrefix + ". Pendiente aplicar en Asterisk — use «Recargar Asterisk».");
                    refresh.run();
                });
            } catch (IllegalArgumentException ex) {
                Platform.runLater(() -> showError(ex.getMessage()));
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                Platform.runLater(() -> showError(msg));
            }
        });
    }

    private void markReloadPending() {
        reloadAsteriskBtn.setDisable(false);
        if (!reloadAsteriskBtn.getStyleClass().contains("pending")) {
            reloadAsteriskBtn.getStyleClass().add("pending");
        }
        reloadAsteriskBtn.setText(reloadService.isSshReady()
                ? "Recargar Asterisk (pendiente)"
                : "Recargar Asterisk (pendiente — configure SSH)");
    }

    private void clearReloadPending() {
        reloadAsteriskBtn.getStyleClass().remove("pending");
        reloadAsteriskBtn.setDisable(true);
        reloadAsteriskBtn.setText("Recargar Asterisk");
    }

    private void runReloadAsterisk() {
        if (!reloadService.isSshReady()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setHeaderText("SSH requerido");
            a.setContentText(
                    "Configure SSH en la pestaña «Logs Issabel» (activar SSH, usuario root y contraseña), "
                            + "luego pulse de nuevo «Recargar Asterisk».");
            a.showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Recargar Asterisk / FreePBX");
        confirm.setContentText(
                "Se ejecutará fwconsole reload en " + reloadService.sshHostLabel()
                        + ".\nPuede tardar 1–3 minutos. ¿Continuar?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        reloadAsteriskBtn.setDisable(true);
        status.setText("Recargando Asterisk en " + reloadService.sshHostLabel() + "…");
        worker.execute(() -> {
            try {
                String output = reloadService.reloadAsterisk();
                String tail = output == null || output.isBlank()
                        ? "(sin salida)"
                        : output.trim();
                if (tail.length() > 500) {
                    tail = "…" + tail.substring(tail.length() - 500);
                }
                String finalTail = tail;
                Platform.runLater(() -> {
                    clearReloadPending();
                    status.setText("Asterisk recargado correctamente.");
                    Alert ok = new Alert(Alert.AlertType.INFORMATION);
                    ok.setHeaderText("Recarga completada");
                    ok.setContentText("fwconsole reload finalizó en " + reloadService.sshHostLabel()
                            + ".\n\n" + finalTail);
                    ok.showAndWait();
                });
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                Platform.runLater(() -> {
                    reloadAsteriskBtn.setDisable(false);
                    status.setText("Error al recargar Asterisk: " + msg);
                    showError(msg);
                });
            }
        });
    }

    @FunctionalInterface
    private interface WriteAction {
        Object run() throws Exception;
    }

    private void warnSelect(String entity) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Selección requerida");
        a.setContentText("Seleccione una fila de " + entity + " para editar.");
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("No se pudo guardar");
        a.setContentText(msg);
        a.showAndWait();
        status.setText("Error: " + msg);
    }

    private void refreshTimeGroups() {
        loadAsync("Grupos de horario", service::listTimeGroups, rows -> {
            cachedTimeGroups = rows;
            timeGroupsTable.getItems().setAll(rows);
        });
    }

    private void refreshTimeGroupDetails(int groupId) {
        loadAsync("Franjas horarias", () -> service.listTimeGroupDetails(groupId),
                timeGroupDetailsTable.getItems()::setAll);
    }

    private void refreshTimeConditions() {
        loadAsync("Condiciones horarias", service::listTimeConditions, timeConditionsTable.getItems()::setAll);
    }

    private void refreshQueues() {
        loadAsync("Colas", service::listQueues, queuesTable.getItems()::setAll);
    }

    private void refreshExtensions() {
        loadAsync("Extensiones", service::listExtensions, extensionsTable.getItems()::setAll);
    }

    private void refreshTrunks() {
        loadAsync("Troncales", service::listTrunks, trunksTable.getItems()::setAll);
    }

    private void refreshInbound() {
        loadAsync("Rutas entrantes", service::listInboundRoutes, inboundTable.getItems()::setAll);
    }

    private void refreshOutbound() {
        loadAsync("Rutas salientes", service::listOutboundRoutes, outboundTable.getItems()::setAll);
    }

    private void refreshIvrs() {
        loadAsync("IVR", service::listIvrs, rows -> {
            ivrTable.getItems().setAll(rows);
            PbxRows.IvrDetailRow selected = ivrTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                refreshIvrEntries(selected.id());
            } else {
                ivrEntriesTable.getItems().clear();
            }
        });
    }

    private void refreshIvrEntries(int ivrId) {
        loadAsync("Opciones IVR", () -> service.listIvrEntries(ivrId), ivrEntriesTable.getItems()::setAll);
    }

    private void refreshMoh() {
        loadAsync("Música en espera", service::listMoh, mohTable.getItems()::setAll);
    }

    private void refreshAnnouncements() {
        loadAsync("Anuncios", service::listAnnouncements, announcementsTable.getItems()::setAll);
    }

    private void refreshAll() {
        if (!service.isAvailable()) {
            return;
        }
        refreshTimeGroups();
        refreshTimeConditions();
        refreshQueues();
        refreshExtensions();
        refreshTrunks();
        refreshInbound();
        refreshOutbound();
        refreshIvrs();
        refreshMoh();
        refreshAnnouncements();
        cachedRecordings = List.of();
        PbxRows.TimeGroupRow selected = timeGroupsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            refreshTimeGroupDetails(selected.id());
        } else {
            timeGroupDetailsTable.getItems().clear();
        }
    }

    private void buildTables() {
        colInt(timeGroupsTable, "ID", 56, r -> r.id());
        colStr(timeGroupsTable, "Descripción", 220, true, r -> r.description());
        colInt(timeGroupsTable, "Franjas", 72, r -> r.slotCount());

        colInt(timeGroupDetailsTable, "ID", 56, r -> r.id());
        colInt(timeGroupDetailsTable, "Grupo", 64, r -> r.timeGroupId());
        colStr(timeGroupDetailsTable, "Regla horaria", 280, true, r -> r.timeRule());
        colStr(timeGroupDetailsTable, "Nombre", 160, false, r -> r.name());

        colInt(timeConditionsTable, "ID", 56, r -> r.id());
        colStr(timeConditionsTable, "Nombre", 160, false, r -> r.displayName());
        colStr(timeConditionsTable, "Grupo horario", 140, false, r -> r.timeGroupLabel());
        colStr(timeConditionsTable, "Si coincide", 200, true, r -> r.trueGoto());
        colStr(timeConditionsTable, "Si no coincide", 200, true, r -> r.falseGoto());
        colStr(timeConditionsTable, "Departamento", 120, false, r -> r.deptName());

        colStr(queuesTable, "Extensión", 90, false, r -> r.extension());
        colStr(queuesTable, "Descripción", 180, false, r -> r.description());
        colStr(queuesTable, "Destino fallo", 220, true, r -> r.destination());
        colStr(queuesTable, "Espera máx.", 90, false, r -> r.maxWait());
        colStr(queuesTable, "Callback", 80, false, r -> r.callbackId());

        colStr(extensionsTable, "Extensión", 90, false, r -> r.extension());
        colStr(extensionsTable, "Nombre", 180, false, r -> r.name());
        colStr(extensionsTable, "Tecnología", 90, false, r -> r.tech());
        colStr(extensionsTable, "Marcado", 120, false, r -> r.dial());
        colStr(extensionsTable, "Buzón", 90, false, r -> r.voicemail());

        colInt(trunksTable, "ID", 56, r -> r.trunkId());
        colStr(trunksTable, "Nombre", 140, false, r -> r.name());
        colStr(trunksTable, "Tech", 72, false, r -> r.tech());
        colStr(trunksTable, "Canal", 180, true, r -> r.channelId());
        colStr(trunksTable, "CID salida", 120, false, r -> r.outCid());
        colStr(trunksTable, "Deshabilitado", 90, false, r -> r.disabled());
        colStr(trunksTable, "Proveedor", 120, false, r -> r.provider());

        colStr(inboundTable, "DID / ext", 120, false, r -> r.did());
        colStr(inboundTable, "CID", 100, false, r -> r.cidNum());
        colStr(inboundTable, "Destino", 220, true, r -> r.destination());
        colStr(inboundTable, "Descripción", 180, false, r -> r.description());
        colStr(inboundTable, "MOH", 100, false, r -> r.mohClass());

        colInt(outboundTable, "ID", 56, r -> r.routeId());
        colStr(outboundTable, "Nombre", 140, false, r -> r.name());
        colStr(outboundTable, "CID", 100, false, r -> r.outCid());
        colStr(outboundTable, "Emergencia", 80, false, r -> r.emergencyRoute());
        colStr(outboundTable, "Grupo horario", 120, false, r -> r.timeGroupLabel());
        colStr(outboundTable, "Patrones", 200, true, r -> r.patterns());
        colStr(outboundTable, "Troncales", 200, true, r -> r.trunks());

        colInt(ivrTable, "ID", 56, r -> r.id());
        colStr(ivrTable, "Nombre", 140, false, r -> r.name());
        colStr(ivrTable, "Descripción", 160, false, r -> r.description());
        colInt(ivrTable, "Grabación", 80, r -> r.announcement());
        colInt(ivrTable, "Timeout", 72, r -> r.timeoutTime());
        colStr(ivrTable, "Dest. timeout", 180, true, r -> r.timeoutDestination());
        colStr(ivrTable, "Dest. inválido", 180, true, r -> r.invalidDestination());
        colInt(ivrTable, "Opciones", 72, r -> r.entryCount());

        colStr(ivrEntriesTable, "Selección", 90, false, r -> r.selection());
        colStr(ivrEntriesTable, "Destino", 280, true, r -> r.dest());
        colInt(ivrEntriesTable, "Volver IVR", 80, r -> r.ivrRet());

        colStr(mohTable, "Clase", 140, false, r -> r.category());
        colStr(mohTable, "Tipo", 80, false, r -> r.type());
        colInt(mohTable, "Aleatorio", 72, r -> r.random());
        colStr(mohTable, "Aplicación", 120, false, r -> r.application());
        colStr(mohTable, "Formato", 72, false, r -> r.format());

        colInt(announcementsTable, "ID", 56, r -> r.id());
        colStr(announcementsTable, "Descripción", 180, false, r -> r.description());
        colInt(announcementsTable, "Grabación ID", 90, r -> r.recordingId());
        colStr(announcementsTable, "Grabación", 140, false, r -> r.recordingLabel());
        colStr(announcementsTable, "Destino posterior", 200, true, r -> r.postDest());
        colStr(announcementsTable, "Repetir", 80, false, r -> r.repeatMsg());
        colInt(announcementsTable, "Volver IVR", 80, r -> r.returnIvr());
    }

    private <T> void loadAsync(String label, Loader<T> loader, java.util.function.Consumer<List<T>> onSuccess) {
        int gen = ++loadGeneration;
        Platform.runLater(() -> status.setText("Cargando " + label + "…"));
        worker.execute(() -> {
            try {
                List<T> rows = loader.load();
                Platform.runLater(() -> {
                    if (gen != loadGeneration) {
                        return;
                    }
                    onSuccess.accept(rows);
                    status.setText(label + ": " + rows.size() + " filas (BD " + service.pbxDatabaseLabel() + ").");
                });
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                Platform.runLater(() -> {
                    if (gen != loadGeneration) {
                        return;
                    }
                    status.setText("Error en " + label + ": " + msg);
                });
            }
        });
    }

    @FunctionalInterface
    private interface Loader<T> {
        List<T> load() throws Exception;
    }

    private static <T> void colStr(TableView<T> table, String title, double width, boolean grow,
                                   java.util.function.Function<T, String> fn) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(p -> new SimpleStringProperty(fn.apply(p.getValue())));
        TableViewUtil.styleColumn(c, width, grow);
        table.getColumns().add(c);
    }

    private static <T> void colInt(TableView<T> table, String title, double width,
                                   java.util.function.ToIntFunction<T> fn) {
        TableColumn<T, Number> c = new TableColumn<>(title);
        c.setCellValueFactory(p -> new SimpleIntegerProperty(fn.applyAsInt(p.getValue())));
        TableViewUtil.styleColumn(c, width);
        table.getColumns().add(c);
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
