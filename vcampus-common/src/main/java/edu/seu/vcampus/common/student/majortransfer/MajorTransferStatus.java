package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Lifecycle status of a major-transfer application. */
public enum MajorTransferStatus implements Serializable {
    /** Represents draft. */ DRAFT,
    /** Represents submitted. */ SUBMITTED,
    /** Represents source approved. */ SOURCE_APPROVED,
    /** Represents qualified. */ QUALIFIED,
    /** Represents assessed. */ ASSESSED,
    /** Represents pending effective. */ PENDING_EFFECTIVE,
    /** Represents effective. */ EFFECTIVE,
    /** Represents rejected. */ REJECTED,
    /** Represents cancelled. */ CANCELLED,
    /** Represents execution failed. */ EXECUTION_FAILED
}
