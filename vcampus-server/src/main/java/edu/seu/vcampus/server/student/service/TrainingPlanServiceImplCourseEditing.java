package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.ImportTrainingPlanCoursesCommand;
import edu.seu.vcampus.common.student.SaveTrainingPlanCourseCommand;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Plan-course create, update, removal and import flows. */
abstract class TrainingPlanServiceImplCourseEditing extends TrainingPlanServiceImplPlanEditing {

    /** Creates the plan-course editing segment. */
    protected TrainingPlanServiceImplCourseEditing(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    @Override
    public TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command,
            String operatorUserId) {
        return saveCourse(command, operatorUserId, null);
    }

    @Override
    public TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command,
            String operatorUserId, String departmentId) {
        validateCourse(command.planId(), command.courseCode(), command.courseName(),
                command.credits(), command.courseType(), command.semester());
        return locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN_COURSES", command.planId())),
                () -> transactions.inTransaction(connection -> {
            TrainingPlan plan = plans.findById(connection, command.planId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
            requireMajor(connection, plan.majorId(), departmentId);
            Instant now = Instant.now();
            if (command.planCourseId() == null || command.planCourseId().isBlank()) {
                rejectDuplicateCourse(connection, command.planId(), command.courseCode(), null);
                String id = UUID.randomUUID().toString();
                TrainingPlanCourse course = new TrainingPlanCourse(id, command.planId(),
                        command.courseCode(), command.courseName(), command.credits(),
                        command.courseType(), command.semester(), command.isActive(), 0, now, now,
                        command.courseId(), command.offeringDepartmentId(), command.offeringDepartmentName(), command.allocatedQuota());
                plans.insertCourse(connection, course);
                return courseView(course);
            } else {
                TrainingPlanCourse existing = plans.findCourseById(connection, command.planCourseId())
                        .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_COURSE_NOT_FOUND", "课程不存在"));
                if (!existing.planId().equals(command.planId())) {
                    throw new TrainingPlanException("TRAINING_PLAN_COURSE_PLAN_MISMATCH",
                            "课程不属于指定的培养方案");
                }
                rejectDuplicateCourse(connection, command.planId(), command.courseCode(),
                        existing.planCourseId());
                TrainingPlanCourse updated = new TrainingPlanCourse(existing.planCourseId(),
                        existing.planId(), command.courseCode(), command.courseName(),
                        command.credits(), command.courseType(), command.semester(),
                        command.isActive(), existing.rowVersion(), existing.createdAt(), now,
                        command.courseId() != null ? command.courseId() : existing.courseId(),
                        command.offeringDepartmentId() != null ? command.offeringDepartmentId() : existing.offeringDepartmentId(),
                        command.offeringDepartmentName() != null ? command.offeringDepartmentName() : existing.offeringDepartmentName(),
                        command.allocatedQuota() != null ? command.allocatedQuota() : existing.allocatedQuota());
                plans.updateCourse(connection, updated, command.expectedVersion());
                return courseView(updated);
            }
        }));
    }

    @Override
    public void removeCourse(String planCourseId, String operatorUserId) {
        removeCourse(planCourseId, operatorUserId, null);
    }

    @Override
    public void removeCourse(String planCourseId, String operatorUserId, String departmentId) {
        locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN_COURSE", planCourseId)),
                () -> transactions.inTransaction(connection -> {
            TrainingPlanCourse course = plans.findCourseById(connection, planCourseId)
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_COURSE_NOT_FOUND", "课程不存在"));
            TrainingPlan plan = plans.findById(connection, course.planId()).orElseThrow();
            requireMajor(connection, plan.majorId(), departmentId);
            if (plans.hasGradesForCourse(connection, planCourseId)) {
                throw new TrainingPlanException("TRAINING_PLAN_COURSE_IN_USE",
                        "课程已有成绩记录，不能删除");
            }
            plans.deleteCourse(connection, planCourseId);
            return null;
        }));
    }

    @Override
    public List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command,
            String operatorUserId) {
        return importCourses(command, operatorUserId, null);
    }

    @Override
    public List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command,
            String operatorUserId, String departmentId) {
        Objects.requireNonNull(command.planId());
        Objects.requireNonNull(command.courses());
        command.courses().forEach(entry -> validateCourse(command.planId(), entry.courseCode(),
                entry.courseName(), entry.credits(), entry.courseType(), entry.semester()));
        return locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN_COURSES", command.planId())),
                () -> transactions.inTransaction(connection -> {
            TrainingPlan plan = plans.findById(connection, command.planId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
            requireMajor(connection, plan.majorId(), departmentId);
            var incomingCodes = new HashSet<String>();
            for (var entry : command.courses()) {
                String normalizedCode = entry.courseCode().trim().toUpperCase(Locale.ROOT);
                if (!incomingCodes.add(normalizedCode)) {
                    throw duplicateCourse(entry.courseCode());
                }
                rejectDuplicateCourse(connection, command.planId(), entry.courseCode(), null);
            }
            Instant now = Instant.now();
            return command.courses().stream().map(entry -> {
                String id = UUID.randomUUID().toString();
                TrainingPlanCourse course = new TrainingPlanCourse(id, command.planId(),
                        entry.courseCode(), entry.courseName(), entry.credits(),
                        entry.courseType(), entry.semester(), true, 0, now, now);
                plans.insertCourse(connection, course);
                return courseView(course);
            }).toList();
        }));
    }
}
