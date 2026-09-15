package edu.seu.vcampus.server.course.domain;

/** Raised when an outcome import is malformed or conflicts with an earlier source payload. */
public final class OutcomeImportInvalidException extends CourseRuleException {
    public static final String CODE = "COURSE_OUTCOME_IMPORT_INVALID";
    /**
     * Creates a outcome import invalid exception with its required collaborators.
     */
    public OutcomeImportInvalidException() {
        super(CODE, CODE + ": outcome import is invalid");
    }
    /**
     * Creates a outcome import invalid exception with its required collaborators.
     * @param cause the cause
     */
    public OutcomeImportInvalidException(Throwable cause) {
        this();
        initCause(cause);
    }
}
