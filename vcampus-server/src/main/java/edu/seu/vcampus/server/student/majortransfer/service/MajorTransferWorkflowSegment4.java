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
abstract class MajorTransferWorkflowSegment4 extends MajorTransferWorkflowSegment3 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment4(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public MajorTransferApplicationView submit(String userId, SubmitMajorTransferCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (!app.studentId().equals(studentId))
                        throw error("COMMON_FORBIDDEN", "无权操作");
                    if (!MajorTransferStateMachine.studentMaySubmit(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许提交");
                    if (app.reason() == null || app.reason().isBlank())
                        throw error("TRANSFER_REASON_REQUIRED", "申请理由不能为空");
                    if (app.applicationType() == MajorTransferApplicationType.DIFFICULTY) {
                        if (repository.countAttachments(connection, app.applicationId()) == 0)
                            throw error("TRANSFER_DIFFICULTY_EVIDENCE", "学困生申请必须上传至少一份证明材料");
                    }
                    MajorTransferRepository.BatchRow batch =
                            repository.findBatch(connection, app.batchId()).orElseThrow();
                    validateBatchOpen(batch, Instant.now());
                    Student student = students.findById(connection, studentId).orElseThrow(StudentNotFoundException::new);
                    validateEligibility(connection, student, batch,
                            repository.findOption(connection, app.optionId()).orElseThrow());
                    if (!student.classId().equals(app.fromClassId()))
                        throw error("TRANSFER_SOURCE_CHANGED", "原班级已变化，请重新核实申请");
                    int changed = repository.submitApplication(connection, app.applicationId(),
                            command.expectedVersion(), Instant.now());
                    if (changed == 0) throw concurrent();
                    return toApplicationView(connection,
                            repository.findApplication(connection, app.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView withdraw(String userId, WithdrawMajorTransferCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (!app.studentId().equals(studentId))
                        throw error("COMMON_FORBIDDEN", "无权操作");
                    if (!MajorTransferStateMachine.studentMayWithdraw(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许撤回");
                    int changed = changeStatus(connection, app.applicationId(),
                            SUBMITTED, DRAFT, command.expectedVersion(), Instant.now());
                    if (changed == 0) throw concurrent();
                    try (var statement = connection.prepareStatement("UPDATE tblMajorTransferApplication SET submittedAt=NULL WHERE applicationId=?")) {
                        statement.setString(1, app.applicationId()); statement.executeUpdate();
                    }
                    changes.insertChange(connection, UUID.randomUUID().toString(), studentId,
                            "TRANSFER_WITHDRAW", app.applicationId() + ":SUBMITTED", app.applicationId() + ":DRAFT",
                            "学生撤回转专业申请", userId, LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")), Instant.now());
                    return toApplicationView(connection,
                            repository.findApplication(connection, app.applicationId()).orElseThrow());
                }));
    }
}
