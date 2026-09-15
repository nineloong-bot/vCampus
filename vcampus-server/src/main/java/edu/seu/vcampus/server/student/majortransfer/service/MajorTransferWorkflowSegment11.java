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
abstract class MajorTransferWorkflowSegment11 extends MajorTransferWorkflowSegment10 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment11(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public MajorTransferApplicationView finalizeApproval(String adminUserId,
            FinalizeMajorTransferCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireTargetDepartment(connection, app, trustedDepartmentId);
                    if (app.status() != ASSESSED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许终审");
                    Instant now = Instant.now();
                    changeStatus(connection, command.applicationId(),
                            ASSESSED, PENDING_EFFECTIVE, command.expectedVersion(), now);
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.FINAL_APPROVAL, MajorTransferDecision.APPROVE,
                            adminUserId, "终审通过",
                            null, null, null, now));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView execute(String adminUserId, ExecuteMajorTransferCommand command) {
        return execute(adminUserId, command, null);
    }
}
