package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.security.UserIdentity;

import java.util.Optional;

/** Read-only internal account lookup contract for other server modules. */
public interface UserQueryPort {
    /** Finds an active account by its internal user identifier. */
    Optional<UserIdentity> findActiveUser(String userId);

    /** Finds an account identity by its internal user identifier. */
    Optional<UserIdentity> findByUserId(String userId);

    /** Finds an account identity by its normalized login identifier. */
    Optional<UserIdentity> findByLoginId(String loginId);

    /** Reports whether an account currently has the specified base role. */
    boolean hasRole(String userId, UserRole role);
}
