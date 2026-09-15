package edu.seu.vcampus.server.shop.order;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class OrderSql {
    private OrderSql() { }
    interface Row<T> { T read(java.sql.ResultSet rows) throws SQLException; }
    static PreparedStatement bind(Connection c, String sql, Object... args) throws SQLException {
        var p = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object value = args[i] instanceof Instant instant ? java.sql.Timestamp.from(instant) : args[i];
            p.setObject(i + 1, value);
        }
        return p;
    }
    static int update(Connection c, String sql, Object... args) throws SQLException {
        try (var p = bind(c, sql, args)) { return p.executeUpdate(); }
    }
    static <T> List<T> list(Connection c, String sql, Row<T> mapper, Object... args) throws SQLException {
        try (var p = bind(c, sql, args); var r = p.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (r.next()) result.add(mapper.read(r));
            return result;
        }
    }
    static String id() { return java.util.UUID.randomUUID().toString(); }
    static void require(boolean condition, String code) {
        if (!condition) throw new OrderException(code);
    }
}
