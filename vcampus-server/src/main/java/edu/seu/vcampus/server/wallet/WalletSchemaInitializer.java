package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

/** Installs the wallet-owned additive schema and resumes missing foreign keys on restart. */
public final class WalletSchemaInitializer {
    private final Path script;
    /** Uses the authoritative wallet SQL resource. */
    public WalletSchemaInitializer(Path script) { this.script = script; }
    /** Adds missing tables and relationships without rewriting existing balances or receipts. */
    public void initialize(ConnectionProvider connections) throws IOException, SQLException {
        String source = Files.readString(script);
        var create = Pattern.compile("(?is)^\\s*CREATE TABLE (\\w+)");
        var alter = Pattern.compile("(?is)^\\s*ALTER TABLE (\\w+) ADD CONSTRAINT \\w+ FOREIGN KEY \\((\\w+)\\) REFERENCES (\\w+)\\((\\w+)\\)");
        try (var c = connections.open()) {
            var tables = new HashSet<String>();
            try (var rows = c.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (rows.next()) tables.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            for (String sql : source.split(";")) {
                if (sql.isBlank()) continue;
                var table = create.matcher(sql);
                if (table.find() && tables.contains(table.group(1).toLowerCase(Locale.ROOT))) continue;
                var fk = alter.matcher(sql);
                if (fk.find() && hasKey(c, fk.group(1), fk.group(2), fk.group(3), fk.group(4))) continue;
                try (var s = c.createStatement()) { s.execute(sql); }
                if (table.find(0)) tables.add(table.group(1).toLowerCase(Locale.ROOT));
            }
        }
    }
    private boolean hasKey(Connection c, String table, String column, String target, String targetColumn) throws SQLException {
        try (var rows = c.getMetaData().getImportedKeys(null, null, table)) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("FKCOLUMN_NAME"))
                        && target.equalsIgnoreCase(rows.getString("PKTABLE_NAME"))
                        && targetColumn.equalsIgnoreCase(rows.getString("PKCOLUMN_NAME"))) return true;
            }
        }
        return false;
    }
}
