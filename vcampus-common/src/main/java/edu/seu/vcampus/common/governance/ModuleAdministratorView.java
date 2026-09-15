package edu.seu.vcampus.common.governance;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

import java.io.Serializable;

/** Safe projection of one dedicated module-administrator account. */
/**
 * Carries immutable module administrator view data.
 * @param moduleCode the module code
 * @param moduleName the module name
 * @param administratorRole the administrator role
 * @param userId the user identifier
 * @param loginId the login identifier
 * @param accountStatus the account status
 * @param rowVersion the row version
 */
public record ModuleAdministratorView(
        String moduleCode,
        String moduleName,
        UserRole administratorRole,
        String userId,
        String loginId,
        AccountStatus accountStatus,
        long rowVersion
) implements Serializable { }
