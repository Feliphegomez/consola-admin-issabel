package dn.demedallo.admin.update;

import java.util.Locale;
import java.util.Objects;

/**
 * Payload from {@code latest.json} on the update server.
 */
public final class LatestReleaseInfo {

    private final String version;
    private final String downloadUrl;
    private final String zipUrl;
    private final String notes;
    private final String sha256Exe;
    private final String sha256Zip;

    public LatestReleaseInfo(String version, String downloadUrl, String zipUrl, String notes,
            String sha256Exe, String sha256Zip) {
        this.version = version == null ? "" : version.trim();
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl.trim();
        this.zipUrl = zipUrl == null ? "" : zipUrl.trim();
        this.notes = notes == null ? "" : notes.trim();
        this.sha256Exe = sha256Exe == null ? "" : sha256Exe.trim().toLowerCase(Locale.ROOT);
        this.sha256Zip = sha256Zip == null ? "" : sha256Zip.trim().toLowerCase(Locale.ROOT);
    }

    public String version() {
        return version;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public String zipUrl() {
        return zipUrl;
    }

    public String notes() {
        return notes;
    }

    public String sha256Exe() {
        return sha256Exe;
    }

    public String sha256Zip() {
        return sha256Zip;
    }

    public boolean hasDownloadUrl() {
        return !downloadUrl.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LatestReleaseInfo that)) {
            return false;
        }
        return Objects.equals(version, that.version)
                && Objects.equals(downloadUrl, that.downloadUrl)
                && Objects.equals(zipUrl, that.zipUrl)
                && Objects.equals(notes, that.notes)
                && Objects.equals(sha256Exe, that.sha256Exe)
                && Objects.equals(sha256Zip, that.sha256Zip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, downloadUrl, zipUrl, notes, sha256Exe, sha256Zip);
    }
}
