package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.repository.CoursePoolRepository;
import edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Objects;

/** Shared collaborators, constructors and validation helpers for the training plan segments. */
abstract class TrainingPlanServiceImplBase implements TrainingPlanService {
    protected final TransactionManager transactions;
    protected final ResourceLockManager locks;
    protected final TrainingPlanRepository plans;
    protected final StudentRepository students;
    protected final OrganizationRepository organizations;
    protected final CoursePoolRepository coursePool;
    protected final CrossCourseApplicationRepository crossApplications;

    /**
     * Creates a training plan service impl with its required collaborators.
     * @param transactions the transactions
     * @param locks the locks
     * @param plans the plans
     * @param students the students
     * @param organizations the organizations
     */
    protected TrainingPlanServiceImplBase(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations) {
        this(transactions, locks, plans, students, organizations,
                new CoursePoolRepository(), new CrossCourseApplicationRepository());
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
    protected TrainingPlanServiceImplBase(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, CoursePoolRepository coursePool,
            CrossCourseApplicationRepository crossApplications) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.plans = Objects.requireNonNull(plans);
        this.students = Objects.requireNonNull(students);
        this.organizations = Objects.requireNonNull(organizations);
        this.coursePool = Objects.requireNonNull(coursePool);
        this.crossApplications = Objects.requireNonNull(crossApplications);
    }

    void requireMajor(Connection connection, String majorId, String departmentId) {
        if (departmentId != null && organizations.findMajor(connection, majorId)
                .filter(major -> departmentId.equals(major.departmentId())).isEmpty())
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
    }

    static void requireDepartment(String actualDepartmentId, String departmentId) {
        if (departmentId != null && !departmentId.equals(actualDepartmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
    }

    void rejectDuplicateCourse(Connection connection, String planId,
            String courseCode, String allowedCourseId) {
        plans.findCourseByPlanAndCode(connection, planId, courseCode).ifPresent(existing -> {
            if (!existing.planCourseId().equals(allowedCourseId)) throw duplicateCourse(courseCode);
        });
    }

    static TrainingPlanException duplicateCourse(String courseCode) {
        return new TrainingPlanException("TRAINING_PLAN_COURSE_DUPLICATE",
                "课程代码 " + courseCode + " 在该方案中已存在");
    }

    static void validateCourse(String planId, String courseCode, String courseName,
            BigDecimal credits, CourseType courseType, int semester) {
        Objects.requireNonNull(planId);
        Objects.requireNonNull(courseCode);
        Objects.requireNonNull(courseName);
        Objects.requireNonNull(credits);
        Objects.requireNonNull(courseType);
        if (credits.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("credits must be positive");
        if (semester < 1 || semester > 12)
            throw new IllegalArgumentException("semester must be 1-12");
    }
}
