package edu.seu.vcampus.server.wallet.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class WalletSql {
    private WalletSql() { }
    static PreparedStatement prepare(Connection c, String sql, Object... values) throws SQLException {
        PreparedStatement s = c.prepareStatement(sql);
        try {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            return s;
        } catch (SQLException error) { s.close(); throw error; }
    }
    static int update(Connection c, String sql, Object... values) throws SQLException {
        try (var s = prepare(c, sql, values)) { return s.executeUpdate(); }
    }
}
