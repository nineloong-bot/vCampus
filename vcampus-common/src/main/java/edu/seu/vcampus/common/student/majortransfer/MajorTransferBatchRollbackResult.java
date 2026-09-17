package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Result of reverting one target college's final review before effectuation. */
public record MajorTransferBatchRollbackResult(String batchId, String targetDepartmentId,
        int restoredApplications, MajorTransferCollegeStatus status, long collegeVersion)
        implements Serializable {
    /** Validates committed rollback result values. */
    public MajorTransferBatchRollbackResult {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (targetDepartmentId == null || targetDepartmentId.isBlank()) throw new IllegalArgumentException("targetDepartmentId");
        if (restoredApplications < 0 || collegeVersion < 0) throw new IllegalArgumentException("negative value");
        Objects.requireNonNull(status, "status");
    }
}
