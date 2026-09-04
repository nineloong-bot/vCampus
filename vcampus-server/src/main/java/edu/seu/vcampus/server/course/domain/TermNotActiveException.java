package edu.seu.vcampus.server.course.domain;

/** Raised when a phase is opened for a term that is not active. */
public final class TermNotActiveException extends CourseRuleException {
    public static final String CODE = "COURSE_TERM_NOT_ACTIVE";
    public TermNotActiveException() { super(CODE, CODE + ": term is not active"); }
}
