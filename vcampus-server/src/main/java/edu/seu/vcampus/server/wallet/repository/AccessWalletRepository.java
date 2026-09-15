package edu.seu.vcampus.server.wallet.repository;

import edu.seu.vcampus.common.wallet.WalletBalance;
import edu.seu.vcampus.server.wallet.service.WalletException;
import edu.seu.vcampus.server.wallet.service.WalletRules;
import java.sql.Connection;
import java.sql.SQLException;

/** Access account store with conditional versioned updates. */
public final class AccessWalletRepository implements WalletRepository {
    /** Creates a stateless repository. */
    public AccessWalletRepository() { }
    @Override public WalletBalance balance(Connection c, String userId) throws SQLException {
        long balance = 0; int version = 0;
        try (var s = WalletSql.prepare(c, "SELECT balanceCents,rowVersion FROM tblWalletAccount WHERE userId=?", userId);
             var rows = s.executeQuery()) {
            if (rows.next()) { balance = rows.getLong(1); version = rows.getInt(2); }
        }
        long pending = 0;
        try (var s = WalletSql.prepare(c, "SELECT SUM(amountCents) FROM tblWalletEscrow WHERE sellerId=? AND escrowStatus='HELD'", userId);
             var rows = s.executeQuery()) { if (rows.next()) pending = rows.getLong(1); }
        return new WalletBalance(balance, pending, version);
    }
    @Override public long change(Connection c, String userId, long delta) throws SQLException {
        var old = balance(c, userId);
        long next = Math.addExact(old.balanceCents(), delta);
        if (next < 0) throw new WalletException("WALLET_INSUFFICIENT_BALANCE");
        if (next > WalletRules.MAX_CENTS) throw new WalletException("WALLET_BALANCE_LIMIT");
        if (old.version() == 0) {
            WalletSql.update(c, "INSERT INTO tblWalletAccount (userId,balanceCents,rowVersion) VALUES (?,?,1)", userId, next);
        } else if (WalletSql.update(c, "UPDATE tblWalletAccount SET balanceCents=?,rowVersion=rowVersion+1 "
                + "WHERE userId=? AND rowVersion=? AND balanceCents=?", next, userId, old.version(), old.balanceCents()) != 1) {
            throw new WalletException("WALLET_RETRY_REQUIRED");
        }
        return next;
    }
}
