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
abstract class MajorTransferWorkflowSegment7 extends MajorTransferWorkflowSegment6 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment7(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public List<MajorTransferApplicationView> listApplications(MajorTransferApplicationQuery query) {
        return listApplications(query, null);
    }

    @Override
    public List<MajorTransferApplicationView> listApplicationsForCollege(
            MajorTransferApplicationQuery query, String departmentId) {
        Objects.requireNonNull(departmentId, "departmentId");
        return listApplications(query, departmentId);
    }

    @Override
    public MajorTransferApplicationView getApplicationDetail(String applicationId) {
        return transactions.inTransaction(connection -> {
            MajorTransferRepository.ApplicationRow app =
                    repository.findApplication(connection, applicationId)
                            .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
            return toApplicationView(connection, app);
        });
    }

    @Override
    public MajorTransferApplicationView reviewSource(String adminUserId,
                                                      ReviewMajorTransferSourceCommand command) {
        return reviewSource(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView reviewSource(String adminUserId,
            ReviewMajorTransferSourceCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireDepartment(trustedDepartmentId, app.fromDepartmentId());
                    if (app.status() != SUBMITTED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许原学院审核");
                    if (command.decision() == MajorTransferDecision.APPROVE) {
                        if (!command.sourceVerified() || !command.noMisconduct() || !command.admissionAllowed())
                            throw error("TRANSFER_ATTESTATION_REQUIRED", "三项资格审核全部通过才能批准");
                        changeStatus(connection, command.applicationId(),
                                SUBMITTED, SOURCE_APPROVED, command.expectedVersion(), Instant.now());
                    } else {
                        if (command.comment() == null || command.comment().isBlank())
                            throw error("TRANSFER_REJECTION_REASON", "驳回原因不能为空");
                        changeStatus(connection, command.applicationId(),
                                SUBMITTED, REJECTED, command.expectedVersion(), Instant.now());
                    }
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.SOURCE_REVIEW, command.decision(),
                            adminUserId, command.comment(),
                            command.sourceVerified(), command.noMisconduct(),
                            command.admissionAllowed(), Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView reviewQualification(String adminUserId,
                                                             ReviewMajorTransferQualificationCommand command) {
        return reviewQualification(adminUserId, command, null);
    }
}
