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

/** Centralizes major-transfer validation and state-change helpers. */
abstract class MajorTransferValidationService extends MajorTransferServiceBase {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferValidationService(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    protected List<MajorTransferApplicationView> listApplications(
            MajorTransferApplicationQuery query, String departmentId) {
        return transactions.inTransaction(connection -> {
            List<MajorTransferRepository.ApplicationRow> rows;
            if (query.batchId() != null) {
                rows = departmentId == null
                        ? repository.listApplicationsByBatch(connection, query.batchId())
                        : repository.listApplicationsByBatchAndCollege(
                                connection, query.batchId(), departmentId);
            } else {
                rows = List.of();
            }
            if (query.status() != null) {
                rows = rows.stream().filter(r -> r.status() == query.status()).toList();
            }
            if (query.optionId() != null) rows = rows.stream().filter(r -> r.optionId().equals(query.optionId())).toList();
            return rows.stream().map(row -> toApplicationView(connection, row, departmentId)).toList();
        });
    }

    // ── Helpers ──

    protected void requireTargetDepartment(Connection connection,
            MajorTransferRepository.ApplicationRow application,
            String trustedDepartmentId) {
        if (trustedDepartmentId == null) return;
        MajorTransferRepository.OptionRow option = repository.findOption(
                connection, application.optionId()).orElseThrow(() ->
                error("TRANSFER_OPTION_NOT_FOUND", "选项不存在"));
        requireDepartment(trustedDepartmentId, option.targetDepartmentId());
    }

    protected static void requireDepartment(String trustedDepartmentId,
            String actualDepartmentId) {
        if (trustedDepartmentId != null
                && !trustedDepartmentId.equals(actualDepartmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
    }

    protected MajorTransferWorkspace emptyWorkspace(Student student) {
        return new MajorTransferWorkspace(null, List.of(), List.of(),
                null, null, student.majorId(), null,
                student.classId(), null, student.studentNumber(), null, null);
    }

    protected List<MajorTransferEligibilityItem> checkEligibility(Connection connection,
                                                                 Student student,
                                                                 MajorTransferBatchView batch) {
        List<MajorTransferEligibilityItem> items = new ArrayList<>();
        Instant now = Instant.now();
        items.add(new MajorTransferEligibilityItem("报名时间",
                batch.status() == MajorTransferBatchStatus.OPEN && !now.isBefore(batch.applicationStart()) && !now.isAfter(batch.applicationEnd()),
                now.isBefore(batch.applicationStart()) ? "报名尚未开始" :
                        now.isAfter(batch.applicationEnd()) ? "报名已截止" : "在报名期内"));
        items.add(new MajorTransferEligibilityItem("学生类型",
                student.studentType() == StudentType.UNDERGRADUATE,
                student.studentType() == StudentType.UNDERGRADUATE ? "本科生" : "仅面向本科生"));
        items.add(new MajorTransferEligibilityItem("学籍状态",
                student.status() == StudentStatus.ACTIVE,
                student.status() == StudentStatus.ACTIVE ? "正常" : "学籍状态异常"));
        if (repository.hasSuccessfulTransfer(connection, student.studentId())) {
            items.add(new MajorTransferEligibilityItem("转专业记录", false, "已有生效的转专业记录"));
        } else {
            items.add(new MajorTransferEligibilityItem("转专业记录", true, "无生效记录"));
        }
        return List.copyOf(items);
    }

    protected void validateBatchOpen(MajorTransferRepository.BatchRow batch, Instant now) {
        if (batch.status() != MajorTransferBatchStatus.OPEN)
            throw error("TRANSFER_BATCH_CLOSED", "批次未开放");
        if (now.isBefore(batch.applicationStart()) || now.isAfter(batch.applicationEnd()))
            throw error("TRANSFER_BATCH_CLOSED", "不在报名时间内");
    }

    protected void validateEligibility(Connection c, Student student, MajorTransferRepository.BatchRow batch,
            MajorTransferRepository.OptionRow option) {
        if (student.studentType() != StudentType.UNDERGRADUATE || student.status() != StudentStatus.ACTIVE)
            throw error("TRANSFER_INELIGIBLE", "仅允许正常在籍的本科生申请");
        try (var statement = c.prepareStatement("SELECT enrolled, onCampus FROM tblStudent WHERE studentId=?")) {
            statement.setString(1, student.studentId());
            try (var row = statement.executeQuery()) {
                if (!row.next() || isFalse(row.getObject(1)) || isFalse(row.getObject(2)))
                    throw error("TRANSFER_INELIGIBLE", "学生必须在籍且在校");
            }
        } catch (java.sql.SQLException error) { throw new IllegalStateException("无法核实在籍在校状态", error); }
        if (!option.active() || !option.batchId().equals(batch.batchId()) || option.targetMajorId().equals(student.majorId()))
            throw error("TRANSFER_INVALID_TARGET", "目标专业不可用或与当前专业相同");
        var major = organizations.findMajor(c, option.targetMajorId()).orElseThrow();
        if (!major.active() || !organizations.findDepartment(c, major.departmentId()).orElseThrow().active())
            throw error("TRANSFER_INVALID_TARGET", "目标学院或专业未启用");
        int year = organizations.findClass(c, student.classId()).orElseThrow().enrollmentYear();
        var term = batch.applicationStart().atZone(java.time.ZoneId.of("Asia/Shanghai"));
        int grade = term.getYear() - (term.getMonthValue() < 9 ? 1 : 0) - year + 1;
        if (Arrays.stream(option.grades().split(",")).map(String::trim)
                .noneMatch(value -> value.equals(String.valueOf(year)) || value.equals(String.valueOf(grade))))
            throw error("TRANSFER_INELIGIBLE", "当前年级不在该专业允许申请范围内");
        if (repository.hasSuccessfulTransfer(c, student.studentId()))
            throw error("TRANSFER_ALREADY_TRANSFERRED", "已有生效的转专业记录");
        if (repository.listApplicationsByStudent(c, student.studentId()).stream().anyMatch(a ->
                !a.batchId().equals(batch.batchId()) && a.status() != REJECTED && a.status() != CANCELLED && a.status() != EFFECTIVE))
            throw error("TRANSFER_DUPLICATE_APPLICATION", "已有其他批次进行中的申请");
    }

    protected int changeStatus(Connection connection, String id, MajorTransferStatus from,
            MajorTransferStatus to, long version, Instant now) {
        int count = repository.updateApplicationStatus(connection, id, from, to, version, now);
        requireChanged(count);
        return count;
    }

    protected static void requireChanged(int count) { if (count != 1) throw concurrent(); }

    protected static void validateScore(Double score, int weight) {
        if ((weight > 0 && score == null) || (score != null && (!Double.isFinite(score) || score < 0 || score > 100)))
            throw error("TRANSFER_SCORE_INVALID", "有效考核项成绩必填，成绩必须在0至100之间");
    }

    protected void validateAttachment(byte[] content, String declaredType, String fileName) {
        if (content.length > MAX_FILE_SIZE)
            throw error("TRANSFER_ATTACHMENT_TOO_LARGE", "附件大小不能超过5MB");
        String detected = detectContentType(content, declaredType);
        if (detected == null)
            throw error("TRANSFER_ATTACHMENT_INVALID", "仅支持PDF、JPEG、PNG格式");
    }

    protected String detectContentType(byte[] content, String declaredType) {
        if (startsWith(content, PDF_MAGIC)) return "application/pdf";
        if (startsWith(content, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(content, PNG_MAGIC)) return "image/png";
        return null;
    }

    protected static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    protected String resolveStudentId(String userId) {
        return transactions.inTransaction(connection ->
                students.findByUserId(connection, userId)
                        .orElseThrow(StudentNotFoundException::new).studentId());
    }
}
