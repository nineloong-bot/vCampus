package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Lifecycle of one target college inside a school-wide transfer batch. */
public enum MajorTransferCollegeStatus implements Serializable {
    PROCESSING,
    REVIEWED,
    EFFECTIVE
}
