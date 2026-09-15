package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.GradeResult;
import edu.seu.vcampus.common.student.RecordStudentGradeCommand;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;
import edu.seu.vcampus.server.student.repository.*;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the student grade service scope contract. */
class StudentGradeServiceScopeTest {
    @Test
    void gradeReadsAndWritesStayInsideTrustedDepartment() throws Exception {
        var database = new StudentAccessTestDatabase();
        var students = new StudentRepository();
        var plans = new TrainingPlanRepository();
        database.transactions().inTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE tblTrainingPlan (planId VARCHAR(36) PRIMARY KEY, majorId VARCHAR(36) NOT NULL, enrollmentYear LONG NOT NULL, planName VARCHAR(128) NOT NULL, minElectiveCount LONG NOT NULL, minElectiveCredits DECIMAL(4,1) NOT NULL, isActive BOOLEAN NOT NULL, rowVersion LONG NOT NULL, createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
                statement.execute("CREATE TABLE tblTrainingPlanCourse (planCourseId VARCHAR(36) PRIMARY KEY, planId VARCHAR(36) NOT NULL, courseCode VARCHAR(16) NOT NULL, courseName VARCHAR(64) NOT NULL, credits DECIMAL(4,1) NOT NULL, courseType VARCHAR(16) NOT NULL, semester LONG NOT NULL, isActive BOOLEAN NOT NULL, rowVersion LONG NOT NULL, createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL, courseId VARCHAR(36), offeringDepartmentId VARCHAR(36), offeringDepartmentName VARCHAR(64), allocatedQuota LONG)");
                statement.execute("CREATE TABLE tblStudentGrade (gradeId VARCHAR(36) PRIMARY KEY, studentId VARCHAR(36) NOT NULL, planCourseId VARCHAR(36) NOT NULL, result VARCHAR(8) NOT NULL, recordedSemester VARCHAR(16), operatorUserId VARCHAR(36) NOT NULL, rowVersion LONG NOT NULL, createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
            }
            StudentFixtures.insertOrganization(connection, new AccessOrganizationRepository());
            students.insert(connection, StudentProfileUpdateTest.student(StudentStatus.ACTIVE));
            Instant now = Instant.now();
            plans.insert(connection, new TrainingPlan("plan-1", "major-1", 2024,
                    "2024级培养方案", 0, BigDecimal.ZERO, true, 0, now, now));
            plans.insertCourse(connection, new TrainingPlanCourse("course-1", "plan-1",
                    "CS001", "程序设计", new BigDecimal("4.0"), CourseType.REQUIRED,
                    1, true, 0, now, now));
            return null;
        });
        StudentGradeService service = new StudentGradeServiceImpl(database.transactions(),
                new StripedResourceLockManager(), new StudentGradeRepository(), plans, students);
        var command = new RecordStudentGradeCommand("student-1", "course-1",
                GradeResult.PASSED, "2024-2025-1");

        assertThatThrownBy(() -> service.recordGrade(command, "admin", "department-else"))
                .hasMessage("COMMON_FORBIDDEN");
        assertThat(service.recordGrade(command, "admin", "department-1").studentId())
                .isEqualTo("student-1");
        assertThatThrownBy(() -> service.getTranscriptByStudentId(
                "student-1", "department-else")).hasMessage("COMMON_FORBIDDEN");
    }
}
