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

/** Implements a focused segment of the major-transfer workflow. */
abstract class MajorTransferWorkflowSegment2 extends MajorTransferWorkflowSegment1 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment2(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public MajorTransferApplicationView saveDraft(String userId, SaveMajorTransferDraftCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    Instant now = Instant.now();
                    Student student = students.findByUserId(connection, userId)
                            .orElseThrow(StudentNotFoundException::new);
                    MajorTransferRepository.BatchRow batch = repository.findBatch(connection, command.batchId())
                            .orElseThrow(() -> error("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
                    validateBatchOpen(batch, now);
                    MajorTransferRepository.OptionRow option = repository.findOption(connection, command.optionId())
                            .orElseThrow(() -> error("TRANSFER_OPTION_NOT_FOUND", "目标专业不存在"));
                    if (!option.batchId().equals(batch.batchId()))
                        throw error("TRANSFER_INVALID_TARGET", "目标专业不属于该批次");
                    if (!option.active())
                        throw error("TRANSFER_OPTION_INACTIVE", "目标专业未启用");
                    validateEligibility(connection, student, batch, option);

                    if (command.applicationId() != null) {
                        // Update existing draft
                        MajorTransferRepository.ApplicationRow existing =
                                repository.findApplication(connection, command.applicationId())
                                        .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                        if (!existing.studentId().equals(student.studentId()))
                            throw error("COMMON_FORBIDDEN", "无权操作");
                        if (!existing.batchId().equals(batch.batchId()))
                            throw error("TRANSFER_INVALID_TARGET", "不能修改申请所属批次");
                        if (!MajorTransferStateMachine.studentMayEdit(existing.status()))
                            throw error("TRANSFER_STATE_INVALID", "当前状态不允许编辑");
                        requireChanged(repository.updateDraftFields(connection, command.applicationId(),
                                command.optionId(), command.applicationType(), command.reason(),
                                command.expectedVersion(), now));
                    } else {
                        // Create new draft
                        repository.findApplicationByBatchStudent(connection, batch.batchId(),
                                student.studentId()).ifPresent(existing -> {
                            if (existing != null) {
                                throw error("TRANSFER_DUPLICATE_APPLICATION", "该批次已有进行中的申请");
                            }
                        });
                        if (repository.hasSuccessfulTransfer(connection, student.studentId()))
                            throw error("TRANSFER_ALREADY_TRANSFERRED", "已有生效的转专业记录");
                        String appType = command.applicationType() == MajorTransferApplicationType.DIFFICULTY
                                ? "DIFFICULTY" : "ORDINARY";
                        String fromDeptId = null, fromDeptName = null, fromMajorName = null;
                        String fromClassName = null;
                        if (organizations != null) {
                            var major = organizations.findMajor(connection, student.majorId());
                            if (major.isPresent()) {
                                fromMajorName = major.get().majorName();
                                var dept = organizations.findDepartment(connection, major.get().departmentId());
                                if (dept.isPresent()) {
                                    fromDeptId = dept.get().departmentId();
                                    fromDeptName = dept.get().departmentName();
                                }
                            }
                            var cls = organizations.findClass(connection, student.classId());
                            if (cls.isPresent()) fromClassName = cls.get().className();
                        }
                        MajorTransferRepository.ApplicationRow draft =
                                new MajorTransferRepository.ApplicationRow(
                                        UUID.randomUUID().toString(), batch.batchId(),
                                        student.studentId(),
                                        command.applicationType(), DRAFT, command.optionId(),
                                        fromDeptId != null ? fromDeptId : "",
                                        fromDeptName != null ? fromDeptName : "",
                                        student.majorId(),
                                        fromMajorName != null ? fromMajorName : "",
                                        student.classId(),
                                        fromClassName != null ? fromClassName : "",
                                        student.studentNumber(), Integer.toString(organizations.findClass(connection, student.classId()).orElseThrow().enrollmentYear()),
                                        student.studentName(), command.reason(),
                                        null, null, null,
                                        student.rowVersion(), 0,
                                        null, null, null, null, null, null, null,
                                        now, now);
                        repository.insertDraft(connection, draft);
                    }
                    MajorTransferRepository.ApplicationRow saved =
                            repository.findApplicationByBatchStudent(connection, batch.batchId(),
                                    student.studentId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    return toApplicationView(connection, saved);
                }));
    }
}
