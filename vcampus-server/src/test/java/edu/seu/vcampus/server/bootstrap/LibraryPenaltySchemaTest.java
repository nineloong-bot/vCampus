package edu.seu.vcampus.server.bootstrap;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class LibraryPenaltySchemaTest {
    @Test void resumesAnUpgradeInterruptedAfterRoleColumnWasAdded() throws Exception {
        var file = Files.createTempDirectory("library-penalty-retry-").resolve("old.accdb");
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + file + ";newDatabaseVersion=V2010")) {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE tblLibraryPolicy (roleCode VARCHAR(16))");
                sql.execute("CREATE TABLE tblUser (userId VARCHAR(36), roleCode VARCHAR(16))");
                sql.execute("INSERT INTO tblUser VALUES ('teacher-1', 'TEACHER')");
                sql.execute("CREATE TABLE tblBookLoan (loanId VARCHAR(36), borrowerUserId VARCHAR(36), loanStatus VARCHAR(16))");
                sql.execute("INSERT INTO tblBookLoan VALUES ('loan-1', 'teacher-1', 'LOST')");
                sql.execute("ALTER TABLE tblBookLoan ADD COLUMN borrowerRoleCode VARCHAR(16)");
            }
            LibraryPenaltySchema.initialize(connection);
            try (var sql = connection.createStatement(); var result = sql.executeQuery("SELECT * FROM tblBookLoan")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("borrowerRoleCode")).isEqualTo("TEACHER");
                assertThat(result.getString("returnCondition")).isEqualTo("LOST");
            }
            // Completed snapshots must survive later changes to the user's role.
            try (var sql = connection.createStatement()) { sql.execute("UPDATE tblUser SET roleCode = 'STUDENT'"); }
            LibraryPenaltySchema.initialize(connection);
            try (var sql = connection.createStatement(); var result = sql.executeQuery("SELECT borrowerRoleCode FROM tblBookLoan")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("TEACHER");
            }
        }
    }

    @Test void upgradesExistingRecordsIdempotentlyWithoutInventingHistoricalFines() throws Exception {
        var file = Files.createTempDirectory("library-penalty-").resolve("old.accdb");
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + file + ";newDatabaseVersion=V2010")) {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE tblLibraryPolicy (roleCode VARCHAR(16), maxActiveLoans LONG)");
                sql.execute("INSERT INTO tblLibraryPolicy VALUES ('TEACHER', 12)");
                sql.execute("CREATE TABLE tblUser (userId VARCHAR(36), roleCode VARCHAR(16))");
                sql.execute("INSERT INTO tblUser VALUES ('teacher-1', 'TEACHER')");
                sql.execute("CREATE TABLE tblBookLoan (loanId VARCHAR(36), borrowerUserId VARCHAR(36), loanStatus VARCHAR(16))");
                sql.execute("INSERT INTO tblBookLoan VALUES ('loan-1', 'teacher-1', 'LOST')");
            }
            LibraryPenaltySchema.initialize(connection);
            try (var sql = connection.createStatement()) { sql.execute("UPDATE tblLibraryPolicy SET lostFine = 88"); }
            LibraryPenaltySchema.initialize(connection);
            try (var sql = connection.createStatement(); var result = sql.executeQuery("SELECT * FROM tblLibraryPolicy")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("maxActiveLoans")).isEqualTo(12);
                assertThat(result.getBigDecimal("lostFine")).isEqualByComparingTo("88");
            }
            try (var sql = connection.createStatement(); var result = sql.executeQuery("SELECT * FROM tblBookLoan")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("borrowerRoleCode")).isEqualTo("TEACHER");
                assertThat(result.getString("returnCondition")).isEqualTo("LOST");
                assertThat(result.getBigDecimal("overdueFine")).isEqualByComparingTo("0");
                assertThat(result.getBigDecimal("damageFine")).isEqualByComparingTo("0");
            }
        }
    }
}
