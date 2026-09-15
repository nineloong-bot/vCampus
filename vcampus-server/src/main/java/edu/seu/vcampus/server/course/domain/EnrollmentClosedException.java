package edu.seu.vcampus.server.course.domain;

/** Raised when normal enrollment or retake is outside its server-time window. */
public final class EnrollmentClosedException extends CourseRuleException {
    public static final String CODE = "COURSE_ENROLLMENT_NOT_OPEN";

    /**
     * Creates a enrollment closed exception with its required collaborators.
     */
    public EnrollmentClosedException() {
        super(CODE, CODE + ": enrollment window is not open");
    }
}
