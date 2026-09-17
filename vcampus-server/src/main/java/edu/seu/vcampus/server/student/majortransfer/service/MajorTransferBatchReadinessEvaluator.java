package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferCollegeReadinessView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferCollegeStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;

import java.sql.Connection;
import java.util.List;

final class MajorTransferBatchReadinessEvaluator {
    private final MajorTransferRepository transfers;
    private final edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferCollegeBatchRepository colleges;

    MajorTransferBatchReadinessEvaluator(MajorTransferRepository transfers,
            edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferCollegeBatchRepository colleges) {
        this.transfers = transfers;
        this.colleges = colleges;
    }

    MajorTransferCollegeReadinessView evaluate(Connection connection, String batchId,
            String departmentId) {
        var batch = transfers.findBatch(connection, batchId).orElseThrow(() ->
                new MajorTransferException("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
        var options = transfers.listOptionsByBatch(connection, batchId).stream()
                .filter(MajorTransferRepository.OptionRow::active)
                .filter(option -> departmentId.equals(option.targetDepartmentId())).toList();
        if (options.isEmpty()) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
        var college = colleges.findOrCreate(connection, batchId, departmentId, java.time.Instant.now());
        var applications = transfers.listApplicationsByBatchAndTargetCollege(
                connection, batchId, departmentId);
        int assessed = count(applications, MajorTransferStatus.ASSESSED);
        int rejected = count(applications, MajorTransferStatus.REJECTED);
        int cancelled = count(applications, MajorTransferStatus.CANCELLED);
        int pendingEffective = count(applications, MajorTransferStatus.PENDING_EFFECTIVE);
        int unresolved = applications.size() - assessed - pendingEffective - rejected - cancelled;
        boolean processing = college.status() == MajorTransferCollegeStatus.PROCESSING;
        boolean reviewed = college.status() == MajorTransferCollegeStatus.REVIEWED;
        String reason = readinessReason(batch.status(), processing, reviewed, assessed,
                pendingEffective, unresolved, applications, options);
        boolean readyNow = reason == null;
        return new MajorTransferCollegeReadinessView(batchId, departmentId, college.status(),
                assessed, pendingEffective, rejected, cancelled, unresolved,
                processing && readyNow, reviewed, reviewed && readyNow, reason,
                college.rowVersion());
    }

    private String readinessReason(MajorTransferBatchStatus batchStatus, boolean processing,
            boolean reviewed, int assessed, int pendingEffective, int unresolved,
            List<MajorTransferRepository.ApplicationRow> applications,
            List<MajorTransferRepository.OptionRow> options) {
        if (batchStatus != MajorTransferBatchStatus.CLOSED) return "批次尚未关闭";
        if (unresolved > 0) return "还有 " + unresolved + " 份申请未处理完毕";
        if (processing && pendingEffective > 0) return "存在未回退的待生效申请";
        if (reviewed && assessed > 0) return "还有 " + assessed + " 份申请未处理完毕";
        return quotaReason(applications, options);
    }

    private String quotaReason(List<MajorTransferRepository.ApplicationRow> applications,
            List<MajorTransferRepository.OptionRow> options) {
        for (var option : options) {
            long accepted = applications.stream().filter(a ->
                    (a.status() == MajorTransferStatus.ASSESSED
                            || a.status() == MajorTransferStatus.PENDING_EFFECTIVE)
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
