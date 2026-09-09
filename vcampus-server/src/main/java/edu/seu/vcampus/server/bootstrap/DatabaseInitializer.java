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
        // UCanAccess requires separate connections for table creation and index creation
        try (Connection connection = DriverManager.getConnection(url)) {
            for (Path file : sqlFiles(schemaDir)) {
                executeStatements(connection, file, true);
            }
            for (Path file : sqlFiles(seedDir)) {
                executeStatements(connection, file, true);
            }
        }
        // Reopen connection for index creation (UCanAccess cache sync)
        try (Connection connection = DriverManager.getConnection(url)) {
            for (Path file : sqlFiles(schemaDir)) {
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
        String sql = Files.readString(sqlFile).replaceAll("YESNO", "BIT");
        // Strip CONSTRAINT ... REFERENCES lines (UCanAccess FK bug)
        StringBuilder cleaned = new StringBuilder();
        boolean skipNextRef = false;
        for (String line : sql.split("\n")) {
            String upper = line.trim().toUpperCase();
            if (upper.startsWith("CONSTRAINT")) { skipNextRef = true; continue; }
            if (skipNextRef && upper.startsWith("REFERENCES")) { skipNextRef = false; continue; }
            skipNextRef = false;
            cleaned.append(line).append("\n");
        }
        for (String statementText : cleaned.toString().split(";")) {
            String trimmed = statementText.trim();
            if (trimmed.isEmpty()) continue;
            boolean isIndex = trimmed.toUpperCase().startsWith("CREATE INDEX")
                    || trimmed.toUpperCase().startsWith("CREATE UNIQUE INDEX");
            if (tablesOnly == isIndex) continue;
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
