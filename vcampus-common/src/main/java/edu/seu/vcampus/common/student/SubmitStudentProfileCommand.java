package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Locks the current draft and submits it for administrator review. */
public record SubmitStudentProfileCommand(long expectedApplicationVersion)
        implements Serializable { }
