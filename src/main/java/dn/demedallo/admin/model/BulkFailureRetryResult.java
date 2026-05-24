package dn.demedallo.admin.model;

import java.util.List;

/** Result of bulk re-queue operation. */
public final class BulkFailureRetryResult {

    public final int successCount;
    public final int failedCount;
    public final int skippedCount;
    public final int totalAttempted;
    public final List<String> errorSamples;

    public BulkFailureRetryResult(int successCount, int failedCount, int skippedCount,
            List<String> errorSamples) {
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.skippedCount = skippedCount;
        this.totalAttempted = successCount + failedCount;
        this.errorSamples = errorSamples == null ? List.of() : List.copyOf(errorSamples);
    }
}
