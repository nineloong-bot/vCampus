package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Saves the personal section into the authenticated student's draft. */
public record SaveStudentPersonalDraftCommand(StudentPersonalProfile personal,
                                              long expectedApplicationVersion)
        implements Serializable { }
