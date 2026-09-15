package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

/**
 * Transactional training plan service.
 *
 * <p>The plan queries, plan-course maintenance and cross-course application
 * flows are implemented by the package-private segment chain this class
 * extends, keeping the public surface unchanged.</p>
 */
public final class TrainingPlanServiceImpl extends TrainingPlanServiceImplCrossCourseReviewing {

    /**
     * Creates a training plan service impl with its required collaborators.
     * @param transactions the transactions
     * @param locks the locks
     * @param plans the plans
     * @param students the students
     * @param organizations the organizations
     */
    public TrainingPlanServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations) {
        this(transactions, locks, plans, students, organizations,
                new CoursePoolRepository(),
                new CrossCourseApplicationRepository());
    }

    /**
     * Creates a training plan service impl with its required collaborators.
     * @param transactions the transactions
     * @param locks the locks
     * @param plans the plans
     * @param students the students
     * @param organizations the organizations
     * @param coursePool the course pool
     * @param crossApplications the cross applications
     */
    public TrainingPlanServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        super(transactions, locks, plans, students, organizations, coursePool, crossApplications);
    }
}
