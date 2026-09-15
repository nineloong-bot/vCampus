package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Course category within a training plan. */
public enum CourseType implements Serializable {
    /** Represents required. */ REQUIRED,
    /** Represents elective. */ ELECTIVE,
    /** Represents cross disciplinary. */ CROSS_DISCIPLINARY
}
