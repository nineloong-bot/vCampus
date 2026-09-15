package edu.seu.vcampus.server.wallet.service;

import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.wallet.repository.AccessWalletRepository;
import edu.seu.vcampus.server.wallet.repository.WalletJournal;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;

/** Posts library fines to the existing wallet journal without order escrow or independent commits. */
public final class WalletFineService implements WalletFinePort {
    private final Clock clock;
    private final AccessWalletRepository accounts = new AccessWalletRepository();
    private final WalletJournal journal = new WalletJournal();

    /** Creates the wallet-owned fine gateway using the application's clock. */
    public WalletFineService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public WalletOperationResult pay(TransactionContext transaction, String loanId, String userId,
            long cents) throws SQLException {
        if (transaction.connection().getAutoCommit()) throw new WalletException("WALLET_TRANSACTION_REQUIRED");
        var previous = receipt(transaction, loanId, userId, cents);
        if (previous != null) return previous;
        var connection = transaction.connection();
        long after = accounts.change(connection, userId, -cents);
        var result = journal.record(connection, "F:LIBRARY:" + loanId, "LIBRARY_FINE", userId,
                "LIBRARY", loanId, cents, after, clock.instant());
        journal.transfer(connection, result.operationId(), "USER", userId, "SYSTEM", "LIBRARY_FINE", cents);
        return result;
    }

    @Override public WalletOperationResult receipt(TransactionContext transaction, String loanId, String userId,
            long cents) throws SQLException {
        WalletRules.key(loanId, 36);
        WalletRules.key(userId, 36);
        WalletRules.cents(cents);
        return journal.replay(transaction.connection(), "F:LIBRARY:" + loanId, "LIBRARY_FINE",
                userId, "LIBRARY", loanId, cents);
    }
}
