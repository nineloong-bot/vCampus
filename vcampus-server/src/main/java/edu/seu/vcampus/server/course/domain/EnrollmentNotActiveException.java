package edu.seu.vcampus.server.course.domain;

/** Raised when an adjustment refers to a non-active enrollment. */
public final class EnrollmentNotActiveException extends CourseRuleException {
    public static final String CODE = "COURSE_ENROLLMENT_NOT_ACTIVE";

    /**
     * Creates a enrollment not active exception with its required collaborators.
     */
    public EnrollmentNotActiveException() {
        super(CODE, CODE + ": enrollment is not active");
    }
}
