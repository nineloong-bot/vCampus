package edu.seu.vcampus.server.student.security;

import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

/** Resolves a student's current college and enforces a live administrator binding. */
public final class StudentCollegeScopeAuthorizationService {
    private final TransactionManager transactions;

    /** Creates a scope checker backed by server-side database state. */
    public StudentCollegeScopeAuthorizationService(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /** Returns the single department actively administered by the supplied user. */
    public String requireActiveDepartment(String administratorUserId) {
        if (administratorUserId == null) forbidden();
        return transactions.inTransaction(connection -> {
            String sql = """
                    SELECT a.departmentId
                    FROM tblStudentCollegeAdministrator a
                    INNER JOIN tblUser u ON a.userId=u.userId
                    WHERE a.userId=? AND a.isActive=TRUE
                    AND u.roleCode='COLLEGE_ADMIN' AND u.accountStatus='ACTIVE'
                    """;
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, administratorUserId);
                try (var result = statement.executeQuery()) {
                    var departments = new ArrayList<String>();
                    while (result.next()) departments.add(result.getString(1));
                    if (departments.size() != 1) forbidden();
                    return departments.getFirst();
                }
            } catch (SQLException error) {
                throw new PersistenceException("Student college binding lookup failed", error);
            }
        });
    }

    /** Rejects unless the user actively administers the target student's current college. */
    public void requireStudentAccess(String administratorUserId, String studentId) {
        String departmentId = requireActiveDepartment(administratorUserId);
        transactions.inTransaction(connection -> {
            requireStudentAccess(connection, departmentId, studentId);
            return null;
        });
    }

    /** Rejects unless the student belongs to the trusted department in this transaction. */
    public void requireStudentAccess(Connection connection, String departmentId, String studentId) {
        if (connection == null || departmentId == null || studentId == null) forbidden();
        boolean allowed;
        String sql = """
                SELECT COUNT(*) FROM (tblStudent s INNER JOIN tblClass c ON s.classId=c.classId)
                INNER JOIN tblMajor m ON c.majorId=m.majorId
                WHERE s.studentId=? AND m.departmentId=?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, departmentId);
            try (var result = statement.executeQuery()) {
                result.next();
                allowed = result.getLong(1) == 1;
            }
        } catch (SQLException error) {
            throw new PersistenceException("Student college scope lookup failed", error);
        }
        if (!allowed) forbidden();
    }

    /** Rejects unless the major belongs to the trusted department. */
    public void requireMajorAccess(Connection connection, String departmentId, String majorId) {
        requireMatch(connection,
                "SELECT COUNT(*) FROM tblMajor WHERE majorId=? AND departmentId=?",
                majorId, departmentId);
    }

    /** Rejects unless the class belongs to a major in the trusted department. */
    public void requireClassAccess(Connection connection, String departmentId, String classId) {
        requireMatch(connection, """
                SELECT COUNT(*) FROM tblClass c INNER JOIN tblMajor m ON c.majorId=m.majorId
                WHERE c.classId=? AND m.departmentId=?
                """, classId, departmentId);
    }

    /** Rejects unless the training plan belongs to a major in the trusted department. */
    public void requirePlanAccess(Connection connection, String departmentId, String planId) {
        requireMatch(connection, """
                SELECT COUNT(*) FROM tblTrainingPlan p INNER JOIN tblMajor m ON p.majorId=m.majorId
                WHERE p.planId=? AND m.departmentId=?
                """, planId, departmentId);
    }

    private static void requireMatch(Connection connection, String sql, String entityId,
            String departmentId) {
        if (connection == null || entityId == null || departmentId == null) forbidden();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId);
            statement.setString(2, departmentId);
            try (var result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) != 1) forbidden();
            }
        } catch (SQLException error) {
            throw new PersistenceException("Student college entity scope lookup failed", error);
        }
    }

    private static void forbidden() { throw new IllegalArgumentException("COMMON_FORBIDDEN"); }
}
