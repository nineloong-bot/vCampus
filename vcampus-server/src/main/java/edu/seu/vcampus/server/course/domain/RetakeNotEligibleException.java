package edu.seu.vcampus.server.course.domain;

/** Raised when no failed historical result permits the requested retake. */
public final class RetakeNotEligibleException extends CourseRuleException {
    public static final String CODE = "COURSE_RETAKE_NOT_ELIGIBLE";
    /**
     * Creates a retake not eligible exception with its required collaborators.
     */
    public RetakeNotEligibleException() {
        super(CODE, CODE + ": no failed course outcome permits this retake");
    }
}
