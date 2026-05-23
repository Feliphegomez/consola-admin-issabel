package dn.demedallo.admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads last lines from a local log file (admin console or mounted path).
 */
public final class LocalLogTailService {

    public String tail(Path file, int lines) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("No existe: " + file.toAbsolutePath());
        }
        List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
        int n = Math.max(50, Math.min(lines, 5000));
        int from = Math.max(0, all.size() - n);
        return String.join("\n", all.subList(from, all.size()));
    }

    public static Path latestAdminLog(Path logDir) throws IOException {
        if (!Files.isDirectory(logDir)) {
            throw new IOException("Directorio no encontrado: " + logDir);
        }
        List<Path> logs = new ArrayList<>();
        try (var stream = Files.list(logDir)) {
            stream.filter(p -> Files.isRegularFile(p)
                            && p.getFileName().toString().startsWith("admin-")
                            && p.getFileName().toString().endsWith(".log"))
                    .forEach(logs::add);
        }
        if (logs.isEmpty()) {
            throw new IOException("Sin archivos admin-*.log en " + logDir);
        }
        logs.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });
        return logs.getFirst();
    }
}
