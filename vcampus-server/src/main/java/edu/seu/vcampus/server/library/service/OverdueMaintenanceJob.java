package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.server.library.repository.LoanRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;
import java.util.Objects;

/** Idempotently persists overdue status for active loans past their due time. */
public final class OverdueMaintenanceJob {
    private final LoanRepository loans;
    private final TransactionManager transactions;
    private final Clock clock;

    /**
     * Creates a overdue maintenance job with its required collaborators.
     * @param loans the loans
     * @param transactions the transactions
     * @param clock the clock
     */
    public OverdueMaintenanceJob(LoanRepository loans, TransactionManager transactions,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans, "loans");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Performs the run once operation.
     * @return the operation result
     */
    public int runOnce() {
        return transactions.inTransaction(connection ->
                loans.markOverdue(connection, clock.instant()));
    }
}
