package edu.seu.vcampus.server.wallet.repository;

import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.common.wallet.WalletEntryView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.wallet.service.WalletException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/** Immutable operation receipts and balanced journal entries. */
public final class WalletJournal {
    /** Creates a stateless journal. */
    public WalletJournal() { }
    /** Returns a matching receipt or rejects reuse with different operation contents. */
    public WalletOperationResult replay(Connection c, String key, String type, String actor, String peer,
                                        String order, long amount) throws SQLException {
        try (var s = WalletSql.prepare(c, "SELECT * FROM tblWalletOperation WHERE businessKey=?", key);
             var rows = s.executeQuery()) {
            if (!rows.next()) return null;
            if (!type.equals(rows.getString("operationType")) || !actor.equals(rows.getString("actorId"))
                    || !peer.equals(rows.getString("peerId")) || !order.equals(rows.getString("orderKey"))
                    || amount != rows.getLong("amountCents")) throw new WalletException("WALLET_IDEMPOTENCY_CONFLICT");
            return new WalletOperationResult(rows.getString("operationId"), rows.getLong("balanceAfter"));
        }
    }
    /** Appends an operation receipt in the same transaction as its balance change. */
    public WalletOperationResult record(Connection c, String key, String type, String actor, String peer,
                                         String order, long amount, long after, Instant time) throws SQLException {
        String id = UUID.randomUUID().toString();
        WalletSql.update(c, "INSERT INTO tblWalletOperation (operationId,businessKey,operationType,actorId,peerId,"
                + "orderKey,amountCents,balanceAfter,createdAt) VALUES (?,?,?,?,?,?,?,?,?)",
                id, key, type, actor, peer, order, amount, after, Timestamp.from(time));
        return new WalletOperationResult(id, after);
    }
    /** Writes the two opposite sides of one transfer. */
    public void transfer(Connection c, String id, String fromKind, String fromKey,
                         String toKind, String toKey, long amount) throws SQLException {
        entry(c, id, fromKind, fromKey, -amount); entry(c, id, toKind, toKey, amount);
    }
    private void entry(Connection c, String id, String kind, String key, long delta) throws SQLException {
        WalletSql.update(c, "INSERT INTO tblWalletEntry (entryId,operationId,accountKind,accountKey,deltaCents) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), id, kind, key, delta);
    }
    /** Returns only the user's spendable-money entries, ordered deterministically. */
    public PageResult<WalletEntryView> history(Connection c, String userId, int page, int size) throws SQLException {
        String from = " FROM tblWalletEntry e INNER JOIN tblWalletOperation o ON e.operationId=o.operationId"
                + " WHERE e.accountKind='USER' AND e.accountKey=?";
        long total;
        try (var s = WalletSql.prepare(c, "SELECT COUNT(*)" + from, userId); var rows = s.executeQuery()) {
            rows.next(); total = rows.getLong(1);
        }
        var items = new ArrayList<WalletEntryView>();
        long offset = (long) (page - 1) * size;
        try (var s = WalletSql.prepare(c, "SELECT o.operationId,o.operationType,e.deltaCents,o.orderKey,o.createdAt,o.balanceAfter"
                + from + " ORDER BY o.createdAt DESC,o.operationId DESC", userId); var rows = s.executeQuery()) {
            long index = 0;
            while (rows.next() && items.size() < size) {
                if (index++ < offset) continue;
                items.add(new WalletEntryView(rows.getString(1), rows.getString(2), rows.getLong(3),
                        rows.getLong(6), "-".equals(rows.getString(4)) ? null : rows.getString(4), rows.getTimestamp(5).toInstant()));
            }
        }
        return new PageResult<>(items, page, size, total);
    }
}
