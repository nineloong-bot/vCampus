package edu.seu.vcampus.server.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/** Creates a vCampus Access database from the schema and seed SQL files. */
public final class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    /** Usage: DatabaseInitializer <schema-dir> <seed-dir> <output.accdb> */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("用法: DatabaseInitializer <schema目录> <seed目录> <输出.accdb>");
            System.exit(2);
        }
        Path schemaDir = Path.of(args[0]);
        Path seedDir = Path.of(args[1]);
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);
        String url = "jdbc:ucanaccess://" + output
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        // Persist each schema file before creating the next one. UCanAccess can keep
        // later DDL only in its in-memory catalog when many tables are created in a
        // single session, which made tblTrainingPlan disappear after a restart.
        for (Path file : sqlFiles(schemaDir)) {
            try (Connection connection = DriverManager.getConnection(url)) {
                executeStatements(connection, file, true);
            }
        }
        try (Connection connection = DriverManager.getConnection(url)) {
            for (Path file : sqlFiles(seedDir)) {
                executeStatements(connection, file, true);
            }
        }
        for (Path file : sqlFiles(schemaDir)) {
            try (Connection connection = DriverManager.getConnection(url)) {
                executeStatements(connection, file, false);
            }
        }
        System.out.println("数据库已生成: " + output);
    }

    private static List<Path> sqlFiles(Path directory) throws Exception {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private static void execute(Connection connection, Path sqlFile) throws Exception {
        executeStatements(connection, sqlFile, true);
        executeStatements(connection, sqlFile, false);
    }

    private static void executeStatements(Connection connection, Path sqlFile,
            boolean tablesOnly) throws Exception {
        String sql = Files.readString(sqlFile).replaceAll("YESNO", "BOOLEAN");
        // Strip only foreign-key clauses (UCanAccess FK bug). Primary-key and
        // unique constraints are part of the release database's integrity model
        // and must survive database generation.
        StringBuilder cleaned = new StringBuilder();
        boolean skipNextRef = false;
        for (String line : sql.split("\n")) {
            String upper = line.trim().toUpperCase();
            // UCanAccess silently treats a statement beginning with a SQL line
            // comment as a comment-only statement, discarding the DDL or DML after
            // it. Remove comments before splitting and executing statements.
            if (upper.startsWith("--")) continue;
            if (upper.startsWith("CONSTRAINT") && upper.contains("FOREIGN KEY")) {
                skipNextRef = !upper.contains("REFERENCES");
                continue;
            }
            if (skipNextRef && upper.startsWith("REFERENCES")) { skipNextRef = false; continue; }
            skipNextRef = false;
            cleaned.append(line).append("\n");
        }
        // Removing a foreign-key clause can leave a trailing comma before the
        // closing parenthesis. UCanAccess accepts that malformed DDL but does not
        // persist the affected table after the connection closes.
        String normalized = cleaned.toString().replaceAll(",\\s*\\)", "\n)");
        for (String statementText : normalized.split(";")) {
            String trimmed = statementText.trim();
            if (trimmed.isEmpty()) continue;
            boolean isIndex = trimmed.toUpperCase().startsWith("CREATE INDEX")
                    || trimmed.toUpperCase().startsWith("CREATE UNIQUE INDEX");
            if (tablesOnly == isIndex) continue;
            // UCanAccess 5.x / Jackcess has a defect when adding optional lookup
            // indexes to these freshly-created tables. Business UNIQUE constraints
            // are inline in CREATE TABLE, so skipping these indexes affects only
            // lookup performance, not integrity.
            if (isIndex && sqlFile.getFileName().toString().equals("030_training_plan.sql")) {
                System.out.println("SKIP (UCanAccess compatibility): " + trimmed);
                continue;
            }
            String preview = trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed;
            try (var statement = connection.createStatement()) {
                statement.execute(trimmed);
                System.out.println("OK: " + preview);
            } catch (Exception e) {
                System.err.println("FAIL: " + preview);
                System.err.println("  -> " + e.getMessage());
                throw e;
            }
        }
    }
}
