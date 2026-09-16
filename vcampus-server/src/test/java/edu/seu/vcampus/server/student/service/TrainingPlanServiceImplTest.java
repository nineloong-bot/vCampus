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
                        + "credits DECIMAL(4,1) NOT NULL, totalHours LONG, "
                        + "courseType VARCHAR(16) NOT NULL, "
                        + "semester LONG NOT NULL, isActive BOOLEAN NOT NULL, rowVersion LONG NOT NULL, "
                        + "createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL, "
                        + "courseId VARCHAR(36), offeringDepartmentId VARCHAR(36), "
                        + "offeringDepartmentName VARCHAR(128), allocatedQuota LONG)");
                statement.execute("CREATE TABLE tblStudentGrade ("
                        + "gradeId VARCHAR(36) PRIMARY KEY, studentId VARCHAR(36) NOT NULL, "
                        + "planCourseId VARCHAR(36) NOT NULL, result VARCHAR(8) NOT NULL, "
                        + "recordedSemester VARCHAR(16), operatorUserId VARCHAR(36) NOT NULL, "
                        + "rowVersion LONG NOT NULL, createdAt DATETIME NOT NULL, "
                        + "updatedAt DATETIME NOT NULL)");
                statement.execute("CREATE UNIQUE INDEX uk_training_plan_major_year "
                        + "ON tblTrainingPlan (majorId, enrollmentYear)");
                statement.execute("CREATE UNIQUE INDEX uk_training_plan_course_code "
                        + "ON tblTrainingPlanCourse (planId, courseCode)");
                statement.execute("CREATE TABLE tblCourse ("
                        + "courseId VARCHAR(36) PRIMARY KEY, courseCode VARCHAR(24) NOT NULL, "
                        + "courseName VARCHAR(128) NOT NULL, departmentId VARCHAR(36) NOT NULL, "
                        + "departmentName VARCHAR(64) NOT NULL, credit DECIMAL(4,1) NOT NULL, "
                        + "totalHours LONG NOT NULL, description VARCHAR(255), "
                        + "isActive BOOLEAN NOT NULL, rowVersion LONG NOT NULL, "
                        + "createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
                statement.execute("CREATE UNIQUE INDEX uk_tblCourse_courseCode "
                        + "ON tblCourse (courseCode)");
                statement.execute("CREATE TABLE tblCrossCourseApplication ("
                        + "applicationId VARCHAR(36) PRIMARY KEY, courseId VARCHAR(36) NOT NULL, "
                        + "courseCode VARCHAR(24) NOT NULL, courseName VARCHAR(128) NOT NULL, "
                        + "credits DECIMAL(4,1) NOT NULL, offeringDepartmentId VARCHAR(36) NOT NULL, "
                        + "offeringDepartmentName VARCHAR(64) NOT NULL, targetDepartmentId VARCHAR(36) NOT NULL, "
                        + "targetDepartmentName VARCHAR(64) NOT NULL, targetPlanId VARCHAR(36) NOT NULL, "
                        + "targetPlanName VARCHAR(128) NOT NULL, semester LONG NOT NULL, "
                        + "requestedQuota LONG NOT NULL, allocatedQuota LONG, "
                        + "applicantUserId VARCHAR(36) NOT NULL, applicantName VARCHAR(64), "
                        + "reason VARCHAR(255), status VARCHAR(16) NOT NULL, "
                        + "reviewerUserId VARCHAR(36), reviewComment VARCHAR(255), "
                        + "reviewedAt DATETIME, rowVersion LONG NOT NULL, "
                        + "createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
            }
            return null;
        });
        var organizations = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            organizations.insertDepartment(connection,
                    new Department("department-1", "CS", "计算机学院", true, 0));
            organizations.insertDepartment(connection,
                    new Department("department-2", "MATH", "数学学院", true, 0));
            organizations.insertMajor(connection,
                    new Major("major-1", "department-1", "090", "计算机科学", "1,2,3,4", true, 0));
            try (var st = connection.prepareStatement("INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, "course-math-01");
                st.setString(2, "MATH101");
                st.setString(3, "概率论与数理统计");
                st.setString(4, "department-2");
                st.setString(5, "数学学院");
                st.setBigDecimal(6, new BigDecimal("3.0"));
                st.setLong(7, 48);
                st.setString(8, "基础数学");
                st.setBoolean(9, true);
                st.setLong(10, 0);
                st.setTimestamp(11, java.sql.Timestamp.from(java.time.Instant.now()));
                st.setTimestamp(12, java.sql.Timestamp.from(java.time.Instant.now()));
                st.executeUpdate();
            }
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
    void refusesToEditHistoricalPlanWithEnrolledStudents() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0), "admin");
        database.transactions().inTransaction(connection -> {
            try (var classInsert = connection.prepareStatement(
                    "INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, TRUE, 0)");
                 var studentInsert = connection.prepareStatement(
                         "INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, 'UNDERGRADUATE', ?, '男', ?, #2024-09-01#, 'ACTIVE', 0, NOW(), NOW())")) {
                classInsert.setString(1, "class-2024");
                classInsert.setString(2, "major-1");
                classInsert.setString(3, "090-2024-01");
                classInsert.setString(4, "计算机科学2024级");
                classInsert.setInt(5, 2024);
                classInsert.setInt(6, 1);
                classInsert.executeUpdate();
                studentInsert.setString(1, "student-2024");
                studentInsert.setString(2, "user-2024");
                studentInsert.setString(3, "20240001");
                studentInsert.setString(4, "历史学生");
                studentInsert.setString(5, "class-2024");
                studentInsert.executeUpdate();
            }
            return null;
        });

        assertThatThrownBy(() -> service.savePlan(new SaveTrainingPlanCommand(plan.planId(),
                "major-1", 2024, "被禁止修改", 2, new BigDecimal("10.0"), true,
                plan.rowVersion()), "admin"))
                .isInstanceOfSatisfying(TrainingPlanException.class,
                        error -> assertThat(error.code()).isEqualTo("TRAINING_PLAN_IMMUTABLE"));
    }

    @Test
    void refusesToAddCourseToHistoricalPlanWithEnrolledStudents() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0), "admin");
        database.transactions().inTransaction(connection -> {
            try (var classInsert = connection.prepareStatement(
                    "INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, TRUE, 0)");
                 var studentInsert = connection.prepareStatement(
                         "INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, 'UNDERGRADUATE', ?, '男', ?, #2024-09-01#, 'ACTIVE', 0, NOW(), NOW())")) {
                classInsert.setString(1, "class-2024");
                classInsert.setString(2, "major-1");
                classInsert.setString(3, "090-2024-01");
                classInsert.setString(4, "计算机科学2024级");
                classInsert.setInt(5, 2024);
                classInsert.setInt(6, 1);
                classInsert.executeUpdate();
                studentInsert.setString(1, "student-2024");
                studentInsert.setString(2, "user-2024");
                studentInsert.setString(3, "20240001");
                studentInsert.setString(4, "历史学生");
                studentInsert.setString(5, "class-2024");
                studentInsert.executeUpdate();
            }
            return null;
        });

        assertThatThrownBy(() -> service.saveCourse(courseCommand(plan.planId(), "CS001"), "admin"))
                .isInstanceOfSatisfying(TrainingPlanException.class,
                        error -> assertThat(error.code()).isEqualTo("TRAINING_PLAN_IMMUTABLE"));
    }

    @Test
    void courseHoursSurviveSaveAndPlanDetailReload() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 3, new BigDecimal("18.0"), true, 0), "admin");
        var command = new SaveTrainingPlanCourseCommand(plan.planId(), null, "CS102",
                "数据结构", new BigDecimal("3.0"), 48, CourseType.REQUIRED, 2,
                true, 0, null, "department-1", "计算机学院", null);

        var saved = service.saveCourse(command, "admin");
        var reloaded = service.getPlan(plan.planId()).courses().getFirst();

        assertThat(saved.totalHours()).isEqualTo(48);
        assertThat(reloaded.totalHours()).isEqualTo(48);
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

    @Test
    void acceptsEighthSemesterAndRejectsNinthSemester() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0), "admin");
        var eighth = new SaveTrainingPlanCourseCommand(plan.planId(), null, "CS-8",
                "毕业设计", new BigDecimal("2.0"), CourseType.ELECTIVE, 8, true, 0);
        var ninth = new SaveTrainingPlanCourseCommand(plan.planId(), null, "CS-9",
                "超出学制课程", new BigDecimal("2.0"), CourseType.ELECTIVE, 9, true, 0);

        assertThat(service.saveCourse(eighth, "admin").semester()).isEqualTo(8);
        assertThatIllegalArgumentException().isThrownBy(() -> service.saveCourse(ninth, "admin"))
                .withMessage("semester must be 1-8");
        assertThatIllegalArgumentException().isThrownBy(() -> service.submitCrossCourseApplication(
                new edu.seu.vcampus.common.student.SubmitCrossCourseApplicationCommand(
                        "course-math-01", plan.planId(), 9, 20, "超出学制"), "admin"))
                .withMessage("开设学期必须在 1-8 之间");
    }

    private static SaveTrainingPlanCourseCommand courseCommand(String planId, String code) {
        return new SaveTrainingPlanCourseCommand(planId, null, code, "数据结构",
                new BigDecimal("3.0"), CourseType.REQUIRED, 2, true, 0);
    }

    @Test
    void collegeCannotCreateOrReadPlanOutsideItsDepartment() {
        var command = new SaveTrainingPlanCommand(null, "major-1", 2024,
                "2024级培养方案", 2, new BigDecimal("10.0"), true, 0);
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.savePlan(command, "admin", "department-else"))
                .withMessage("COMMON_FORBIDDEN");
        var plan = service.savePlan(command, "admin", "department-1");
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.getPlan(plan.planId(), "department-else"))
                .withMessage("COMMON_FORBIDDEN");
    }

    @Test
    void coursePoolSearchAndCrossCourseApplicationApprovalFlow() {
        // 1. List course pool
        var poolCourses = service.listCoursePool(new edu.seu.vcampus.common.student.CoursePoolQuery("department-2", "概率论"));
        assertThat(poolCourses).hasSize(1);
        assertThat(poolCourses.get(0).courseCode()).isEqualTo("MATH101");
        assertThat(poolCourses.get(0).departmentName()).isEqualTo("数学学院");

        // 2. Create target CS plan
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "计算机2024级方案", 2, new BigDecimal("8.0"), true, 0), "admin");

        // 3. Submit cross course application from CS to MATH
        var submit = new edu.seu.vcampus.common.student.SubmitCrossCourseApplicationCommand(
                "course-math-01", plan.planId(), 3, 35, "培养交叉复合型人才");
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.submitCrossCourseApplication(submit, "cs-admin", "department-2"))
                .withMessage("COMMON_FORBIDDEN");
        var app = service.submitCrossCourseApplication(submit, "cs-admin", "department-1");
        assertThat(app.applicationId()).isNotNull();
        assertThat(app.status()).isEqualTo(edu.seu.vcampus.common.student.CrossCourseApplicationStatus.PENDING);
        assertThat(app.requestedQuota()).isEqualTo(35);

        // 4. Offering department reviews and approves with 30 quota
        assertThat(service.listCrossCourseApplications(
                new edu.seu.vcampus.common.student.CrossCourseApplicationQuery(null, null, null),
                "admin", "department-else")).isEmpty();
        var review = new edu.seu.vcampus.common.student.ReviewCrossCourseApplicationCommand(
                app.applicationId(), true, 30, "同意并分配30个选课名额");
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.reviewCrossCourseApplication(review, "cs-admin", "department-1"))
                .withMessage("COMMON_FORBIDDEN");
        var reviewedApp = service.reviewCrossCourseApplication(
                review, "math-admin", "department-2");
        assertThat(reviewedApp.status()).isEqualTo(edu.seu.vcampus.common.student.CrossCourseApplicationStatus.APPROVED);
        assertThat(reviewedApp.allocatedQuota()).isEqualTo(30);

        // 5. Verify the course is automatically added to target training plan as CROSS_DISCIPLINARY
        var updatedPlan = service.getPlan(plan.planId());
        assertThat(updatedPlan.courses()).hasSize(1);
        var course = updatedPlan.courses().get(0);
        assertThat(course.courseCode()).isEqualTo("MATH101");
        assertThat(course.courseType()).isEqualTo(CourseType.CROSS_DISCIPLINARY);
        assertThat(course.offeringDepartmentName()).isEqualTo("数学学院");
        assertThat(course.allocatedQuota()).isEqualTo(30);
    }

    @Test
    void crossCourseApplicationCanBeRejectedWithReason() {
        var plan = service.savePlan(new SaveTrainingPlanCommand(null, "major-1", 2024,
                "计算机2024级方案", 2, new BigDecimal("8.0"), true, 0), "admin");

        var app = service.submitCrossCourseApplication(
                new edu.seu.vcampus.common.student.SubmitCrossCourseApplicationCommand(
                        "course-math-01", plan.planId(), 3, 50, "申请引入"),
                "cs-admin");

        var rejected = service.reviewCrossCourseApplication(
                new edu.seu.vcampus.common.student.ReviewCrossCourseApplicationCommand(
                        app.applicationId(), false, null, "本学期该课程选课容量已满"),
                "math-admin");

        assertThat(rejected.status()).isEqualTo(edu.seu.vcampus.common.student.CrossCourseApplicationStatus.REJECTED);
        assertThat(rejected.reviewComment()).isEqualTo("本学期该课程选课容量已满");

        // Course must NOT be added to target plan
        var updatedPlan = service.getPlan(plan.planId());
        assertThat(updatedPlan.courses()).isEmpty();
    }
}
