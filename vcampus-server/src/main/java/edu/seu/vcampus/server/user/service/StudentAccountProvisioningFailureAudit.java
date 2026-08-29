package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.repository.AuditRepository;

import java.util.Objects;

/** Records a failed student-account provisioning attempt after caller rollback. */
public final class StudentAccountProvisioningFailureAudit {
    private final TransactionManager transactions;
    private final AuditRepository audits;

    /** Creates the post-rollback audit entry point. */
    public StudentAccountProvisioningFailureAudit(
            TransactionManager transactions, AuditRepository audits) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.audits = Objects.requireNonNull(audits, "audits");
    }

    /**
     * Records a stable failure code in an independent short transaction.
     * The student admission coordinator must call this only after its outer
     * provisioning transaction has ended and rollback is complete; this method
     * must never be called from inside that caller-owned transaction.
     */
    public void recordAfterRollback(String actorUserId, String targetUserId,
                                    String resultCode, String clientAddress) {
        requireFailureCode(resultCode);
        transactions.inTransaction(connection -> {
            audits.record(connection, actorUserId, "STUDENT_ACCOUNT_PROVISIONED",
                    "USER", targetUserId, resultCode, clientAddress);
            return null;
        });
    }

    private static void requireFailureCode(String resultCode) {
        if (resultCode == null || resultCode.isBlank() || "SUCCESS".equals(resultCode)
                || !resultCode.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new IllegalArgumentException("A stable failure code is required");
        }
    }
}
