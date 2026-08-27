package edu.seu.vcampus.server.student.handler;

import java.util.Set;

/** Authenticated identity used by the student command boundary. */
public record StudentPrincipal(String userId, Set<String> roles, Set<String> permissions) {
    public StudentPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean hasPermission(String permission) { return permissions.contains(permission); }
}
