package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CrossCourseApplicationQuery;
import edu.seu.vcampus.common.student.CrossCourseApplicationStatus;
import edu.seu.vcampus.common.student.CrossCourseApplicationView;
import edu.seu.vcampus.common.student.SubmitCrossCourseApplicationCommand;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.CoursePoolItem;
import edu.seu.vcampus.server.student.domain.CrossCourseApplication;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
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

/** Cross-course application submission and listing flows. */
abstract class TrainingPlanServiceImplCrossCourseSubmitting extends TrainingPlanServiceImplCourseEditing {

    /** Creates the cross-course submitting segment. */
    protected TrainingPlanServiceImplCrossCourseSubmitting(TransactionManager transactions,
            ResourceLockManager locks, TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    @Override
    public CrossCourseApplicationView submitCrossCourseApplication(
            SubmitCrossCourseApplicationCommand command, String operatorUserId) {
        return submitCrossCourseApplication(command, operatorUserId, null);
    }

    @Override
    public CrossCourseApplicationView submitCrossCourseApplication(
            SubmitCrossCourseApplicationCommand command, String operatorUserId,
            String departmentId) {
        if (command.courseId() == null || command.courseId().isBlank()) {
            throw new IllegalArgumentException("课程不能为空");
        }
        if (command.targetPlanId() == null || command.targetPlanId().isBlank()) {
            throw new IllegalArgumentException("目标培养方案不能为空");
        }
        if (command.semester() < 1 || command.semester() > 12) {
            throw new IllegalArgumentException("开设学期必须在 1-12 之间");
        }
        if (command.requestedQuota() <= 0) {
            throw new IllegalArgumentException("申请名额必须大于 0");
        }

        return transactions.inTransaction(connection -> {
            CoursePoolItem course = coursePool.findById(connection, command.courseId())
                    .orElseThrow(() -> new TrainingPlanException("COURSE_NOT_FOUND", "所选课程在课程库中不存在"));
            TrainingPlan plan = plans.findById(connection, command.targetPlanId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "目标培养方案不存在"));

            Major major = organizations.findMajor(connection, plan.majorId())
                    .orElseThrow(() -> new TrainingPlanException("MAJOR_NOT_FOUND", "专业不存在"));
            requireDepartment(major.departmentId(), departmentId);
            Department targetDept = organizations.findDepartment(connection, major.departmentId())
                    .orElseThrow(() -> new TrainingPlanException("DEPARTMENT_NOT_FOUND", "院系不存在"));

            List<TrainingPlanCourse> existingCourses = plans.listCourses(connection, plan.planId());
            boolean alreadyInPlan = existingCourses.stream()
                    .anyMatch(c -> c.courseCode().equalsIgnoreCase(course.courseCode()));
            if (alreadyInPlan) {
                throw new TrainingPlanException("COURSE_ALREADY_IN_PLAN", "该课程已在当前培养方案中，无需重复申请");
            }

            Instant now = Instant.now();
            String appId = UUID.randomUUID().toString();
            CrossCourseApplication app = new CrossCourseApplication(
                    appId, course.courseId(), course.courseCode(), course.courseName(),
                    course.credit(), course.departmentId(), course.departmentName(),
                    targetDept.departmentId(), targetDept.departmentName(),
                    plan.planId(), plan.planName(), command.semester(),
                    command.requestedQuota(), null, operatorUserId, "教务管理员",
                    command.reason(), CrossCourseApplicationStatus.PENDING,
                    null, null, null, 0, now, now);

            crossApplications.insert(connection, app);
            return applicationView(app);
        });
    }

    @Override
    public List<CrossCourseApplicationView> listCrossCourseApplications(
            CrossCourseApplicationQuery query, String operatorUserId) {
        return listCrossCourseApplications(query, operatorUserId, null);
    }

    @Override
    public List<CrossCourseApplicationView> listCrossCourseApplications(
            CrossCourseApplicationQuery query, String operatorUserId, String departmentId) {
        return transactions.inTransaction(connection -> {
            String offeringDept = query != null ? query.offeringDepartmentId() : null;
            String targetDept = query != null ? query.targetDepartmentId() : null;
            CrossCourseApplicationStatus status = query != null ? query.status() : null;
            List<CrossCourseApplication> list =
                    crossApplications.list(connection, offeringDept, targetDept, status);
            return list.stream().filter(app -> departmentId == null
                            || departmentId.equals(app.offeringDepartmentId())
                            || departmentId.equals(app.targetDepartmentId()))
                    .map(this::applicationView).toList();
        });
    }
}
