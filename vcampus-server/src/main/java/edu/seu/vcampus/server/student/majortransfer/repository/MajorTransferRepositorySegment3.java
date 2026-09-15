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
abstract class MajorTransferRepositorySegment3 extends MajorTransferRepositorySegment2 {

    // ── Application CRUD ──

    public String insertDraft(Connection connection, ApplicationRow row) {
        String sql = "INSERT INTO tblMajorTransferApplication (applicationId, batchId, studentId, "
                + "applicationType, applicationStatus, optionId, fromDepartmentId, fromDepartmentName, "
                + "fromMajorId, fromMajorName, fromClassId, fromClassName, fromStudentNumber, "
                + "fromGrade, studentName, reason, baseStudentVersion, applicationVersion, "
                + "createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.applicationId());
            ps.setString(2, row.batchId());
            ps.setString(3, row.studentId());
            ps.setString(4, row.applicationType().name());
            ps.setString(5, row.status().name());
            ps.setString(6, row.optionId());
            ps.setString(7, row.fromDepartmentId());
            ps.setString(8, row.fromDepartmentName());
            ps.setString(9, row.fromMajorId());
            ps.setString(10, row.fromMajorName());
            ps.setString(11, row.fromClassId());
            ps.setString(12, row.fromClassName());
            ps.setString(13, row.fromStudentNumber());
            ps.setString(14, row.fromGrade());
            ps.setString(15, row.studentName());
            ps.setString(16, row.reason());
            ps.setLong(17, row.baseStudentVersion());
            ps.setLong(18, row.applicationVersion());
            ps.setTimestamp(19, Timestamp.from(row.createdAt()));
            ps.setTimestamp(20, Timestamp.from(row.updatedAt()));
            ps.executeUpdate();
            return row.applicationId();
        } catch (SQLException e) {
            if (e.getErrorCode() == 19 || (e.getMessage() != null
                    && e.getMessage().contains("UNIQUE constraint failed")
                    && e.getMessage().contains("batchId"))) {
                throw new IllegalStateException("TRANSFER_DUPLICATE_APPLICATION: 学生在该批次已有申请");
            }
            throw new OrganizationPersistenceException("Cannot insert application draft", e);
        }
    }

    public int updateDraftFields(Connection connection, String applicationId, String optionId,
                                 MajorTransferApplicationType applicationType,
                                 String reason, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblMajorTransferApplication SET optionId=?, applicationType=?, "
                + "reason=?, applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationVersion=? AND applicationStatus='DRAFT'";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, optionId);
            ps.setString(2, applicationType.name());
            ps.setString(3, reason);
            ps.setTimestamp(4, Timestamp.from(updatedAt));
            ps.setString(5, applicationId);
            ps.setLong(6, expectedVersion);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update draft", e);
        }
    }

    public int updateApplicationStatus(Connection connection, String applicationId,
                                       MajorTransferStatus from, MajorTransferStatus to,
                                       long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblMajorTransferApplication SET applicationStatus=?, "
                + "applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationStatus=? AND applicationVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, to.name());
            ps.setTimestamp(2, Timestamp.from(updatedAt));
            ps.setString(3, applicationId);
            ps.setString(4, from.name());
            ps.setLong(5, expectedVersion);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update application status", e);
        }
    }

    public int submitApplication(Connection connection, String applicationId,
                                 long expectedVersion, Instant submittedAt) {
        String sql = "UPDATE tblMajorTransferApplication SET applicationStatus='SUBMITTED', "
                + "submittedAt=?, applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationStatus='DRAFT' AND applicationVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(submittedAt));
            ps.setTimestamp(2, Timestamp.from(submittedAt));
            ps.setString(3, applicationId);
            ps.setLong(4, expectedVersion);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot submit application", e);
        }
    }

    public int recordSourceReview(Connection connection, String applicationId,
                                  String reviewerUserId, boolean sourceVerified,
                                  boolean noMisconduct, boolean admissionAllowed,
                                  String comment, Instant reviewedAt) {
        String sql = "UPDATE tblMajorTransferApplication SET sourceReviewerUserId=?, "
                + "sourceReviewedAt=?, sourceComment=?, applicationVersion=applicationVersion+1, "
                + "updatedAt=? WHERE applicationId=? AND sourceReviewerUserId IS NULL";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, reviewerUserId);
            ps.setTimestamp(2, Timestamp.from(reviewedAt));
            ps.setString(3, comment);
            ps.setTimestamp(4, Timestamp.from(reviewedAt));
            ps.setString(5, applicationId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot record source review", e);
        }
    }
}
