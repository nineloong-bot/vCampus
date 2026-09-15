package edu.seu.vcampus.server.course.domain;

/** Raised when an offering overlaps an active enrollment's schedule. */
public final class ScheduleConflictException extends CourseRuleException {
    public static final String CODE = "COURSE_SCHEDULE_CONFLICT";

    /**
     * Creates a schedule conflict exception with its required collaborators.
     */
    public ScheduleConflictException() {
        super(CODE, CODE + ": offering conflicts with the active schedule");
    }
}
