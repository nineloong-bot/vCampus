package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Ordered review stages in the major-transfer workflow. */
public enum MajorTransferReviewStage implements Serializable {
    /** Represents source review. */ SOURCE_REVIEW,
    /** Represents qualification review. */ QUALIFICATION_REVIEW,
    /** Represents assessment. */ ASSESSMENT,
    /** Represents final approval. */ FINAL_APPROVAL,
    /** Represents execution. */ EXECUTION
}
