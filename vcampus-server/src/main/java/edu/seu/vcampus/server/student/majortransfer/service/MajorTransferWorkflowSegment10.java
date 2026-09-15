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
abstract class MajorTransferWorkflowSegment10 extends MajorTransferWorkflowSegment9 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment10(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
    }


    @Override
    public MajorTransferImportResult importScores(String adminUserId,
            ImportMajorTransferScoresCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_OPTION", command.optionId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.OptionRow option =
                            repository.findOption(connection, command.optionId())
                                    .orElseThrow(() -> error("TRANSFER_OPTION_NOT_FOUND", "选项不存在"));
                    requireDepartment(trustedDepartmentId, option.targetDepartmentId());
                    int total = command.entries().size();
                    int success = 0;
                    var failures = new java.util.ArrayList<MajorTransferImportResult.Failure>();
                    for (var entry : command.entries()) {
                        try {
                            MajorTransferRepository.ApplicationRow app =
                                    repository.findApplication(connection, entry.applicationId())
                                            .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                            if (!command.optionId().equals(app.optionId())) {
                                failures.add(new MajorTransferImportResult.Failure(
                                        entry.applicationId(), "申请不属于所选招生专业"));
                                continue;
                            }
                            if (app.status() != QUALIFIED) {
                                failures.add(new MajorTransferImportResult.Failure(
                                        entry.applicationId(), "申请状态不允许录入成绩"));
                                continue;
                            }
                            Double written = entry.writtenScore() != null
                                    ? entry.writtenScore().doubleValue() : null;
                            Double interview = entry.interviewScore() != null
                                    ? entry.interviewScore().doubleValue() : null;
                            validateScore(written, option.writtenWeightPct());
                            validateScore(interview, option.interviewWeightPct());
                            Double fs = finalScore(written, interview,
                                    option.writtenWeightPct(), option.interviewWeightPct());
                            requireChanged(repository.recordScores(connection, entry.applicationId(),
                                    written, interview, fs, app.applicationVersion(), Instant.now()));
                            repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                                    UUID.randomUUID().toString(), app.applicationId(),
                                    MajorTransferReviewStage.ASSESSMENT,
                                    MajorTransferDecision.APPROVE, adminUserId,
                                    "批量导入成绩，综合成绩：" + fs, null, null, null, Instant.now()));
                            success++;
                        } catch (Exception e) {
                            failures.add(new MajorTransferImportResult.Failure(
                                    entry.applicationId(), e.getMessage()));
                        }
                    }
                    return new MajorTransferImportResult(total, success, failures.size(), failures);
                }));
    }

    // ── Admin: final approval and execution ──

    @Override
    public MajorTransferApplicationView finalizeApproval(String adminUserId,
                                                          FinalizeMajorTransferCommand command) {
        return finalizeApproval(adminUserId, command, null);
    }
}
