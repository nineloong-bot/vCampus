package edu.seu.vcampus.server.course.domain;

/** Raised when an outcome import is malformed or conflicts with an earlier source payload. */
public final class OutcomeImportInvalidException extends CourseRuleException {
    public static final String CODE = "COURSE_OUTCOME_IMPORT_INVALID";
    public OutcomeImportInvalidException() {
        super(CODE, CODE + ": outcome import is invalid");
    }
    public OutcomeImportInvalidException(Throwable cause) {
        this();
        initCause(cause);
    }
}
