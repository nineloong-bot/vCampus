package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Immutable view of a major-transfer application with reviews and attachments. */
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
        long applicationVersion,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public record AttachmentInfo(
            String attachmentId,
            String fileName,
            String contentType,
            long fileSize
    ) implements Serializable { }
}
