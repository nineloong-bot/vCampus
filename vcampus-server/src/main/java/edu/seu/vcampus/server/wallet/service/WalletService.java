package edu.seu.vcampus.server.wallet.service;

import edu.seu.vcampus.common.wallet.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.wallet.repository.AccessWalletRepository;
import edu.seu.vcampus.server.wallet.repository.WalletJournal;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Independent wallet queries and virtual recharge, with durable receipt replay. */
public final class WalletService implements WalletQueryPort {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;
    private final AccessWalletRepository accounts = new AccessWalletRepository();
    private final WalletJournal journal = new WalletJournal();
    /** Uses the shared transaction boundary and wallet-specific resource locks. */
    public WalletService(TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.clock = Objects.requireNonNull(clock);
    }
    @Override public WalletBalance getBalance(String userId) {
        WalletRules.key(userId, 36);
        return transactions.inTransaction(c -> accounts.balance(c, userId));
    }
    /** Credits only a server-authorized user; retries return the original receipt. */
    public WalletOperationResult recharge(String userId, String requestId, BigDecimal amount) {
        WalletRules.key(userId, 36); WalletRules.key(requestId, 100);
        long cents = WalletRules.rechargeCents(amount);
        String key = "R:" + userId.length() + ":" + userId + ":" + requestId;
        return locks.withLocks(List.of(new ResourceKey("WALLET", userId)), () ->
            transactions.inTransaction(c -> {
                var previous = journal.replay(c, key, "RECHARGE", userId, "SYSTEM", "-", cents);
                if (previous != null) return previous;
                long after = accounts.change(c, userId, cents);
                var receipt = journal.record(c, key, "RECHARGE", userId, "SYSTEM", "-", cents, after, clock.instant());
                journal.transfer(c, receipt.operationId(), "SYSTEM", "VIRTUAL_RECHARGE", "USER", userId, cents);
                return receipt;
            }));
    }
    /** Returns a bounded page of the authorized user's own ledger. */
    public PageResult<WalletEntryView> history(String userId, WalletHistoryQuery query) {
        WalletRules.key(userId, 36);
        if (query == null || query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new WalletException("WALLET_INVALID_REQUEST");
        return transactions.inTransaction(c -> journal.history(c, userId, query.page(), query.pageSize()));
    }
}
