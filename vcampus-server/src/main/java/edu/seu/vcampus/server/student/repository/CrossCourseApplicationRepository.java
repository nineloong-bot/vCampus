package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.CrossCourseApplicationStatus;
import edu.seu.vcampus.server.student.domain.CrossCourseApplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Provides cross course application repository behavior. */
public final class CrossCourseApplicationRepository {

    /**
     * Performs the insert operation.
     * @param connection the connection
     * @param app the app
     */
    public void insert(Connection connection, CrossCourseApplication app) {
        String sql = "INSERT INTO tblCrossCourseApplication ("
                + "applicationId, courseId, courseCode, courseName, credits, "
                + "offeringDepartmentId, offeringDepartmentName, "
                + "targetDepartmentId, targetDepartmentName, "
                + "targetPlanId, targetPlanName, semester, "
                + "requestedQuota, allocatedQuota, applicantUserId, applicantName, "
                + "reason, status, reviewerUserId, reviewComment, reviewedAt, "
                + "rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, app.applicationId());
            stmt.setString(2, app.courseId());
            stmt.setString(3, app.courseCode());
            stmt.setString(4, app.courseName());
            stmt.setBigDecimal(5, app.credits());
            stmt.setString(6, app.offeringDepartmentId());
            stmt.setString(7, app.offeringDepartmentName());
            stmt.setString(8, app.targetDepartmentId());
            stmt.setString(9, app.targetDepartmentName());
            stmt.setString(10, app.targetPlanId());
            stmt.setString(11, app.targetPlanName());
            stmt.setInt(12, app.semester());
            stmt.setInt(13, app.requestedQuota());
            if (app.allocatedQuota() != null) stmt.setInt(14, app.allocatedQuota());
            else stmt.setNull(14, java.sql.Types.INTEGER);
            stmt.setString(15, app.applicantUserId());
            stmt.setString(16, app.applicantName());
            stmt.setString(17, app.reason());
            stmt.setString(18, app.status().name());
            stmt.setString(19, app.reviewerUserId());
            stmt.setString(20, app.reviewComment());
            if (app.reviewedAt() != null) stmt.setTimestamp(21, Timestamp.from(app.reviewedAt()));
            else stmt.setNull(21, java.sql.Types.TIMESTAMP);
            stmt.setLong(22, app.rowVersion());
            stmt.setTimestamp(23, Timestamp.from(app.createdAt()));
            stmt.setTimestamp(24, Timestamp.from(app.updatedAt()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert cross course application", e);
        }
    }

    /**
     * Performs the update operation.
     * @param connection the connection
     * @param app the app
     * @param expectedVersion the expected version
     */
    public void update(Connection connection, CrossCourseApplication app, long expectedVersion) {
        String sql = "UPDATE tblCrossCourseApplication SET "
                + "allocatedQuota=?, reviewerUserId=?, reviewComment=?, reviewedAt=?, "
                + "status=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND rowVersion=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (app.allocatedQuota() != null) stmt.setInt(1, app.allocatedQuota());
            else stmt.setNull(1, java.sql.Types.INTEGER);
            stmt.setString(2, app.reviewerUserId());
            stmt.setString(3, app.reviewComment());
            if (app.reviewedAt() != null) stmt.setTimestamp(4, Timestamp.from(app.reviewedAt()));
            else stmt.setNull(4, java.sql.Types.TIMESTAMP);
            stmt.setString(5, app.status().name());
            stmt.setTimestamp(6, Timestamp.from(app.updatedAt()));
            stmt.setString(7, app.applicationId());
            stmt.setLong(8, expectedVersion);
            if (stmt.executeUpdate() != 1) {
                throw new ConcurrentModificationException("Cross course application version changed");
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update cross course application", e);
        }
    }

    /**
     * Performs the find by identifier operation.
     * @param connection the connection
     * @param applicationId the application identifier
     * @return the operation result
     */
    public Optional<CrossCourseApplication> findById(Connection connection, String applicationId) {
        String sql = "SELECT applicationId, courseId, courseCode, courseName, credits, "
                + "offeringDepartmentId, offeringDepartmentName, "
                + "targetDepartmentId, targetDepartmentName, "
                + "targetPlanId, targetPlanName, semester, "
                + "requestedQuota, allocatedQuota, applicantUserId, applicantName, "
                + "reason, status, reviewerUserId, reviewComment, reviewedAt, "
                + "rowVersion, createdAt, updatedAt "
                + "FROM tblCrossCourseApplication WHERE applicationId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, applicationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot find cross course application by id", e);
        }
    }

    /**
     * Performs the list operation.
     * @param connection the connection
     * @param offeringDepartmentId the offering department identifier
     * @param targetDepartmentId the target department identifier
     * @param status the status
     * @return the operation result
     */
    public List<CrossCourseApplication> list(Connection connection,
            String offeringDepartmentId, String targetDepartmentId, CrossCourseApplicationStatus status) {
        StringBuilder sql = new StringBuilder("SELECT applicationId, courseId, courseCode, courseName, credits, "
                + "offeringDepartmentId, offeringDepartmentName, "
                + "targetDepartmentId, targetDepartmentName, "
                + "targetPlanId, targetPlanName, semester, "
                + "requestedQuota, allocatedQuota, applicantUserId, applicantName, "
                + "reason, status, reviewerUserId, reviewComment, reviewedAt, "
                + "rowVersion, createdAt, updatedAt "
                + "FROM tblCrossCourseApplication WHERE 1=1 ");
        List<String> params = new ArrayList<>();
        if (offeringDepartmentId != null && !offeringDepartmentId.isBlank()) {
            sql.append("AND offeringDepartmentId = ? ");
            params.add(offeringDepartmentId);
        }
        if (targetDepartmentId != null && !targetDepartmentId.isBlank()) {
            sql.append("AND targetDepartmentId = ? ");
            params.add(targetDepartmentId);
        }
        if (status != null) {
            sql.append("AND status = ? ");
            params.add(status.name());
        }
        sql.append("ORDER BY createdAt DESC");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<CrossCourseApplication> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return List.copyOf(list);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list cross course applications", e);
        }
    }

    private CrossCourseApplication map(ResultSet rs) throws SQLException {
        Integer allocatedQuota = rs.getObject("allocatedQuota") != null ? rs.getInt("allocatedQuota") : null;
        Timestamp reviewedAtTs = rs.getTimestamp("reviewedAt");
        return new CrossCourseApplication(
                rs.getString("applicationId"),
                rs.getString("courseId"),
                rs.getString("courseCode"),
                rs.getString("courseName"),
                rs.getBigDecimal("credits"),
                rs.getString("offeringDepartmentId"),
                rs.getString("offeringDepartmentName"),
                rs.getString("targetDepartmentId"),
                rs.getString("targetDepartmentName"),
                rs.getString("targetPlanId"),
                rs.getString("targetPlanName"),
                rs.getInt("semester"),
                rs.getInt("requestedQuota"),
                allocatedQuota,
                rs.getString("applicantUserId"),
                rs.getString("applicantName"),
                rs.getString("reason"),
                CrossCourseApplicationStatus.valueOf(rs.getString("status")),
                rs.getString("reviewerUserId"),
                rs.getString("reviewComment"),
                reviewedAtTs != null ? reviewedAtTs.toInstant() : null,
                rs.getLong("rowVersion"),
                rs.getTimestamp("createdAt").toInstant(),
                rs.getTimestamp("updatedAt").toInstant());
    }
}
