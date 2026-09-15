package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Identifies an application and carries the optional approval or required rejection note. */
/**
 * Carries immutable review student profile command data.
 * @param applicationId the application identifier
 * @param reviewComment the review comment
 */
public record ReviewStudentProfileCommand(String applicationId, String reviewComment)
        implements Serializable { }
