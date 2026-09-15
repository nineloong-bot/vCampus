package edu.seu.vcampus.server.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** SQL script parsing helpers for the application schema initializer segments. */
abstract class ApplicationSchemaInitializerParsing extends ApplicationSchemaInitializerBase {

    ApplicationSchemaInitializerParsing(Path resourceRoot) {
        super(resourceRoot);
    }

    static List<String> statements(Path script) throws IOException {
        List<String> statements = new ArrayList<>();
        String compatibleSql = compatibleSql(script);
        for (String sql : compatibleSql.split(";")) {
            if (!sql.isBlank()) statements.add(sql.strip());
        }
        return statements;
    }

    static String compatibleSql(Path script) throws IOException {
        String sql = Files.readString(script).replaceAll("(?i)\\bYESNO\\b", "BOOLEAN");
        boolean trainingPlan = script.getFileName().toString().equals("030_training_plan.sql");
        StringBuilder cleaned = new StringBuilder();
        boolean skipReference = false;
        for (String line : sql.split("\\R")) {
            String upper = line.strip().toUpperCase(Locale.ROOT);
            if (upper.startsWith("--")) continue;
            if (trainingPlan && upper.startsWith("CONSTRAINT")
                    && upper.contains("FOREIGN KEY")) {
                skipReference = !upper.contains("REFERENCES");
                continue;
            }
            if (trainingPlan && skipReference && upper.startsWith("REFERENCES")) {
                skipReference = false;
                continue;
            }
            skipReference = false;
            cleaned.append(line).append('\n');
        }
        return cleaned.toString().replaceAll(",\\s*\\)", "\n)");
    }

    static List<String> splitValues(String values) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < values.length(); index++) {
            char character = values.charAt(index);
            if (character == '\'') {
                if (quoted && index + 1 < values.length() && values.charAt(index + 1) == '\'') {
                    current.append("''");
                    index++;
                } else {
                    quoted = !quoted;
                    current.append(character);
                }
            } else if (character == ',' && !quoted) {
                result.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        result.add(current.toString().strip());
        return result;
    }
}
