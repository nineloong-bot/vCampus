package edu.seu.vcampus.server.student.security;

import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionManager;
import java.sql.SQLException;
import java.util.Objects;

/** Resolves a student's current college and enforces a live administrator binding. */
public final class StudentCollegeScopeAuthorizationService {
    private final TransactionManager transactions;

    /** Creates a scope checker backed by server-side database state. */
    public StudentCollegeScopeAuthorizationService(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /** Rejects unless the user actively administers the target student's current college. */
    public void requireStudentAccess(String administratorUserId, String studentId) {
        if (administratorUserId == null || studentId == null) forbidden();
        boolean allowed = transactions.inTransaction(connection -> {
            String sql = """
                    SELECT COUNT(*) FROM (((tblStudent s INNER JOIN tblClass c ON s.classId=c.classId)
                    INNER JOIN tblMajor m ON c.majorId=m.majorId)
                    INNER JOIN tblStudentCollegeAdministrator a ON m.departmentId=a.departmentId)
                    INNER JOIN tblUser u ON a.userId=u.userId
                    WHERE s.studentId=? AND a.userId=? AND a.isActive=TRUE
                    AND u.roleCode='COLLEGE_ADMIN' AND u.accountStatus='ACTIVE'
                    """;
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, studentId);
                statement.setString(2, administratorUserId);
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1) == 1;
                }
            } catch (SQLException error) {
                throw new PersistenceException("Student college scope lookup failed", error);
            }
        });
        if (!allowed) forbidden();
    }

    private static void forbidden() { throw new IllegalArgumentException("COMMON_FORBIDDEN"); }
}
