package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Result of rolling back one target-major option. */
public record MajorTransferOptionRollbackResult(
        String optionId, String batchId, String targetMajorName, int restoredApplications,
        MajorTransferOptionFinalizationStatus status, long optionVersion) implements Serializable {
    /** Validates the committed rollback result. */
    public MajorTransferOptionRollbackResult {
        require(optionId, "optionId"); require(batchId, "batchId");
        require(targetMajorName, "targetMajorName"); Objects.requireNonNull(status, "status");
        if (restoredApplications < 0 || optionVersion < 0) throw new IllegalArgumentException("negative value");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}
