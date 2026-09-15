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
abstract class TrainingPlanRepositoryOperations1 extends TrainingPlanRepositoryOperations2 {

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
        return search(connection, majorId, enrollmentYear, offset, limit, null);
    }
}
