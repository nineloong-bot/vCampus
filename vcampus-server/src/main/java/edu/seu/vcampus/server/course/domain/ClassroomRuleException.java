package edu.seu.vcampus.server.course.domain;

/** Signals an unavailable, undersized, or already occupied classroom. */
public final class ClassroomRuleException extends CourseRuleException {
    /** Creates a stable classroom scheduling rejection. */
    public ClassroomRuleException(String code) { super(code, code); }
}
