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
        try (Connection connection = DriverManager.getConnection(url)) {
            for (Path file : sqlFiles(schemaDir)) {
                execute(connection, file);
            }
            for (Path file : sqlFiles(seedDir)) {
                execute(connection, file);
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
        String sql = Files.readString(sqlFile);
        for (String statementText : sql.split(";")) {
            if (!statementText.trim().isEmpty()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(statementText);
                }
            }
        }
    }
}
