package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;
import edu.seu.vcampus.server.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.List;

/** Access persistence for college-administrator assignments. */
public final class AccessStudentCollegeAdministrationRepository
        implements StudentCollegeAdministrationRepository {
    private final StudentCollegeAdministrationQueries queries =
            new StudentCollegeAdministrationQueries();

    @Override
    public List<StudentCollegeAdministratorView> listAdministrators(Connection connection) {
        return queries.listAdministrators(connection);
    }

    @Override
    public List<DepartmentView> listDepartments(Connection connection) {
        return queries.listDepartments(connection);
    }

    @Override
    public void requireDepartment(Connection connection, String id, long version) {
        try (var statement = connection.prepareStatement(
                "SELECT isActive,rowVersion FROM tblDepartment WHERE departmentId=?")) {
            statement.setString(1, id);
            try (var row = statement.executeQuery()) {
                if (!row.next() || !row.getBoolean(1)) invalid();
                if (row.getLong(2) != version) concurrent();
            }
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public void requireAdministrator(Connection connection, String id) {
        try (var statement = connection.prepareStatement(
                "SELECT roleCode,accountStatus FROM tblUser WHERE userId=?")) {
            statement.setString(1, id);
            try (var row = statement.executeQuery()) {
                if (!row.next() || !"COLLEGE_ADMIN".equals(row.getString(1))
                        || !"ACTIVE".equals(row.getString(2))) invalid();
            }
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public void requireUnassigned(Connection connection, String id) {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM tblStudentCollegeAdministrator
                WHERE userId=? AND isActive=TRUE
                """)) {
            statement.setString(1, id);
            try (var row = statement.executeQuery()) {
                row.next();
                if (row.getLong(1) > 0) invalid();
            }
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public void requireAssignment(Connection connection, String department,
            String user, long version) {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM tblStudentCollegeAdministrator
                WHERE departmentId=? AND userId=? AND isActive=TRUE AND rowVersion=?
                """)) {
            statement.setString(1, department);
            statement.setString(2, user);
            statement.setLong(3, version);
            try (var row = statement.executeQuery()) {
                row.next();
                if (row.getLong(1) != 1) concurrent();
            }
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public long countActive(Connection connection, String department) {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM tblStudentCollegeAdministrator
                WHERE departmentId=? AND isActive=TRUE
                """)) {
            statement.setString(1, department);
            try (var row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public void assign(Connection connection, String department, String user) {
        try (var update = connection.prepareStatement("""
                UPDATE tblStudentCollegeAdministrator
                SET isActive=TRUE,rowVersion=rowVersion+1,updatedAt=NOW()
                WHERE departmentId=? AND userId=?
                """)) {
            update.setString(1, department);
            update.setString(2, user);
            if (update.executeUpdate() == 0) insert(connection, department, user);
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public void deactivate(Connection connection, String department,
            String user, long version) {
        try (var statement = connection.prepareStatement("""
                UPDATE tblStudentCollegeAdministrator
                SET isActive=FALSE,rowVersion=rowVersion+1,updatedAt=NOW()
                WHERE departmentId=? AND userId=? AND isActive=TRUE AND rowVersion=?
                """)) {
            statement.setString(1, department);
            statement.setString(2, user);
            statement.setLong(3, version);
            if (statement.executeUpdate() != 1) concurrent();
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    @Override
    public void transfer(Connection connection, String source, String target,
            String user, long version) {
        try (var statement = connection.prepareStatement("""
                UPDATE tblStudentCollegeAdministrator
                SET departmentId=?,rowVersion=rowVersion+1,updatedAt=NOW()
                WHERE departmentId=? AND userId=? AND isActive=TRUE AND rowVersion=?
                """)) {
            statement.setString(1, target);
            statement.setString(2, source);
            statement.setString(3, user);
            statement.setLong(4, version);
            if (statement.executeUpdate() != 1) concurrent();
        } catch (SQLException error) {
            throw failure(error);
        }
    }

    private static void insert(Connection connection, String department, String user)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO tblStudentCollegeAdministrator
                (departmentId,userId,isActive,rowVersion,createdAt,updatedAt)
                VALUES (?,?,TRUE,0,NOW(),NOW())
                """)) {
            statement.setString(1, department);
            statement.setString(2, user);
            statement.executeUpdate();
        }
    }

    private static void invalid() {
        throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
    }

    private static void concurrent() {
        throw new ConcurrentModificationException("COMMON_CONCURRENT_MODIFICATION");
    }

    private static PersistenceException failure(SQLException error) {
        return new PersistenceException("College administration persistence failed", error);
    }
}
