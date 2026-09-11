package edu.seu.vcampus.server.student.support;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

/** Creates an isolated Access database with the student schema for integration tests. */
public final class StudentAccessTestDatabase {
    private final ConnectionProvider provider;

    public StudentAccessTestDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        // Do not enable immediatelyReleaseResources here: concurrent student tests
        // otherwise race UCanAccess connection startup against mirror shutdown.
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010";
        provider = () -> DriverManager.getConnection(url);
        Path[] schemas = {
                Path.of("..", "vcampus-database", "schema", "001_common.sql"),
                Path.of("..", "vcampus-database", "schema", "020_student.sql"),
                Path.of("..", "vcampus-database", "schema", "025_major_transfer.sql")
        };
        // Two-pass DDL: tables first, then indexes — matches DatabaseInitializer.
        // UCanAccess can keep later DDL only in its in-memory catalog when many
        // tables are created in a single session, which makes tables disappear
        // after the connection closes.
        for (Path schema : schemas) {
            try (Connection connection = provider.open()) {
                executeSchema(connection, schema, true);
            }
        }
        try (Connection connection = provider.open()) {
            connection.createStatement().execute(
                    "CREATE TABLE tblUser (userId VARCHAR(36) PRIMARY KEY, loginId VARCHAR(16) NOT NULL)");
        }
        for (Path schema : schemas) {
            try (Connection connection = provider.open()) {
                executeSchema(connection, schema, false);
            }
        }
        try (Connection connection = provider.open()) {
            connection.createStatement().execute(
                    "CREATE UNIQUE INDEX uk_tblUser_loginId ON tblUser (loginId)");
        }
    }

    public ConnectionProvider provider() {
        return provider;
    }

    public TransactionManager transactions() {
        return new TransactionManager(provider);
    }

    public void setSequence(String key, int currentValue, int maxValue) throws Exception {
        try (Connection connection = provider.open()) {
            try (var delete = connection.prepareStatement(
                    "DELETE FROM tblNumberSequence WHERE sequenceKey = ?")) {
                delete.setString(1, key);
                delete.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt) VALUES (?, ?, ?, 0, NOW())")) {
                insert.setString(1, key);
                insert.setInt(2, currentValue);
                insert.setInt(3, maxValue);
                insert.executeUpdate();
            }
        }
    }

    public int sequenceValue(String key) throws Exception {
        try (Connection connection = provider.open();
             var statement = connection.prepareStatement(
                     "SELECT currentValue FROM tblNumberSequence WHERE sequenceKey = ?")) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Missing sequence " + key);
                }
                return result.getInt(1);
            }
        }
    }

    public int count(String table) throws Exception {
        if (!table.matches("tbl[A-Za-z]+")) throw new IllegalArgumentException("Invalid table");
        try (Connection connection = provider.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    public String stringValue(String sql) throws Exception {
        try (Connection connection = provider.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static void executeSchema(Connection connection, Path schema, boolean tablesOnly) throws Exception {
        String sql = Files.readString(schema).replaceAll("YESNO", "BOOLEAN");
        // Strip CONSTRAINT ... REFERENCES lines (UCanAccess FK bug)
        StringBuilder cleaned = new StringBuilder();
        boolean skipNextRef = false;
        for (String line : sql.split("\n")) {
            String upper = line.trim().toUpperCase();
            if (upper.startsWith("--")) continue;
            if (upper.startsWith("CONSTRAINT")) { skipNextRef = true; continue; }
            if (skipNextRef && upper.startsWith("REFERENCES")) { skipNextRef = false; continue; }
            skipNextRef = false;
            cleaned.append(line).append("\n");
        }
        String normalized = cleaned.toString().replaceAll(",\\s*\\)", "\n)");
        for (String statementString : normalized.split(";")) {
            String trimmed = statementString.trim();
            if (trimmed.isEmpty()) continue;
            boolean isIndex = trimmed.toUpperCase().startsWith("CREATE INDEX")
                    || trimmed.toUpperCase().startsWith("CREATE UNIQUE INDEX");
            if (tablesOnly == isIndex) continue;
            // UCanAccess 5.x / Jackcess defect on secondary indexes for training-plan tables
            if (isIndex && schema.getFileName().toString().equals("030_training_plan.sql")) continue;
            try (var statement = connection.createStatement()) {
                statement.execute(trimmed);
            }
        }
    }
}
