package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.student.repository.OrganizationPersistenceException;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists major-transfer batches, options, applications, attachments, reviews, and executions. */
public final class MajorTransferRepository {

    // ── Nested persistence records ──

    public record BatchRow(String batchId, String batchName, MajorTransferBatchStatus status,
                           Instant applicationStart, Instant applicationEnd,
                           Instant publicityStart, Instant publicityEnd, Instant effectiveDate,
                           long rowVersion, Instant createdAt, Instant updatedAt) {}

    public record OptionRow(String optionId, String batchId, String targetMajorId,
                            String targetDepartmentId, String targetMajorName,
                            String targetDepartmentName, String grades, int receiveQuota,
                            int interviewQuota, Double writtenPassScore, Double interviewPassScore,
                            int writtenWeightPct, int interviewWeightPct,
                            boolean difficultyQuotaExempt, String requirements, boolean active,
                            long rowVersion, Instant createdAt, Instant updatedAt) {}

    public record ApplicationRow(String applicationId, String batchId, String studentId,
                                 MajorTransferApplicationType applicationType,
                                 MajorTransferStatus status, String optionId,
                                 String fromDepartmentId, String fromDepartmentName,
                                 String fromMajorId, String fromMajorName,
                                 String fromClassId, String fromClassName,
                                 String fromStudentNumber, String fromGrade,
                                 String studentName, String reason,
                                 Double writtenScore, Double interviewScore, Double finalScore,
                                 long baseStudentVersion, long applicationVersion,
                                 Instant submittedAt,
                                 String sourceReviewerUserId, Instant sourceReviewedAt,
                                 String sourceComment,
                                 String qualificationReviewerUserId,
                                 Instant qualificationReviewedAt, String qualificationComment,
                                 Instant createdAt, Instant updatedAt) {}

    public record AttachmentRow(String attachmentId, String applicationId, String fileName,
                                String contentType, long fileSize, Instant createdAt) {}

    public record ReviewRow(String reviewId, String applicationId,
                            MajorTransferReviewStage reviewStage, MajorTransferDecision decision,
                            String reviewerUserId, String comment,
                            Boolean sourceVerified, Boolean noMisconduct, Boolean admissionAllowed,
                            Instant createdAt) {}

    public record ExecutionRow(String executionId, String applicationId,
                               String toClassId, String toClassName,
                               String toMajorId, String toMajorName,
                               String toDepartmentId, String toDepartmentName,
                               String courseRecognitionStatus, String operatorUserId,
                               LocalDate effectiveDate, Instant createdAt) {}

    // ── Batch CRUD ──

