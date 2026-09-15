package edu.seu.vcampus.server.course.domain;

/** Raised when the authenticated user has no active student eligibility. */
public final class StudentIneligibleException extends CourseRuleException {
    public static final String CODE = "COURSE_STUDENT_INELIGIBLE";

    /**
     * Creates a student ineligible exception with its required collaborators.
     */
    public StudentIneligibleException() {
        super(CODE, CODE + ": student is not eligible for enrollment");
    }
}
