package edu.seu.vcampus.server.course.domain;

/** Rejection raised when a teacher or classroom cannot accept a schedule. */
public final class SchedulingRuleException extends CourseRuleException {
    /** Creates a rejection with a safe protocol error code. */
    public SchedulingRuleException(String code) { super(code, code); }
}
