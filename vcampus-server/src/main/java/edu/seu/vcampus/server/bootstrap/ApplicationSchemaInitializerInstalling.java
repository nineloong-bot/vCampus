package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import io.github.spannm.jackcess.Database;
import net.ucanaccess.jdbc.UcanaccessConnection;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/** Idempotent schema and seed installation for the application schema initializer segments. */
abstract class ApplicationSchemaInitializerInstalling extends ApplicationSchemaInitializerSeeds {

    ApplicationSchemaInitializerInstalling(Path resourceRoot) {
        super(resourceRoot);
    }

    static void installSchema(ConnectionProvider connections, Path script)
            throws IOException, SQLException {
        try (Connection connection = connections.open()) {
            Set<String> tables = tableNames(connection);
            Set<String> preexistingTables = Set.copyOf(tables);
            Set<String> indexes = indexNames(connection);
            for (String sql : statements(script)) {
                Matcher table = CREATE_TABLE.matcher(sql);
                if (table.find() && tables.contains(normalize(table.group(1)))) continue;
                Matcher index = CREATE_INDEX.matcher(sql);
                if (index.find()) {
                    // UCanAccess 5.x cannot persist optional lookup indexes for these
                    // freshly-created Access tables. Business UNIQUE constraints are
                    // declared inline in CREATE TABLE and are therefore still enforced.
                    if (script.getFileName().toString().equals("030_training_plan.sql")) continue;
                    if (indexes.contains(normalize(index.group(1)))) continue;
                }
                Matcher alter = ALTER_TABLE.matcher(sql);
                if (alter.find() && preexistingTables.contains(normalize(alter.group(1)))) continue;
                Matcher insert = INSERT.matcher(sql);
                if (insert.matches() && SEED_KEYS.containsKey(normalize(insert.group(1)))
                        && seedExists(connection, sql)) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                if (table.find(0)) tables.add(normalize(table.group(1)));
                if (index.find(0)) indexes.add(normalize(index.group(1)));
            }
        }
    }

    static void installSeeds(ConnectionProvider connections, Path script)
            throws IOException, SQLException {
        try (Connection connection = connections.open()) {
            List<String> postInsertStatements = new ArrayList<>();
            boolean inserted = false;
            for (String sql : statements(script)) {
                Matcher insert = INSERT.matcher(sql);
                if (!insert.matches()) {
                    postInsertStatements.add(sql);
                } else if (!seedExists(connection, sql)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                    inserted = true;
                }
            }
            // Seed UPDATE statements initialize rows created by this script. Replaying
            // them on every server start would overwrite real user edits and sequences.
            if (inserted) {
                for (String sql : postInsertStatements) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
        }
    }

    private static Set<String> tableNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) names.add(normalize(tables.getString("TABLE_NAME")));
        }
        return names;
    }

    private static Set<String> indexNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try {
            Database database = ((UcanaccessConnection) connection).getDbIO();
            for (String table : database.getTableNames()) {
                for (var index : database.getTable(table).getIndexes()) names.add(normalize(index.getName()));
            }
        } catch (IOException error) {
            throw new SQLException("Unable to inspect Access indexes", error);
        }
        return names;
    }
}
