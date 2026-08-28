package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.UserRole;

import java.util.Set;

/** Maps the seeded base roles to their fixed user-module permissions. */
final class UserPermissions {
    private static final Set<String> ADMIN = Set.of("USER_READ_ALL", "USER_ROLE_WRITE",
            "USER_STATUS_WRITE", "USER_AUDIT_READ");

    private UserPermissions() { }

    static Set<String> forRole(UserRole role) {
        return role == UserRole.ADMIN ? ADMIN : Set.of();
    }
}
