package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CrossCourseApplicationView;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.CrossCourseApplication;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.sql.Connection;
import java.util.List;

/** View mapping helpers for the training plan segments. */
abstract class TrainingPlanServiceImplViews extends TrainingPlanServiceImplBase {

    /** Creates the view-mapping segment. */
    protected TrainingPlanServiceImplViews(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    TrainingPlanDetailView detailView(Connection connection, TrainingPlan plan) {
        var major = organizations.findMajor(connection, plan.majorId());
        String majorName = major.map(m -> m.majorName()).orElse(null);
        String departmentName = major.flatMap(m ->
                organizations.findDepartment(connection, m.departmentId()))
                .map(d -> d.departmentName()).orElse(null);
        List<TrainingPlanCourseView> courses = plans.listCourses(connection, plan.planId())
                .stream().map(this::courseView).toList();
        return new TrainingPlanDetailView(plan.planId(), plan.majorId(), majorName,
                departmentName, plan.enrollmentYear(), plan.planName(),
                plan.minElectiveCount(), plan.minElectiveCredits(),
                plan.active(), plan.rowVersion(), courses);
    }

    TrainingPlanCourseView courseView(TrainingPlanCourse course) {
        return new TrainingPlanCourseView(course.planCourseId(), course.courseCode(),
                course.courseName(), course.credits(), course.courseType(),
                course.semester(), course.active(), course.rowVersion(),
                course.courseId(), course.offeringDepartmentId(), course.offeringDepartmentName(),
                course.allocatedQuota());
    }

    CrossCourseApplicationView applicationView(CrossCourseApplication app) {
        return new CrossCourseApplicationView(
                app.applicationId(), app.courseId(), app.courseCode(), app.courseName(),
                app.credits(), app.offeringDepartmentId(), app.offeringDepartmentName(),
                app.targetDepartmentId(), app.targetDepartmentName(),
                app.targetPlanId(), app.targetPlanName(), app.semester(),
                app.requestedQuota(), app.allocatedQuota(), app.applicantUserId(),
                app.applicantName(), app.reason(), app.status(), app.reviewerUserId(),
                app.reviewComment(), app.reviewedAt(), app.createdAt(), app.updatedAt());
    }
}
