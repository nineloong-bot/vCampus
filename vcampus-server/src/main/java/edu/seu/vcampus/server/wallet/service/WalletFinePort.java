package edu.seu.vcampus.server.wallet.service;

import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.persistence.TransactionContext;
import java.sql.SQLException;

/** Trusted library-to-wallet boundary; callers own the transaction and WALLET user lock. */
public interface WalletFinePort {
    /** Debits an assessed loan fine once, or returns its original durable receipt. */
    WalletOperationResult pay(TransactionContext transaction, String loanId, String userId, long cents)
            throws SQLException;

    /** Reads a matching payment without creating an account; null means unpaid. */
    WalletOperationResult receipt(TransactionContext transaction, String loanId, String userId, long cents)
            throws SQLException;
}
