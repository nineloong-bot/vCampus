package edu.seu.vcampus.server.course.domain;

/** Raised when a drop, change, or late-add operation is outside its server-time window. */
public final class AdjustmentClosedException extends CourseRuleException {
    public static final String CODE = "COURSE_ADJUSTMENT_NOT_OPEN";

    public AdjustmentClosedException() {
        super(CODE, CODE + ": adjustment window is not open");
    }
}
