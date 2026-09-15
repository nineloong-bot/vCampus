package edu.seu.vcampus.server.shop.governance;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Small JDBC primitives shared only within the governance repository package. */
final class GovernanceSql {
    private GovernanceSql() { }
    static int update(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            bind(s,args);
            return s.executeUpdate();
        }
    }
    static List<List<String>> rows(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            bind(s,args);
            try (var r = s.executeQuery()) {
                var rows = new ArrayList<List<String>>();
                while(r.next()) {
                    var row = new ArrayList<String>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.add(r.getString(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    static String scalar(Connection c, String sql, Object... args) throws SQLException {
        var rows=rows(c,sql,args);
        return rows.isEmpty()?null:rows.getFirst().getFirst();
    }
    static String required(Connection c,String sql,Object... args) throws SQLException {
        var value=scalar(c,sql,args);
        if(value==null) throw new IllegalArgumentException("Record not found");
        return value;
    }
    static boolean exists(Connection c,String sql,Object... args) throws SQLException {
        return scalar(c,sql,args)!=null;
    }
    static void bind(java.sql.PreparedStatement s,Object[] args) throws SQLException {
        for(int i=0;i<args.length;i++) {
            Object a=args[i];
            s.setObject(i+1,a instanceof Instant instant ? Timestamp.from(instant):a);
        }
    }
    static String text(String text,int max) {
        if(text==null||text.isBlank()||text.length()>max) throw new IllegalArgumentException("Invalid text");
        return text.strip();
    }
    static String id() { return java.util.UUID.randomUUID().toString(); }
}
