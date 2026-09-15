package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.persistence.ConnectionProvider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** Applies small idempotent column migrations to an existing Access database. */
final class AccessSchemaEvolution {
    private AccessSchemaEvolution() {
    }

    static void ensureColumn(ConnectionProvider connections, String table, String column,
            String definition) throws SQLException {
        try (Connection connection = connections.open()) {
            if (columnSize(connection, table, column) != null) return;
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " "
                        + definition);
            }
        }
    }

    private static Integer columnSize(Connection connection, String table, String column)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, null)) {
            while (columns.next()) {
                if (columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)
                        .equals(column.toLowerCase(Locale.ROOT))) {
                    return columns.getInt("COLUMN_SIZE");
                }
            }
        }
        return null;
    }
}
