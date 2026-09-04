package edu.seu.vcampus.server.course.domain;

/** Raised when an administrator tries to open a second selection phase. */
public final class SelectionPhaseAlreadyOpenException extends CourseRuleException {
    public static final String CODE = "COURSE_SELECTION_PHASE_ALREADY_OPEN";
    public SelectionPhaseAlreadyOpenException() { super(CODE, CODE + ": another phase is open"); }
}
