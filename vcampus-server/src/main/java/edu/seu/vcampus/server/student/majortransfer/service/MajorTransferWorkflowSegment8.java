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
abstract class MajorTransferWorkflowSegment8 extends MajorTransferWorkflowSegment7 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment8(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public MajorTransferApplicationView reviewQualification(String adminUserId,
            ReviewMajorTransferQualificationCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireTargetDepartment(connection, app, trustedDepartmentId);
                    if (app.status() != SOURCE_APPROVED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许转入学院资格审核");
                    if (command.decision() == MajorTransferDecision.APPROVE) {
                        changeStatus(connection, command.applicationId(),
                                SOURCE_APPROVED, QUALIFIED, command.expectedVersion(), Instant.now());
                    } else {
                        if (command.comment() == null || command.comment().isBlank())
                            throw error("TRANSFER_REJECTION_REASON", "驳回原因不能为空");
                        changeStatus(connection, command.applicationId(),
                                SOURCE_APPROVED, REJECTED, command.expectedVersion(), Instant.now());
                    }
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.QUALIFICATION_REVIEW, command.decision(),
                            adminUserId, command.comment(),
                            null, null, null, Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView cancel(String adminUserId, CancelMajorTransferCommand command) {
        return cancel(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView cancel(String adminUserId,
            CancelMajorTransferCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireTargetDepartment(connection, app, trustedDepartmentId);
                    if (!MajorTransferStateMachine.adminMayCancel(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许取消");
                    changeStatus(connection, command.applicationId(),
                            app.status(), CANCELLED, command.expectedVersion(), Instant.now());
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.EXECUTION, MajorTransferDecision.REJECT,
                            adminUserId, command.reason(),
                            null, null, null, Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    // ── Admin: assessment and ranking ──

    @Override
    public MajorTransferApplicationView recordScore(String adminUserId,
                                                     RecordMajorTransferScoreCommand command) {
        return recordScore(adminUserId, command, null);
    }
}
