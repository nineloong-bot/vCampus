package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.service.StudentNotFoundException;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus.*;

/** Holds major-transfer dependencies and response mapping helpers. */
abstract class MajorTransferServiceBase implements MajorTransferService {
    protected static final int MAX_ATTACHMENTS = 3;
    protected static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MiB
    protected static final int MAX_REASON_LENGTH = 2000;

    protected static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    protected static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    protected static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    protected final TransactionManager transactions;
    protected final ResourceLockManager locks;
    protected final MajorTransferRepository repository;
    protected final StudentRepository students;
    protected final StudentChangeRepository changes;
    protected final AccessOrganizationRepository organizations;
    protected final UserQueryPort users;

    protected MajorTransferServiceBase(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.repository = Objects.requireNonNull(repository);
        this.students = Objects.requireNonNull(students);
        this.changes = Objects.requireNonNull(changes);
        this.organizations = organizations;
        this.users = Objects.requireNonNull(users);
    }

    protected MajorTransferApplicationView toApplicationView(Connection connection,
                                                             MajorTransferRepository.ApplicationRow row) {
        return toApplicationView(connection, row, null);
    }

    protected MajorTransferApplicationView toApplicationView(Connection connection,
            MajorTransferRepository.ApplicationRow row, String managedDepartmentId) {
        List<MajorTransferReviewView> reviews = repository.listReviews(connection, row.applicationId())
                .stream().map(this::toReviewView).toList();
        List<MajorTransferApplicationView.AttachmentInfo> attachments =
                repository.listAttachments(connection, row.applicationId()).stream()
                        .map(a -> new MajorTransferApplicationView.AttachmentInfo(
                                a.attachmentId(), a.fileName(), a.contentType(), a.fileSize()))
                        .toList();
        String targetMajorId = null, targetMajorName = null, targetDeptId = null, targetDeptName = null;
        MajorTransferRepository.OptionRow option =
                repository.findOption(connection, row.optionId()).orElse(null);
        if (option != null) {
            targetMajorId = option.targetMajorId();
            targetMajorName = option.targetMajorName();
            targetDeptId = option.targetDepartmentId();
            targetDeptName = option.targetDepartmentName();
        }
        return new MajorTransferApplicationView(
                row.applicationId(), row.batchId(), row.studentId(), row.studentName(),
                row.applicationType(), row.status(), row.optionId(),
                targetMajorId, targetMajorName, targetDeptId, targetDeptName,
                row.fromDepartmentId(), row.fromDepartmentName(),
                row.fromMajorId(), row.fromMajorName(),
                row.fromClassId(), row.fromClassName(),
                row.fromStudentNumber(), row.fromGrade(),
                row.reason(), row.writtenScore(), row.interviewScore(), row.finalScore(),
                reviews, attachments,
                managedDepartmentId != null && managedDepartmentId.equals(row.fromDepartmentId()),
                managedDepartmentId != null && managedDepartmentId.equals(targetDeptId),
                row.applicationVersion(),
                row.submittedAt(), row.createdAt(), row.updatedAt());
    }

    protected MajorTransferReviewView toReviewView(MajorTransferRepository.ReviewRow row) {
        return new MajorTransferReviewView(row.reviewId(), row.applicationId(),
                row.reviewStage(), row.decision(), row.reviewerUserId(), row.comment(),
                row.sourceVerified(), row.noMisconduct(), row.admissionAllowed(), row.createdAt());
    }

    protected MajorTransferBatchView toBatchView(MajorTransferRepository.BatchRow row) {
        return new MajorTransferBatchView(row.batchId(), row.batchName(), row.status(),
                row.applicationStart(), row.applicationEnd(),
                row.publicityStart(), row.publicityEnd(), row.effectiveDate(), row.rowVersion());
    }

    protected MajorTransferOptionView toOptionView(MajorTransferRepository.OptionRow row) {
        return new MajorTransferOptionView(row.optionId(), row.batchId(),
                row.targetMajorId(), row.targetDepartmentId(),
                row.targetMajorName(), row.targetDepartmentName(),
                row.grades(), row.receiveQuota(), row.interviewQuota(),
                row.writtenPassScore(), row.interviewPassScore(),
                row.writtenWeightPct(), row.interviewWeightPct(),
                row.difficultyQuotaExempt(), row.requirements(),
                row.active(), row.rowVersion());
    }

    static Double finalScore(Double written, Double interview, int writtenPct, int interviewPct) {
        if (written == null && writtenPct == 0) written = 0.0;
        if (interview == null && interviewPct == 0) interview = 0.0;
        if (written == null || interview == null) return null;
        BigDecimal w = BigDecimal.valueOf(written).multiply(BigDecimal.valueOf(writtenPct))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal i = BigDecimal.valueOf(interview).multiply(BigDecimal.valueOf(interviewPct))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return w.add(i).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    protected static MajorTransferException error(String code, String message) {
        return new MajorTransferException(code, message);
    }

    protected static boolean isFalse(Object value) {
        return Boolean.FALSE.equals(value) || Integer.valueOf(0).equals(value);
    }

    protected static java.util.ConcurrentModificationException concurrent() {
        return new java.util.ConcurrentModificationException("数据已被修改，请刷新");
    }
}
