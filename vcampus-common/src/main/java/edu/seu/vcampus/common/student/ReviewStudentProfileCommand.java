package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Identifies an application and carries the optional approval or required rejection note. */
public record ReviewStudentProfileCommand(String applicationId, String reviewComment)
        implements Serializable { }
