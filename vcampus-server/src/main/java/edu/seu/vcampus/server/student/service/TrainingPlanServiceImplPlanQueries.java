package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;
import edu.seu.vcampus.common.student.TrainingPlanQuery;
import edu.seu.vcampus.common.student.TrainingPlanSummary;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.util.List;

/** Read-only plan queries for the training plan segments. */
abstract class TrainingPlanServiceImplPlanQueries extends TrainingPlanServiceImplCoursePool {

    /** Creates the plan-query segment. */
    protected TrainingPlanServiceImplPlanQueries(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    @Override
    public TrainingPlanDetailView getPlan(String planId) {
        return getPlan(planId, null);
    }

    @Override
    public TrainingPlanDetailView getPlan(String planId, String departmentId) {
        return transactions.inTransaction(connection -> {
            TrainingPlan plan = plans.findById(connection, planId)
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
            requireMajor(connection, plan.majorId(), departmentId);
            return detailView(connection, plan);
        });
    }

    @Override
    public TrainingPlanDetailView getPlanByMajorAndYear(String majorId, int enrollmentYear) {
        return transactions.inTransaction(connection -> {
            TrainingPlan plan = plans.findByMajorAndYear(connection, majorId, enrollmentYear)
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
            return detailView(connection, plan);
        });
    }

    @Override
    public PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query) {
        return searchPlans(query, null);
    }

    @Override
    public PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query,
            String departmentId) {
        if (query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new IllegalArgumentException("Invalid page");
        int offset = (query.page() - 1) * query.pageSize();
        return transactions.inTransaction(connection -> {
            List<TrainingPlanSummary> items = plans.search(connection, query.majorId(),
                    query.enrollmentYear(), offset, query.pageSize(), departmentId);
            int total = plans.countSearch(connection, query.majorId(), query.enrollmentYear(),
                    departmentId);
            return new PageResult<>(items, query.page(), query.pageSize(), total);
        });
    }

    @Override
    public TrainingPlanDetailView getMyPlan(String userId) {
        return transactions.inTransaction(connection -> {
            Student student = students.findByUserId(connection, userId)
                    .orElseThrow(StudentNotFoundException::new);
            TrainingPlan plan = plans.findByMajorAndYear(connection, student.majorId(),
                            student.studentNumber().isEmpty() ? 0 :
                            Integer.parseInt("20" + student.studentNumber().substring(3, 5)))
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND",
                            "您所在专业年级暂无培养方案"));
            return detailView(connection, plan);
        });
    }
}
