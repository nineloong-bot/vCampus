package edu.seu.vcampus.server.course.domain;

/** Raised when a student already has the same course active in the target term. */
public final class DuplicateEnrollmentException extends CourseRuleException {
    public static final String CODE = "COURSE_DUPLICATE_ENROLLMENT";

    /**
     * Creates a duplicate enrollment exception with its required collaborators.
     */
    public DuplicateEnrollmentException() {
        super(CODE, CODE + ": course is already active in this term");
    }
}
