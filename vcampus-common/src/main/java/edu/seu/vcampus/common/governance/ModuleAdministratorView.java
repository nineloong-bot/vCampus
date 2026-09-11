package edu.seu.vcampus.common.governance;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

import java.io.Serializable;

/** Safe projection of one dedicated module-administrator account. */
public record ModuleAdministratorView(
        String moduleCode,
        String moduleName,
        UserRole administratorRole,
        String userId,
        String loginId,
        AccountStatus accountStatus,
        long rowVersion
) implements Serializable { }
