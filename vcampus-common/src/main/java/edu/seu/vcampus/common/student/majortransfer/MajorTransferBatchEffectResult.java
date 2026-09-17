package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Result of atomically effectuating one target college's prepared transfers. */
public record MajorTransferBatchEffectResult(String batchId, String targetDepartmentId,
        int effectiveStudents, int droppedEnrollments, MajorTransferCollegeStatus status,
        long collegeVersion) implements Serializable {
    /** Validates committed effectuation result values. */
    public MajorTransferBatchEffectResult {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (targetDepartmentId == null || targetDepartmentId.isBlank()) throw new IllegalArgumentException("targetDepartmentId");
        if (effectiveStudents < 0 || droppedEnrollments < 0 || collegeVersion < 0)
            throw new IllegalArgumentException("negative value");
        Objects.requireNonNull(status, "status");
    }
}
