package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.ImportTrainingPlanCoursesCommand;
import edu.seu.vcampus.common.student.SaveTrainingPlanCommand;
import edu.seu.vcampus.common.student.SaveTrainingPlanCourseCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrainingPlanServiceImplTest {
    private StudentAccessTestDatabase database;
    private TrainingPlanServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        database.transactions().inTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE tblTrainingPlan ("
                        + "planId VARCHAR(36) PRIMARY KEY, majorId VARCHAR(36) NOT NULL, "
                        + "enrollmentYear LONG NOT NULL, planName VARCHAR(128) NOT NULL, "
                        + "minElectiveCount LONG NOT NULL, minElectiveCredits DECIMAL(4,1) NOT NULL, "
                        + "isActive BOOLEAN NOT NULL, rowVersion LONG NOT NULL, "
                        + "createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
                statement.execute("CREATE TABLE tblTrainingPlanCourse ("
                        + "planCourseId VARCHAR(36) PRIMARY KEY, planId VARCHAR(36) NOT NULL, "
                        + "courseCode VARCHAR(16) NOT NULL, courseName VARCHAR(64) NOT NULL, "
                        + "credits DECIMAL(4,1) NOT NULL, courseType VARCHAR(16) NOT NULL, "
                        + "semester LONG NOT NULL, isActive BOOLEAN NOT NULL, rowVersion LONG NOT NULL, "
                        + "createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
                statement.execute("CREATE TABLE tblStudentGrade ("
                        + "gradeId VARCHAR(36) PRIMARY KEY, studentId VARCHAR(36) NOT NULL, "
                        + "planCourseId VARCHAR(36) NOT NULL, result VARCHAR(8) NOT NULL, "
                        + "recordedSemester VARCHAR(16), operatorUserId VARCHAR(36) NOT NULL, "
                        + "rowVersion LONG NOT NULL, createdAt DATETIME NOT NULL, "
                        + "updatedAt DATETIME NOT NULL)");
            }
            return null;
        });
        var organizations = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            organizations.insertDepartment(connection,
                    new Department("department-1", "CS", "计算机学院", true, 0));
            organizations.insertMajor(connection,
                    new Major("major-1", "department-1", "090", "计算机科学", "1,2,3,4", true, 0));
            return null;
        });
        service = new TrainingPlanServiceImpl(database.transactions(), new StripedResourceLockManager(),
                new TrainingPlanRepository(), new StudentRepository(), organizations);
    }

    @Test
    void newPlanPersistsItsConfiguredMinimumElectiveCount() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 3, new BigDecimal("18.0"), true, 0), "admin");

        assertThat(plan.minElectiveCount()).isEqualTo(3);
        assertThat(service.getPlan(plan.planId()).minElectiveCount()).isEqualTo(3);
    }

    @Test
    void importRejectsCourseWithNonPositiveCreditsWithoutWritingIt() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0), "admin");

        assertThatIllegalArgumentException().isThrownBy(() -> service.importCourses(
                new ImportTrainingPlanCoursesCommand(plan.planId(), List.of(
                        new ImportTrainingPlanCoursesCommand.CourseEntry("CS001", "非法课程",
                                new BigDecimal("-1.0"), CourseType.REQUIRED, 1))), "admin"));
        assertThat(service.getPlan(plan.planId()).courses()).isEmpty();
    }

    @Test
    void rejectsDuplicateCourseCodesWhenDatabaseHasNoSecondaryIndexes() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0), "admin");
        service.saveCourse(courseCommand(plan.planId(), "CS001"), "admin");

        assertThatThrownBy(() -> service.saveCourse(courseCommand(plan.planId(), "CS001"), "admin"))
                .isInstanceOfSatisfying(TrainingPlanException.class,
                        error -> assertThat(error.code()).isEqualTo("TRAINING_PLAN_COURSE_DUPLICATE"));
        assertThat(service.getPlan(plan.planId()).courses()).hasSize(1);
    }

    @Test
    void refusesToRemoveCourseReferencedByAStudentGrade() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0), "admin");
        var course = service.saveCourse(courseCommand(plan.planId(), "CS001"), "admin");
        database.transactions().inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblStudentGrade (gradeId, studentId, planCourseId, result, "
                            + "recordedSemester, operatorUserId, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, 'PASSED', '2024-1', 'admin', 0, NOW(), NOW())")) {
                statement.setString(1, "grade-1");
                statement.setString(2, "student-1");
                statement.setString(3, course.planCourseId());
                statement.executeUpdate();
            }
            return null;
        });

        assertThatThrownBy(() -> service.removeCourse(course.planCourseId(), "admin"))
                .isInstanceOfSatisfying(TrainingPlanException.class,
                        error -> assertThat(error.code()).isEqualTo("TRAINING_PLAN_COURSE_IN_USE"));
        assertThat(service.getPlan(plan.planId()).courses()).hasSize(1);
    }

    private static SaveTrainingPlanCourseCommand courseCommand(String planId, String code) {
        return new SaveTrainingPlanCourseCommand(planId, null, code, "数据结构",
                new BigDecimal("3.0"), CourseType.REQUIRED, 2, true, 0);
    }
}
