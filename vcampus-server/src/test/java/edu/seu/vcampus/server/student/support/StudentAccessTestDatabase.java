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
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        provider = () -> DriverManager.getConnection(url);
        try (Connection connection = provider.open()) {
            executeSchema(connection, Path.of("..", "vcampus-database", "schema", "020_student.sql"));
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

    private static void executeSchema(Connection connection, Path schema) throws Exception {
        String sql = Files.readString(schema);
        for (String statementSql : sql.split(";")) {
            String statementText = statementSql.trim();
            if (!statementText.isEmpty()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(statementText);
                }
            }
        }
    }
}
