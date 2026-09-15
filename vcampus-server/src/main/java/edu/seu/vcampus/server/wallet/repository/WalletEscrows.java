package edu.seu.vcampus.server.wallet.repository;

import edu.seu.vcampus.server.wallet.service.WalletException;
import edu.seu.vcampus.server.wallet.service.WalletPosting;
import java.sql.Connection;
import java.sql.SQLException;

/** Order escrow persistence with one mutually exclusive terminal transition. */
public final class WalletEscrows {
    /** Creates a stateless escrow store. */
    public WalletEscrows() { }
    /** Reserves the order identity once; duplicate holds cannot debit a second time. */
    public void hold(Connection c, WalletPosting p) throws SQLException {
        try (var s = WalletSql.prepare(c, "SELECT orderKey FROM tblWalletEscrow WHERE orderKey=?", p.orderKey());
             var rows = s.executeQuery()) {
            if (rows.next()) throw new WalletException("WALLET_ORDER_ALREADY_FUNDED");
        }
        WalletSql.update(c, "INSERT INTO tblWalletEscrow (orderKey,buyerId,sellerId,amountCents,escrowStatus) VALUES (?,?,?,?,'HELD')",
                p.orderKey(), p.buyerId(), p.sellerId(), p.amountCents());
    }
    /** Claims an exact held order for refund or settlement, rejecting mismatched contents. */
    public void finish(Connection c, WalletPosting p) throws SQLException {
        String state = p.kind() == WalletPosting.Kind.REFUND ? "REFUNDED" : "SETTLED";
        int updated = WalletSql.update(c, "UPDATE tblWalletEscrow SET escrowStatus=? WHERE orderKey=? "
                + "AND buyerId=? AND sellerId=? AND amountCents=? AND escrowStatus='HELD'",
                state, p.orderKey(), p.buyerId(), p.sellerId(), p.amountCents());
        if (updated != 1) throw new WalletException("WALLET_ESCROW_CONFLICT");
    }
}
