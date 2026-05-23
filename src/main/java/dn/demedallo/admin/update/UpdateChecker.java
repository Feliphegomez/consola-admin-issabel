package dn.demedallo.admin.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and parses {@code latest.json}; compares semantic-ish versions (numeric dot segments).
 */
public final class UpdateChecker {

    /** Public manifest (HTTPS). */
    public static final String DEFAULT_LATEST_JSON_URL =
            "https://updates.demedallo.com/consola-admin-issabel/latest.json";

    private static final Duration CONNECT = Duration.ofSeconds(12);
    private static final Duration REQUEST = Duration.ofSeconds(20);

    private static final Pattern STR = Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA_BLOCK = Pattern.compile(
            "\"sha256\"\\s*:\\s*\\{([^}]*)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SHA_PAIR = Pattern.compile(
            "\"(exe|zip)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.CASE_INSENSITIVE);

    private UpdateChecker() {
    }

    public static LatestReleaseInfo fetchLatest(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST)
                .header("Accept", "application/json")
                .header("User-Agent", "ConsolaAdminIssabel-UpdateCheck/1")
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return parseJson(resp.body());
    }

    static LatestReleaseInfo parseJson(String json) {
        if (json == null) {
            json = "";
        }
        String flat = json.replace("\r", "").replace("\n", " ");
        String version = "";
        String downloadUrl = "";
        String zipUrl = "";
        String notes = "";
        String shaExe = "";
        String shaZip = "";

        Matcher sm = STR.matcher(flat);
        while (sm.find()) {
            String key = sm.group(1).toLowerCase(Locale.ROOT);
            String val = unescapeJson(sm.group(2));
            switch (key) {
                case "version" -> version = val;
                case "download_url" -> downloadUrl = val;
                case "zip_url" -> zipUrl = val;
                case "notes" -> notes = val;
                case "sha256_exe" -> shaExe = val;
                case "sha256_zip" -> shaZip = val;
                default -> {
                }
            }
        }

        Matcher nest = SHA_BLOCK.matcher(json.replace("\r", ""));
        if (nest.find()) {
            Matcher inner = SHA_PAIR.matcher(nest.group(1));
            while (inner.find()) {
                String k = inner.group(1).toLowerCase(Locale.ROOT);
                String v = unescapeJson(inner.group(2));
                if ("exe".equals(k)) {
                    shaExe = v;
                } else if ("zip".equals(k)) {
                    shaZip = v;
                }
            }
        }

        return new LatestReleaseInfo(version, downloadUrl, zipUrl, notes, shaExe, shaZip);
    }

    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\', '"' -> sb.append(n);
                    default -> sb.append(n);
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * @return negative if {@code a} &lt; {@code b}, zero if equal, positive if {@code a} &gt; {@code b}
     */
    public static int compareVersions(String a, String b) {
        List<Integer> pa = parseVersionParts(a);
        List<Integer> pb = parseVersionParts(b);
        int n = Math.max(pa.size(), pb.size());
        for (int i = 0; i < n; i++) {
            int va = i < pa.size() ? pa.get(i) : 0;
            int vb = i < pb.size() ? pb.get(i) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersionParts(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int dash = s.indexOf('-');
        if (dash >= 0) {
            s = s.substring(0, dash);
        }
        List<Integer> out = new ArrayList<>();
        for (String part : s.split("\\.")) {
            String digits = part.replaceAll("[^0-9].*", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                out.add(Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
                out.add(0);
            }
        }
        if (out.isEmpty()) {
            out.add(0);
        }
        return out;
    }
}
