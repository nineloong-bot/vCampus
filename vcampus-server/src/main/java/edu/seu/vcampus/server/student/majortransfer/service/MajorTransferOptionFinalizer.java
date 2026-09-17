package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferOptionFinalizationRepository;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.numbering.StudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;

import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** Final-reviews, rolls back, and effectuates one target-major option atomically. */
public final class MajorTransferOptionFinalizer {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final MajorTransferRepository transfers;
    private final MajorTransferBatchProcessor processor;
    private final MajorTransferOptionFinalizationRepository finalizations =
            new MajorTransferOptionFinalizationRepository();
    private final MajorTransferOptionReadinessEvaluator readiness;
    private final MajorTransferBatchRanker ranker = new MajorTransferBatchRanker();
    private final MajorTransferOptionFinalizationSupport support;

    /** Creates an option finalizer with caller-owned repositories and ports. */
    public MajorTransferOptionFinalizer(TransactionManager transactions, ResourceLockManager locks,
            MajorTransferRepository transfers, StudentRepository students,
            StudentChangeRepository changes, AccessOrganizationRepository organizations,
            StudentNumberGenerator numbers, MajorTransferEnrollmentPort enrollments) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.transfers = Objects.requireNonNull(transfers);
        this.processor = new MajorTransferBatchProcessor(transfers, students, changes,
                organizations, numbers, enrollments);
        this.readiness = new MajorTransferOptionReadinessEvaluator(transfers, finalizations);
        this.support = new MajorTransferOptionFinalizationSupport(transactions, transfers);
    }

    /** Returns transaction-time readiness for an option owned by the trusted college. */
    public MajorTransferOptionReadinessView readiness(String optionId, String departmentId) {
        return transactions.inTransaction(c -> readiness.evaluate(c, optionId, departmentId));
    }

    /** Gives final approval only to assessed applications for the selected option. */
    public MajorTransferOptionReviewResult finalizeOption(String operator,
            FinalizeMajorTransferOptionCommand command, String departmentId) {
        return locks.withLocks(support.lockKeys(command.optionId()), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.optionId(), departmentId);
            requireVersion(state.optionVersion(), command.expectedOptionVersion());
            requireReviewable(state);
            var option = transfers.findOption(c, command.optionId()).orElseThrow();
            var applications = transfers.listApplicationsByOption(c, command.optionId(),
                    MajorTransferStatus.ASSESSED);
            var options = Map.of(option.optionId(), option);
            var ranked = ranker.rank(applications, options);
            var assignments = processor.plan(c, ranked.approved(), options);
            Instant now = Instant.now();
            List<MajorTransferOptionFinalizationRepository.PreparedTransferRow> prepared = new ArrayList<>();
            for (var assignment : assignments) {
                var app = support.find(ranked.approved(), assignment.applicationId());
                var student = processor.currentStudent(c, app);
                prepared.add(new MajorTransferOptionFinalizationRepository.PreparedTransferRow(
                        app.applicationId(), app.batchId(), departmentId, assignment.targetMajorId(),
                        assignment.targetClass().classId(), assignment.cohortYear(),
                        student.rowVersion(), app.applicationVersion() + 1, now));
            }
            finalizations.replacePrepared(c, option.optionId(), prepared);
            support.decide(c, ranked.approved(), MajorTransferStatus.PENDING_EFFECTIVE,
                    MajorTransferDecision.APPROVE, "专业终审通过", operator, now);
            support.decide(c, ranked.rejected(), MajorTransferStatus.REJECTED,
                    MajorTransferDecision.REJECT, "成绩排名超出专业招生名额", operator, now);
            updateLifecycle(c, option.optionId(), MajorTransferOptionFinalizationStatus.PROCESSING,
                    MajorTransferOptionFinalizationStatus.REVIEWED, state.optionVersion(), operator, now);
            return new MajorTransferOptionReviewResult(option.optionId(), option.batchId(),
                    option.targetMajorName(), prepared.size(),
                    MajorTransferOptionFinalizationStatus.REVIEWED, state.optionVersion() + 1);
        }));
    }

    /** Applies every previously approved application for the selected option exactly once. */
    public MajorTransferOptionEffectResult effectiveOption(String operator,
            EffectiveMajorTransferOptionCommand command, String departmentId) {
        return locks.withLocks(support.lockKeys(command.optionId()), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.optionId(), departmentId);
            requireVersion(state.optionVersion(), command.expectedOptionVersion());
            if (!state.canEffect()) throw error("TRANSFER_OPTION_NOT_READY", state.reason());
            var option = transfers.findOption(c, command.optionId()).orElseThrow();
            var applications = transfers.listApplicationsByOption(c, command.optionId(),
                    MajorTransferStatus.PENDING_EFFECTIVE);
            var prepared = finalizations.listPrepared(c, command.optionId());
            if (applications.size() != prepared.size()) {
                throw error("TRANSFER_PREPARATION_STALE", "终审准备数据已变化，请撤销终审后重试");
            }
            var assignments = processor.preparedAssignments(c, applications,
                    support.legacyPrepared(prepared), Map.of(option.optionId(), option));
            Instant now = Instant.now(); int dropped = 0;
            for (var assignment : assignments) {
                dropped += processor.apply(c, operator, now, assignment,
                        support.find(applications, assignment.applicationId()), option);
            }
            updateLifecycle(c, option.optionId(), MajorTransferOptionFinalizationStatus.REVIEWED,
                    MajorTransferOptionFinalizationStatus.EFFECTIVE, state.optionVersion(), operator, now);
            return new MajorTransferOptionEffectResult(option.optionId(), option.batchId(),
                    option.targetMajorName(), assignments.size(), dropped,
                    MajorTransferOptionFinalizationStatus.EFFECTIVE, state.optionVersion() + 1);
        }));
    }

    /** Rolls back the selected option's final-review decisions before effectuation. */
    public MajorTransferOptionRollbackResult rollbackOption(String operator,
            RollbackMajorTransferOptionCommand command, String departmentId) {
        return locks.withLocks(support.lockKeys(command.optionId()), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.optionId(), departmentId);
            requireVersion(state.optionVersion(), command.expectedOptionVersion());
            if (!state.canRollback()) throw error("TRANSFER_OPTION_REVIEW_NOT_REVERSIBLE",
                    "该专业尚未终审或已经生效");
            var option = transfers.findOption(c, command.optionId()).orElseThrow();
            var applications = transfers.listApplicationsByOption(c, command.optionId()).stream()
                    .filter(app -> support.wasFinalApproval(c, app)).toList();
            Instant now = Instant.now();
            for (var app : applications) {
                if (transfers.updateApplicationStatus(c, app.applicationId(), app.status(),
                        MajorTransferStatus.ASSESSED, app.applicationVersion(), now) != 1) throw concurrent();
                support.audit(c, app, MajorTransferDecision.REJECT, "撤销专业终审", operator,
                        MajorTransferReviewStage.FINAL_APPROVAL_ROLLBACK, now);
            }
            finalizations.deletePrepared(c, option.optionId());
            updateLifecycle(c, option.optionId(), MajorTransferOptionFinalizationStatus.REVIEWED,
                    MajorTransferOptionFinalizationStatus.PROCESSING, state.optionVersion(), operator, now);
            return new MajorTransferOptionRollbackResult(option.optionId(), option.batchId(),
                    option.targetMajorName(), applications.size(),
                    MajorTransferOptionFinalizationStatus.PROCESSING, state.optionVersion() + 1);
        }));
    }

    private void requireReviewable(MajorTransferOptionReadinessView state) {
        if (state.status() == MajorTransferOptionFinalizationStatus.PROCESSING
                && state.assessed() + state.pendingEffective() + state.rejected()
                + state.cancelled() + state.unresolved() == 0) {
            throw error("TRANSFER_OPTION_NO_APPLICATIONS", "该专业暂无转入申请");
        }
        if (state.status() == MajorTransferOptionFinalizationStatus.PROCESSING
                && state.assessed() == 0 && state.unresolved() == 0) {
            throw error("TRANSFER_OPTION_NO_ASSESSED_APPLICATIONS", "该专业没有待终审申请");
        }
        if (!state.canReview()) throw error("TRANSFER_OPTION_NOT_READY", state.reason());
    }

    private void updateLifecycle(Connection c, String optionId,
            MajorTransferOptionFinalizationStatus from, MajorTransferOptionFinalizationStatus to,
            long version, String operator, Instant now) {
        if (finalizations.updateStatus(c, optionId, from, to, version, operator, now) != 1) throw concurrent();
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw concurrent();
    }

    private static ConcurrentModificationException concurrent() {
        return new ConcurrentModificationException("转专业数据已被修改");
    }

    private static MajorTransferException error(String code, String message) {
        return new MajorTransferException(code, message == null ? "当前状态不可操作" : message);
    }
}
