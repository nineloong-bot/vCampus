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
    void distributionDatabaseContainsTheDocumentedReleaseAccounts() throws Exception {
        Path database = distributionDatabase();
        assertThat(database).isRegularFile().isNotEmptyFile();
        Map<String, Expected> expected = new LinkedHashMap<>();
        expected.put("ADMIN", manager("SUPER_ADMIN"));
        expected.put("STUDENT", manager("STUDENT_ADMIN"));
        expected.put("COURSE", manager("COURSE_ADMIN"));
        expected.put("LIBRARY", manager("LIBRARY_ADMIN"));
        expected.put("SHOP", manager("SHOP_ADMIN"));
        expected.put("USER", manager("USER_ADMIN"));
        expected.put("CSADMIN", manager("COLLEGE_ADMIN"));
        expected.put("MATHADMIN", manager("COLLEGE_ADMIN"));
        expected.put("T001", manager("TEACHER"));
        expected.put("213240001", new Expected("STUDENT", true,
                "123456".toCharArray()));
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
            try (var statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM tblUser u INNER JOIN tblStudent s ON u.userId=s.userId
                    WHERE u.loginId=?
                    """)) {
                statement.setString(1, "213240001");
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getInt(1)).as("student demo account profile").isEqualTo(1);
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
        return new Expected(role, false, "123456".toCharArray());
    }

    private record Expected(String role, boolean mustChangePassword, char[] password) { }
}
