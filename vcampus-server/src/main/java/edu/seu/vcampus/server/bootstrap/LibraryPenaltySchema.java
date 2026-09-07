package edu.seu.vcampus.server.bootstrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;

/** Additive upgrade for databases created before configurable library penalties. */
final class LibraryPenaltySchema {
    private LibraryPenaltySchema() { }

    static void initialize(Connection connection) throws SQLException {
        addColumns(connection, "tblLibraryPolicy", new String[][]{
                {"firstTierDays", "LONG DEFAULT 7 NOT NULL"}, {"secondTierDays", "LONG DEFAULT 30 NOT NULL"},
                {"firstDailyFine", "CURRENCY DEFAULT 0.5 NOT NULL"}, {"secondDailyFine", "CURRENCY DEFAULT 1 NOT NULL"},
                {"thirdDailyFine", "CURRENCY DEFAULT 2 NOT NULL"}, {"minorDamageFine", "CURRENCY DEFAULT 10 NOT NULL"},
                {"majorDamageFine", "CURRENCY DEFAULT 50 NOT NULL"}, {"lostFine", "CURRENCY DEFAULT 100 NOT NULL"}});
        addColumns(connection, "tblBookLoan", new String[][]{
                {"borrowerRoleCode", "VARCHAR(16)"},
                {"overdueFine", "CURRENCY DEFAULT 0 NOT NULL"}, {"damageFine", "CURRENCY DEFAULT 0 NOT NULL"},
                {"returnCondition", "VARCHAR(16)"}});
        // Null marks each record still awaiting backfill, so interrupted upgrades can resume.
        try (var query = connection.createStatement();
             var users = query.executeQuery("SELECT userId, roleCode FROM tblUser WHERE roleCode IN ('STUDENT', 'TEACHER')");
             var update = connection.prepareStatement("UPDATE tblBookLoan SET borrowerRoleCode = ? WHERE borrowerUserId = ? AND borrowerRoleCode IS NULL")) {
            while (users.next()) {
                update.setString(1, users.getString("roleCode")); update.setString(2, users.getString("userId"));
                update.executeUpdate();
            }
        }
        try (var update = connection.createStatement()) {
            update.executeUpdate("UPDATE tblBookLoan SET borrowerRoleCode = 'STUDENT' WHERE borrowerRoleCode IS NULL");
            update.executeUpdate("UPDATE tblBookLoan SET returnCondition = 'LOST' WHERE loanStatus = 'LOST' AND returnCondition IS NULL");
            update.executeUpdate("UPDATE tblBookLoan SET returnCondition = 'NORMAL' WHERE returnCondition IS NULL");
        }
    }

    private static void addColumns(Connection connection, String table, String[][] definitions) throws SQLException {
        var existing = columns(connection, table);
        for (String[] column : definitions) {
            if (existing.contains(column[0].toLowerCase(Locale.ROOT))) continue;
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column[0] + " " + column[1]);
            }
        }
    }

    private static HashSet<String> columns(Connection connection, String table) throws SQLException {
        var names = new HashSet<String>();
        try (var columns = connection.getMetaData().getColumns(null, null, null, null)) {
            while (columns.next()) if (table.equalsIgnoreCase(columns.getString("TABLE_NAME")))
                names.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
