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
abstract class MajorTransferWorkflowSegment9 extends MajorTransferWorkflowSegment8 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment9(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public MajorTransferApplicationView recordScore(String adminUserId,
            RecordMajorTransferScoreCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (app.status() != QUALIFIED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许录入成绩");
                    MajorTransferRepository.OptionRow option =
                            repository.findOption(connection, app.optionId()).orElseThrow();
                    requireDepartment(trustedDepartmentId, option.targetDepartmentId());
                    Double written = command.writtenScore() != null
                            ? command.writtenScore().doubleValue() : null;
                    Double interview = command.interviewScore() != null
                            ? command.interviewScore().doubleValue() : null;
                    validateScore(written, option.writtenWeightPct());
                    validateScore(interview, option.interviewWeightPct());
                    Double finalScore = finalScore(written, interview,
                            option.writtenWeightPct(), option.interviewWeightPct());
                    requireChanged(repository.recordScores(connection, command.applicationId(),
                            written, interview, finalScore,
                            command.expectedVersion(), Instant.now()));
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), app.applicationId(), MajorTransferReviewStage.ASSESSMENT,
                            MajorTransferDecision.APPROVE, adminUserId, "考核成绩已录入，综合成绩：" + finalScore,
                            null, null, null, Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferImportResult importScores(String adminUserId,
                                                   ImportMajorTransferScoresCommand command) {
        return importScores(adminUserId, command, null);
    }
}
