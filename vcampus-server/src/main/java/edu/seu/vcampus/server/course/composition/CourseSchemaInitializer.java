package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.server.persistence.ConnectionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Idempotently installs the course-owned Access schema into the shared database. */
public final class CourseSchemaInitializer {
    private final Path schemaFile;

    public CourseSchemaInitializer(Path schemaFile) {
        this.schemaFile = Objects.requireNonNull(schemaFile).toAbsolutePath().normalize();
    }

    /** Creates all course tables when they are absent. */
    public void initialize(ConnectionProvider database) throws IOException, SQLException {
        Objects.requireNonNull(database);
        try (Connection connection = database.open()) {
            if (hasTable(connection, "tblTerm")) return;
            String schema = Files.readString(schemaFile);
            for (String sql : schema.split(";")) {
                if (sql.isBlank()) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static boolean hasTable(Connection connection, String expected) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (expected.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }
}
