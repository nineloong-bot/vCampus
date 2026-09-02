package edu.seu.vcampus.server.user.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDistributionAccountsTest {
    @Test
    void distributionDatabaseContainsTheVerifiedUnifiedCampusAccounts() throws Exception {
        Path database = distributionDatabase();
        assertThat(database).isRegularFile().isNotEmptyFile();
        Map<String, Expected> expected = new LinkedHashMap<>();
        expected.put("ADMIN", new Expected("ADMIN", true, "admin123".toCharArray()));
        expected.put("TEACHER01", new Expected("TEACHER", false, "admin123".toCharArray()));
        expected.put("213230001", new Expected("STUDENT", false, "admin123".toCharArray()));
        expected.put("SHOPOWNER", new Expected("STUDENT", false, "admin123".toCharArray()));
        expected.put("SHOPDRAFT", new Expected("STUDENT", false, "admin123".toCharArray()));
        expected.put("SHOPPENDING", new Expected("STUDENT", false, "admin123".toCharArray()));
        PasswordHasher hasher = new PasswordHasher();
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true")) {
            for (var entry : expected.entrySet()) {
                try (var statement = connection.prepareStatement("""
                        SELECT passwordHash, passwordSalt, passwordIterations,
                               roleCode, accountStatus, mustChangePassword
                        FROM tblUser WHERE loginId=?
                        """)) {
                    statement.setString(1, entry.getKey());
                    try (var row = statement.executeQuery()) {
                        assertThat(row.next()).as("demo account %s", entry.getKey()).isTrue();
                        Expected account = entry.getValue();
                        assertThat(row.getString("roleCode")).isEqualTo(account.role());
                        assertThat(row.getString("accountStatus")).isEqualTo("ACTIVE");
                        assertThat(row.getBoolean("mustChangePassword"))
                                .isEqualTo(account.mustChangePassword());
                        assertThat(hasher.verify(account.password(),
                                row.getString("passwordHash"), row.getString("passwordSalt"),
                                row.getInt("passwordIterations"))).isTrue();
                    }
                }
            }
        } finally {
            expected.values().forEach(value -> Arrays.fill(value.password(), '\0'));
        }
    }

    private static Path distributionDatabase() {
        Path fromModule = Path.of("..", "vcampus-distribution", "data", "vCampus.accdb");
        return Files.exists(fromModule) ? fromModule.toAbsolutePath()
                : Path.of("vcampus-distribution", "data", "vCampus.accdb").toAbsolutePath();
    }

    private record Expected(String role, boolean mustChangePassword, char[] password) { }
}
