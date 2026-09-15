package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Saves the personal section into the authenticated student's draft. */
/**
 * Carries immutable save student personal draft command data.
 * @param personal the personal
 * @param expectedApplicationVersion the expected application version
 */
public record SaveStudentPersonalDraftCommand(StudentPersonalProfile personal,
                                              long expectedApplicationVersion)
        implements Serializable { }
