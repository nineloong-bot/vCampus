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

/** Maps JDBC values to major-transfer persistence rows. */
abstract class MajorTransferRepositoryMappings {

    // ── Mapping helpers ──

    protected static BatchRow mapBatch(ResultSet rs) throws SQLException {
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

    protected static OptionRow mapOption(ResultSet rs) throws SQLException {
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

    protected static ApplicationRow mapApplication(ResultSet rs) throws SQLException {
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

    protected static AttachmentRow mapAttachment(ResultSet rs) throws SQLException {
        return new AttachmentRow(rs.getString("attachmentId"), rs.getString("applicationId"),
                rs.getString("fileName"), rs.getString("contentType"),
                rs.getLong("fileSize"), rs.getTimestamp("createdAt").toInstant());
    }

    protected static ReviewRow mapReview(ResultSet rs) throws SQLException {
        return new ReviewRow(rs.getString("reviewId"), rs.getString("applicationId"),
                MajorTransferReviewStage.valueOf(rs.getString("reviewStage")),
                MajorTransferDecision.valueOf(rs.getString("decision")),
                rs.getString("reviewerUserId"), rs.getString("comment"),
                nullableBoolean(rs, "sourceVerified"), nullableBoolean(rs, "noMisconduct"),
                nullableBoolean(rs, "admissionAllowed"),
                rs.getTimestamp("createdAt").toInstant());
    }

    protected static ExecutionRow mapExecution(ResultSet rs) throws SQLException {
        return new ExecutionRow(rs.getString("executionId"), rs.getString("applicationId"),
                rs.getString("toClassId"), rs.getString("toClassName"),
                rs.getString("toMajorId"), rs.getString("toMajorName"),
                rs.getString("toDepartmentId"), rs.getString("toDepartmentName"),
                rs.getString("courseRecognitionStatus"), rs.getString("operatorUserId"),
                rs.getDate("effectiveDate").toLocalDate(),
                rs.getTimestamp("createdAt").toInstant());
    }

    protected static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    protected static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    protected static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }

    protected static void setTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) ps.setNull(index, Types.TIMESTAMP);
        else ps.setTimestamp(index, Timestamp.from(value));
    }

    protected static void setDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) ps.setNull(index, Types.DOUBLE);
        else ps.setDouble(index, value);
    }

    protected static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) ps.setNull(index, Types.BOOLEAN);
        else ps.setBoolean(index, value);
    }
}
