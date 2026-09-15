package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CoursePoolItemView;
import edu.seu.vcampus.common.student.CoursePoolQuery;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.CoursePoolItem;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.util.List;

/** Course-pool listing for the training plan segments. */
abstract class TrainingPlanServiceImplCoursePool extends TrainingPlanServiceImplViews {

    /** Creates the course-pool segment. */
    protected TrainingPlanServiceImplCoursePool(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }

    @Override
    public List<CoursePoolItemView> listCoursePool(CoursePoolQuery query) {
        return transactions.inTransaction(connection -> {
            String deptId = query != null ? query.departmentId() : null;
            String keyword = query != null ? query.keyword() : null;
            List<CoursePoolItem> list = coursePool.listCourses(connection, deptId, keyword);
            return list.stream().map(c -> new CoursePoolItemView(
                    c.courseId(), c.courseCode(), c.courseName(),
                    c.departmentId(), c.departmentName(),
                    c.credit(), c.totalHours(), c.description(), c.active())).toList();
        });
    }
}
