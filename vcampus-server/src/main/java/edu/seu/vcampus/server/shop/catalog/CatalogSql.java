package edu.seu.vcampus.server.shop.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;

final class CatalogSql {
    private CatalogSql() { }
    static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        var s = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
        return s;
    }
    static void update(Connection c, String sql, Object... args) throws SQLException {
        try (var s = prepare(c, sql, args)) { s.executeUpdate(); }
    }
    static String scalar(Connection c, String sql, Object... args) throws SQLException {
        try (var s = prepare(c, sql, args); var r = s.executeQuery()) {
            return r.next() ? r.getString(1) : null;
        }
    }
    static String text(String value) { return value == null ? "" : value.trim(); }
    static void require(boolean condition, String error) {
        if (!condition) throw new CatalogException(error);
    }
    static byte[] bytes(Serializable value) {
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(buffer)) { out.writeObject(value); }
            return buffer.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Cannot encode receipt", e); }
    }
    static String hash(Serializable value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value))); }
        catch (Exception e) { throw new IllegalStateException("Cannot fingerprint request", e); }
    }
    static Serializable replay(Connection c, String key, Serializable command) throws SQLException {
        try (var s = prepare(c, "SELECT fingerprint,resultData FROM tblCatalogReceipt WHERE receiptKey=?", key);
             var r = s.executeQuery()) {
            if (!r.next()) return null;
            require(r.getString(1).equals(hash(command)), "重复请求编号不能用于不同内容");
            try (var in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(r.getString(2))))) {
                return (Serializable) in.readObject();
            } catch (Exception e) { throw new IllegalStateException("Cannot decode stored receipt", e); }
        }
    }
    static void receipt(Connection c, String key, Serializable command, Serializable result) throws SQLException {
        update(c, "INSERT INTO tblCatalogReceipt(receiptKey,fingerprint,resultData) VALUES(?,?,?)",
                key, hash(command), Base64.getEncoder().encodeToString(bytes(result)));
    }
    static String key(String user, String operation, String request) {
        require(!text(request).isEmpty() && request.length() <= 100, "缺少有效请求编号");
        return operation + ":" + user + ":" + request;
    }
}
