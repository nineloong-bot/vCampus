package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchReadinessView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;

import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class MajorTransferBatchReadinessEvaluator {
    private final MajorTransferRepository transfers;

    MajorTransferBatchReadinessEvaluator(MajorTransferRepository transfers) {
        this.transfers = transfers;
    }

    MajorTransferBatchReadinessView evaluate(Connection connection, String batchId,
            String departmentId) {
        var batch = transfers.findBatch(connection, batchId).orElseThrow(() ->
                new MajorTransferException("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
        var options = transfers.listOptionsByBatch(connection, batchId).stream()
                .filter(MajorTransferRepository.OptionRow::active).toList();
        Set<String> departments = options.stream().map(
                MajorTransferRepository.OptionRow::targetDepartmentId).collect(Collectors.toSet());
        if (departments.size() != 1 || !departments.contains(departmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
        var applications = transfers.listApplicationsByBatch(connection, batchId);
        int assessed = count(applications, MajorTransferStatus.ASSESSED);
        int rejected = count(applications, MajorTransferStatus.REJECTED);
        int cancelled = count(applications, MajorTransferStatus.CANCELLED);
        int unresolved = applications.size() - assessed - rejected - cancelled;
        String reason = batch.status() != MajorTransferBatchStatus.CLOSED ? "批次尚未关闭"
                : applications.isEmpty() ? "批次没有正式申请"
                : unresolved > 0 ? "还有 " + unresolved + " 份申请未处理完毕"
                : quotaReason(applications, options);
        return new MajorTransferBatchReadinessView(batchId, assessed, rejected, cancelled,
                unresolved, reason == null, reason, batch.rowVersion());
    }

    private String quotaReason(List<MajorTransferRepository.ApplicationRow> applications,
            List<MajorTransferRepository.OptionRow> options) {
        for (var option : options) {
            long accepted = applications.stream().filter(a ->
                    a.status() == MajorTransferStatus.ASSESSED
                            && option.optionId().equals(a.optionId())).count();
            if (accepted > option.receiveQuota()) {
                return option.targetMajorName() + "拟录取人数超过名额";
            }
        }
        return null;
    }

    private static int count(List<MajorTransferRepository.ApplicationRow> rows,
            MajorTransferStatus status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }
}
