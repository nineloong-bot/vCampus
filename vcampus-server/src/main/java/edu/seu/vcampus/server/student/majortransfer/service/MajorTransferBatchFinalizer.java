package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.numbering.StudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Atomically validates and applies all successful transfers in one target-college batch. */
public final class MajorTransferBatchFinalizer {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final MajorTransferRepository transfers;
    private final StudentRepository students;
    private final StudentChangeRepository changes;
    private final AccessOrganizationRepository organizations;
    private final StudentNumberGenerator numbers;
    private final MajorTransferEnrollmentPort enrollments;
    private final MajorTransferBatchPlanner planner = new MajorTransferBatchPlanner();
    private final MajorTransferBatchReadinessEvaluator readiness;

    /** Creates a batch finalizer with caller-owned repositories and ports. */
    public MajorTransferBatchFinalizer(TransactionManager transactions, ResourceLockManager locks,
            MajorTransferRepository transfers, StudentRepository students,
            StudentChangeRepository changes, AccessOrganizationRepository organizations,
            StudentNumberGenerator numbers, MajorTransferEnrollmentPort enrollments) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.transfers = Objects.requireNonNull(transfers);
        this.students = Objects.requireNonNull(students);
        this.changes = Objects.requireNonNull(changes);
        this.organizations = Objects.requireNonNull(organizations);
        this.numbers = Objects.requireNonNull(numbers);
        this.enrollments = Objects.requireNonNull(enrollments);
        this.readiness = new MajorTransferBatchReadinessEvaluator(transfers);
    }

    /** Returns transaction-time readiness for a batch owned by the trusted target college. */
    public MajorTransferBatchReadinessView readiness(String batchId, String departmentId) {
        return transactions.inTransaction(c -> readiness.evaluate(c, batchId, departmentId));
    }

    /** Gives final approval to every assessed application without changing enrollment. */
    public MajorTransferBatchFinalizationResult finalizeBatch(String operatorUserId,
            FinalizeMajorTransferBatchCommand command, String departmentId) {
        return locks.withLocks(lockKeys(command.batchId()), () -> transactions.inTransaction(c -> {
            MajorTransferBatchReadinessView state = readiness.evaluate(c, command.batchId(), departmentId);
            if (state.batchVersion() != command.expectedVersion()) {
                throw new ConcurrentModificationException("转专业批次已被修改");
            }
            if (!state.ready()) throw error("TRANSFER_BATCH_NOT_READY", state.reason());
            var applications = transfers.listApplicationsByBatch(c, command.batchId()).stream()
                    .filter(a -> a.status() == MajorTransferStatus.ASSESSED).toList();
            if (applications.isEmpty()) throw error("TRANSFER_BATCH_ALREADY_REVIEWED", "批次已终审或没有可终审申请");
            Instant now = Instant.now();
            for (var app : applications) {
                if (transfers.updateApplicationStatus(c, app.applicationId(),
                        MajorTransferStatus.ASSESSED, MajorTransferStatus.PENDING_EFFECTIVE,
                        app.applicationVersion(), now) != 1) {
                    throw new ConcurrentModificationException("转专业申请已被修改");
                }
                transfers.insertReview(c, new MajorTransferRepository.ReviewRow(UUID.randomUUID().toString(),
                        app.applicationId(), MajorTransferReviewStage.FINAL_APPROVAL,
                        MajorTransferDecision.APPROVE, operatorUserId, "批次终审通过", null, null, null, now));
            }
            return new MajorTransferBatchFinalizationResult(command.batchId(), applications.size(), 0,
                    MajorTransferBatchStatus.CLOSED);
        }));
    }

    /** Applies all previously approved applications in one transaction. */
    public MajorTransferBatchFinalizationResult effectiveBatch(String operatorUserId,
            EffectiveMajorTransferBatchCommand command, String departmentId) {
        return locks.withLocks(lockKeys(command.batchId()), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.batchId(), departmentId);
            if (state.batchVersion() != command.expectedVersion()) throw new ConcurrentModificationException("转专业批次已被修改");
            if (!state.ready()) throw error("TRANSFER_BATCH_NOT_READY", "还有转专业申请未处理完毕");
            var applications = transfers.listApplicationsByBatch(c, command.batchId()).stream()
                    .filter(a -> a.status() == MajorTransferStatus.PENDING_EFFECTIVE).toList();
            if (applications.isEmpty()) throw error("TRANSFER_BATCH_NOT_READY", "批次尚未终审");
            var options = optionMap(c, command.batchId());
            var assignments = plan(c, applications, options);
            Instant now = Instant.now(); int dropped = 0;
            for (var assignment : assignments) {
                dropped += apply(c, operatorUserId, now, assignment,
                        applications.stream().filter(a -> a.applicationId().equals(assignment.applicationId())).findFirst().orElseThrow(),
                        options.get(assignment.optionId()));
            }
            if (transfers.updateBatchStatus(c, command.batchId(), MajorTransferBatchStatus.CLOSED,
                    MajorTransferBatchStatus.EFFECTIVE, command.expectedVersion(), now) != 1)
                throw new ConcurrentModificationException("转专业批次已被修改");
            return new MajorTransferBatchFinalizationResult(command.batchId(), assignments.size(), dropped,
                    MajorTransferBatchStatus.EFFECTIVE);
        }));
    }

    /** Rolls back final approval without changing student enrollment. */
    public MajorTransferBatchFinalizationResult rollbackBatch(String operatorUserId,
            RollbackMajorTransferBatchCommand command, String departmentId) {
        return locks.withLocks(lockKeys(command.batchId()), () -> transactions.inTransaction(c -> {
            var state = readiness.evaluate(c, command.batchId(), departmentId);
            if (state.batchVersion() != command.expectedVersion()) throw new ConcurrentModificationException("转专业批次已被修改");
            var apps = transfers.listApplicationsByBatch(c, command.batchId()).stream()
                    .filter(a -> a.status() == MajorTransferStatus.PENDING_EFFECTIVE).toList();
            for (var app : apps) {
                transfers.updateApplicationStatus(c, app.applicationId(), MajorTransferStatus.PENDING_EFFECTIVE,
                        MajorTransferStatus.ASSESSED, app.applicationVersion(), Instant.now());
                transfers.deleteFinalApproval(c, app.applicationId());
            }
            return new MajorTransferBatchFinalizationResult(command.batchId(), 0, 0, MajorTransferBatchStatus.CLOSED);
        }));
    }

    private List<MajorTransferBatchPlanner.Assignment> plan(Connection c,
            List<MajorTransferRepository.ApplicationRow> applications,
            Map<String, MajorTransferRepository.OptionRow> options) {
        List<MajorTransferBatchPlanner.Candidate> candidates = new ArrayList<>();
        Map<String, StudentClass> classes = new LinkedHashMap<>();
        for (var app : applications) {
            Student student = currentStudent(c, app);
            StudentClass source = organizations.findClass(c, student.classId()).orElseThrow();
            var option = Optional.ofNullable(options.get(app.optionId())).orElseThrow();
            var major = organizations.findMajor(c, option.targetMajorId())
                    .filter(value -> value.active()).orElseThrow(() -> error(
                            "TRANSFER_INVALID_TARGET", "目标专业未启用"));
            int targetCohortYear = targetCohortYear(c, app.batchId(), source.enrollmentYear());
            candidates.add(new MajorTransferBatchPlanner.Candidate(app.applicationId(),
                    student.studentId(), option.optionId(), option.targetDepartmentId(),
                    major.majorId(), major.majorCode(), targetCohortYear));
            organizations.listActiveClasses(c, major.majorId()).stream()
                    .filter(value -> value.enrollmentYear() == targetCohortYear)
                    .forEach(value -> classes.put(value.classId(), value));
        }
        List<MajorTransferBatchPlanner.ClassSlot> slots = classes.values().stream()
                .map(value -> new MajorTransferBatchPlanner.ClassSlot(value,
                        classStudentCount(c, value.classId()))).toList();
        return planner.plan(candidates, slots);
    }

    private int apply(Connection c, String operator, Instant now,
            MajorTransferBatchPlanner.Assignment assignment,
            MajorTransferRepository.ApplicationRow app, MajorTransferRepository.OptionRow option) {
        Student student = currentStudent(c, app);
        String number = numbers.next(new TransactionContext(c), assignment.targetMajorCode(),
                assignment.cohortYear(), assignment.targetClass().classNumber());
        students.updateEnrollment(c, student.studentId(), assignment.targetClass().classId(),
                number, student.rowVersion(), now);
        changes.insertChange(c, UUID.randomUUID().toString(), student.studentId(), "MAJOR_TRANSFER",
                app.fromClassName() + "/" + app.fromStudentNumber(),
                assignment.targetClass().className() + "/" + number, "转专业批次终审生效",
                operator, LocalDate.now(), now);
        int dropped = enrollments.reconcile(c, student.studentId(), assignment.targetMajorCode(),
                assignment.cohortYear(), operator, now).droppedEnrollments();
        writeAudits(c, operator, now, app, option, assignment);
        if (transfers.updateApplicationStatus(c, app.applicationId(), MajorTransferStatus.PENDING_EFFECTIVE,
                MajorTransferStatus.EFFECTIVE, app.applicationVersion(), now) != 1) {
            throw new ConcurrentModificationException("转专业申请已被修改");
        }
        return dropped;
    }

    private void writeAudits(Connection c, String operator, Instant now,
            MajorTransferRepository.ApplicationRow app, MajorTransferRepository.OptionRow option,
            MajorTransferBatchPlanner.Assignment assignment) {
        transfers.insertExecution(c, new MajorTransferRepository.ExecutionRow(UUID.randomUUID().toString(),
                app.applicationId(), assignment.targetClass().classId(), assignment.targetClass().className(),
                option.targetMajorId(), option.targetMajorName(), option.targetDepartmentId(),
                option.targetDepartmentName(), "PENDING", operator, LocalDate.now(), now));
        transfers.insertReview(c, new MajorTransferRepository.ReviewRow(UUID.randomUUID().toString(),
                app.applicationId(), MajorTransferReviewStage.EXECUTION, MajorTransferDecision.APPROVE,
                operator, "转专业已生效", null, null, null, now));
    }

    private Student currentStudent(Connection c, MajorTransferRepository.ApplicationRow app) {
        Student student = students.findById(c, app.studentId()).orElseThrow();
        if (student.status() != StudentStatus.ACTIVE || !student.classId().equals(app.fromClassId())
                || student.rowVersion() != app.baseStudentVersion()) {
            throw error("TRANSFER_SOURCE_CHANGED", "学生学籍或原班级已变化");
        }
        return student;
    }

    private Map<String, MajorTransferRepository.OptionRow> optionMap(Connection c, String batchId) {
        return transfers.listOptionsByBatch(c, batchId).stream().filter(
                MajorTransferRepository.OptionRow::active).collect(Collectors.toMap(
                MajorTransferRepository.OptionRow::optionId, Function.identity()));
    }

    private List<ResourceKey> lockKeys(String batchId) {
        List<ResourceKey> keys = new ArrayList<>();
        keys.add(new ResourceKey("TRANSFER_BATCH", batchId));
        transactions.inTransaction(c -> { transfers.listApplicationsByBatch(c, batchId).forEach(app -> {
            keys.add(new ResourceKey("TRANSFER_APPLICATION", app.applicationId()));
            keys.add(new ResourceKey("STUDENT", app.studentId()));
        }); return null; });
        return keys;
    }

    private int classStudentCount(Connection c, String classId) {
        try (var statement = c.prepareStatement("SELECT COUNT(*) FROM tblStudent WHERE classId=?")) {
            statement.setString(1, classId); try (var result = statement.executeQuery()) {
                result.next(); return result.getInt(1);
            }
        } catch (SQLException error) { throw new PersistenceException("Cannot count class students", error); }
    }

    private int targetCohortYear(Connection c, String batchId, int sourceYear) {
        return transfers.findBatch(c, batchId).map(b -> {
            LocalDate start = b.applicationStart().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
            int grade = start.getYear() - (start.getMonthValue() < 9 ? 1 : 0) - sourceYear + 1;
            return grade == 2 ? sourceYear + 1 : sourceYear;
        }).orElse(sourceYear == 2025 ? 2026 : sourceYear);
    }

    private static MajorTransferException error(String code, String message) {
        return new MajorTransferException(code, message);
    }
}
