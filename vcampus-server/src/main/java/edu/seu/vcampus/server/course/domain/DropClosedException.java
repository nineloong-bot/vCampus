package edu.seu.vcampus.server.course.domain;

/** Raised when a drop operation is outside both server-time mutation windows. */
public final class DropClosedException extends CourseRuleException {
    public static final String CODE = "COURSE_DROP_NOT_OPEN";

    public DropClosedException() {
        super(CODE, CODE + ": drop window is not open");
    }
}
