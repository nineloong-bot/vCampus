package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Readiness and allowed actions for one target college in a transfer batch. */
public record MajorTransferCollegeReadinessView(String batchId, String targetDepartmentId,
        MajorTransferCollegeStatus status, int assessed, int pendingEffective, int rejected,
        int cancelled, int unresolved, boolean canReview, boolean canRollback,
        boolean canEffect, String reason, long collegeVersion) implements Serializable {
    /** Validates identifiers, status, counts, and optimistic version. */
    public MajorTransferCollegeReadinessView {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (targetDepartmentId == null || targetDepartmentId.isBlank())
            throw new IllegalArgumentException("targetDepartmentId");
        Objects.requireNonNull(status, "status");
        if (assessed < 0 || pendingEffective < 0 || rejected < 0 || cancelled < 0
                || unresolved < 0 || collegeVersion < 0) throw new IllegalArgumentException("negative value");
    }
}
