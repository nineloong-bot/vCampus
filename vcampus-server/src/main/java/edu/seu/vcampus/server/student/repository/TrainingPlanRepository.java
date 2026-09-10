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

/** Stores training plans and their courses inside caller-owned transactions. */
public final class TrainingPlanRepository {

    public Optional<TrainingPlan> findById(Connection connection, String planId) {
        String sql = "SELECT planId, majorId, enrollmentYear, planName, minElectiveCount, "
                + "minElectiveCredits, isActive, rowVersion, createdAt, updatedAt "
                + "FROM tblTrainingPlan WHERE planId = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, planId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapPlan(result)) : Optional.empty();
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot read training plan", error);
        }
    }

    public Optional<TrainingPlan> findByMajorAndYear(Connection connection, String majorId,
            int enrollmentYear) {
        String sql = "SELECT planId, majorId, enrollmentYear, planName, minElectiveCount, "
                + "minElectiveCredits, isActive, rowVersion, createdAt, updatedAt "
                + "FROM tblTrainingPlan WHERE majorId = ? AND enrollmentYear = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, majorId);
            statement.setInt(2, enrollmentYear);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapPlan(result)) : Optional.empty();
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot read training plan", error);
        }
    }

    public void insert(Connection connection, TrainingPlan plan) {
        String sql = "INSERT INTO tblTrainingPlan (planId, majorId, enrollmentYear, planName, "
                + "minElectiveCount, minElectiveCredits, isActive, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.planId());
            statement.setString(2, plan.majorId());
            statement.setInt(3, plan.enrollmentYear());
            statement.setString(4, plan.planName());
            statement.setLong(5, plan.minElectiveCount());
            statement.setBigDecimal(6, plan.minElectiveCredits());
            statement.setBoolean(7, plan.active());
            statement.setLong(8, plan.rowVersion());
            statement.setTimestamp(9, Timestamp.from(plan.createdAt()));
            statement.setTimestamp(10, Timestamp.from(plan.updatedAt()));
            statement.executeUpdate();
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot insert training plan", error);
        }
    }

    public void update(Connection connection, TrainingPlan plan, long expectedVersion) {
        String sql = "UPDATE tblTrainingPlan SET planName=?, minElectiveCount=?, "
                + "minElectiveCredits=?, isActive=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE planId=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.planName());
            statement.setLong(2, plan.minElectiveCount());
            statement.setBigDecimal(3, plan.minElectiveCredits());
            statement.setBoolean(4, plan.active());
            statement.setTimestamp(5, Timestamp.from(plan.updatedAt()));
            statement.setString(6, plan.planId());
            statement.setLong(7, expectedVersion);
            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Training plan version changed");
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot update training plan", error);
        }
    }

    public List<TrainingPlanSummary> search(Connection connection, String majorId,
            Integer enrollmentYear, int offset, int limit) {
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
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM tblTrainingPlan p WHERE 1=1");
        List<Object> params = new ArrayList<>();
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
                + "semester, isActive, rowVersion, createdAt, updatedAt "
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
                + "semester, isActive, rowVersion, createdAt, updatedAt "
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

    public void insertCourse(Connection connection, TrainingPlanCourse course) {
        String sql = "INSERT INTO tblTrainingPlanCourse (planCourseId, planId, courseCode, "
                + "courseName, credits, courseType, semester, isActive, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.planCourseId());
            statement.setString(2, course.planId());
            statement.setString(3, course.courseCode());
            statement.setString(4, course.courseName());
            statement.setBigDecimal(5, course.credits());
            statement.setString(6, course.courseType().name());
            statement.setInt(7, course.semester());
            statement.setBoolean(8, course.active());
            statement.setLong(9, course.rowVersion());
            statement.setTimestamp(10, Timestamp.from(course.createdAt()));
            statement.setTimestamp(11, Timestamp.from(course.updatedAt()));
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
                + "courseType=?, semester=?, isActive=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE planCourseId=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.courseCode());
            statement.setString(2, course.courseName());
            statement.setBigDecimal(3, course.credits());
            statement.setString(4, course.courseType().name());
            statement.setInt(5, course.semester());
            statement.setBoolean(6, course.active());
            statement.setTimestamp(7, Timestamp.from(course.updatedAt()));
            statement.setString(8, course.planCourseId());
            statement.setLong(9, expectedVersion);
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

    private TrainingPlan mapPlan(java.sql.ResultSet result) throws java.sql.SQLException {
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

    private TrainingPlanCourse mapCourse(java.sql.ResultSet result) throws java.sql.SQLException {
        return new TrainingPlanCourse(
                result.getString("planCourseId"),
                result.getString("planId"),
                result.getString("courseCode"),
                result.getString("courseName"),
                result.getBigDecimal("credits"),
                CourseType.valueOf(result.getString("courseType")),
                result.getInt("semester"),
                result.getBoolean("isActive"),
                result.getLong("rowVersion"),
                result.getTimestamp("createdAt").toInstant(),
                result.getTimestamp("updatedAt").toInstant());
    }

    private TrainingPlanSummary mapSummary(java.sql.ResultSet result)
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
