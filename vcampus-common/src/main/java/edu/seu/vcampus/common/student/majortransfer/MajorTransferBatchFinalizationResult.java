package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Reports the committed outcome of one atomic batch finalization. */
public record MajorTransferBatchFinalizationResult(String batchId, int effectiveStudents,
        int droppedEnrollments, MajorTransferBatchStatus status) implements Serializable {
    /** Validates committed result fields. */
    public MajorTransferBatchFinalizationResult {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (effectiveStudents < 0 || droppedEnrollments < 0)
            throw new IllegalArgumentException("negative finalization count");
        Objects.requireNonNull(status, "status");
    }
}
