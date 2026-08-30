package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.user.UserRole;

import java.sql.Connection;
import java.util.Set;

/** Reads role permissions inside an existing database transaction. */
public interface PermissionRepository {
    /** Returns all permission codes currently granted to the supplied role. */
    Set<String> findByRole(Connection connection, UserRole role);
}
