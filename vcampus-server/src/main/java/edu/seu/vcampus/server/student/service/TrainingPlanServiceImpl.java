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
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final UserQueryPort users;
    private final edu.seu.vcampus.server.student.repository.CoursePoolRepository coursePool;
    private final edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository crossApplications;

    public TrainingPlanServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations, UserQueryPort users) {
        this(transactions, locks, plans, students, organizations,
                new edu.seu.vcampus.server.student.repository.CoursePoolRepository(),
                new edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository(),
                users);
    }

    public TrainingPlanServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            TrainingPlanRepository plans, StudentRepository students,
            OrganizationRepository organizations,
            edu.seu.vcampus.server.student.repository.CoursePoolRepository coursePool,
            edu.seu.vcampus.server.student.repository.CrossCourseApplicationRepository crossApplications,
            UserQueryPort users) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.plans = Objects.requireNonNull(plans);
        this.students = Objects.requireNonNull(students);
        this.organizations = Objects.requireNonNull(organizations);
        this.users = Objects.requireNonNull(users);
        this.coursePool = Objects.requireNonNull(coursePool);
        this.crossApplications = Objects.requireNonNull(crossApplications);
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
                requirePlanEditable(connection, existing);
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
            requirePlanEditable(connection, plan);
            Instant now = Instant.now();
            if (command.planCourseId() == null || command.planCourseId().isBlank()) {
                rejectDuplicateCourse(connection, command.planId(), command.courseCode(), null);
                String id = UUID.randomUUID().toString();
                TrainingPlanCourse course = new TrainingPlanCourse(id, command.planId(),
                        command.courseCode(), command.courseName(), command.credits(),
                        command.totalHours(), command.courseType(), command.semester(), command.isActive(), 0, now, now,
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
                        command.credits(), command.totalHours(), command.courseType(), command.semester(),
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
            requirePlanEditable(connection, plan);
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
            requirePlanEditable(connection, plan);
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
            String campusCardNumber = users.findByUserId(student.userId())
                    .orElseThrow(StudentNotFoundException::new).loginId();
            TrainingPlan plan = plans.findByMajorAndYear(connection, student.majorId(),
                            CampusCardEnrollmentYear.from(campusCardNumber))
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
        boolean editable = isPlanEditable(connection, plan);
        return new TrainingPlanDetailView(plan.planId(), plan.majorId(), majorName,
                departmentName, plan.enrollmentYear(), plan.planName(),
                plan.minElectiveCount(), plan.minElectiveCredits(),
                plan.active(), plan.rowVersion(), courses, editable);
    }

    private boolean isPlanEditable(java.sql.Connection connection, TrainingPlan plan) {
        try {
            requirePlanEditable(connection, plan);
            return true;
        } catch (TrainingPlanException ignored) {
            return false;
        }
    }

    private TrainingPlanCourseView courseView(TrainingPlanCourse course) {
        return new TrainingPlanCourseView(course.planCourseId(), course.courseCode(),
                course.courseName(), course.credits(), course.totalHours(), course.courseType(),
                course.semester(), course.active(), course.rowVersion(),
                course.courseId(), course.offeringDepartmentId(), course.offeringDepartmentName(),
                course.allocatedQuota());
    }

    @Override
    public List<CoursePoolItemView> listCoursePool(CoursePoolQuery query) {
        return transactions.inTransaction(connection -> {
            String deptId = query != null ? query.departmentId() : null;
            String keyword = query != null ? query.keyword() : null;
            List<edu.seu.vcampus.server.student.domain.CoursePoolItem> list = coursePool.listCourses(connection, deptId, keyword);
            return list.stream().map(c -> new CoursePoolItemView(
                    c.courseId(), c.courseCode(), c.courseName(),
                    c.departmentId(), c.departmentName(),
                    c.credit(), c.totalHours(), c.description(), c.active())).toList();
        });
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
        if (command.semester() < 1 || command.semester() > 8) {
            throw new IllegalArgumentException("开设学期必须在 1-8 之间");
        }
        if (command.requestedQuota() <= 0) {
            throw new IllegalArgumentException("申请名额必须大于 0");
        }

        return transactions.inTransaction(connection -> {
            edu.seu.vcampus.server.student.domain.CoursePoolItem course = coursePool.findById(connection, command.courseId())
                    .orElseThrow(() -> new TrainingPlanException("COURSE_NOT_FOUND", "所选课程在课程库中不存在"));
            TrainingPlan plan = plans.findById(connection, command.targetPlanId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "目标培养方案不存在"));

            edu.seu.vcampus.server.student.domain.Major major = organizations.findMajor(connection, plan.majorId())
                    .orElseThrow(() -> new TrainingPlanException("MAJOR_NOT_FOUND", "专业不存在"));
            requireDepartment(major.departmentId(), departmentId);
            edu.seu.vcampus.server.student.domain.Department targetDept = organizations.findDepartment(connection, major.departmentId())
                    .orElseThrow(() -> new TrainingPlanException("DEPARTMENT_NOT_FOUND", "院系不存在"));

            List<TrainingPlanCourse> existingCourses = plans.listCourses(connection, plan.planId());
            boolean alreadyInPlan = existingCourses.stream()
                    .anyMatch(c -> c.courseCode().equalsIgnoreCase(course.courseCode()));
            if (alreadyInPlan) {
                throw new TrainingPlanException("COURSE_ALREADY_IN_PLAN", "该课程已在当前培养方案中，无需重复申请");
            }

            Instant now = Instant.now();
            String appId = UUID.randomUUID().toString();
            edu.seu.vcampus.server.student.domain.CrossCourseApplication app = new edu.seu.vcampus.server.student.domain.CrossCourseApplication(
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
            List<edu.seu.vcampus.server.student.domain.CrossCourseApplication> list =
                    crossApplications.list(connection, offeringDept, targetDept, status);
            return list.stream().filter(app -> departmentId == null
                            || departmentId.equals(app.offeringDepartmentId())
                            || departmentId.equals(app.targetDepartmentId()))
                    .map(this::applicationView).toList();
        });
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
            edu.seu.vcampus.server.student.domain.CrossCourseApplication app = crossApplications.findById(connection, command.applicationId())
                    .orElseThrow(() -> new TrainingPlanException("APPLICATION_NOT_FOUND", "跨学科申请不存在"));
            requireDepartment(app.offeringDepartmentId(), departmentId);
            if (app.status() != CrossCourseApplicationStatus.PENDING) {
                throw new TrainingPlanException("APPLICATION_NOT_PENDING", "该申请已完成审批，无法重复操作");
            }

            Instant now = Instant.now();
            CrossCourseApplicationStatus newStatus = command.approved()
                    ? CrossCourseApplicationStatus.APPROVED : CrossCourseApplicationStatus.REJECTED;
            Integer quota = command.approved() ? command.allocatedQuota() : null;

            edu.seu.vcampus.server.student.domain.CrossCourseApplication updated = new edu.seu.vcampus.server.student.domain.CrossCourseApplication(
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
                TrainingPlan targetPlan = plans.findById(connection, app.targetPlanId())
                        .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_NOT_FOUND", "目标培养方案不存在"));
                requirePlanEditable(connection, targetPlan);
                List<TrainingPlanCourse> existingCourses = plans.listCourses(connection, app.targetPlanId());
                boolean exists = existingCourses.stream()
                        .anyMatch(c -> c.courseCode().equalsIgnoreCase(app.courseCode()));
                if (!exists) {
                    String planCourseId = UUID.randomUUID().toString();
                    int totalHours = coursePool.findById(connection, app.courseId())
                            .map(edu.seu.vcampus.server.student.domain.CoursePoolItem::totalHours)
                            .orElse(0);
                    TrainingPlanCourse course = new TrainingPlanCourse(
                            planCourseId, app.targetPlanId(), app.courseCode(), app.courseName(),
                            app.credits(), totalHours, CourseType.CROSS_DISCIPLINARY, app.semester(),
                            true, 0, now, now,
                            app.courseId(), app.offeringDepartmentId(), app.offeringDepartmentName(), quota);
                    plans.insertCourse(connection, course);
                }
            }

            return applicationView(updated);
        });
    }

    private CrossCourseApplicationView applicationView(edu.seu.vcampus.server.student.domain.CrossCourseApplication app) {
        return new CrossCourseApplicationView(
                app.applicationId(), app.courseId(), app.courseCode(), app.courseName(),
                app.credits(), app.offeringDepartmentId(), app.offeringDepartmentName(),
                app.targetDepartmentId(), app.targetDepartmentName(),
                app.targetPlanId(), app.targetPlanName(), app.semester(),
                app.requestedQuota(), app.allocatedQuota(), app.applicantUserId(),
                app.applicantName(), app.reason(), app.status(), app.reviewerUserId(),
                app.reviewComment(), app.reviewedAt(), app.createdAt(), app.updatedAt());
    }

    private void requireMajor(java.sql.Connection connection, String majorId,
            String departmentId) {
        if (departmentId != null && organizations.findMajor(connection, majorId)
                .filter(major -> departmentId.equals(major.departmentId())).isEmpty())
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
    }

    private void requirePlanEditable(java.sql.Connection connection, TrainingPlan plan) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        int currentAcademicYear = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        LocalDate autumnStart = LocalDate.of(currentAcademicYear, 9, 1);

        String termSql = "SELECT academicYearStart, startDate FROM tblTerm "
                + "WHERE termStatus='ACTIVE' AND season='AUTUMN'";
        try (var statement = connection.prepareStatement(termSql)) {
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    currentAcademicYear = (int) result.getLong("academicYearStart");
                    java.sql.Date d = result.getDate("startDate");
                    if (d != null) {
                        autumnStart = d.toLocalDate();
                    }
                } else {
                    String fallbackSql = "SELECT academicYearStart, startDate FROM tblTerm "
                            + "WHERE season='AUTUMN' ORDER BY academicYearStart DESC";
                    try (var fallbackSt = connection.prepareStatement(fallbackSql);
                         var fallbackRs = fallbackSt.executeQuery()) {
                        if (fallbackRs.next()) {
                            currentAcademicYear = (int) fallbackRs.getLong("academicYearStart");
                            java.sql.Date d = fallbackRs.getDate("startDate");
                            if (d != null) {
                                autumnStart = d.toLocalDate();
                            }
                        }
                    }
                }
            }
        } catch (java.sql.SQLException ignored) {
            // Fallback to default calendar-derived currentAcademicYear and autumnStart if tblTerm is absent
        }

        if (plan.enrollmentYear() < currentAcademicYear) {
            throw new TrainingPlanException("TRAINING_PLAN_IMMUTABLE",
                    "大二、大三、大四等往届培养方案不可修改");
        }
        if (plan.enrollmentYear() == currentAcademicYear && !today.isBefore(autumnStart)) {
            throw new TrainingPlanException("TRAINING_PLAN_IMMUTABLE",
                    "秋季学期已开课，培养方案已锁定，仅允许在秋季开课前编辑大一培养方案");
        }
    }

    private static void requireDepartment(String actualDepartmentId, String departmentId) {
        if (departmentId != null && !departmentId.equals(actualDepartmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
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
    }
}
