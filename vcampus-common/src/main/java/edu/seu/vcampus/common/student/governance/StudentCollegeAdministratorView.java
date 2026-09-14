package edu.seu.vcampus.common.student.governance;

import edu.seu.vcampus.common.user.AccountStatus;

import java.io.Serializable;

/** Safe account and active-assignment projection for one college administrator. */
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
