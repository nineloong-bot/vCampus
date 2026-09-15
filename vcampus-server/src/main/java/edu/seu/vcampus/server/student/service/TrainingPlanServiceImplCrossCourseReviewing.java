package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CrossCourseApplicationStatus;
import edu.seu.vcampus.common.student.CrossCourseApplicationView;
import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.ReviewCrossCourseApplicationCommand;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.CrossCourseApplication;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cross-course application review flow and its plan side effect. */
abstract class TrainingPlanServiceImplCrossCourseReviewing extends TrainingPlanServiceImplCrossCourseSubmitting {

    /** Creates the cross-course reviewing segment. */
    protected TrainingPlanServiceImplCrossCourseReviewing(TransactionManager transactions,
            ResourceLockManager locks, TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    @Override
    public CrossCourseApplicationView reviewCrossCourseApplication(
            ReviewCrossCourseApplicationCommand command, String operatorUserId) {
        return reviewCrossCourseApplication(command, operatorUserId, null);
    }

    @Override
    public CrossCourseApplicationView reviewCrossCourseApplication(
            ReviewCrossCourseApplicationCommand command, String operatorUserId,
            String departmentId) {
        if (command.applicationId() == null || command.applicationId().isBlank()) {
            throw new IllegalArgumentException("申请ID不能为空");
        }
        if (command.approved() && (command.allocatedQuota() == null || command.allocatedQuota() <= 0)) {
            throw new IllegalArgumentException("同意申请时必须分配大于 0 的选课名额");
        }

        return transactions.inTransaction(connection -> {
            CrossCourseApplication app = crossApplications.findById(connection, command.applicationId())
                    .orElseThrow(() -> new TrainingPlanException("APPLICATION_NOT_FOUND", "跨学科申请不存在"));
            requireDepartment(app.offeringDepartmentId(), departmentId);
            if (app.status() != CrossCourseApplicationStatus.PENDING) {
                throw new TrainingPlanException("APPLICATION_NOT_PENDING", "该申请已完成审批，无法重复操作");
            }

            Instant now = Instant.now();
            CrossCourseApplicationStatus newStatus = command.approved()
                    ? CrossCourseApplicationStatus.APPROVED : CrossCourseApplicationStatus.REJECTED;
            Integer quota = command.approved() ? command.allocatedQuota() : null;

            CrossCourseApplication updated = new CrossCourseApplication(
                    app.applicationId(), app.courseId(), app.courseCode(), app.courseName(),
                    app.credits(), app.offeringDepartmentId(), app.offeringDepartmentName(),
                    app.targetDepartmentId(), app.targetDepartmentName(),
                    app.targetPlanId(), app.targetPlanName(), app.semester(),
                    app.requestedQuota(), quota, app.applicantUserId(), app.applicantName(),
                    app.reason(), newStatus, operatorUserId, command.reviewComment(),
                    now, app.rowVersion(), app.createdAt(), now);

            crossApplications.update(connection, updated, app.rowVersion());

            // If approved, automatically add this course as CROSS_DISCIPLINARY into the target training plan!
            if (command.approved()) {
                List<TrainingPlanCourse> existingCourses = plans.listCourses(connection, app.targetPlanId());
                boolean exists = existingCourses.stream()
                        .anyMatch(c -> c.courseCode().equalsIgnoreCase(app.courseCode()));
                if (!exists) {
                    String planCourseId = UUID.randomUUID().toString();
                    TrainingPlanCourse course = new TrainingPlanCourse(
                            planCourseId, app.targetPlanId(), app.courseCode(), app.courseName(),
                            app.credits(), CourseType.CROSS_DISCIPLINARY, app.semester(),
                            true, 0, now, now,
                            app.courseId(), app.offeringDepartmentId(), app.offeringDepartmentName(), quota);
                    plans.insertCourse(connection, course);
                }
            }

            return applicationView(updated);
        });
    }
}
