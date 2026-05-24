package dn.demedallo.admin.service;

import dn.demedallo.admin.util.AdminLogSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Issabel/FreePBX MOH classes live under {@code /var/lib/asterisk/moh/}, not in MySQL {@code music}.
 */
public final class PbxMohService {

    private static final Pattern SAFE_CLASS = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$");
    private static final String MOH_ROOT = "/var/lib/asterisk/moh";

    private final RemoteLogTailService ssh = new RemoteLogTailService();
    private final AdminLogSettings logSettings;
    private final String host;

    public PbxMohService(String eccpHost) {
        this.logSettings = AdminLogSettings.load();
        this.host = eccpHost == null || eccpHost.isBlank() ? "127.0.0.1" : eccpHost.trim();
    }

    public boolean isSshReady() {
        return logSettings.isSshReady();
    }

    public void requireSsh(String action) {
        if (!isSshReady()) {
            throw new IllegalStateException(
                    "SSH no configurado para " + action
                            + ". Active SSH en «Logs Issabel» (usuario root).");
        }
    }

    public static void validateClassName(String category) {
        String name = category == null ? "" : category.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nombre de clase MOH es obligatorio.");
        }
        if (!SAFE_CLASS.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Nombre de clase MOH inválido (use letras, números, guión y guión bajo).");
        }
    }

    /**
     * Lists MOH class directory names on the Issabel server.
     */
    public List<String> listMohDirectoriesOnServer() throws Exception {
        requireSsh("listar clases MOH");
        String cmd = "ls -1 " + MOH_ROOT + " 2>/dev/null | head -200";
        String out = ssh.exec(host, logSettings.sshPort, logSettings.sshUser,
                logSettings.sshPassword, cmd);
        Set<String> names = new LinkedHashSet<>();
        if (out != null) {
            for (String line : out.split("\\R")) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("ls:")) {
                    names.add(t);
                }
            }
        }
        return new ArrayList<>(names);
    }

    public void createMohDirectory(String category) throws Exception {
        validateClassName(category);
        requireSsh("crear clase MOH");
        String safe = category.trim();
        String cmd = "mkdir -p '" + MOH_ROOT + "/" + safe.replace("'", "'\\''")
                + "' && chown asterisk:asterisk '" + MOH_ROOT + "/" + safe.replace("'", "'\\''") + "' 2>/dev/null || true";
        ssh.exec(host, logSettings.sshPort, logSettings.sshUser, logSettings.sshPassword, cmd);
    }

    public void deleteMohDirectory(String category) throws Exception {
        validateClassName(category);
        if ("default".equalsIgnoreCase(category.trim())) {
            throw new IllegalArgumentException("No se puede eliminar la clase MOH «default» del sistema.");
        }
        requireSsh("eliminar clase MOH");
        String safe = category.trim();
        String path = MOH_ROOT + "/" + safe.replace("'", "'\\''");
        String countCmd = "find '" + path + "' -maxdepth 1 -type f 2>/dev/null | wc -l";
        String countOut = ssh.exec(host, logSettings.sshPort, logSettings.sshUser,
                logSettings.sshPassword, countCmd).trim();
        int files = 0;
        try {
            files = Integer.parseInt(countOut.replaceAll("\\D", ""));
        } catch (NumberFormatException ignored) {
            // treat as non-empty
            files = 1;
        }
        if (files > 0) {
            throw new IllegalStateException(
                    "La carpeta MOH «" + safe + "» contiene archivos de audio. Elimínelos en Issabel GUI antes de borrar la clase.");
        }
        String cmd = "rmdir '" + path + "' 2>/dev/null || rm -rf '" + path + "'";
        ssh.exec(host, logSettings.sshPort, logSettings.sshUser, logSettings.sshPassword, cmd);
    }

    public static List<String> mergeClassNames(List<String> fromDb, List<String> fromServer) {
        Set<String> merged = new LinkedHashSet<>();
        if (fromDb != null) {
            for (String s : fromDb) {
                if (s != null && !s.isBlank()) {
                    merged.add(s.trim());
                }
            }
        }
        if (fromServer != null) {
            merged.addAll(fromServer);
        }
        if (!merged.contains("default")) {
            merged.add("default");
        }
        List<String> sorted = new ArrayList<>(merged);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }
}
