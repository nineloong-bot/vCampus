package edu.seu.vcampus.server.wallet.service;

import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.persistence.TransactionContext;
import java.sql.SQLException;

/** Same-transaction gateway for trusted order services; never commits independently. */
public interface WalletPostingPort {
    /** Applies or replays one posting on the caller's non-autocommit connection. */
    WalletOperationResult post(TransactionContext transaction, WalletPosting posting) throws SQLException;
}
