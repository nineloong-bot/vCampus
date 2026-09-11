package edu.seu.vcampus.server.student.majortransfer.security;

import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Enforces college scope for major-transfer reads and review stages. */
public final class MajorTransferCollegeAuthorizationService {
    private final TransactionManager transactions;

    public MajorTransferCollegeAuthorizationService(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public void requireCanRead(String administratorUserId, String applicationId) {
        requireScope(administratorUserId, applicationId, Scope.EITHER);
    }

    public void requireCanReadAttachment(String administratorUserId, String attachmentId) {
        if (administratorUserId == null || attachmentId == null) forbidden();
        String applicationId = transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT applicationId FROM tblMajorTransferAttachment WHERE attachmentId=?")) {
                statement.setString(1, attachmentId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) forbidden();
                    return result.getString(1);
                }
            } catch (SQLException error) {
                throw new PersistenceException("Major-transfer attachment scope lookup failed", error);
            }
        });
        requireCanRead(administratorUserId, applicationId);
    }

    public void requireSourceApproval(String administratorUserId, String applicationId) {
        requireScope(administratorUserId, applicationId, Scope.SOURCE);
    }

    public void requireTargetApproval(String administratorUserId, String applicationId) {
        requireScope(administratorUserId, applicationId, Scope.TARGET);
    }

    /** Returns the administrator's one active college, or rejects invalid/inactive bindings. */
    public String findActiveDepartmentId(String administratorUserId) {
        return transactions.inTransaction(connection ->
                findActiveDepartmentId(connection, administratorUserId));
    }

    /** Returns the administrator's one active college within an existing transaction. */
    public String findActiveDepartmentId(Connection connection, String administratorUserId) {
        if (administratorUserId == null) forbidden();
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
                if (!result.next()) forbidden();
                String departmentId = result.getString(1);
                if (result.next()) forbidden();
                return departmentId;
            }
        } catch (SQLException error) {
            throw new PersistenceException("Major-transfer college binding lookup failed", error);
        }
    }

    private void requireScope(String administratorUserId, String applicationId, Scope scope) {
        if (administratorUserId == null || applicationId == null) forbidden();
        boolean allowed = transactions.inTransaction(connection -> {
            String departmentId = findActiveDepartmentId(connection, administratorUserId);
            Departments application = findApplicationDepartments(connection, applicationId);
            return switch (scope) {
                case SOURCE -> departmentId.equals(application.source());
                case TARGET -> departmentId.equals(application.target());
                case EITHER -> departmentId.equals(application.source())
                        || departmentId.equals(application.target());
            };
        });
        if (!allowed) forbidden();
    }

    private static Departments findApplicationDepartments(Connection connection, String applicationId) {
        String sql = """
                SELECT a.fromDepartmentId, o.targetDepartmentId
                FROM tblMajorTransferApplication a
                INNER JOIN tblMajorTransferOption o ON a.optionId=o.optionId
                WHERE a.applicationId=?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, applicationId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) forbidden();
                return new Departments(result.getString(1), result.getString(2));
            }
        } catch (SQLException error) {
            throw new PersistenceException("Major-transfer application scope lookup failed", error);
        }
    }

    private static void forbidden() {
        throw new IllegalArgumentException("COMMON_FORBIDDEN");
    }

    private enum Scope { SOURCE, TARGET, EITHER }

    private record Departments(String source, String target) { }
}
