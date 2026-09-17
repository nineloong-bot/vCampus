package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Lifecycle of final review and effectuation for one transfer option. */
public enum MajorTransferOptionFinalizationStatus implements Serializable {
    PROCESSING,
    REVIEWED,
    EFFECTIVE
}
