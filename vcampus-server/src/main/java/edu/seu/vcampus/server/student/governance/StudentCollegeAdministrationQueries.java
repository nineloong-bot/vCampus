package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.server.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Provides student college administration queries behavior. */
final class StudentCollegeAdministrationQueries {
    List<StudentCollegeAdministratorView> listAdministrators(Connection connection) {
        String sql = """
                SELECT u.userId,u.loginId,u.accountStatus,
                       a.departmentId,d.departmentCode,d.departmentName,a.rowVersion
                FROM (tblUser u LEFT JOIN tblStudentCollegeAdministrator a
                      ON u.userId=a.userId AND a.isActive=TRUE)
                LEFT JOIN tblDepartment d ON a.departmentId=d.departmentId
                WHERE u.roleCode='COLLEGE_ADMIN'
                ORDER BY u.loginId
                """;
        try (var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            List<StudentCollegeAdministratorView> result = new ArrayList<>();
            while (rows.next()) result.add(mapAdministrator(rows));
            return List.copyOf(result);
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    List<DepartmentView> listDepartments(Connection connection) {
        try (var statement = connection.prepareStatement("""
                SELECT departmentId,departmentCode,departmentName,isActive,rowVersion
                FROM tblDepartment ORDER BY departmentCode
                """); var rows = statement.executeQuery()) {
            List<DepartmentView> result = new ArrayList<>();
            while (rows.next()) {
                result.add(new DepartmentView(rows.getString("departmentId"),
                        rows.getString("departmentCode"), rows.getString("departmentName"),
                        rows.getBoolean("isActive"), rows.getLong("rowVersion")));
            }
            return List.copyOf(result);
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    private static StudentCollegeAdministratorView mapAdministrator(ResultSet row)
            throws SQLException {
        String departmentId = row.getString("departmentId");
        return new StudentCollegeAdministratorView(row.getString("userId"),
                row.getString("loginId"), AccountStatus.valueOf(row.getString("accountStatus")),
                departmentId, row.getString("departmentCode"), row.getString("departmentName"),
                departmentId != null, departmentId == null ? 0 : row.getLong("rowVersion"));
    }

    private static PersistenceException failure(SQLException error) {
        return new PersistenceException("College administration query failed", error);
    }
}
