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

    private static void forbidden() { throw new IllegalArgumentException("COMMON_FORBIDDEN"); }
}
