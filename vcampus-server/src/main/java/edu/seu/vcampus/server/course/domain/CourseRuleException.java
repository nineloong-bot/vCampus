package edu.seu.vcampus.server.course.domain;

/** Base exception for a course-domain rule rejection with a stable client error code. */
public class CourseRuleException extends RuntimeException {
    private final String code;

    protected CourseRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable error code suitable for an error response or adjustment audit. */
    public final String code() {
        return code;
    }

    /** Alias for integrations that name the property errorCode. */
    public final String errorCode() {
        return code;
    }
}
