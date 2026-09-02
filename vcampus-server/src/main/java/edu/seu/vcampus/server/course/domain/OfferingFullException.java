package edu.seu.vcampus.server.course.domain;

/** Raised when an offering has no remaining capacity. */
public final class OfferingFullException extends CourseRuleException {
    public static final String CODE = "COURSE_OFFERING_FULL";

    public OfferingFullException() {
        super(CODE, CODE + ": offering has no remaining capacity");
    }
}
