package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Result of final review for one target-major option. */
public record MajorTransferOptionReviewResult(
        String optionId, String batchId, String targetMajorName, int preparedApplications,
        MajorTransferOptionFinalizationStatus status, long optionVersion) implements Serializable {
    /** Validates the committed review result. */
    public MajorTransferOptionReviewResult {
        require(optionId, "optionId"); require(batchId, "batchId");
        require(targetMajorName, "targetMajorName"); Objects.requireNonNull(status, "status");
        if (preparedApplications < 0 || optionVersion < 0) throw new IllegalArgumentException("negative value");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}
