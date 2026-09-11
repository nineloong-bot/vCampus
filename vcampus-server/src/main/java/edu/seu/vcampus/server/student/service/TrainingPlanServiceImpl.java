package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.repository.TrainingPlanRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Transactional training plan service. */
public final class TrainingPlanServiceImpl implements TrainingPlanService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final TrainingPlanRepository plans;
    private final StudentRepository students;
    private final OrganizationRepository organizations;

    public TrainingPlanServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.plans = Objects.requireNonNull(plans);
        this.students = Objects.requireNonNull(students);
        this.organizations = Objects.requireNonNull(organizations);
    }

    @Override
    public TrainingPlanDetailView getPlan(String planId) {
        return transactions.inTransaction(connection -> {
            TrainingPlan plan = plans.findById(connection, planId)
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
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
        if (query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new IllegalArgumentException("Invalid page");
        int offset = (query.page() - 1) * query.pageSize();
        return transactions.inTransaction(connection -> {
            List<TrainingPlanSummary> items = plans.search(connection, query.majorId(),
                    query.enrollmentYear(), offset, query.pageSize());
            int total = plans.countSearch(connection, query.majorId(), query.enrollmentYear());
            return new PageResult<>(items, query.page(), query.pageSize(), total);
        });
    }

    @Override
    public TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId) {
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
                TrainingPlan updated = new TrainingPlan(existing.planId(), existing.majorId(),
                        existing.enrollmentYear(), command.planName(),
                        command.minElectiveCount(), command.minElectiveCredits(),
                        command.isActive(), existing.rowVersion(), existing.createdAt(), now);
                plans.update(connection, updated, command.expectedVersion());
                return detailView(connection, plans.findById(connection, existing.planId()).orElseThrow());
            }
        }));
    }

    @Override
    public TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command,
            String operatorUserId) {
        validateCourse(command.planId(), command.courseCode(), command.courseName(),
                command.credits(), command.courseType(), command.semester());
        return locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN_COURSES", command.planId())),
                () -> transactions.inTransaction(connection -> {
            plans.findById(connection, command.planId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
            Instant now = Instant.now();
            if (command.planCourseId() == null || command.planCourseId().isBlank()) {
                rejectDuplicateCourse(connection, command.planId(), command.courseCode(), null);
                String id = UUID.randomUUID().toString();
                TrainingPlanCourse course = new TrainingPlanCourse(id, command.planId(),
                        command.courseCode(), command.courseName(), command.credits(),
                        command.courseType(), command.semester(), command.isActive(), 0, now, now);
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
                        command.isActive(), existing.rowVersion(), existing.createdAt(), now);
                plans.updateCourse(connection, updated, command.expectedVersion());
                return courseView(updated);
            }
        }));
    }

    @Override
    public void removeCourse(String planCourseId, String operatorUserId) {
        locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN_COURSE", planCourseId)),
                () -> transactions.inTransaction(connection -> {
            plans.findCourseById(connection, planCourseId)
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_COURSE_NOT_FOUND", "课程不存在"));
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
        Objects.requireNonNull(command.planId());
        Objects.requireNonNull(command.courses());
        command.courses().forEach(entry -> validateCourse(command.planId(), entry.courseCode(),
                entry.courseName(), entry.credits(), entry.courseType(), entry.semester()));
        return locks.withLocks(List.of(new ResourceKey("TRAINING_PLAN_COURSES", command.planId())),
                () -> transactions.inTransaction(connection -> {
            plans.findById(connection, command.planId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "培养方案不存在"));
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

    private TrainingPlanDetailView detailView(java.sql.Connection connection, TrainingPlan plan) {
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

    private TrainingPlanCourseView courseView(TrainingPlanCourse course) {
        return new TrainingPlanCourseView(course.planCourseId(), course.courseCode(),
                course.courseName(), course.credits(), course.courseType(),
                course.semester(), course.active(), course.rowVersion());
    }

    private void rejectDuplicateCourse(java.sql.Connection connection, String planId,
            String courseCode, String allowedCourseId) {
        plans.findCourseByPlanAndCode(connection, planId, courseCode).ifPresent(existing -> {
            if (!existing.planCourseId().equals(allowedCourseId)) throw duplicateCourse(courseCode);
        });
    }

    private TrainingPlanException duplicateCourse(String courseCode) {
        return new TrainingPlanException("TRAINING_PLAN_COURSE_DUPLICATE",
                "课程代码 " + courseCode + " 在该方案中已存在");
    }

    private void validateCourse(String planId, String courseCode, String courseName,
            BigDecimal credits, CourseType courseType, int semester) {
        Objects.requireNonNull(planId);
        Objects.requireNonNull(courseCode);
        Objects.requireNonNull(courseName);
        Objects.requireNonNull(credits);
        Objects.requireNonNull(courseType);
        if (credits.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("credits must be positive");
        if (semester < 1 || semester > 8)
            throw new IllegalArgumentException("semester must be 1-8");
        if (courseType == CourseType.ELECTIVE && (semester < 3 || semester > 6))
            throw new TrainingPlanException("TRAINING_PLAN_ELECTIVE_SEMESTER_INVALID",
                    "选修课只能安排在第3-6学期");
    }
}
