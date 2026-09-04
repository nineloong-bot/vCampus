package edu.seu.vcampus.server.course.domain;

/** A failed course must use the retake mutation rather than normal enrollment. */
public final class RetakeRequiredException extends CourseRuleException {
    public static final String CODE = "COURSE_RETAKE_REQUIRED";
    public RetakeRequiredException() { super(CODE, "failed course requires retake enrollment"); }
}
