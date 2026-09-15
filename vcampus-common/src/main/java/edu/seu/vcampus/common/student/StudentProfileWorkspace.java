package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Approved profile paired with the student's current or most recent application. */
/**
 * Carries immutable student profile workspace data.
 * @param formalProfile the formal profile
 * @param application the application
 */
public record StudentProfileWorkspace(StudentProfileData formalProfile,
                                      StudentProfileApplicationView application)
        implements Serializable { }
