package edu.seu.vcampus.server.course.domain;

/** Raised when a phase transition or edit is invalid for its current state. */
public final class SelectionPhaseInvalidStateException extends CourseRuleException {
    public static final String CODE = "COURSE_SELECTION_PHASE_INVALID_STATE";
    public SelectionPhaseInvalidStateException() { super(CODE, CODE + ": invalid phase state"); }
}
