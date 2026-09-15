package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Status of a cross-disciplinary course application. */
public enum CrossCourseApplicationStatus implements Serializable {
    /** Represents pending. */ PENDING,
    /** Represents approved. */ APPROVED,
    /** Represents rejected. */ REJECTED
}
