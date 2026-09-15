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
public final class MajorTransferRepository extends MajorTransferRepositorySegment6 {


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
                               LocalDate effectiveDate, Instant createdAt) {}    /** Creates a stateless major-transfer repository. */
    public MajorTransferRepository() {
    }
}
