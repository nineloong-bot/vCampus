package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Writes post-rollback failures in best-effort independent short transactions. */
final class UserAuditWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAuditWriter.class);
    private final TransactionManager transactions;
    private final AuditRepository audits;

    UserAuditWriter(TransactionManager transactions, AuditRepository audits) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.audits = Objects.requireNonNull(audits, "audits");
    }

    void failure(String actorUserId, String actionCode, String targetId,
                 RuntimeException error, String clientAddress) {
        String resultCode = UserAuditResultCodes.from(error);
        try {
            transactions.inTransaction(connection -> {
                audits.record(connection, actorUserId, actionCode, "USER", targetId,
                        resultCode, clientAddress);
                return null;
            });
        } catch (RuntimeException auditFailure) {
            // Never replace the already-established business failure. Deliberately
            // omit exception messages and request metadata because they may be sensitive.
            LOGGER.warn("A user-operation failure audit could not be persisted");
        }
    }
}
