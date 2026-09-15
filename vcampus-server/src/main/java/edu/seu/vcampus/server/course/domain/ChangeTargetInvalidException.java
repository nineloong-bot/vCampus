package edu.seu.vcampus.server.course.domain;

/** Raised when a requested change target cannot replace the source offering. */
public final class ChangeTargetInvalidException extends CourseRuleException {
    public static final String CODE = "COURSE_CHANGE_TARGET_INVALID";

    /**
     * Creates a change target invalid exception with its required collaborators.
     */
    public ChangeTargetInvalidException() {
        super(CODE, CODE + ": change target is invalid");
    }
}
