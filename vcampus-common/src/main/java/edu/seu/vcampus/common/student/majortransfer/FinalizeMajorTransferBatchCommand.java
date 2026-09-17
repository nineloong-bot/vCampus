package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests final approval of one closed transfer batch without changing enrollment. */
public record FinalizeMajorTransferBatchCommand(String batchId, long expectedCollegeVersion)
        implements Serializable {
    /** Validates the stable batch identifier and optimistic version. */
    public FinalizeMajorTransferBatchCommand {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (expectedCollegeVersion < 0) throw new IllegalArgumentException("expectedCollegeVersion");
    }
}
