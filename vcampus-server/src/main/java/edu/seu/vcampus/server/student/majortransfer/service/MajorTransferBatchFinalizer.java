package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferCollegeBatchRepository;
import edu.seu.vcampus.server.student.numbering.StudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;

import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Atomically validates and applies all successful transfers in one target-college batch. */
public final class MajorTransferBatchFinalizer {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final MajorTransferRepository transfers;
    private final MajorTransferBatchProcessor processor;
    private final MajorTransferBatchReadinessEvaluator readiness;
    private final MajorTransferCollegeBatchRepository colleges = new MajorTransferCollegeBatchRepository();

    /** Creates a batch finalizer with caller-owned repositories and ports. */
    public MajorTransferBatchFinalizer(TransactionManager transactions, ResourceLockManager locks,
            MajorTransferRepository transfers, StudentRepository students,
            StudentChangeRepository changes, AccessOrganizationRepository organizations,
            StudentNumberGenerator numbers, MajorTransferEnrollmentPort enrollments) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.transfers = Objects.requireNonNull(transfers);
        this.processor = new MajorTransferBatchProcessor(transfers,
                Objects.requireNonNull(students), Objects.requireNonNull(changes),
                Objects.requireNonNull(organizations), Objects.requireNonNull(numbers),
                Objects.requireNonNull(enrollments));
        this.readiness = new MajorTransferBatchReadinessEvaluator(transfers, colleges);
    }

    /** Returns transaction-time readiness for a batch owned by the trusted target college. */
    public MajorTransferCollegeReadinessView readiness(String batchId, String departmentId) {
        return transactions.inTransaction(c -> readiness.evaluate(c, batchId, departmentId));
    }

    private final MajorTransferBatchRanker ranker = new MajorTransferBatchRanker();

    /** Gives final approval to every assessed application without changing enrollment. */
    public MajorTransferBatchReviewResult finalizeBatch(String operatorUserId,
            FinalizeMajorTransferBatchCommand command, String departmentId) {
        return locks.withLocks(lockKeys(command.batchId(), departmentId), () -> transactions.inTransaction(c -> {
            MajorTransferCollegeReadinessView state = readiness.evaluate(c, command.batchId(), departmentId);
            if (state.collegeVersion() != command.expectedCollegeVersion()) {
                throw new ConcurrentModificationException("转专业批次已被修改");
            }
            if (!state.canReview()) throw error("TRANSFER_BATCH_NOT_READY", state.reason());
            var applications = transfers.listApplicationsByBatchAndTargetCollege(c,
                    command.batchId(), departmentId).stream()
                    .filter(a -> a.status() == MajorTransferStatus.ASSESSED).toList();
            var options = optionMap(c, command.batchId(), departmentId);
            var ranked = ranker.rank(applications, options);
            var assignments = processor.plan(c, ranked.approved(), options);
            Instant now = Instant.now();
            List<MajorTransferCollegeBatchRepository.PreparedTransferRow> prepared = new ArrayList<>();
            for (var assignment : assignments) {
                var app = ranked.approved().stream().filter(value -> value.applicationId()
                        .equals(assignment.applicationId())).findFirst().orElseThrow();
                var student = processor.currentStudent(c, app);
                prepared.add(new MajorTransferCollegeBatchRepository.PreparedTransferRow(
                        app.applicationId(), app.batchId(), departmentId,
                        assignment.targetMajorId(), assignment.targetClass().classId(),
                        assignment.cohortYear(), student.rowVersion(),
                        app.applicationVersion() + 1, now));
            }
            colleges.replacePrepared(c, command.batchId(), departmentId, prepared);
            for (var app : ranked.approved()) {
                if (transfers.updateApplicationStatus(c, app.applicationId(),
                        MajorTransferStatus.ASSESSED, MajorTransferStatus.PENDING_EFFECTIVE,
                        app.applicationVersion(), now) != 1) throw concurrent();
                transfers.insertReview(c, new MajorTransferRepository.ReviewRow(UUID.randomUUID().toString(),
                        app.applicationId(), MajorTransferReviewStage.FINAL_APPROVAL,
                        MajorTransferDecision.APPROVE, operatorUserId, "批次终审通过", null, null, null, now));
            }
            for (var app : ranked.rejected()) {
                if (transfers.updateApplicationStatus(c, app.applicationId(),
                        MajorTransferStatus.ASSESSED, MajorTransferStatus.REJECTED,
                        app.applicationVersion(), now) != 1) throw concurrent();
                transfers.insertReview(c, new MajorTransferRepository.ReviewRow(UUID.randomUUID().toString(),
                        app.applicationId(), MajorTransferReviewStage.FINAL_APPROVAL,
                        MajorTransferDecision.REJECT, operatorUserId, "成绩排名超出招生名额自动淘汰", null, null, null, now));
            }
            if (colleges.updateStatus(c, command.batchId(), departmentId,
                    MajorTransferCollegeStatus.PROCESSING, MajorTransferCollegeStatus.REVIEWED,
                    command.expectedCollegeVersion(), operatorUserId, now) != 1) throw concurrent();
            return new MajorTransferBatchReviewResult(command.batchId(), departmentId,
                    ranked.approved().size(), MajorTransferCollegeStatus.REVIEWED,
                    command.expectedCollegeVersion() + 1);
        }));
    }

    /** Applies all previously approved applications in one transaction. */
    public MajorTransferBatchEffectResult effectiveBatch(String operatorUserId,
            EffectiveMajorTransferBatchCommand command, String departmentId) {
        return locks.withLocks(lockKeys(command.batchId(), departmentId), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.batchId(), departmentId);
            if (state.collegeVersion() != command.expectedCollegeVersion()) {
                throw new ConcurrentModificationException("转专业批次已被修改");
            }
            if (!state.canEffect()) {
                throw error("TRANSFER_BATCH_NOT_READY",
                        state.reason() != null ? state.reason() : "还有转专业申请未处理完毕");
            }
            var applications = transfers.listApplicationsByBatchAndTargetCollege(c,
                    command.batchId(), departmentId).stream()
                    .filter(a -> a.status() == MajorTransferStatus.PENDING_EFFECTIVE).toList();
            var prepared = colleges.listPrepared(c, command.batchId(), departmentId);
            if (applications.size() != prepared.size())
                throw error("TRANSFER_PREPARATION_STALE", "终审准备数据已变化，请撤销终审后重试");
            var options = optionMap(c, command.batchId(), departmentId);
            var assignments = processor.preparedAssignments(c, applications, prepared, options);
            Instant now = Instant.now(); int dropped = 0;
            for (var assignment : assignments) {
                dropped += processor.apply(c, operatorUserId, now, assignment,
                        applications.stream().filter(a -> a.applicationId().equals(assignment.applicationId())).findFirst().orElseThrow(),
                        options.get(assignment.optionId()));
            }
            if (colleges.updateStatus(c, command.batchId(), departmentId,
                    MajorTransferCollegeStatus.REVIEWED, MajorTransferCollegeStatus.EFFECTIVE,
                    command.expectedCollegeVersion(), operatorUserId, now) != 1) throw concurrent();
            return new MajorTransferBatchEffectResult(command.batchId(), departmentId,
                    assignments.size(), dropped, MajorTransferCollegeStatus.EFFECTIVE,
                    command.expectedCollegeVersion() + 1);
        }));
    }

    /** Rolls back final approval without changing student enrollment. */
    public MajorTransferBatchRollbackResult rollbackBatch(String operatorUserId,
            RollbackMajorTransferBatchCommand command, String departmentId) {
        return locks.withLocks(lockKeys(command.batchId(), departmentId), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.batchId(), departmentId);
            if (state.collegeVersion() != command.expectedCollegeVersion()) throw new ConcurrentModificationException("转专业批次已被修改");
            if (!state.canRollback()) throw error("TRANSFER_COLLEGE_REVIEW_NOT_REVERSIBLE",
                    "本学院尚未终审或已经生效");
            var apps = transfers.listApplicationsByBatchAndTargetCollege(c,
                    command.batchId(), departmentId).stream()
                    .filter(a -> a.status() == MajorTransferStatus.PENDING_EFFECTIVE).toList();
            Instant now = Instant.now();
            for (var app : apps) {
                if (transfers.updateApplicationStatus(c, app.applicationId(),
                        MajorTransferStatus.PENDING_EFFECTIVE, MajorTransferStatus.ASSESSED,
                        app.applicationVersion(), now) != 1) throw concurrent();
                transfers.insertReview(c, new MajorTransferRepository.ReviewRow(
                        UUID.randomUUID().toString(), app.applicationId(),
                        MajorTransferReviewStage.FINAL_APPROVAL_ROLLBACK,
                        MajorTransferDecision.REJECT, operatorUserId, "撤销批次终审",
                        null, null, null, now));
            }
            colleges.deletePrepared(c, command.batchId(), departmentId);
            if (colleges.updateStatus(c, command.batchId(), departmentId,
                    MajorTransferCollegeStatus.REVIEWED, MajorTransferCollegeStatus.PROCESSING,
                    command.expectedCollegeVersion(), operatorUserId, now) != 1) throw concurrent();
            return new MajorTransferBatchRollbackResult(command.batchId(), departmentId,
                    apps.size(), MajorTransferCollegeStatus.PROCESSING,
                    command.expectedCollegeVersion() + 1);
        }));
    }

    private Map<String, MajorTransferRepository.OptionRow> optionMap(Connection c, String batchId,
            String departmentId) {
        return transfers.listOptionsByBatch(c, batchId).stream().filter(
                MajorTransferRepository.OptionRow::active)
                .filter(option -> departmentId.equals(option.targetDepartmentId())).collect(Collectors.toMap(
                MajorTransferRepository.OptionRow::optionId, Function.identity()));
    }

    private List<ResourceKey> lockKeys(String batchId, String departmentId) {
        List<ResourceKey> keys = new ArrayList<>();
        keys.add(new ResourceKey("TRANSFER_BATCH", batchId));
        keys.add(new ResourceKey("TRANSFER_BATCH_COLLEGE", batchId + ":" + departmentId));
        transactions.inTransaction(c -> { transfers.listApplicationsByBatchAndTargetCollege(
                c, batchId, departmentId).forEach(app -> {
            keys.add(new ResourceKey("TRANSFER_APPLICATION", app.applicationId()));
            keys.add(new ResourceKey("STUDENT", app.studentId()));
        }); return null; });
        return keys;
    }

    private static ConcurrentModificationException concurrent() {
        return new ConcurrentModificationException("转专业数据已被修改");
    }

    private static MajorTransferException error(String code, String message) {
        return new MajorTransferException(code, message);
    }
}
