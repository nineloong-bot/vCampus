package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests returning a batch's pending applications to assessed status. */
public record RollbackMajorTransferBatchCommand(String batchId, long expectedCollegeVersion)
        implements Serializable {
    /** Validates the batch identifier and optimistic version. */
    public RollbackMajorTransferBatchCommand {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (expectedCollegeVersion < 0) throw new IllegalArgumentException("expectedCollegeVersion");
    }
}
