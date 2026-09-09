package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Lifecycle status of a major-transfer application. */
public enum MajorTransferStatus implements Serializable {
    DRAFT,
    SUBMITTED,
    SOURCE_APPROVED,
    QUALIFIED,
    ASSESSED,
    PROPOSED,
    PENDING_EFFECTIVE,
    EFFECTIVE,
    REJECTED,
    CANCELLED,
    EXECUTION_FAILED
}
