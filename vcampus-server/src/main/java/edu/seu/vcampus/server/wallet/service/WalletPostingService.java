package edu.seu.vcampus.server.wallet.service;

import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.wallet.repository.AccessWalletRepository;
import edu.seu.vcampus.server.wallet.repository.WalletEscrows;
import edu.seu.vcampus.server.wallet.repository.WalletJournal;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;

/** Trusted order-to-wallet gateway. Caller rolls back the whole transaction on any failure. */
public final class WalletPostingService implements WalletPostingPort {
    private final Clock clock;
    private final AccessWalletRepository accounts = new AccessWalletRepository();
    private final WalletEscrows escrows = new WalletEscrows();
    private final WalletJournal journal = new WalletJournal();
    /** Creates an internal posting gateway with an injectable clock. */
    public WalletPostingService(Clock clock) { this.clock = Objects.requireNonNull(clock); }
    @Override public WalletOperationResult post(TransactionContext transaction, WalletPosting p) throws SQLException {
        Objects.requireNonNull(transaction); Objects.requireNonNull(p);
        var c = transaction.connection();
        if (c.getAutoCommit()) throw new WalletException("WALLET_TRANSACTION_REQUIRED");
        String key = "I:" + p.businessKey();
        String actor = p.kind() == WalletPosting.Kind.SETTLE ? p.sellerId() : p.buyerId();
        String peer = p.kind() == WalletPosting.Kind.SETTLE ? p.buyerId() : p.sellerId();
        String type = switch (p.kind()) { case HOLD -> "PAYMENT"; case REFUND -> "REFUND"; case SETTLE -> "INCOME"; };
        var previous = journal.replay(c, key, type, actor, peer, p.orderKey(), p.amountCents());
        if (previous != null) return previous;
        long after;
        if (p.kind() == WalletPosting.Kind.HOLD) {
            escrows.hold(c, p);
            after = accounts.change(c, p.buyerId(), -p.amountCents());
        } else {
            escrows.finish(c, p);
            after = accounts.change(c, actor, p.amountCents());
        }
        var receipt = journal.record(c, key, type, actor, peer, p.orderKey(), p.amountCents(), after, clock.instant());
        if (p.kind() == WalletPosting.Kind.HOLD) {
            journal.transfer(c, receipt.operationId(), "USER", actor, "ESCROW", p.orderKey(), p.amountCents());
        } else {
            journal.transfer(c, receipt.operationId(), "ESCROW", p.orderKey(), "USER", actor, p.amountCents());
        }
        return receipt;
    }
}
