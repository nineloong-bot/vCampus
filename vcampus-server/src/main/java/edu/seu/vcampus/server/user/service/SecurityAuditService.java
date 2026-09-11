package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.repository.AuditRepository;

import java.util.Objects;

/** Executes sanitized, administrator-authorized security audit searches. */
public final class SecurityAuditService {
    private final TransactionManager transactions;
    private final AuditRepository audits;

    /** Creates the query service from the shared transaction and audit boundaries. */
    public SecurityAuditService(TransactionManager transactions, AuditRepository audits) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.audits = Objects.requireNonNull(audits, "audits");
    }

    /** Returns one validated page ordered newest-first by repository contract. */
    public PageResult<SecurityAuditView> search(SecurityAuditQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.page() < 0 || query.pageSize() < 1 || query.pageSize() > 100
                || query.fromInclusive() != null && query.toExclusive() != null
                && !query.fromInclusive().isBefore(query.toExclusive())) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return transactions.inTransaction(connection -> audits.search(connection, query));
    }
}
