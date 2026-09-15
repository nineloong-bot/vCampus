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

/** Holds mapping and SQL support for {@link TrainingPlanRepository}. */
abstract class TrainingPlanRepositoryPersistenceSupport {

    protected TrainingPlan mapPlan(java.sql.ResultSet result) throws java.sql.SQLException {
        return new TrainingPlan(
                result.getString("planId"),
                result.getString("majorId"),
                result.getInt("enrollmentYear"),
                result.getString("planName"),
                result.getLong("minElectiveCount"),
                result.getBigDecimal("minElectiveCredits"),
                result.getBoolean("isActive"),
                result.getLong("rowVersion"),
                result.getTimestamp("createdAt").toInstant(),
                result.getTimestamp("updatedAt").toInstant());
    }

    protected TrainingPlanCourse mapCourse(java.sql.ResultSet result) throws java.sql.SQLException {
        Integer quota = result.getObject("allocatedQuota") != null ? result.getInt("allocatedQuota") : null;
        return new TrainingPlanCourse(
                result.getString("planCourseId"),
                result.getString("planId"),
                result.getString("courseCode"),
                result.getString("courseName"),
                result.getBigDecimal("credits"),
                TrainingPlanCourseTypeCodec.read(result.getString("courseType")),
                result.getInt("semester"),
                result.getBoolean("isActive"),
                result.getLong("rowVersion"),
                result.getTimestamp("createdAt").toInstant(),
                result.getTimestamp("updatedAt").toInstant(),
                result.getString("courseId"),
                result.getString("offeringDepartmentId"),
                result.getString("offeringDepartmentName"),
                quota);
    }

    protected TrainingPlanSummary mapSummary(java.sql.ResultSet result)
            throws java.sql.SQLException {
        return new TrainingPlanSummary(
                result.getString("planId"),
                result.getString("majorId"),
                result.getString("majorName"),
                result.getString("departmentName"),
                result.getInt("enrollmentYear"),
                result.getString("planName"),
                result.getLong("minElectiveCount"),
                result.getBigDecimal("minElectiveCredits"),
                result.getInt("courseCount"),
                result.getBigDecimal("totalRequiredCredits"),
                result.getBigDecimal("totalElectiveCredits"),
                result.getBoolean("isActive"),
                result.getLong("rowVersion"));
    }
}
