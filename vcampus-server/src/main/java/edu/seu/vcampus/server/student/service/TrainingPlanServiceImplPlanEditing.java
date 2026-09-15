package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.SaveTrainingPlanCommand;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Plan create/update flow for the training plan segments. */
abstract class TrainingPlanServiceImplPlanEditing extends TrainingPlanServiceImplPlanQueries {

    /** Creates the plan-editing segment. */
    protected TrainingPlanServiceImplPlanEditing(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    @Override
    public TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId) {
        return savePlan(command, operatorUserId, null);
    }

    @Override
    public TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId,
            String departmentId) {
        Objects.requireNonNull(command.majorId());
        Objects.requireNonNull(command.planName());
        if (command.minElectiveCount() < 0)
            throw new IllegalArgumentException("minElectiveCount must be non-negative");
        if (command.minElectiveCredits().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("minElectiveCredits must be non-negative");
        return locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN",
                command.majorId() + ":" + command.enrollmentYear())),
                () -> transactions.inTransaction(connection -> {
            organizations.findMajor(connection, command.majorId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_MAJOR_NOT_FOUND", "专业不存在"));
            requireMajor(connection, command.majorId(), departmentId);
            Instant now = Instant.now();
            if (command.planId() == null || command.planId().isBlank()) {
                plans.findByMajorAndYear(connection, command.majorId(), command.enrollmentYear())
                        .ifPresent(existing -> {
                            throw new TrainingPlanException("TRAINING_PLAN_DUPLICATE",
                                    "该专业年级的培养方案已存在");
                        });
                String planId = UUID.randomUUID().toString();
                TrainingPlan plan = new TrainingPlan(planId, command.majorId(),
                        command.enrollmentYear(), command.planName(),
                        command.minElectiveCount(), command.minElectiveCredits(),
                        command.isActive(), 0, now, now);
                plans.insert(connection, plan);
                return detailView(connection, plan);
            } else {
                TrainingPlan existing = plans.findById(connection, command.planId())
                        .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
                requireMajor(connection, existing.majorId(), departmentId);
                TrainingPlan updated = new TrainingPlan(existing.planId(), existing.majorId(),
                        existing.enrollmentYear(), command.planName(),
                        command.minElectiveCount(), command.minElectiveCredits(),
                        command.isActive(), existing.rowVersion(), existing.createdAt(), now);
                plans.update(connection, updated, command.expectedVersion());
                return detailView(connection, plans.findById(connection, existing.planId()).orElseThrow());
            }
        }));
    }
}
