package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.course.composition.CourseSchemaInitializer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import io.github.spannm.jackcess.Database;
import net.ucanaccess.jdbc.UcanaccessConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Installs the common, user, role seed, and course database resources in dependency order. */
public final class ApplicationSchemaInitializer {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+([A-Za-z0-9_]+)");
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+([A-Za-z0-9_]+)\\s+ON\\s+([A-Za-z0-9_]+)");
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\s+([A-Za-z0-9_]+)");
    private static final Pattern INSERT = Pattern.compile(
            "(?is)^\\s*INSERT\\s+INTO\\s+([A-Za-z0-9_]+)\\s*\\((.*?)\\)\\s*VALUES\\s*\\((.*)\\)\\s*$");
    private static final Map<String, List<String>> SEED_KEYS = Map.ofEntries(
            Map.entry("tblrole", List.of("roleCode")),
            Map.entry("tbluser", List.of("userId")),
            Map.entry("tblpermission", List.of("permissionCode")),
            Map.entry("tblrolepermission", List.of("roleCode", "permissionCode")),
            Map.entry("tbldepartment", List.of("departmentId")),
            Map.entry("tblmajor", List.of("majorId")),
            Map.entry("tblclass", List.of("classId")),
            Map.entry("tblnumbersequence", List.of("sequenceKey")),
            Map.entry("tblstudent", List.of("studentId")),
            Map.entry("tbllibrarypolicy", List.of("policyId")),
            Map.entry("tblterm", List.of("termId")),
            Map.entry("tblcourse", List.of("courseId")),
            Map.entry("tblcourseoffering", List.of("offeringId")),
            Map.entry("tblcourseschedule", List.of("scheduleId")),
            Map.entry("tblenrollment", List.of("enrollmentId")),
            Map.entry("tblbook", List.of("bookId")),
            Map.entry("tblbookcopy", List.of("copyId")),
            Map.entry("tblbookloan", List.of("loanId")),
            Map.entry("tblsellerapplication", List.of("applicationId")),
            Map.entry("tblshop", List.of("shopId")),
            Map.entry("tblproduct", List.of("productId")),
            Map.entry("tblproductsku", List.of("skuId")),
            Map.entry("tblcart", List.of("cartId")),
            Map.entry("tblcartitem", List.of("cartItemId")),
            Map.entry("tblordergroup", List.of("orderGroupId")),
            Map.entry("tblorder", List.of("orderId")),
            Map.entry("tblorderitem", List.of("orderItemId")),
            Map.entry("tblpayment", List.of("paymentId")));

    private final Path resourceRoot;

    /** Creates an initializer for a root that contains {@code schema/} and {@code seed/}. */
    public ApplicationSchemaInitializer(Path resourceRoot) {
        this.resourceRoot = Objects.requireNonNull(resourceRoot, "resourceRoot").toAbsolutePath().normalize();
    }

    /** Repeatedly safe installer for all module schemas and the unified manual-test dataset. */
    public void initialize(ConnectionProvider connections) throws IOException, SQLException {
        Objects.requireNonNull(connections, "connections");
        installSchema(connections, schema("001_common.sql"));
        installSchema(connections, schema("010_user.sql"));
        installSchema(connections, schema("020_student.sql"));
        new CourseSchemaInitializer(schema("030_course.sql")).initialize(connections);
        installSchema(connections, schema("040_library.sql"));
        installSchema(connections, schema("050_shop.sql"));
        installSeeds(connections, seed("010_roles_permissions.sql"));
        installSeeds(connections, seed("020_test_accounts.sql"));
        installSeeds(connections, seed("040_library_policy.sql"));
        installSeeds(connections, seed("060_unified_demo_data.sql"));
    }

    private Path schema(String name) throws IOException {
        return required(resourceRoot.resolve("schema").resolve(name));
    }

    private Path seed(String name) throws IOException {
        return required(resourceRoot.resolve("seed").resolve(name));
    }

    private static Path required(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Database resource is missing: " + file);
        }
        return file;
    }

    private static void installSchema(ConnectionProvider connections, Path script)
            throws IOException, SQLException {
        try (Connection connection = connections.open()) {
            Set<String> tables = tableNames(connection);
            Set<String> preexistingTables = Set.copyOf(tables);
            Set<String> indexes = indexNames(connection);
            for (String sql : statements(script)) {
                Matcher table = CREATE_TABLE.matcher(sql);
                if (table.find() && tables.contains(normalize(table.group(1)))) continue;
                Matcher index = CREATE_INDEX.matcher(sql);
                if (index.find() && indexes.contains(normalize(index.group(1)))) continue;
                Matcher alter = ALTER_TABLE.matcher(sql);
                if (alter.find() && preexistingTables.contains(normalize(alter.group(1)))) continue;
                Matcher insert = INSERT.matcher(sql);
                if (insert.matches() && SEED_KEYS.containsKey(normalize(insert.group(1)))
                        && seedExists(connection, sql)) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                if (table.find(0)) tables.add(normalize(table.group(1)));
                if (index.find(0)) indexes.add(normalize(index.group(1)));
            }
        }
    }

    private static void installSeeds(ConnectionProvider connections, Path script)
            throws IOException, SQLException {
        try (Connection connection = connections.open()) {
            for (String sql : statements(script)) {
                Matcher insert = INSERT.matcher(sql);
                if (!insert.matches() || !seedExists(connection, sql)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
        }
    }

    private static boolean seedExists(Connection connection, String sql) throws SQLException {
        Matcher insert = INSERT.matcher(sql);
        if (!insert.matches()) throw new SQLException("Unsupported seed statement: " + sql);
        String table = insert.group(1);
        List<String> keys = SEED_KEYS.get(normalize(table));
        if (keys == null) throw new SQLException("No idempotency key configured for seed table: " + table);
        List<String> columns = splitValues(insert.group(2));
        List<String> values = splitValues(insert.group(3));
        StringBuilder query = new StringBuilder("SELECT 1 FROM ").append(table).append(" WHERE ");
        List<String> keyValues = new ArrayList<>();
        for (String key : keys) {
            int column = indexOf(columns, key);
            if (column < 0 || column >= values.size()) {
                throw new SQLException("Seed does not provide key " + key + " for " + table);
            }
            if (!keyValues.isEmpty()) query.append(" AND ");
            query.append(key).append(" = ?");
            keyValues.add(literal(values.get(column)));
        }
        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            for (int index = 0; index < keyValues.size(); index++) {
                statement.setString(index + 1, keyValues.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static int indexOf(List<String> columns, String sought) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).strip().equalsIgnoreCase(sought)) return index;
        }
        return -1;
    }

    private static String literal(String value) throws SQLException {
        String stripped = value.strip();
        if (stripped.length() >= 2 && stripped.startsWith("'") && stripped.endsWith("'")) {
            return stripped.substring(1, stripped.length() - 1).replace("''", "'");
        }
        throw new SQLException("Seed key must be a SQL string literal: " + value);
    }

    private static List<String> statements(Path script) throws IOException {
        List<String> statements = new ArrayList<>();
        for (String sql : Files.readString(script).split(";")) {
            if (!sql.isBlank()) statements.add(sql.strip());
        }
        return statements;
    }

    private static List<String> splitValues(String values) {
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

    private static Set<String> tableNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) names.add(normalize(tables.getString("TABLE_NAME")));
        }
        return names;
    }

    private static Set<String> indexNames(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try {
            Database database = ((UcanaccessConnection) connection).getDbIO();
            for (String table : database.getTableNames()) {
                for (var index : database.getTable(table).getIndexes()) names.add(normalize(index.getName()));
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
