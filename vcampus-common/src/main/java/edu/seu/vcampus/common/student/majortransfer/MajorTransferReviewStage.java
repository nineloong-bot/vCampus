package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Ordered review stages in the major-transfer workflow. */
public enum MajorTransferReviewStage implements Serializable {
    SOURCE_REVIEW,
    QUALIFICATION_REVIEW,
    ASSESSMENT,
    PROPOSAL,
    FINAL_APPROVAL,
    EXECUTION
}
