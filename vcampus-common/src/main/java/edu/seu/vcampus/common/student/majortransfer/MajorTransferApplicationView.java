package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Immutable view of a major-transfer application with reviews and attachments. */
/**
 * Carries immutable major transfer application view data.
 * @param applicationId the application identifier
 * @param batchId the batch identifier
 * @param studentId the student identifier
 * @param studentName the student name
 * @param applicationType the application type
 * @param status the status
 * @param optionId the option identifier
 * @param targetMajorId the target major identifier
 * @param targetMajorName the target major name
 * @param targetDepartmentId the target department identifier
 * @param targetDepartmentName the target department name
 * @param fromDepartmentId the from department identifier
 * @param fromDepartmentName the from department name
 * @param fromMajorId the from major identifier
 * @param fromMajorName the from major name
 * @param fromClassId the from class identifier
 * @param fromClassName the from class name
 * @param fromStudentNumber the from student number
 * @param fromGrade the from grade
 * @param reason the reason
 * @param writtenScore the written score
 * @param interviewScore the interview score
 * @param finalScore the final score
 * @param reviews the reviews
 * @param attachments the attachments
 * @param sourceApprovalAllowed the source approval allowed
 * @param targetApprovalAllowed the target approval allowed
 * @param applicationVersion the application version
 * @param submittedAt the submitted at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record MajorTransferApplicationView(
        String applicationId,
        String batchId,
        String studentId,
        String studentName,
        MajorTransferApplicationType applicationType,
        MajorTransferStatus status,
        String optionId,
        String targetMajorId,
        String targetMajorName,
        String targetDepartmentId,
        String targetDepartmentName,
        String fromDepartmentId,
        String fromDepartmentName,
        String fromMajorId,
        String fromMajorName,
        String fromClassId,
        String fromClassName,
        String fromStudentNumber,
        String fromGrade,
        String reason,
        Double writtenScore,
        Double interviewScore,
        Double finalScore,
        List<MajorTransferReviewView> reviews,
        List<AttachmentInfo> attachments,
        boolean sourceApprovalAllowed,
        boolean targetApprovalAllowed,
        long applicationVersion,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    /**
 * Carries immutable attachment info data.
 * @param attachmentId the attachment identifier
 * @param fileName the file name
 * @param contentType the content type
 * @param fileSize the file size
 */
public record AttachmentInfo(
            String attachmentId,
            String fileName,
            String contentType,
            long fileSize
    ) implements Serializable { }
}
