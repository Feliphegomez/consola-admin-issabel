package dn.demedallo.admin.update;

import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.ApplicationBuildInfo;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.stage.Stage;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Remote update check against {@link UpdateChecker#DEFAULT_LATEST_JSON_URL}.
 */
public final class UpdatePrompt {

    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();

    private UpdatePrompt() {
    }

    /**
     * Runs once after the app window is shown: logs result; shows a dialog only when a newer version exists.
     */
    public static void checkOnStartup(Stage owner, HostServices hostServices) {
        runCheck(owner, hostServices, true);
    }

    /** Manual check from About dialog: always informs the user of the outcome. */
    public static void checkManual(Stage owner, HostServices hostServices) {
        runCheck(owner, hostServices, false);
    }

    private static void runCheck(Stage owner, HostServices hostServices, boolean startupSilent) {
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            if (!startupSilent) {
                alert(owner, Alert.AlertType.INFORMATION, "Ya hay una comprobacion de actualizacion en curso.");
            }
            return;
        }
        String url = UpdateChecker.DEFAULT_LATEST_JSON_URL;
        String current = ApplicationBuildInfo.version();
        CompletableFuture.runAsync(() -> {
            try {
                LatestReleaseInfo info = UpdateChecker.fetchLatest(url);
                Platform.runLater(() -> {
                    IN_FLIGHT.set(false);
                    presentResult(owner, hostServices, current, info, startupSilent);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    IN_FLIGHT.set(false);
                    AppLogFile.appendLine("[update] ERR fetch latest.json | EN: " + ex.getMessage());
                    if (!startupSilent) {
                        alert(owner, Alert.AlertType.ERROR,
                                "No se pudo comprobar actualizaciones: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private static void presentResult(Stage owner, HostServices hostServices, String current,
            LatestReleaseInfo info, boolean startupSilent) {
        if (info.version().isEmpty()) {
            if (!startupSilent) {
                alert(owner, Alert.AlertType.WARNING, "El manifiesto remoto no incluye \"version\".");
            }
            return;
        }
        int cmp = UpdateChecker.compareVersions(current, info.version());
        if (cmp == 0) {
            AppLogFile.appendLine("[update] version actual | EN: local=" + current + " remote=" + info.version());
            if (!startupSilent) {
                alert(owner, Alert.AlertType.INFORMATION,
                        "Su version (" + current + ") coincide con la ultima publicada (" + info.version() + ").");
            }
            return;
        }
        if (cmp > 0) {
            AppLogFile.appendLine("[update] build mas reciente que servidor | EN: local=" + current
                    + " remote=" + info.version());
            if (!startupSilent) {
                alert(owner, Alert.AlertType.INFORMATION,
                        "Su version (" + current + ") es mas reciente que la publicada (" + info.version() + ").");
            }
            return;
        }
        AppLogFile.appendLine("[update] actualizacion disponible | EN: local=" + current + " remote=" + info.version());
        showUpdateAvailableDialog(owner, hostServices, current, info);
    }

    private static void showUpdateAvailableDialog(Stage owner, HostServices hostServices,
            String current, LatestReleaseInfo info) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            a.initOwner(owner);
        }
        a.setTitle("Actualizacion disponible");
        a.setHeaderText("Hay una version mas reciente: " + info.version() + " (usted tiene " + current + ").");
        TextArea notes = new TextArea(info.notes().isEmpty() ? "(Sin notas de version.)" : info.notes());
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefRowCount(8);
        notes.setMaxWidth(Double.MAX_VALUE);
        notes.getStyleClass().add("app-dialog-notes");
        a.getDialogPane().setContent(notes);
        a.getDialogPane().setMinWidth(480);
        ButtonType btnExe = new ButtonType("Descargar .exe", ButtonData.OK_DONE);
        boolean hasZip = info.zipUrl() != null && !info.zipUrl().isBlank();
        ButtonType btnZip = null;
        if (hasZip) {
            btnZip = new ButtonType("Descargar .zip", ButtonData.APPLY);
            a.getButtonTypes().setAll(btnExe, btnZip, ButtonType.CANCEL);
        } else {
            a.getButtonTypes().setAll(btnExe, ButtonType.CANCEL);
        }
        ButtonType finalBtnZip = btnZip;
        a.showAndWait().ifPresent(bt -> {
            if (bt == btnExe) {
                if (info.hasDownloadUrl()) {
                    openUrl(hostServices, info.downloadUrl());
                } else {
                    alert(owner, Alert.AlertType.WARNING, "El manifiesto no incluye download_url.");
                }
            } else if (finalBtnZip != null && bt == finalBtnZip) {
                openUrl(hostServices, info.zipUrl());
            }
        });
    }

    private static void openUrl(HostServices hostServices, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String target = url.trim();
        try {
            if (hostServices != null) {
                hostServices.showDocument(target);
            } else {
                java.awt.Desktop.getDesktop().browse(URI.create(target));
            }
        } catch (Exception ex) {
            AppLogFile.appendLine("[update] ERR open URL | EN: " + ex.getMessage());
        }
    }

    private static void alert(Stage owner, Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        if (owner != null) {
            a.initOwner(owner);
        }
        a.showAndWait();
    }
}
