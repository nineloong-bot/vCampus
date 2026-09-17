package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Result of effectuating one target-major option. */
public record MajorTransferOptionEffectResult(
        String optionId, String batchId, String targetMajorName,
        int effectiveStudents, int droppedEnrollments,
        MajorTransferOptionFinalizationStatus status, long optionVersion) implements Serializable {
    /** Validates the committed effectuation result. */
    public MajorTransferOptionEffectResult {
        require(optionId, "optionId"); require(batchId, "batchId");
        require(targetMajorName, "targetMajorName"); Objects.requireNonNull(status, "status");
        if (effectiveStudents < 0 || droppedEnrollments < 0 || optionVersion < 0)
            throw new IllegalArgumentException("negative value");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}
