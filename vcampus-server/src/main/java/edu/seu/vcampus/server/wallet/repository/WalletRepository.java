package edu.seu.vcampus.server.wallet.repository;

import edu.seu.vcampus.common.wallet.WalletBalance;
import java.sql.Connection;
import java.sql.SQLException;

/** Wallet account persistence operating only on caller-owned connections. */
public interface WalletRepository {
    /** Reads balance and pending income without inserting an account. */
    WalletBalance balance(Connection c, String userId) throws SQLException;
    /** Changes spendable balance with optimistic concurrency and nonnegative checks. */
    long change(Connection c, String userId, long delta) throws SQLException;
}
