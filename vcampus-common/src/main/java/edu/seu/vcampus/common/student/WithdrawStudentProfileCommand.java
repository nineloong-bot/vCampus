package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Student request to return a pending application to its editable draft state. */
/**
 * Carries immutable withdraw student profile command data.
 * @param expectedApplicationVersion the expected application version
 */
public record WithdrawStudentProfileCommand(long expectedApplicationVersion)
        implements Serializable { }
