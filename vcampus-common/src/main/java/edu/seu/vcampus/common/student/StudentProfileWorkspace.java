package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Approved profile paired with the student's current or most recent application. */
public record StudentProfileWorkspace(StudentProfileData formalProfile,
                                      StudentProfileApplicationView application)
        implements Serializable { }
