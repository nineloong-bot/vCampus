package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Locks the current draft and submits it for administrator review. */
/**
 * Carries immutable submit student profile command data.
 * @param expectedApplicationVersion the expected application version
 */
public record SubmitStudentProfileCommand(long expectedApplicationVersion)
        implements Serializable { }
