package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Summarizes whether a target-college batch can be finalized atomically. */
public record MajorTransferBatchReadinessView(String batchId, int assessed, int rejected,
        int cancelled, int unresolved, boolean ready, String reason, long batchVersion)
        implements Serializable {
    /** Validates readiness counts and identifier. */
    public MajorTransferBatchReadinessView {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (assessed < 0 || rejected < 0 || cancelled < 0 || unresolved < 0 || batchVersion < 0)
            throw new IllegalArgumentException("negative readiness value");
    }
}
