package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Result of preparing and approving one target college's transfer applications. */
public record MajorTransferBatchReviewResult(String batchId, String targetDepartmentId,
        int preparedApplications, MajorTransferCollegeStatus status, long collegeVersion)
        implements Serializable {
    /** Validates committed review result values. */
    public MajorTransferBatchReviewResult {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (targetDepartmentId == null || targetDepartmentId.isBlank()) throw new IllegalArgumentException("targetDepartmentId");
        if (preparedApplications < 0 || collegeVersion < 0) throw new IllegalArgumentException("negative value");
        Objects.requireNonNull(status, "status");
    }
}
