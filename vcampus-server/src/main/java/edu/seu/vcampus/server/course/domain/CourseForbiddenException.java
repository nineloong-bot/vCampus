package edu.seu.vcampus.server.course.domain;

/** Raised when an authenticated identity lacks the student role required here. */
public final class CourseForbiddenException extends CourseRuleException {
    public static final String CODE = "COMMON_FORBIDDEN";

    public CourseForbiddenException() {
        super(CODE, CODE + ": student role is required");
    }
}
