package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Student request to return a pending application to its editable draft state. */
public record WithdrawStudentProfileCommand(long expectedApplicationVersion)
        implements Serializable { }
