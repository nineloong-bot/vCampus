package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

final class WalletTestDatabase implements AutoCloseable {
    final Path path = Files.createTempDirectory("wallet-test-").resolve("wallet.accdb");
    WalletTestDatabase() throws Exception {
        try (var c = connections().open(); var s = c.createStatement()) {
            s.execute("CREATE TABLE tblUser (userId VARCHAR(36) PRIMARY KEY)");
            for (String id : new String[]{"buyer", "seller", "other"}) {
                s.execute("INSERT INTO tblUser VALUES ('" + id + "')");
            }
            for (String sql : Files.readString(Path.of("../vcampus-database/schema/051_shop_wallet.sql")).split(";")) {
                if (!sql.isBlank()) s.execute(sql);
            }
        }
    }
    ConnectionProvider connections() {
        return () -> DriverManager.getConnection("jdbc:ucanaccess://" + path + ";newDatabaseVersion=V2010");
    }
    long count(String table) throws Exception {
        try (var c = connections().open(); var s = c.createStatement();
             var rows = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next(); return rows.getLong(1);
        }
    }
    @Override public void close() throws Exception {
        try (var paths = Files.walk(path.getParent())) {
            for (Path file : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
