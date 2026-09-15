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
abstract class TrainingPlanRepositoryOperations2 extends TrainingPlanRepositoryOperations3 {

    /** Searches training plans restricted to a trusted department. */
    public List<TrainingPlanSummary> search(Connection connection, String majorId,
            Integer enrollmentYear, int offset, int limit, String departmentId) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.planId, p.majorId, m.majorName, d.departmentName, p.enrollmentYear, "
                + "p.planName, p.minElectiveCount, p.minElectiveCredits, p.isActive, p.rowVersion, "
                + "(SELECT COUNT(*) FROM tblTrainingPlanCourse c WHERE c.planId = p.planId AND c.isActive = TRUE) AS courseCount, "
                + "(SELECT IIF(SUM(c2.credits) IS NULL, 0, SUM(c2.credits)) FROM tblTrainingPlanCourse c2 "
                + "WHERE c2.planId = p.planId AND c2.isActive = TRUE AND c2.courseType = 'REQUIRED') AS totalRequiredCredits, "
                + "(SELECT IIF(SUM(c3.credits) IS NULL, 0, SUM(c3.credits)) FROM tblTrainingPlanCourse c3 "
                + "WHERE c3.planId = p.planId AND c3.isActive = TRUE AND c3.courseType = 'ELECTIVE') AS totalElectiveCredits "
                + "FROM (tblTrainingPlan p INNER JOIN tblMajor m ON p.majorId = m.majorId) "
                + "INNER JOIN tblDepartment d ON m.departmentId = d.departmentId WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (departmentId != null) {
            sql.append(" AND m.departmentId = ?");
            params.add(departmentId);
        }
        if (majorId != null && !majorId.isBlank()) {
            sql.append(" AND p.majorId = ?");
            params.add(majorId);
        }
        if (enrollmentYear != null) {
            sql.append(" AND p.enrollmentYear = ?");
            params.add(enrollmentYear);
        }
        sql.append(" ORDER BY p.enrollmentYear DESC, m.majorCode");
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String s) statement.setString(i + 1, s);
                else if (param instanceof Integer n) statement.setInt(i + 1, n);
                else statement.setInt(i + 1, (int) param);
            }
            try (var result = statement.executeQuery()) {
                List<TrainingPlanSummary> values = new ArrayList<>();
                while (result.next()) values.add(mapSummary(result));
                return List.copyOf(values);
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot search training plans", error);
        }
    }

    public int countSearch(Connection connection, String majorId, Integer enrollmentYear) {
        return countSearch(connection, majorId, enrollmentYear, null);
    }

    /** Counts matching training plans restricted to a trusted department. */
    public int countSearch(Connection connection, String majorId, Integer enrollmentYear,
            String departmentId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM tblTrainingPlan p INNER JOIN tblMajor m "
                        + "ON p.majorId=m.majorId WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (departmentId != null) {
            sql.append(" AND m.departmentId = ?");
            params.add(departmentId);
        }
        if (majorId != null && !majorId.isBlank()) {
            sql.append(" AND p.majorId = ?");
            params.add(majorId);
        }
        if (enrollmentYear != null) {
            sql.append(" AND p.enrollmentYear = ?");
            params.add(enrollmentYear);
        }
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String s) statement.setString(i + 1, s);
                else statement.setInt(i + 1, (Integer) param);
            }
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot count training plans", error);
        }
    }

    public List<TrainingPlanCourse> listCourses(Connection connection, String planId) {
        String sql = "SELECT planCourseId, planId, courseCode, courseName, credits, courseType, "
                + "semester, isActive, rowVersion, createdAt, updatedAt, "
                + "courseId, offeringDepartmentId, offeringDepartmentName, allocatedQuota "
                + "FROM tblTrainingPlanCourse WHERE planId = ? ORDER BY courseType, semester, courseCode";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, planId);
            try (var result = statement.executeQuery()) {
                List<TrainingPlanCourse> values = new ArrayList<>();
                while (result.next()) values.add(mapCourse(result));
                return List.copyOf(values);
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot list training plan courses", error);
        }
    }

    public Optional<TrainingPlanCourse> findCourseById(Connection connection,
            String planCourseId) {
        String sql = "SELECT planCourseId, planId, courseCode, courseName, credits, courseType, "
                + "semester, isActive, rowVersion, createdAt, updatedAt, "
                + "courseId, offeringDepartmentId, offeringDepartmentName, allocatedQuota "
                + "FROM tblTrainingPlanCourse WHERE planCourseId = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, planCourseId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapCourse(result)) : Optional.empty();
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot read training plan course", error);
        }
    }
}
