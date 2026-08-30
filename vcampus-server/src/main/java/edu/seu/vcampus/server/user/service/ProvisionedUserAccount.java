package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

/** Non-sensitive account identity returned to the server-side admission coordinator. */
public record ProvisionedUserAccount(
        String userId,
        String loginId,
        UserRole role,
        AccountStatus status
) {
}
