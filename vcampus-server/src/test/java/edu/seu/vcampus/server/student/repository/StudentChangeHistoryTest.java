package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentChangeHistoryTest {
    @Test
    void returnsNewestChangeFirstWithCompleteAuditData() throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new StudentChangeRepository();
        database.transactions().inTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO tblDepartment VALUES ('d1','CS','计算机学院',TRUE,0)");
                statement.execute("INSERT INTO tblMajor VALUES ('m1','d1','090','计算机科学',TRUE,0)");
                statement.execute("INSERT INTO tblClass VALUES ('c1','m1','090-24-1','计科24-1',2024,1,TRUE,0)");
                statement.execute("INSERT INTO tblStudent VALUES ('s1','u1','09024101','UNDERGRADUATE','张三','MALE',NULL,NULL,'c1',DATE(), 'ACTIVE',0,NOW(),NOW())");
            }
            repository.insertAdmission(connection, "h1", "s1", "studentNumber=09024101",
                    "admin", LocalDate.of(2024, 9, 1), Instant.parse("2024-09-01T00:00:00Z"));
            repository.insertChange(connection, "h2", "s1", "STATUS_CHANGE", "ACTIVE",
                    "SUSPENDED", "休学", "admin", LocalDate.of(2025, 3, 1),
                    Instant.parse("2025-03-01T00:00:00Z"));
            return null;
        });

        var changes = database.transactions().inTransaction(
                connection -> repository.listByStudentId(connection, "s1"));

        assertThat(changes).extracting("changeId").containsExactly("h2", "h1");
        assertThat(changes.getFirst().reason()).isEqualTo("休学");
        assertThat(changes.getFirst().effectiveDate()).isEqualTo(LocalDate.of(2025, 3, 1));
    }
}
