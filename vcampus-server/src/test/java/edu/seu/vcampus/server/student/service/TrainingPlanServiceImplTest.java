package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.ImportTrainingPlanCoursesCommand;
import edu.seu.vcampus.common.student.SaveTrainingPlanCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
                statement.execute("CREATE UNIQUE INDEX uk_training_plan_major_year "
                        + "ON tblTrainingPlan (majorId, enrollmentYear)");
                statement.execute("CREATE UNIQUE INDEX uk_training_plan_course_code "
                        + "ON tblTrainingPlanCourse (planId, courseCode)");
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
}
