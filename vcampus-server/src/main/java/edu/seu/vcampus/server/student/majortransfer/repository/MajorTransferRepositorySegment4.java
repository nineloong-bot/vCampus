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
abstract class MajorTransferRepositorySegment4 extends MajorTransferRepositorySegment3 {

    public int recordQualificationReview(Connection connection, String applicationId,
                                         String reviewerUserId, String comment,
                                         Instant reviewedAt) {
        String sql = "UPDATE tblMajorTransferApplication SET qualificationReviewerUserId=?, "
                + "qualificationReviewedAt=?, qualificationComment=?, "
                + "applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND qualificationReviewerUserId IS NULL";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, reviewerUserId);
            ps.setTimestamp(2, Timestamp.from(reviewedAt));
            ps.setString(3, comment);
            ps.setTimestamp(4, Timestamp.from(reviewedAt));
            ps.setString(5, applicationId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot record qualification review", e);
        }
    }

    public int recordScores(Connection connection, String applicationId,
                            Double writtenScore, Double interviewScore, Double finalScore,
                            long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblMajorTransferApplication SET writtenScore=?, interviewScore=?, "
                + "finalScore=?, applicationStatus='ASSESSED', applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            setDouble(ps, 1, writtenScore);
            setDouble(ps, 2, interviewScore);
            setDouble(ps, 3, finalScore);
            ps.setTimestamp(4, Timestamp.from(updatedAt));
            ps.setString(5, applicationId);
            ps.setLong(6, expectedVersion);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot record scores", e);
        }
    }

    public Optional<ApplicationRow> findApplication(Connection connection, String applicationId) {
        String sql = "SELECT * FROM tblMajorTransferApplication WHERE applicationId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapApplication(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot read application", e);
        }
    }

    public Optional<ApplicationRow> findApplicationByBatchStudent(Connection connection,
            String batchId, String studentId) {
        String sql = "SELECT * FROM tblMajorTransferApplication WHERE batchId = ? AND studentId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            ps.setString(2, studentId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapApplication(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot read application by batch/student", e);
        }
    }

    public List<ApplicationRow> listApplicationsByStudent(Connection connection, String studentId) {
        String sql = "SELECT * FROM tblMajorTransferApplication WHERE studentId = ? ORDER BY createdAt DESC";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (var rs = ps.executeQuery()) {
                List<ApplicationRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapApplication(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list applications by student", e);
        }
    }

    public List<ApplicationRow> listApplicationsByOption(Connection connection, String optionId,
                                                         MajorTransferStatus... statuses) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM tblMajorTransferApplication WHERE optionId = ?");
        if (statuses.length > 0) {
            sql.append(" AND applicationStatus IN (");
            for (int i = 0; i < statuses.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY finalScore DESC, fromStudentNumber ASC");
        try (var ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, optionId);
            for (int i = 0; i < statuses.length; i++) {
                ps.setString(i + 2, statuses[i].name());
            }
            try (var rs = ps.executeQuery()) {
                List<ApplicationRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapApplication(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list applications by option", e);
        }
    }
}
