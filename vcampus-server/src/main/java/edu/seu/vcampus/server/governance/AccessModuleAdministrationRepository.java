package edu.seu.vcampus.server.governance;

import edu.seu.vcampus.common.governance.ModuleAdministratorView;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/** Access persistence for the dedicated module-administrator role model. */
public final class AccessModuleAdministrationRepository
        implements ModuleAdministrationRepository {
    private static final String ROLES =
            "'STUDENT_ADMIN','COURSE_ADMIN','LIBRARY_ADMIN','SHOP_ADMIN','USER_ADMIN'";

    @Override
    public List<ModuleAdministratorView> list(Connection connection) {
        String sql = "SELECT userId,loginId,roleCode,accountStatus,rowVersion "
                + "FROM tblUser WHERE roleCode IN (" + ROLES + ") ORDER BY roleCode,loginId";
        // Keep the SQL assembled from a fixed server-side role list; no client data is interpolated.
        try (var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            List<ModuleAdministratorView> result = new ArrayList<>();
            while (rows.next()) {
                UserRole role = UserRole.valueOf(rows.getString("roleCode"));
                result.add(new ModuleAdministratorView(moduleCode(role), moduleName(role), role,
                        rows.getString("userId"), rows.getString("loginId"),
                        AccountStatus.valueOf(rows.getString("accountStatus")),
                        rows.getLong("rowVersion")));
            }
            return List.copyOf(result);
        } catch (SQLException error) {
            throw persistence(error);
        }
    }

    @Override
    public AdministratorAccount requireAccount(
            Connection connection, String userId, long expectedVersion) {
        try (var statement = connection.prepareStatement("""
                SELECT loginId,roleCode,accountStatus,rowVersion
                FROM tblUser WHERE userId=?
                """)) {
            statement.setString(1, userId);
            try (var row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
                long actualVersion = row.getLong("rowVersion");
                if (actualVersion != expectedVersion) {
                    throw new ConcurrentModificationException(
                            "COMMON_CONCURRENT_MODIFICATION");
                }
                return new AdministratorAccount(userId, row.getString("loginId"),
                        UserRole.valueOf(row.getString("roleCode")),
                        AccountStatus.valueOf(row.getString("accountStatus")), actualVersion);
            }
        } catch (SQLException error) {
            throw persistence(error);
        }
    }

    @Override
    public long countActive(Connection connection, UserRole role) {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM tblUser WHERE roleCode=? AND accountStatus='ACTIVE'
                """)) {
            statement.setString(1, role.name());
            try (var row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        } catch (SQLException error) {
            throw persistence(error);
        }
    }

    @Override
    public void updateRoleAndStatus(Connection connection, String userId, UserRole role,
                                    AccountStatus status, long expectedVersion) {
        try (var statement = connection.prepareStatement("""
                UPDATE tblUser SET roleCode=?,accountStatus=?,rowVersion=rowVersion+1,
                updatedAt=NOW() WHERE userId=? AND rowVersion=?
                """)) {
            statement.setString(1, role.name());
            statement.setString(2, status.name());
            statement.setString(3, userId);
            statement.setLong(4, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException("COMMON_CONCURRENT_MODIFICATION");
            }
        } catch (SQLException error) {
            throw persistence(error);
        }
    }

    private static String moduleCode(UserRole role) {
        return role.name().substring(0, role.name().length() - "_ADMIN".length());
    }

    private static String moduleName(UserRole role) {
        return switch (role) {
            case STUDENT_ADMIN -> "学籍管理";
            case COURSE_ADMIN -> "课程管理";
            case LIBRARY_ADMIN -> "图书管理";
            case SHOP_ADMIN -> "商城管理";
            case USER_ADMIN -> "用户管理";
            default -> throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        };
    }

    private static PersistenceException persistence(SQLException error) {
        return new PersistenceException("Module administration persistence failed", error);
    }
}
