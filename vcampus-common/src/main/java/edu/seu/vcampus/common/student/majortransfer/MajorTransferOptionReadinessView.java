package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Readiness and lifecycle state for one target-major option. */
public record MajorTransferOptionReadinessView(
        String optionId, String batchId, String targetDepartmentId,
        String targetMajorId, String targetMajorName,
        MajorTransferOptionFinalizationStatus status,
        int assessed, int pendingEffective, int rejected, int cancelled, int unresolved,
        boolean canReview, boolean canRollback, boolean canEffect,
        String reason, long optionVersion) implements Serializable {
    /** Validates option identity, counters, status, and version. */
    public MajorTransferOptionReadinessView {
        requireText(optionId, "optionId");
        requireText(batchId, "batchId");
        requireText(targetDepartmentId, "targetDepartmentId");
        requireText(targetMajorId, "targetMajorId");
        requireText(targetMajorName, "targetMajorName");
        Objects.requireNonNull(status, "status");
        if (assessed < 0 || pendingEffective < 0 || rejected < 0 || cancelled < 0
                || unresolved < 0 || optionVersion < 0) throw new IllegalArgumentException("negative value");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}
