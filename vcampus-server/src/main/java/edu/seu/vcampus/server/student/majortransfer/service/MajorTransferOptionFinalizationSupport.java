package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferDecision;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferReviewStage;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferCollegeBatchRepository;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferOptionFinalizationRepository;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Shared audit, locking, and snapshot helpers for option finalization. */
final class MajorTransferOptionFinalizationSupport {
    private final TransactionManager transactions;
    private final MajorTransferRepository transfers;

    MajorTransferOptionFinalizationSupport(TransactionManager transactions,
            MajorTransferRepository transfers) {
        this.transactions = transactions;
        this.transfers = transfers;
    }

    List<ResourceKey> lockKeys(String optionId) {
        List<ResourceKey> keys = new ArrayList<>();
        keys.add(new ResourceKey("TRANSFER_OPTION_FINALIZATION", optionId));
        transactions.inTransaction(c -> {
            transfers.findOption(c, optionId).ifPresent(option ->
                    keys.add(new ResourceKey("TRANSFER_BATCH", option.batchId())));
            var applications = transfers.listApplicationsByOption(c, optionId);
            applications.stream().sorted(Comparator.comparing(
                            MajorTransferRepository.ApplicationRow::applicationId))
                    .forEach(app -> keys.add(new ResourceKey(
                            "TRANSFER_APPLICATION", app.applicationId())));
            applications.stream().map(MajorTransferRepository.ApplicationRow::studentId)
                    .distinct().sorted().forEach(id -> keys.add(new ResourceKey("STUDENT", id)));
            return null;
        });
        return keys;
    }

    void decide(Connection connection,
            List<MajorTransferRepository.ApplicationRow> applications,
            MajorTransferStatus target, MajorTransferDecision decision, String comment,
            String operator, Instant now) {
        for (var app : applications) {
            if (transfers.updateApplicationStatus(connection, app.applicationId(),
                    MajorTransferStatus.ASSESSED, target, app.applicationVersion(), now) != 1) {
                throw new java.util.ConcurrentModificationException("转专业数据已被修改");
            }
            audit(connection, app, decision, comment, operator,
                    MajorTransferReviewStage.FINAL_APPROVAL, now);
        }
    }

    void audit(Connection connection, MajorTransferRepository.ApplicationRow app,
            MajorTransferDecision decision, String comment, String operator,
            MajorTransferReviewStage stage, Instant now) {
        transfers.insertReview(connection, new MajorTransferRepository.ReviewRow(
                UUID.randomUUID().toString(), app.applicationId(), stage, decision,
                operator, comment, null, null, null, now));
    }

    boolean wasFinalApproval(Connection connection,
            MajorTransferRepository.ApplicationRow application) {
        if (application.status() != MajorTransferStatus.PENDING_EFFECTIVE
                && application.status() != MajorTransferStatus.REJECTED) return false;
        return transfers.listReviews(connection, application.applicationId()).stream()
                .filter(review -> review.reviewStage() == MajorTransferReviewStage.FINAL_APPROVAL
                        || review.reviewStage() == MajorTransferReviewStage.FINAL_APPROVAL_ROLLBACK)
                .reduce((first, second) -> second)
                .map(review -> review.reviewStage() == MajorTransferReviewStage.FINAL_APPROVAL)
                .orElse(false);
    }

    List<MajorTransferCollegeBatchRepository.PreparedTransferRow> legacyPrepared(
            List<MajorTransferOptionFinalizationRepository.PreparedTransferRow> rows) {
        return rows.stream().map(row -> new MajorTransferCollegeBatchRepository.PreparedTransferRow(
                row.applicationId(), row.batchId(), row.targetDepartmentId(), row.targetMajorId(),
                row.targetClassId(), row.targetCohortYear(), row.studentVersion(),
                row.applicationVersion(), row.preparedAt())).toList();
    }

    MajorTransferRepository.ApplicationRow find(
            List<MajorTransferRepository.ApplicationRow> rows, String applicationId) {
        return rows.stream().filter(row -> row.applicationId().equals(applicationId))
                .findFirst().orElseThrow();
    }
}