    public String insertBatch(Connection connection, BatchRow row) {
        String sql = "INSERT INTO tblMajorTransferBatch (batchId, batchName, batchStatus, "
                + "applicationStart, applicationEnd, publicityStart, publicityEnd, effectiveDate, "
                + "rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.batchId());
            ps.setString(2, row.batchName());
            ps.setString(3, row.status().name());
            ps.setTimestamp(4, Timestamp.from(row.applicationStart()));
            ps.setTimestamp(5, Timestamp.from(row.applicationEnd()));
            setTimestamp(ps, 6, row.publicityStart());
            setTimestamp(ps, 7, row.publicityEnd());
            setTimestamp(ps, 8, row.effectiveDate());
            ps.setLong(9, row.rowVersion());
            ps.setTimestamp(10, Timestamp.from(row.createdAt()));
            ps.setTimestamp(11, Timestamp.from(row.updatedAt()));
            ps.executeUpdate();
            return row.batchId();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert batch", e);
        }
    }

    public int updateBatch(Connection connection, String batchId, String batchName,
                           MajorTransferBatchStatus status, Instant applicationStart,
                           Instant applicationEnd, Instant publicityStart, Instant publicityEnd,
                           Instant effectiveDate, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblMajorTransferBatch SET batchName=?, batchStatus=?, "
                + "applicationStart=?, applicationEnd=?, publicityStart=?, publicityEnd=?, "
                + "effectiveDate=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE batchId=? AND rowVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchName);
            ps.setString(2, status.name());
            ps.setTimestamp(3, Timestamp.from(applicationStart));
            ps.setTimestamp(4, Timestamp.from(applicationEnd));
            setTimestamp(ps, 5, publicityStart);
            setTimestamp(ps, 6, publicityEnd);
            setTimestamp(ps, 7, effectiveDate);
            ps.setTimestamp(8, Timestamp.from(updatedAt));
            ps.setString(9, batchId);
            ps.setLong(10, expectedVersion);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update batch", e);
        }
    }

    public Optional<BatchRow> findBatch(Connection connection, String batchId) {
        String sql = "SELECT * FROM tblMajorTransferBatch WHERE batchId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapBatch(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot read batch", e);
        }
    }

    public List<BatchRow> listBatches(Connection connection) {
        String sql = "SELECT * FROM tblMajorTransferBatch ORDER BY createdAt DESC";
        try (var ps = connection.prepareStatement(sql); var rs = ps.executeQuery()) {
            List<BatchRow> rows = new ArrayList<>();
            while (rs.next()) rows.add(mapBatch(rs));
            return List.copyOf(rows);
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list batches", e);
        }
    }

    public Optional<BatchRow> findOpenBatchAt(Connection connection, Instant instant) {
        String sql = "SELECT * FROM tblMajorTransferBatch WHERE batchStatus = 'OPEN' "
                + "AND applicationStart <= ? AND applicationEnd >= ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(instant));
            ps.setTimestamp(2, Timestamp.from(instant));
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapBatch(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot find open batch", e);
        }
    }

    public List<BatchRow> findOverlappingOpenBatches(Connection connection,
            Instant start, Instant end, String excludeBatchId) {
        String sql = "SELECT * FROM tblMajorTransferBatch "
                + "WHERE batchStatus = 'OPEN' AND applicationStart <= ? AND applicationEnd >= ?";
        if (excludeBatchId != null) sql += " AND batchId <> ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(end));
            ps.setTimestamp(2, Timestamp.from(start));
            if (excludeBatchId != null) ps.setString(3, excludeBatchId);
            try (var rs = ps.executeQuery()) {
                List<BatchRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapBatch(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot check overlapping batches", e);
        }
    }

    // ── Option CRUD ──

    public String insertOption(Connection connection, OptionRow row) {
        String sql = "INSERT INTO tblMajorTransferOption (optionId, batchId, targetMajorId, "
                + "targetDepartmentId, targetMajorName, targetDepartmentName, grades, "
                + "receiveQuota, interviewQuota, writtenPassScore, interviewPassScore, "
                + "writtenWeightPct, interviewWeightPct, difficultyQuotaExempt, requirements, "
                + "isActive, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.optionId());
            ps.setString(2, row.batchId());
            ps.setString(3, row.targetMajorId());
            ps.setString(4, row.targetDepartmentId());
            ps.setString(5, row.targetMajorName());
            ps.setString(6, row.targetDepartmentName());
            ps.setString(7, row.grades());
            ps.setInt(8, row.receiveQuota());
            ps.setInt(9, row.interviewQuota());
            setDouble(ps, 10, row.writtenPassScore());
            setDouble(ps, 11, row.interviewPassScore());
            ps.setInt(12, row.writtenWeightPct());
            ps.setInt(13, row.interviewWeightPct());
            ps.setBoolean(14, row.difficultyQuotaExempt());
            ps.setString(15, row.requirements());
            ps.setBoolean(16, row.active());
            ps.setLong(17, row.rowVersion());
            ps.setTimestamp(18, Timestamp.from(row.createdAt()));
            ps.setTimestamp(19, Timestamp.from(row.updatedAt()));
            ps.executeUpdate();
            return row.optionId();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert option", e);
        }
    }

    public int updateOption(Connection connection, OptionRow row) {
        String sql = "UPDATE tblMajorTransferOption SET grades=?, receiveQuota=?, "
                + "interviewQuota=?, writtenPassScore=?, interviewPassScore=?, "
                + "writtenWeightPct=?, interviewWeightPct=?, difficultyQuotaExempt=?, "
                + "requirements=?, isActive=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE optionId=? AND rowVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.grades());
            ps.setInt(2, row.receiveQuota());
            ps.setInt(3, row.interviewQuota());
            setDouble(ps, 4, row.writtenPassScore());
            setDouble(ps, 5, row.interviewPassScore());
            ps.setInt(6, row.writtenWeightPct());
            ps.setInt(7, row.interviewWeightPct());
            ps.setBoolean(8, row.difficultyQuotaExempt());
            ps.setString(9, row.requirements());
            ps.setBoolean(10, row.active());
            ps.setTimestamp(11, Timestamp.from(row.updatedAt()));
            ps.setString(12, row.optionId());
            ps.setLong(13, row.rowVersion());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update option", e);
        }
    }

    public Optional<OptionRow> findOption(Connection connection, String optionId) {
        String sql = "SELECT * FROM tblMajorTransferOption WHERE optionId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, optionId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapOption(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot read option", e);
        }
    }

    public List<OptionRow> listOptionsByBatch(Connection connection, String batchId) {
        String sql = "SELECT * FROM tblMajorTransferOption WHERE batchId = ? ORDER BY targetMajorName";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            try (var rs = ps.executeQuery()) {
                List<OptionRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapOption(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list options", e);
        }
    }

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

    public List<ApplicationRow> listApplicationsByBatch(Connection connection, String batchId) {
        String sql = "SELECT * FROM tblMajorTransferApplication WHERE batchId = ? ORDER BY createdAt";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            try (var rs = ps.executeQuery()) {
                List<ApplicationRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapApplication(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list applications by batch", e);
        }
    }

    public boolean hasSuccessfulTransfer(Connection connection, String studentId) {
        String sql = "SELECT COUNT(*) FROM tblMajorTransferApplication "
                + "WHERE studentId = ? AND applicationStatus IN ('PENDING_EFFECTIVE', 'EFFECTIVE')";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot check successful transfer", e);
        }
    }

    // ── Attachment CRUD ──

    public edu.seu.vcampus.common.student.majortransfer.MajorTransferAttachmentDocument readAttachment(Connection connection, String id) {
        try (var statement = connection.prepareStatement("SELECT fileName, contentType, content FROM tblMajorTransferAttachment WHERE attachmentId=?")) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("附件不存在");
                return new edu.seu.vcampus.common.student.majortransfer.MajorTransferAttachmentDocument(
                        result.getString(1), result.getString(2), result.getBytes(3));
            }
        } catch (SQLException error) { throw new OrganizationPersistenceException("Cannot read attachment", error); }
    }

    public String insertAttachment(Connection connection, String attachmentId,
                                   String applicationId, String fileName, String contentType,
                                   long fileSize, byte[] content, Instant createdAt) {
        String sql = "INSERT INTO tblMajorTransferAttachment "
                + "(attachmentId, applicationId, fileName, contentType, fileSize, content, createdAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, attachmentId);
            ps.setString(2, applicationId);
            ps.setString(3, fileName);
            ps.setString(4, contentType);
            ps.setLong(5, fileSize);
            ps.setBytes(6, content);
            ps.setTimestamp(7, Timestamp.from(createdAt));
            ps.executeUpdate();
            return attachmentId;
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert attachment", e);
        }
    }

    public List<AttachmentRow> listAttachments(Connection connection, String applicationId) {
        String sql = "SELECT attachmentId, applicationId, fileName, contentType, fileSize, createdAt "
                + "FROM tblMajorTransferAttachment WHERE applicationId = ? ORDER BY createdAt";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                List<AttachmentRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapAttachment(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list attachments", e);
        }
    }

    public int countAttachments(Connection connection, String applicationId) {
        String sql = "SELECT COUNT(*) FROM tblMajorTransferAttachment WHERE applicationId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot count attachments", e);
        }
    }

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

    // ── Mapping helpers ──

    private static BatchRow mapBatch(ResultSet rs) throws SQLException {
        return new BatchRow(rs.getString("batchId"), rs.getString("batchName"),
                MajorTransferBatchStatus.valueOf(rs.getString("batchStatus")),
                rs.getTimestamp("applicationStart").toInstant(),
                rs.getTimestamp("applicationEnd").toInstant(),
                instant(rs, "publicityStart"), instant(rs, "publicityEnd"),
                instant(rs, "effectiveDate"),
                rs.getLong("rowVersion"),
                rs.getTimestamp("createdAt").toInstant(),
                rs.getTimestamp("updatedAt").toInstant());
    }

    private static OptionRow mapOption(ResultSet rs) throws SQLException {
        return new OptionRow(rs.getString("optionId"), rs.getString("batchId"),
                rs.getString("targetMajorId"), rs.getString("targetDepartmentId"),
                rs.getString("targetMajorName"), rs.getString("targetDepartmentName"),
                rs.getString("grades"), rs.getInt("receiveQuota"),
                rs.getInt("interviewQuota"), nullableDouble(rs, "writtenPassScore"),
                nullableDouble(rs, "interviewPassScore"),
                rs.getInt("writtenWeightPct"), rs.getInt("interviewWeightPct"),
                rs.getBoolean("difficultyQuotaExempt"), rs.getString("requirements"),
                rs.getBoolean("isActive"), rs.getLong("rowVersion"),
                rs.getTimestamp("createdAt").toInstant(),
                rs.getTimestamp("updatedAt").toInstant());
    }

    private static ApplicationRow mapApplication(ResultSet rs) throws SQLException {
        return new ApplicationRow(rs.getString("applicationId"), rs.getString("batchId"),
                rs.getString("studentId"),
                MajorTransferApplicationType.valueOf(rs.getString("applicationType")),
                MajorTransferStatus.valueOf(rs.getString("applicationStatus")),
                rs.getString("optionId"),
                rs.getString("fromDepartmentId"), rs.getString("fromDepartmentName"),
                rs.getString("fromMajorId"), rs.getString("fromMajorName"),
                rs.getString("fromClassId"), rs.getString("fromClassName"),
                rs.getString("fromStudentNumber"), rs.getString("fromGrade"),
                rs.getString("studentName"), rs.getString("reason"),
                nullableDouble(rs, "writtenScore"), nullableDouble(rs, "interviewScore"),
                nullableDouble(rs, "finalScore"),
                rs.getLong("baseStudentVersion"), rs.getLong("applicationVersion"),
                instant(rs, "submittedAt"),
                rs.getString("sourceReviewerUserId"), instant(rs, "sourceReviewedAt"),
                rs.getString("sourceComment"),
                rs.getString("qualificationReviewerUserId"),
                instant(rs, "qualificationReviewedAt"), rs.getString("qualificationComment"),
                rs.getTimestamp("createdAt").toInstant(),
                rs.getTimestamp("updatedAt").toInstant());
    }

    private static AttachmentRow mapAttachment(ResultSet rs) throws SQLException {
        return new AttachmentRow(rs.getString("attachmentId"), rs.getString("applicationId"),
                rs.getString("fileName"), rs.getString("contentType"),
                rs.getLong("fileSize"), rs.getTimestamp("createdAt").toInstant());
    }

    private static ReviewRow mapReview(ResultSet rs) throws SQLException {
        return new ReviewRow(rs.getString("reviewId"), rs.getString("applicationId"),
                MajorTransferReviewStage.valueOf(rs.getString("reviewStage")),
                MajorTransferDecision.valueOf(rs.getString("decision")),
                rs.getString("reviewerUserId"), rs.getString("comment"),
                nullableBoolean(rs, "sourceVerified"), nullableBoolean(rs, "noMisconduct"),
                nullableBoolean(rs, "admissionAllowed"),
                rs.getTimestamp("createdAt").toInstant());
    }

    private static ExecutionRow mapExecution(ResultSet rs) throws SQLException {
        return new ExecutionRow(rs.getString("executionId"), rs.getString("applicationId"),
                rs.getString("toClassId"), rs.getString("toClassName"),
                rs.getString("toMajorId"), rs.getString("toMajorName"),
                rs.getString("toDepartmentId"), rs.getString("toDepartmentName"),
                rs.getString("courseRecognitionStatus"), rs.getString("operatorUserId"),
                rs.getDate("effectiveDate").toLocalDate(),
                rs.getTimestamp("createdAt").toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }

    private static void setTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) ps.setNull(index, Types.TIMESTAMP);
        else ps.setTimestamp(index, Timestamp.from(value));
    }

    private static void setDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) ps.setNull(index, Types.DOUBLE);
        else ps.setDouble(index, value);
    }

    private static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) ps.setNull(index, Types.BOOLEAN);
        else ps.setBoolean(index, value);
    }
}
