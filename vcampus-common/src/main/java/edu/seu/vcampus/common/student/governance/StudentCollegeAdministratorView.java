package edu.seu.vcampus.common.student.governance;

import edu.seu.vcampus.common.user.AccountStatus;

import java.io.Serializable;

/** Safe account and active-assignment projection for one college administrator. */
/**
 * Carries immutable student college administrator view data.
 * @param userId the user identifier
 * @param loginId the login identifier
 * @param accountStatus the account status
 * @param departmentId the department identifier
 * @param departmentCode the department code
 * @param departmentName the department name
 * @param assigned the assigned
 * @param assignmentVersion the assignment version
 */
public record StudentCollegeAdministratorView(
        String userId,
        String loginId,
        AccountStatus accountStatus,
        String departmentId,
        String departmentCode,
        String departmentName,
        boolean assigned,
        long assignmentVersion
) implements Serializable { }
