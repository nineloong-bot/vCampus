package edu.seu.vcampus.server.course.domain;

/** Raised when normal enrollment or retake is outside its server-time window. */
public final class EnrollmentClosedException extends CourseRuleException {
    public static final String CODE = "COURSE_ENROLLMENT_NOT_OPEN";

    public EnrollmentClosedException() {
        super(CODE, CODE + ": enrollment window is not open");
    }
}
