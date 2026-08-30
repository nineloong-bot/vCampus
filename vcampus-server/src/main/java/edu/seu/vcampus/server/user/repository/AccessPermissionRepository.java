package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Access JDBC implementation of database-backed role permission lookup. */
public final class AccessPermissionRepository implements PermissionRepository {
    /** Loads permission codes from tblRolePermission in deterministic order. */
    @Override
    public Set<String> findByRole(Connection connection, UserRole role) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(role, "role");
        String sql = "SELECT permissionCode FROM tblRolePermission "
                + "WHERE roleCode=? ORDER BY permissionCode";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            Set<String> result = new LinkedHashSet<>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
            return Set.copyOf(result);
        } catch (SQLException error) {
            throw new PersistenceException("Could not read role permissions", error);
        }
    }
}
