package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Lifecycle status of a major-transfer batch. */
public enum MajorTransferBatchStatus implements Serializable {
    DRAFT,
    OPEN,
    CLOSED
}
