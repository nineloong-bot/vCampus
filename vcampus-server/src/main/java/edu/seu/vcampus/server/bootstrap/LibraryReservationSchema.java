package edu.seu.vcampus.server.bootstrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;

/** Additive upgrade for databases created before the reservation queue existed. */
final class LibraryReservationSchema {
    private LibraryReservationSchema() { }

    static void initialize(Connection connection) throws SQLException {
        createTable(connection);
        addColumn(connection, "tblLibraryPolicy", "reserveDays", "LONG DEFAULT 3 NOT NULL");
    }

    private static void createTable(Connection connection) throws SQLException {
        if (tables(connection).contains("tblbookreservation")) return;
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblBookReservation ("
                    + "reservationId VARCHAR(36) PRIMARY KEY, "
                    + "copyId VARCHAR(36) NOT NULL, bookId VARCHAR(36) NOT NULL, "
                    + "userId VARCHAR(36) NOT NULL, reserverRoleCode VARCHAR(16) NOT NULL, "
                    + "reservedAt DATETIME NOT NULL, queueOrder LONG NOT NULL, "
                    + "reservationStatus VARCHAR(16) NOT NULL, readyAt DATETIME, expiresAt DATETIME, "
                    + "rowVersion LONG NOT NULL)");
        }
        // UCanAccess can refuse to persist lookup indexes on freshly created tables; the
        // reservation table is small enough that a missing index only costs a table scan.
        for (String index : new String[]{
                "CREATE INDEX idx_tblBookReservation_copy ON tblBookReservation (copyId, reservationStatus)",
                "CREATE INDEX idx_tblBookReservation_user ON tblBookReservation (userId, reservationStatus)"}) {
            try (var statement = connection.createStatement()) {
                statement.execute(index);
            } catch (SQLException ignored) {
                // Optional performance index; safe to skip.
            }
        }
    }

    private static void addColumn(Connection connection, String table, String column,
            String definition) throws SQLException {
        if (columns(connection, table).contains(column.toLowerCase(Locale.ROOT))) return;
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static HashSet<String> tables(Connection connection) throws SQLException {
        var names = new HashSet<String>();
        try (var tables = connection.getMetaData().getTables(null, null, null, null)) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name != null) names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static HashSet<String> columns(Connection connection, String table) throws SQLException {
        var names = new HashSet<String>();
        try (var columns = connection.getMetaData().getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                    names.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }
}