package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferOptionFinalizationStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferOptionReadinessView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferOptionFinalizationRepository;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;

/** Computes final-review readiness for one transfer option. */
final class MajorTransferOptionReadinessEvaluator {
    private final MajorTransferRepository transfers;
    private final MajorTransferOptionFinalizationRepository finalizations;

    MajorTransferOptionReadinessEvaluator(MajorTransferRepository transfers,
            MajorTransferOptionFinalizationRepository finalizations) {
        this.transfers = transfers;
        this.finalizations = finalizations;
    }

    MajorTransferOptionReadinessView evaluate(Connection connection, String optionId,
            String trustedDepartmentId) {
        var option = transfers.findOption(connection, optionId).orElseThrow(() ->
                new MajorTransferException("TRANSFER_OPTION_NOT_FOUND", "目标专业不存在"));
        if (!option.targetDepartmentId().equals(trustedDepartmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
        var batch = transfers.findBatch(connection, option.batchId()).orElseThrow(() ->
                new MajorTransferException("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
        var lifecycle = finalizations.findOrCreate(connection, option, Instant.now());
        var applications = transfers.listApplicationsByOption(connection, optionId).stream()
                .filter(row -> row.status() != MajorTransferStatus.DRAFT).toList();
        int assessed = count(applications, MajorTransferStatus.ASSESSED);
        int pending = count(applications, MajorTransferStatus.PENDING_EFFECTIVE);
        int rejected = count(applications, MajorTransferStatus.REJECTED);
        int cancelled = count(applications, MajorTransferStatus.CANCELLED);
        int unresolved = applications.size() - assessed - pending - rejected - cancelled
                - count(applications, MajorTransferStatus.EFFECTIVE);
        String reason = reason(batch.status(), lifecycle.status(), applications.size(),
                assessed, pending, unresolved);
        boolean processing = lifecycle.status() == MajorTransferOptionFinalizationStatus.PROCESSING;
        boolean reviewed = lifecycle.status() == MajorTransferOptionFinalizationStatus.REVIEWED;
        return new MajorTransferOptionReadinessView(option.optionId(), option.batchId(),
                option.targetDepartmentId(), option.targetMajorId(), option.targetMajorName(),
                lifecycle.status(), assessed, pending, rejected, cancelled, unresolved,
                processing && reason == null, reviewed, reviewed && reason == null,
                reason, lifecycle.rowVersion());
    }

    private static String reason(MajorTransferBatchStatus batchStatus,
            MajorTransferOptionFinalizationStatus status, int applications,
            int assessed, int pending, int unresolved) {
        if (batchStatus != MajorTransferBatchStatus.CLOSED) return "批次尚未关闭";
        if (status == MajorTransferOptionFinalizationStatus.EFFECTIVE) return "该专业已生效";
        if (unresolved > 0) return "还有 " + unresolved + " 份申请未处理完毕";
        if (status == MajorTransferOptionFinalizationStatus.PROCESSING) {
            if (applications == 0) return "该专业暂无转入申请";
            if (pending > 0) return "存在未回退的待生效申请";
            if (assessed == 0) return "该专业没有待终审申请";
        }
        if (status == MajorTransferOptionFinalizationStatus.REVIEWED && assessed > 0) {
            return "还有 " + assessed + " 份申请未处理完毕";
        }
        return null;
    }

    private static int count(List<MajorTransferRepository.ApplicationRow> rows,
            MajorTransferStatus status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }
}
