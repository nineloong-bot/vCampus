package edu.seu.vcampus.server.course.integration;

import edu.seu.vcampus.server.bootstrap.ApplicationSchemaInitializer;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessMajorTransferEnrollmentAdapterTest {
    private Connection connection;
    private AccessCourseRepository courses;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        Path database = data.resolve(UUID.randomUUID() + ".accdb");
        String url = "jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        new ApplicationSchemaInitializer(databaseRoot()).initialize(
                () -> DriverManager.getConnection(url));
        connection = DriverManager.getConnection(url);
        connection.setAutoCommit(false);
        courses = new AccessCourseRepository();
        seed();
    }

    @AfterEach void close() throws Exception { connection.close(); }

    @Test
    void dropsCurrentCoursesOutsideTargetPlanAndUpdatesIndependentCounters() {
        var result = new AccessMajorTransferEnrollmentAdapter().reconcile(connection,
                "mt-student", "MT-CS", 2026, "admin", Instant.parse("2026-09-10T00:00:00Z"));

        assertThat(result.droppedEnrollments()).isEqualTo(2);
        assertThat(status("mt-enrollment-keep")).isEqualTo("ACTIVE");
        assertThat(status("mt-enrollment-drop")).isEqualTo("DROPPED");
        assertThat(status("mt-enrollment-retake")).isEqualTo("DROPPED");
        assertThat(number("SELECT enrolledCount FROM tblCourseOffering WHERE offeringId='mt-offer-drop'"))
                .isZero();
        assertThat(number("SELECT enrolledCount FROM tblCourseRetakeQuota WHERE offeringId='mt-offer-retake'"))
                .isZero();
        assertThat(number("SELECT COUNT(*) FROM tblEnrollmentAdjustment WHERE studentId='mt-student' "
                + "AND adjustmentType='MAJOR_TRANSFER_AUTO_DROP' AND operationResult='SUCCEEDED'"))
                .isEqualTo(2);
    }

    private String status(String enrollmentId) {
        return text("SELECT enrollmentStatus FROM tblEnrollment WHERE enrollmentId='" + enrollmentId + "'");
    }

    private int number(String sql) {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getInt(1);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private String text(String sql) {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getString(1);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void seed() throws Exception {
        execute("INSERT INTO tblUser (userId,loginId,passwordHash,passwordSalt,passwordIterations,"+
                "roleCode,accountStatus,mustChangePassword,failedLoginCount,rowVersion,createdAt,updatedAt) " +
                "VALUES ('mt-teacher','MT_TEACHER','qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4='," +
                "'mW5pbqIFUpGT2Zlkq7TsSA==',120000,'TEACHER','ACTIVE',FALSE,0,0,NOW(),NOW())");
        execute("INSERT INTO tblDepartment (departmentId,departmentCode,departmentName,isActive,rowVersion) "
                + "VALUES ('mt-dept','MT','目标学院',TRUE,0)");
        execute("INSERT INTO tblMajor (majorId,departmentId,majorCode,majorName,isActive,rowVersion) "
                + "VALUES ('mt-major','mt-dept','MT-CS','目标专业',TRUE,0)");
        execute("INSERT INTO tblClass (classId,majorId,classCode,className,enrollmentYear,classNumber,"
                + "isActive,rowVersion) VALUES ('mt-class','mt-major','MT-26-1','目标班级',2026,1,TRUE,0)");
        execute("INSERT INTO tblStudent (studentId,userId,studentNumber,studentType,studentName,gender,"
                + "classId,enrollmentDate,studentStatus,rowVersion,createdAt,updatedAt) VALUES "
                + "('mt-student','mt-user','21326001','UNDERGRADUATE','陈思远','男','mt-class',"
                + "#2026-09-01#,'ACTIVE',0,NOW(),NOW())");
        execute("INSERT INTO tblTrainingPlan (planId,majorId,enrollmentYear,planName,minElectiveCount,"+
                "minElectiveCredits,isActive,rowVersion,createdAt,updatedAt) VALUES "
                + "('mt-plan','mt-major',2026,'目标培养方案',0,0,TRUE,0,NOW(),NOW())");
        for (String[] course : new String[][]{{"keep","保留课"},{"drop","移除课"},{"retake","移除重修"}}) {
            execute("INSERT INTO tblCourse (courseId,courseCode,courseName,credit,totalHours,isActive,"+
                    "rowVersion,createdAt,updatedAt) VALUES ('mt-course-" + course[0] + "','MT-"+
                    course[0] + "','" + course[1] + "',3,48,TRUE,0,NOW(),NOW())");
        }
        execute("INSERT INTO tblTrainingPlanCourse (planCourseId,planId,courseCode,courseName,credits,"+
                "courseType,semester,courseNature,courseCategory,offeringUnit,isActive,rowVersion,createdAt,updatedAt) "
                + "VALUES ('mt-pc','mt-plan','MT-keep','保留课',3,'REQUIRED',1,'REQUIRED','专业课','目标学院',TRUE,0,NOW(),NOW())");
        execute("INSERT INTO tblTerm (termId,termCode,termName,startDate,endDate,academicYearStart,season,"+
                "enrollmentStartAt,enrollmentEndAt,adjustmentStartAt,adjustmentEndAt,termStatus,rowVersion,createdAt,updatedAt) "
                + "VALUES ('mt-term','MT-2026-A','当前学期',#2026-09-01#,#2027-01-20#,2026,'AUTUMN',"
                + "#2026-08-01#,#2026-09-30#,#2026-10-01#,#2026-10-15#,'ACTIVE',0,NOW(),NOW())");
        offering("keep", 1); offering("drop", 1); offering("retake", 0);
        enrollment("keep", "NORMAL"); enrollment("drop", "NORMAL"); enrollment("retake", "RETAKE");
        execute("UPDATE tblCourseRetakeQuota SET enrolledCount=1 WHERE offeringId='mt-offer-retake'");
    }

    private void offering(String suffix, int normalCount) throws Exception {
        execute("INSERT INTO tblCourseOffering (offeringId,termId,courseId,teacherUserId,className,"+
                "capacity,enrolledCount,offeringStatus,rowVersion,createdAt,updatedAt) VALUES ('mt-offer-"+
                suffix + "','mt-term','mt-course-" + suffix + "','mt-teacher','"+
                suffix + "班',40," + normalCount + ",'OPEN',0,NOW(),NOW())");
        execute("INSERT INTO tblCourseRetakeQuota (offeringId,capacity,enrolledCount) VALUES "
                + "('mt-offer-" + suffix + "',5,0)");
    }

    private void enrollment(String suffix, String type) throws Exception {
        execute("INSERT INTO tblEnrollment (enrollmentId,offeringId,studentId,enrollmentType,"+
                "enrollmentStatus,enrolledAt,droppedAt,rowVersion,createdAt,updatedAt) VALUES ('mt-enrollment-"+
                suffix + "','mt-offer-" + suffix + "','mt-student','" + type + "','ACTIVE',NOW(),NULL,0,NOW(),NOW())");
    }

    private void execute(String sql) throws Exception { connection.createStatement().execute(sql); }

    private static Path databaseRoot() {
        Path direct = Path.of("vcampus-database");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database");
    }
}
