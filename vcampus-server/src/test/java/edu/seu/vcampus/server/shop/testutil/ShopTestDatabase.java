package edu.seu.vcampus.server.shop.testutil;

import edu.seu.vcampus.server.persistence.ConnectionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ShopTestDatabase implements AutoCloseable {
    private final Path directory;
    private final Path database;

    public ShopTestDatabase() throws Exception {
        directory = Files.createTempDirectory("vcampus-shop-");
        database = directory.resolve("shop.accdb");
        try (Connection connection = open()) {
            execute(connection, "CREATE TABLE tblUser (userId VARCHAR(36) PRIMARY KEY)");
            for (String userId : new String[] {"student-1", "teacher-1", "inactive-1",
                    "other-1", "owner-1", "stranger-1", "admin-1", "admin-7"}) {
                try (var statement = connection.prepareStatement(
                        "INSERT INTO tblUser (userId) VALUES (?)")) {
                    statement.setString(1, userId);
                    statement.executeUpdate();
                }
            }
            String schema = Files.readString(Path.of("..", "vcampus-database", "schema", "050_shop.sql"));
            for (String statement : schema.split(";")) {
                if (!statement.isBlank()) {
                    execute(connection, statement.strip());
                }
            }
        }
    }

    public ConnectionProvider connections() {
        return this::open;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new IllegalStateException(error);
                }
            });
        }
    }
}
