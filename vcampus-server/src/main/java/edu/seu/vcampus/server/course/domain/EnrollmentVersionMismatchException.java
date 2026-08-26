package edu.seu.vcampus.server.course.domain;

/** Raised when an adjustment command does not match the retained enrollment version. */
public final class EnrollmentVersionMismatchException extends CourseRuleException {
    public static final String CODE = "COURSE_ENROLLMENT_VERSION_CONFLICT";

    public EnrollmentVersionMismatchException() {
        super(CODE, CODE + ": enrollment has changed");
    }
}
