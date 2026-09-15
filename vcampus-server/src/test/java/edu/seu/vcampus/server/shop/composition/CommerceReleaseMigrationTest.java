package edu.seu.vcampus.server.shop.composition;

import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in migration acceptance against an isolated copy of the release Access database.
 * The result manifest is vcampus-server/target/commerce-release-validation/manifest.properties.
 * Enable with -Dcommerce.release.validate=true; the source database is never opened through JDBC.
 */
@EnabledIfSystemProperty(named = "commerce.release.validate", matches = "true")
class CommerceReleaseMigrationTest {
    private static final List<String> BASELINE = List.of("tblUser", "tblProduct", "tblOrder");
    private static final List<String> SCHEMAS = List.of("051_shop_wallet.sql", "052_shop_catalog.sql",
            "053_shop_orders.sql", "054_shop_governance.sql");

    @Test
    void releaseCopySurvivesTwoCanonicalRuntimeInitializations() throws Exception {
        Path repository = repositoryRoot();
        Path source = repository.resolve("vcampus-distribution/data/vCampus.accdb").toRealPath();
        Path resources = repository.resolve("vcampus-database").toRealPath();
        Path output = repository.resolve("vcampus-server/target/commerce-release-validation");
        Files.createDirectories(output);
        Path copy = output.resolve("release-copy-" + UUID.randomUUID() + ".accdb");
        Path manifest = output.resolve("manifest.properties");
        Properties result = new Properties();
        result.setProperty("status", "FAILED");
        result.setProperty("startedAt", Instant.now().toString());
        result.setProperty("source", source.toString());
        result.setProperty("copy", copy.toAbsolutePath().toString());
        result.setProperty("resources", resources.toString());
        result.setProperty("schedulerStarted", "false");
        String sourceHash = sha256(source);
        result.setProperty("source.sha256.before", sourceHash);
        try {
            assertTrue(copy.toAbsolutePath().normalize().startsWith(output.toAbsolutePath().normalize()));
            assertFalse(copy.toAbsolutePath().normalize().equals(source));
            Files.copy(source, copy);
            assertEquals(sourceHash, sha256(copy), "The validation copy must match the source snapshot");
            ConnectionProvider connections = () -> DriverManager.getConnection(
                    "jdbc:ucanaccess://" + copy.toAbsolutePath() + ";immediatelyReleaseResources=true");
            try (var validationSession = connections.open()) {
                assertFalse(validationSession.isClosed());
                Map<String, Long> baseline = counts(connections, BASELINE);
                recordCounts(result, "baseline", baseline);
                List<String> commerceTables = canonicalCommerceTables(resources);
                assertTrue(commerceTables.containsAll(List.of("tblWalletAccount", "tblProductCatalog",
                        "tblShopOrderState", "tblShopQualification")));
                Map<String, Long> previous = null;
                for (int pass = 1; pass <= 2; pass++) {
                    try (ApplicationRuntime runtime = ApplicationRuntime.create(connections, resources, Clock.systemUTC())) {
                        assertNotNull(runtime.router());
                        Map<String, Long> preserved = counts(connections, BASELINE);
                        recordCounts(result, "pass" + pass + ".baseline", preserved);
                        assertEquals(baseline, preserved, "Runtime startup must preserve release user/product/order counts");
                        Map<String, Long> migrated = counts(connections, commerceTables);
                        recordCounts(result, "pass" + pass + ".commerce", migrated);
                        if (previous != null) assertEquals(previous, migrated, "Second initialization must preserve migrated rows");
                        previous = migrated;
                    }
                    result.setProperty("pass" + pass + ".runtimeClosed", "true");
                }
                result.setProperty("source.sha256.after", sha256(source));
                assertEquals(sourceHash, result.getProperty("source.sha256.after"), "The source release file must remain unchanged");
                result.setProperty("validatedTables", String.join(",", commerceTables));
            }
            result.setProperty("status", "PASSED");
        } catch (Exception | AssertionError failure) {
            result.setProperty("status", "FAILED");
            result.setProperty("failureType", failure.getClass().getName());
            throw failure;
        } finally {
            result.setProperty("finishedAt", Instant.now().toString());
            try (var writer = Files.newBufferedWriter(manifest)) {
                result.store(writer, "Commerce release copy migration acceptance; source file is never opened by JDBC");
            }
        }
    }

    private static Map<String, Long> counts(ConnectionProvider connections, List<String> tables) throws Exception {
        Map<String, Long> values = new LinkedHashMap<>();
        try (var connection = connections.open()) {
            for (String table : tables) {
                assertTrue(table.matches("[A-Za-z][A-Za-z0-9_]*"));
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next(), "Table must be queryable: " + table);
                    values.put(table, rows.getLong(1));
                }
            }
        }
        return values;
    }

    private static List<String> canonicalCommerceTables(Path resources) throws Exception {
        var create = Pattern.compile("(?im)^\\s*CREATE\\s+TABLE\\s+([A-Za-z][A-Za-z0-9_]*)\\s*\\(");
        List<String> tables = new ArrayList<>();
        for (String schema : SCHEMAS) {
            var matches = create.matcher(Files.readString(resources.resolve("schema").resolve(schema)).replace("\uFEFF", ""));
            int before = tables.size();
            while (matches.find()) tables.add(matches.group(1));
            assertTrue(tables.size() > before, "Each commerce schema must define validated tables: " + schema);
        }
        return List.copyOf(tables);
    }

    private static void recordCounts(Properties result, String prefix, Map<String, Long> values) {
        values.forEach((table, count) -> result.setProperty(prefix + "." + table, count.toString()));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = stream.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path repositoryRoot() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isDirectory(path.resolve("vcampus-database/schema"))) return path;
        }
        throw new IllegalStateException("Canonical database resources were not found");
    }
}
