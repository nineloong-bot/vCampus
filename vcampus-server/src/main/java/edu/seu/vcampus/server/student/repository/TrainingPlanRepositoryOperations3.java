package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;
import edu.seu.vcampus.common.student.TrainingPlanSummary;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Implements a focused group of TrainingPlanRepository persistence operations. */
abstract class TrainingPlanRepositoryOperations3 extends TrainingPlanRepositoryPersistenceSupport {

    public Optional<TrainingPlanCourse> findCourseByPlanAndCode(Connection connection,
            String planId, String courseCode) {
        String sql = "SELECT planCourseId, planId, courseCode, courseName, credits, courseType, "
                + "semester, isActive, rowVersion, createdAt, updatedAt, "
                + "courseId, offeringDepartmentId, offeringDepartmentName, allocatedQuota "
                + "FROM tblTrainingPlanCourse WHERE planId = ? AND courseCode = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, planId);
            statement.setString(2, courseCode);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapCourse(result)) : Optional.empty();
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot read training plan course", error);
        }
    }

    public boolean hasGradesForCourse(Connection connection, String planCourseId) {
        String sql = "SELECT COUNT(*) FROM tblStudentGrade WHERE planCourseId = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, planCourseId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) > 0;
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot inspect training plan course grades", error);
        }
    }

    public void insertCourse(Connection connection, TrainingPlanCourse course) {
        String sql = "INSERT INTO tblTrainingPlanCourse (planCourseId, planId, courseCode, "
                + "courseName, credits, courseType, semester, isActive, rowVersion, createdAt, updatedAt, "
                + "courseId, offeringDepartmentId, offeringDepartmentName, allocatedQuota) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.planCourseId());
            statement.setString(2, course.planId());
            statement.setString(3, course.courseCode());
            statement.setString(4, course.courseName());
            statement.setBigDecimal(5, course.credits());
            statement.setString(6, TrainingPlanCourseTypeCodec.store(course.courseType()));
            statement.setInt(7, course.semester());
            statement.setBoolean(8, course.active());
            statement.setLong(9, course.rowVersion());
            statement.setTimestamp(10, Timestamp.from(course.createdAt()));
            statement.setTimestamp(11, Timestamp.from(course.updatedAt()));
            statement.setString(12, course.courseId());
            statement.setString(13, course.offeringDepartmentId());
            statement.setString(14, course.offeringDepartmentName());
            if (course.allocatedQuota() != null) statement.setInt(15, course.allocatedQuota());
            else statement.setNull(15, java.sql.Types.INTEGER);
            statement.executeUpdate();
        } catch (java.sql.SQLException error) {
            if (error.getErrorCode() == 19 || error.getMessage() != null
                    && error.getMessage().contains("UNIQUE constraint failed")
                    && error.getMessage().contains("courseCode")) {
                throw new TrainingPlanException("TRAINING_PLAN_COURSE_DUPLICATE",
                        "课程代码 " + course.courseCode() + " 在该方案中已存在");
            }
            throw new OrganizationPersistenceException("Cannot insert training plan course", error);
        }
    }

    public void updateCourse(Connection connection, TrainingPlanCourse course,
            long expectedVersion) {
        String sql = "UPDATE tblTrainingPlanCourse SET courseCode=?, courseName=?, credits=?, "
                + "courseType=?, semester=?, isActive=?, rowVersion=rowVersion+1, updatedAt=?, "
                + "courseId=?, offeringDepartmentId=?, offeringDepartmentName=?, allocatedQuota=? "
                + "WHERE planCourseId=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.courseCode());
            statement.setString(2, course.courseName());
            statement.setBigDecimal(3, course.credits());
            statement.setString(4, TrainingPlanCourseTypeCodec.store(course.courseType()));
            statement.setInt(5, course.semester());
            statement.setBoolean(6, course.active());
            statement.setTimestamp(7, Timestamp.from(course.updatedAt()));
            statement.setString(8, course.courseId());
            statement.setString(9, course.offeringDepartmentId());
            statement.setString(10, course.offeringDepartmentName());
            if (course.allocatedQuota() != null) statement.setInt(11, course.allocatedQuota());
            else statement.setNull(11, java.sql.Types.INTEGER);
            statement.setString(12, course.planCourseId());
            statement.setLong(13, expectedVersion);
            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Training plan course version changed");
        } catch (java.sql.SQLException error) {
            if (error.getErrorCode() == 19 || error.getMessage() != null
                    && error.getMessage().contains("UNIQUE constraint failed")
                    && error.getMessage().contains("courseCode")) {
                throw new TrainingPlanException("TRAINING_PLAN_COURSE_DUPLICATE",
                        "课程代码 " + course.courseCode() + " 在该方案中已存在");
            }
            throw new OrganizationPersistenceException("Cannot update training plan course", error);
        }
    }

    public void deleteCourse(Connection connection, String planCourseId) {
        String sql = "DELETE FROM tblTrainingPlanCourse WHERE planCourseId = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, planCourseId);
            statement.executeUpdate();
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot delete training plan course", error);
        }
    }
}
