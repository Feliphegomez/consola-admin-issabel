package dn.demedallo.admin.service;

import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.SpyDialUtil;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Coordinates listen/monitor actions: AMI Originate to supervisor extension, or manual dial code.
 */
public final class ListenUiActions {

    private final AdminMonitorSettings settings;
    private final CallListenService listenService;

    public ListenUiActions(AdminMonitorSettings settings, CallListenService listenService) {
        this.settings = settings;
        this.listenService = listenService;
    }

    public void listenSpyTarget(String spyExtension, String agentChannel, String targetLabel,
                                Consumer<String> statusSink) {
        String spy = spyExtension == null ? "" : spyExtension.trim();
        if (spy.isBlank()) {
            showWarning("Sin objetivo de escucha", "No hay extensión del agente o canal para escuchar.");
            if (statusSink != null) {
                statusSink.accept("Escucha: sin extensión objetivo");
            }
            return;
        }
        String dial = SpyDialUtil.buildDialExtension(settings.spyPrefix, spy);
        String mode = settings.listenDelivery == null ? AdminMonitorSettings.LISTEN_PHONE_AMI
                : settings.listenDelivery;

        if (AdminMonitorSettings.LISTEN_MANUAL.equals(mode)) {
            showManualDial(dial, targetLabel);
            if (statusSink != null) {
                statusSink.accept("Marque manualmente: " + dial);
            }
            return;
        }
        tryAmiThenFallback(dial, spy, agentChannel, targetLabel, statusSink);
    }

    private void tryAmiThenFallback(String dial, String spy, String agentChannel, String targetLabel,
                                    Consumer<String> statusSink) {
        Runnable onFail = () -> Platform.runLater(() -> {
            Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
            choice.setTitle("Escuchar");
            choice.setHeaderText("No se pudo marcar por AMI");
            choice.setContentText("Marque manualmente desde su teléfono o softphone: " + dial);
            choice.getButtonTypes().setAll(new ButtonType("Copiar número"), ButtonType.CANCEL);
            Optional<ButtonType> bt = choice.showAndWait();
            if (bt.isPresent() && "Copiar número".equals(bt.get().getText())) {
                copyToClipboard(dial);
            }
        });

        new Thread(() -> {
            try {
                listenService.startListen(spy, targetLabel, agentChannel);
                Platform.runLater(() -> {
                    if (statusSink != null) {
                        statusSink.accept("Llamada de escucha enviada — conteste en su extensión (" + dial + ")");
                    }
                });
            } catch (ListenException ex) {
                AppLogFile.appendLine("[listen] " + ex.getReason() + ": " + ex.getMessage());
                Platform.runLater(() -> {
                    if (statusSink != null) {
                        statusSink.accept("Escucha: " + ex.getMessage());
                    }
                    if (ex.getReason() == ListenException.Reason.AMI_DISABLED
                            || ex.getReason() == ListenException.Reason.AMI_NOT_CONFIGURED
                            || ex.getReason() == ListenException.Reason.NO_SUPERVISOR_EXT) {
                        showManualDial(dial, targetLabel);
                    } else {
                        onFail.run();
                    }
                });
            } catch (Exception ex) {
                AppLogFile.appendLine("[listen] " + ex.getMessage());
                Platform.runLater(() -> {
                    if (statusSink != null) {
                        statusSink.accept("Escucha: " + ex.getMessage());
                    }
                    onFail.run();
                });
            }
        }, "listen-ami").start();
    }

    private void showManualDial(String dial, String targetLabel) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Escuchar manualmente");
        alert.setHeaderText(targetLabel);
        alert.setContentText("Marque desde su teléfono o softphone:\n\n" + dial);
        alert.getButtonTypes().setAll(new ButtonType("Copiar número"), ButtonType.OK);
        Optional<ButtonType> bt = alert.showAndWait();
        if (bt.isPresent() && "Copiar número".equals(bt.get().getText())) {
            copyToClipboard(dial);
        }
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void showWarning(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(msg);
        a.showAndWait();
    }
}
