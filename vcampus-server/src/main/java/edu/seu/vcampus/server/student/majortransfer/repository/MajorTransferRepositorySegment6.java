package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.student.repository.OrganizationPersistenceException;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository.*;

/** Implements a focused group of major-transfer persistence operations. */
abstract class MajorTransferRepositorySegment6 extends MajorTransferRepositorySegment5 {

    public int deleteAttachment(Connection connection, String attachmentId, String applicationId) {
        String sql = "DELETE FROM tblMajorTransferAttachment WHERE attachmentId = ? AND applicationId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, attachmentId);
            ps.setString(2, applicationId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot delete attachment", e);
        }
    }

    // ── Review CRUD ──

    public String insertReview(Connection connection, ReviewRow row) {
        String sql = "INSERT INTO tblMajorTransferReview "
                + "(reviewId, applicationId, reviewStage, decision, reviewerUserId, comment, "
                + "sourceVerified, noMisconduct, admissionAllowed, createdAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.reviewId());
            ps.setString(2, row.applicationId());
            ps.setString(3, row.reviewStage().name());
            ps.setString(4, row.decision().name());
            ps.setString(5, row.reviewerUserId());
            ps.setString(6, row.comment());
            setBoolean(ps, 7, row.sourceVerified());
            setBoolean(ps, 8, row.noMisconduct());
            setBoolean(ps, 9, row.admissionAllowed());
            ps.setTimestamp(10, Timestamp.from(row.createdAt()));
            ps.executeUpdate();
            return row.reviewId();
        } catch (SQLException e) {
            if (e.getErrorCode() == 19 || (e.getMessage() != null
                    && e.getMessage().contains("UNIQUE constraint failed"))) {
                throw new IllegalStateException("审核阶段 " + row.reviewStage() + " 已有审核记录");
            }
            throw new OrganizationPersistenceException("Cannot insert review", e);
        }
    }

    public List<ReviewRow> listReviews(Connection connection, String applicationId) {
        String sql = "SELECT * FROM tblMajorTransferReview WHERE applicationId = ? ORDER BY createdAt";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                List<ReviewRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapReview(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list reviews", e);
        }
    }

    // ── Execution CRUD ──

    public String insertExecution(Connection connection, ExecutionRow row) {
        String sql = "INSERT INTO tblMajorTransferExecution "
                + "(executionId, applicationId, toClassId, toClassName, toMajorId, toMajorName, "
                + "toDepartmentId, toDepartmentName, courseRecognitionStatus, operatorUserId, "
                + "effectiveDate, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.executionId());
            ps.setString(2, row.applicationId());
            ps.setString(3, row.toClassId());
            ps.setString(4, row.toClassName());
            ps.setString(5, row.toMajorId());
            ps.setString(6, row.toMajorName());
            ps.setString(7, row.toDepartmentId());
            ps.setString(8, row.toDepartmentName());
            ps.setString(9, row.courseRecognitionStatus());
            ps.setString(10, row.operatorUserId());
            ps.setDate(11, Date.valueOf(row.effectiveDate()));
            ps.setTimestamp(12, Timestamp.from(row.createdAt()));
            ps.executeUpdate();
            return row.executionId();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert execution", e);
        }
    }

    public List<ExecutionRow> listExecutions(Connection connection, String applicationId) {
        String sql = "SELECT * FROM tblMajorTransferExecution WHERE applicationId = ? ORDER BY createdAt";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                List<ExecutionRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapExecution(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list executions", e);
        }
    }
}
