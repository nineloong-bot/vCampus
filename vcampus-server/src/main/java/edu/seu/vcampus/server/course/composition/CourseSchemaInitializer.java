package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import io.github.spannm.jackcess.Database;
import net.ucanaccess.jdbc.UcanaccessConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Idempotently installs the course-owned Access schema into the shared database. */
public final class CourseSchemaInitializer {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+([A-Za-z0-9_]+)");
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+([A-Za-z0-9_]+)\\s+ON\\s+([A-Za-z0-9_]+)");
    private final Path schemaFile;

    public CourseSchemaInitializer(Path schemaFile) {
        this.schemaFile = Objects.requireNonNull(schemaFile).toAbsolutePath().normalize();
    }

    /** Creates all course tables when they are absent. */
    public void initialize(ConnectionProvider database) throws IOException, SQLException {
        Objects.requireNonNull(database);
        try (Connection connection = database.open()) {
            Set<String> tables = tableNames(connection);
            Set<String> indexes = indexNames(connection);
            String schema = Files.readString(schemaFile);
            for (String sql : schema.split(";")) {
                if (sql.isBlank()) continue;
                Matcher table = CREATE_TABLE.matcher(sql);
                if (table.find() && tables.contains(normalize(table.group(1)))) continue;
                Matcher index = CREATE_INDEX.matcher(sql);
                if (index.find() && indexes.contains(normalize(index.group(1)))) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                if (table.find(0)) tables.add(normalize(table.group(1)));
                if (index.find(0)) indexes.add(normalize(index.group(1)));
            }
            installIdentityForeignKeys(connection, tables);
        }
    }

    private static void installIdentityForeignKeys(Connection connection, Set<String> tables)
            throws SQLException {
        if (tables.contains("tbluser")) {
            installForeignKey(connection, "tblCourseOffering", "teacherUserId", "tblUser", "userId",
                    "fk_tblCourseOffering_teacher");
        }
        if (tables.contains("tblstudent")) {
            installForeignKey(connection, "tblEnrollment", "studentId", "tblStudent", "studentId",
                    "fk_tblEnrollment_student");
            installForeignKey(connection, "tblEnrollmentAdjustment", "studentId", "tblStudent", "studentId",
                    "fk_tblEnrollmentAdjustment_student");
            installForeignKey(connection, "tblCourseAttempt", "studentId", "tblStudent", "studentId",
                    "fk_tblCourseAttempt_student");
        }
    }

    private static void installForeignKey(Connection connection, String table, String column,
                                          String referencedTable, String referencedColumn,
                                          String constraint) throws SQLException {
        if (hasImportedKey(connection, table, column, referencedTable, referencedColumn)) return;
        String sql = "ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " FOREIGN KEY ("
                + column + ") REFERENCES " + referencedTable + " (" + referencedColumn + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean hasImportedKey(Connection connection, String table, String column,
                                          String referencedTable, String referencedColumn) throws SQLException {
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && referencedTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                        && referencedColumn.equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    private static Set<String> tableNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                names.add(normalize(tables.getString("TABLE_NAME")));
            }
        }
        return names;
    }

    private static Set<String> indexNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try {
            Database database = ((UcanaccessConnection) connection).getDbIO();
            for (String tableName : database.getTableNames()) {
                for (var index : database.getTable(tableName).getIndexes()) {
                    names.add(normalize(index.getName()));
                }
            }
        } catch (IOException error) {
            throw new SQLException("Unable to inspect Access indexes", error);
        }
        return names;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
