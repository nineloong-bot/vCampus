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
    void distributionDatabaseContainsAllVerifiedCourseDemoAccounts() throws Exception {
        Path database = distributionDatabase();
        assertThat(database).isRegularFile().isNotEmptyFile();
        Map<String, Expected> expected = new LinkedHashMap<>();
        expected.put("DEMO_ADMIN", new Expected("SUPER_ADMIN", false,
                "admin123456".toCharArray()));
        expected.put("DEMO_TEACHER", new Expected("TEACHER", false,
                "Teacher123456".toCharArray()));
        expected.put("213242478", new Expected("STUDENT", true,
                "12345678".toCharArray()));
        expected.put("STUDENT_ADMIN", manager("STUDENT_ADMIN"));
        expected.put("COURSE_ADMIN", manager("COURSE_ADMIN"));
        expected.put("LIBRARY_ADMIN", manager("LIBRARY_ADMIN"));
        expected.put("SHOP_ADMIN", manager("SHOP_ADMIN"));
        expected.put("USER_ADMIN", manager("USER_ADMIN"));
        expected.put("CS_COLLEGE_ADMIN", manager("COLLEGE_ADMIN"));
        expected.put("MATH_COLLEGE_ADMIN", manager("COLLEGE_ADMIN"));
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
                                row.getInt("passwordIterations")))
                                .as("demo password baseline %s", entry.getKey()).isTrue();
                    }
                }
            }
        } finally {
            expected.values().forEach(value -> Arrays.fill(value.password(), '\0'));
        }
    }

    private static Path distributionDatabase() {
        String override = System.getProperty("vcampus.demo.database");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath();
        Path fromModule = Path.of("..", "vcampus-distribution", "data", "vCampus.accdb");
        return Files.exists(fromModule) ? fromModule.toAbsolutePath()
                : Path.of("vcampus-distribution", "data", "vCampus.accdb").toAbsolutePath();
    }

    private static Expected manager(String role) {
        return new Expected(role, false, "admin123456".toCharArray());
    }

    private record Expected(String role, boolean mustChangePassword, char[] password) { }
}
