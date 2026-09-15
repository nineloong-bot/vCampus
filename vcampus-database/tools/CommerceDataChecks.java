import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.Row;
import io.github.spannm.jackcess.Table;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic logical-record hashes and account/preservation checks. */
final class CommerceDataChecks {
    private CommerceDataChecks() {
    }

    static Database open(Path path, boolean readOnly) throws Exception {
        return new DatabaseBuilder().withPath(path).withReadOnly(readOnly).open();
    }

    static List<String> columns(Table table) {
        return table.getColumns().stream().map(c -> c.getName() + ":" + c.getType()
                + ":" + c.getLength() + ":" + c.getPrecision() + ":" + c.getScale()).toList();
    }

    static String hash(Table table) throws Exception {
        if (table == null) {
            return "ABSENT";
        }
        List<String> rows = new ArrayList<>();
        for (Row row : table) {
            StringBuilder value = new StringBuilder();
            for (var column : table.getColumns()) {
                Object cell = row.get(column.getName());
                String text = cell == null ? "NULL" : cell.getClass().getName() + ":"
                        + (cell instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : cell);
                value.append(text.length()).append(':').append(text);
            }
            rows.add(value.toString());
        }
        rows.sort(String::compareTo);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(columns(table).toString().getBytes(StandardCharsets.UTF_8));
        for (String row : rows) {
            digest.update((row.length() + ":" + row).getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void accounts(Database main, Database source) throws Exception {
        Map<Object, Row> target = index(main.getTable("tblUser"), "userId");
        for (Row row : source.getTable("tblUser")) {
            Row match = target.get(row.get("userId"));
            if (match == null || !Objects.equals(match.get("loginId"), row.get("loginId"))) {
                throw new IllegalStateException("Account identity mismatch: " + row.get("userId"));
            }
        }
    }

    static Map<Object, Row> index(Table table, String column) {
        Map<Object, Row> result = new java.util.HashMap<>();
        if (table != null) {
            for (Row row : table) {
                result.put(row.get(column), row);
            }
        }
        return result;
    }

    static void mainKeysRetained(Database main, Database source) throws Exception {
        for (String name : MainCommerceMigration.OWNED) {
            Table before = main.getTable(name);
            Table after = source.getTable(name);
            if (before == null || before.getRowCount() == 0) {
                continue;
            }
            String key = before.getPrimaryKeyIndex().getColumns().getFirst().getName();
            Map<Object, Row> retained = index(after, key);
            for (Row row : before) {
                if (!retained.containsKey(row.get(key))) {
                    throw new IllegalStateException("Source omits main history: " + name + " " + row.get(key));
                }
            }
        }
    }

    static String verify(Path main, Path source, Path candidate) throws Exception {
        StringBuilder report = new StringBuilder("table\tscope\tmainCount\tsourceCount\tcandidateCount"
                + "\tmainSHA256\tsourceSHA256\tcandidateSHA256\n");
        try (Database m = open(main, true); Database s = open(source, true);
                Database c = open(candidate, true)) {
            accounts(m, s);
            mainKeysRetained(m, s);
            for (String name : c.getTableNames().stream().sorted().toList()) {
                Table before = m.getTable(name);
                Table incoming = s.getTable(name);
                Table actual = c.getTable(name);
                boolean owned = MainCommerceMigration.OWNED.contains(name);
                String expected = hash(owned ? incoming : before);
                if (!expected.equals(hash(actual))) {
                    throw new IllegalStateException("Record preservation mismatch: " + name);
                }
                report.append(name).append('\t').append(owned ? "commerce-source" : "main-preserved")
                        .append('\t').append(count(before)).append('\t').append(count(incoming))
                        .append('\t').append(count(actual)).append('\t').append(hash(before))
                        .append('\t').append(hash(incoming)).append('\t').append(hash(actual)).append('\n');
            }
            for (String name : m.getTableNames()) {
                if (c.getTable(name) == null) {
                    throw new IllegalStateException("Main table lost: " + name);
                }
            }
            for (String name : MainCommerceMigration.OWNED) {
                if (c.getTable(name) == null) {
                    throw new IllegalStateException("Commerce table missing: " + name);
                }
            }
            CommerceIntegrity.validate(c);
        }
        for (Path p : List.of(main, source, candidate)) {
            report.append("FILE\t").append(p).append('\t').append(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)))).append('\n');
        }
        return report.toString();
    }

    static void verifyNoncommerce(Path main, Path candidate) throws Exception {
        try (Database before = open(main, true); Database after = open(candidate, true)) {
            for (String name : before.getTableNames()) {
                if (!MainCommerceMigration.OWNED.contains(name)
                        && !hash(before.getTable(name)).equals(hash(after.getTable(name)))) {
                    throw new IllegalStateException("Noncommerce preservation mismatch: " + name);
                }
            }
            for (String name : after.getTableNames()) {
                if (!MainCommerceMigration.OWNED.contains(name) && before.getTable(name) == null) {
                    throw new IllegalStateException("Unexpected noncommerce table: " + name);
                }
            }
            CommerceIntegrity.validate(after);
        }
    }
    private static int count(Table table) {
        return table == null ? 0 : table.getRowCount();
    }
}
