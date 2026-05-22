package dn.demedallo.admin.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Append console log lines to daily files ({@code admin-YYYY-MM-DD.log}).
 */
public final class AppLogFile {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Object LOCK = new Object();

    private static Path currentDayFile;
    private static PrintWriter writer;

    private AppLogFile() {
    }

    public static Path directory() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "demedallo-admin-console", "logs");
        }
        return Path.of(System.getProperty("user.home"), ".demedallo-admin-console", "logs");
    }

    public static void appendLine(String line) {
        if (line == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                ensureWriter();
                writer.println(LocalDateTime.now().format(TS) + " " + line);
                writer.flush();
            } catch (IOException ex) {
                System.err.println("AppLogFile: " + ex.getMessage());
            }
        }
    }

    private static void ensureWriter() throws IOException {
        Path dir = directory();
        Files.createDirectories(dir);
        Path dayFile = dir.resolve("admin-" + LocalDate.now() + ".log");
        if (writer != null && dayFile.equals(currentDayFile)) {
            return;
        }
        if (writer != null) {
            writer.close();
        }
        currentDayFile = dayFile;
        writer = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(dayFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8));
    }
}
