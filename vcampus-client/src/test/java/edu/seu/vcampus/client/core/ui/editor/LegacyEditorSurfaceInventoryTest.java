package edu.seu.vcampus.client.core.ui.editor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the approved boundary between embedded editors and transient dialogs. */
class LegacyEditorSurfaceInventoryTest {
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Pattern DIALOG = Pattern.compile("\\b(?:extends\\s+JDialog|new\\s+JDialog\\s*\\()");
    private static final Pattern INPUT = Pattern.compile("JOptionPane\\.show(?:Input|Option)Dialog\\s*\\(");
    private static final Pattern CONFIRM = Pattern.compile("JOptionPane\\.showConfirmDialog\\s*\\(");
    private static final Set<String> ALLOWED_DIALOGS = Set.of(
            "OfferingDetailDialog.java",
            "ChangeDetailDialog.java",
            "ApplicationDetailDialog.java",
            "SimulatedCashierDialog.java",
            "ChangePasswordDialog.java",
            "InitialPasswordChangeDialog.java",
            "LogoutConfirmationDialog.java",
            "SessionReplacementWarningDialog.java",
            "StudentPasswordResetConfirmationDialog.java",
            "TeacherPasswordResetConfirmationDialog.java"
    );

    @Test
    void persistentInputUsesEmbeddedWorkspaces() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(SOURCES)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                inspect(path, Files.readString(path), violations);
            }
        }
        assertThat(violations)
                .as("Persistent input must be embedded; remaining legacy surfaces:%n%s",
                        String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private static void inspect(Path path, String source, List<String> violations) {
        if (!ALLOWED_DIALOGS.contains(path.getFileName().toString())) {
            addMatches(path, source, DIALOG, "JDialog", violations);
        }
        addMatches(path, source, INPUT, "input option dialog", violations);
        Matcher confirms = CONFIRM.matcher(source);
        while (confirms.find()) {
            String invocation = invocation(source, confirms.start());
            List<String> arguments = arguments(invocation);
            if (arguments.size() > 1 && !textOnly(arguments.get(1))) {
                violations.add(location(path, source, confirms.start()) + " form confirmation");
            }
        }
    }

    private static void addMatches(Path path, String source, Pattern pattern, String kind,
            List<String> violations) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            violations.add(location(path, source, matcher.start()) + " " + kind);
        }
    }

    private static String invocation(String source, int start) {
        int open = source.indexOf('(', start);
        int depth = 0;
        boolean quoted = false;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '"' && (index == 0 || source.charAt(index - 1) != '\\')) quoted = !quoted;
            if (quoted) continue;
            if (current == '(') depth++;
            if (current == ')' && --depth == 0) return source.substring(open + 1, index);
        }
        return source.substring(open + 1);
    }

    private static List<String> arguments(String invocation) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int index = 0; index < invocation.length(); index++) {
            char current = invocation.charAt(index);
            if (current == '"' && (index == 0 || invocation.charAt(index - 1) != '\\')) quoted = !quoted;
            if (!quoted && (current == '(' || current == '[' || current == '{')) depth++;
            if (!quoted && (current == ')' || current == ']' || current == '}')) depth--;
            if (!quoted && depth == 0 && current == ',') {
                result.add(invocation.substring(start, index).trim());
                start = index + 1;
            }
        }
        result.add(invocation.substring(start).trim());
        return result;
    }

    private static boolean textOnly(String argument) {
        String compact = argument.strip();
        return compact.startsWith("\"")
                || compact.matches("(?:message|text|courseLabel)")
                || compact.startsWith("ShopUiErrors.message(");
    }

    private static String location(Path path, String source, int offset) {
        long line = source.substring(0, offset).lines().count() + 1;
        return SOURCES.relativize(path) + ":" + line;
    }
}
