package edu.seu.vcampus.server.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyDistributionDataTest {
    @TempDir
    Path tempDir;

    @Test
    void releaseDatabaseContainsRebuiltDataset() throws Exception {
        Path source = Path.of("../vcampus-distribution/data/vCampus.accdb");
        Path database = Files.copy(source, tempDir.resolve("vCampus.accdb"));
            String url = "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true";
        try (var conn = DriverManager.getConnection(url);
             var stmt = conn.createStatement()) {
                assertCount(stmt, "tblStudent", 120);
                assertCount(stmt, "tblCourseOffering", 208);
                assertCount(stmt, "tblEnrollment", 0);
                assertCount(stmt, "tblMajorTransferApplication", 5);
                assertCount(stmt, "tblShop", 6);
                assertCount(stmt, "tblProduct", 72);
                assertCount(stmt, "tblOrder", 20);
                try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM tblBookLoan "
                        + "WHERE loanStatus='OVERDUE' AND dueAt<NOW()")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
        }
    }

    private static void assertCount(java.sql.Statement statement, String table, int expected)
            throws Exception {
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).as(table).isEqualTo(expected);
        }
    }
}
