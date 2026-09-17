package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferDecision;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferReviewStage;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferCollegeBatchRepository;
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

final class MajorTransferBatchProcessor {
    private final MajorTransferRepository transfers;
    private final StudentRepository students;
    private final StudentChangeRepository changes;
    private final AccessOrganizationRepository organizations;
    private final StudentNumberGenerator numbers;
    private final MajorTransferEnrollmentPort enrollments;
    private final MajorTransferBatchPlanner planner = new MajorTransferBatchPlanner();

    MajorTransferBatchProcessor(MajorTransferRepository transfers, StudentRepository students,
            StudentChangeRepository changes, AccessOrganizationRepository organizations,
            StudentNumberGenerator numbers, MajorTransferEnrollmentPort enrollments) {
        this.transfers = transfers;
        this.students = students;
        this.changes = changes;
        this.organizations = organizations;
        this.numbers = numbers;
        this.enrollments = enrollments;
    }

    List<MajorTransferBatchPlanner.Assignment> plan(Connection connection,
            List<MajorTransferRepository.ApplicationRow> applications,
            Map<String, MajorTransferRepository.OptionRow> options) {
        List<MajorTransferBatchPlanner.Candidate> candidates = new ArrayList<>();
        Map<String, StudentClass> classes = new LinkedHashMap<>();
        for (var application : applications) {
            Student student = currentStudent(connection, application);
            StudentClass source = organizations.findClass(connection, student.classId()).orElseThrow();
            var option = Optional.ofNullable(options.get(application.optionId())).orElseThrow();
            var major = organizations.findMajor(connection, option.targetMajorId())
                    .filter(value -> value.active()).orElseThrow(() -> error(
                            "TRANSFER_INVALID_TARGET", "目标专业未启用"));
            int cohort = targetCohortYear(connection, application.batchId(), source.enrollmentYear());
            enrollments.validate(connection, major.majorCode(), cohort);
            candidates.add(new MajorTransferBatchPlanner.Candidate(application.applicationId(),
                    student.studentId(), option.optionId(), option.targetDepartmentId(),
                    major.majorId(), major.majorCode(), cohort));
            organizations.listActiveClasses(connection, major.majorId()).stream()
                    .filter(value -> value.enrollmentYear() == cohort)
                    .forEach(value -> classes.put(value.classId(), value));
        }
        var slots = classes.values().stream().map(value ->
                new MajorTransferBatchPlanner.ClassSlot(value,
                        classStudentCount(connection, value.classId()))).toList();
        return planner.plan(candidates, slots);
    }

    List<MajorTransferBatchPlanner.Assignment> preparedAssignments(Connection connection,
            List<MajorTransferRepository.ApplicationRow> applications,
            List<MajorTransferCollegeBatchRepository.PreparedTransferRow> prepared,
            Map<String, MajorTransferRepository.OptionRow> options) {
        var byId = applications.stream().collect(Collectors.toMap(
                MajorTransferRepository.ApplicationRow::applicationId, Function.identity()));
        List<MajorTransferBatchPlanner.Assignment> result = new ArrayList<>();
        for (var row : prepared) {
            var application = Optional.ofNullable(byId.get(row.applicationId())).orElseThrow(() ->
                    error("TRANSFER_PREPARATION_STALE", "待生效申请已变化"));
            var student = currentStudent(connection, application);
            if (student.rowVersion() != row.studentVersion()
                    || application.applicationVersion() != row.applicationVersion()) {
                throw error("TRANSFER_PREPARATION_STALE", "学生学籍或申请已变化");
            }
            var option = Optional.ofNullable(options.get(application.optionId())).orElseThrow(() ->
                    error("TRANSFER_PREPARATION_STALE", "目标专业已变化"));
            var major = organizations.findMajor(connection, row.targetMajorId())
                    .filter(value -> value.active()).orElseThrow(() ->
                            error("TRANSFER_PREPARATION_STALE", "目标专业已变化"));
            var targetClass = organizations.findClass(connection, row.targetClassId())
                    .filter(value -> value.active() && value.majorId().equals(row.targetMajorId()))
                    .orElseThrow(() -> error("TRANSFER_PREPARATION_STALE", "目标班级已变化"));
            enrollments.validate(connection, major.majorCode(), row.targetCohortYear());
            String sequence = "STUDENT_NUMBER:" + major.majorCode() + ":"
                    + String.format("%02d", row.targetCohortYear() % 100) + ":"
                    + targetClass.classNumber();
            result.add(new MajorTransferBatchPlanner.Assignment(application.applicationId(),
                    application.studentId(), application.optionId(), row.targetDepartmentId(),
                    row.targetMajorId(), major.majorCode(), row.targetCohortYear(), targetClass,
                    sequence));
        }
        return List.copyOf(result);
    }

    int apply(Connection connection, String operator, Instant now,
            MajorTransferBatchPlanner.Assignment assignment,
            MajorTransferRepository.ApplicationRow application,
            MajorTransferRepository.OptionRow option) {
        Student student = currentStudent(connection, application);
        String number = numbers.next(new TransactionContext(connection), assignment.targetMajorCode(),
                assignment.cohortYear(), assignment.targetClass().classNumber());
        students.updateEnrollment(connection, student.studentId(),
                assignment.targetClass().classId(), number, student.rowVersion(), now);
        changes.insertChange(connection, UUID.randomUUID().toString(), student.studentId(),
                "MAJOR_TRANSFER", application.fromClassName() + "/" + application.fromStudentNumber(),
                assignment.targetClass().className() + "/" + number, "转专业批次终审生效",
                operator, LocalDate.now(), now);
        int dropped = enrollments.reconcile(connection, student.studentId(),
                assignment.targetMajorCode(), assignment.cohortYear(), operator, now)
                .droppedEnrollments();
        writeAudits(connection, operator, now, application, option, assignment);
        if (transfers.updateApplicationStatus(connection, application.applicationId(),
                MajorTransferStatus.PENDING_EFFECTIVE, MajorTransferStatus.EFFECTIVE,
                application.applicationVersion(), now) != 1) {
            throw new ConcurrentModificationException("转专业申请已被修改");
        }
        return dropped;
    }

    Student currentStudent(Connection connection, MajorTransferRepository.ApplicationRow application) {
        Student student = students.findById(connection, application.studentId()).orElseThrow();
        if (student.status() != StudentStatus.ACTIVE
                || !student.classId().equals(application.fromClassId())
                || student.rowVersion() != application.baseStudentVersion()) {
            throw error("TRANSFER_SOURCE_CHANGED", "学生学籍或原班级已变化");
        }
        return student;
    }

    private void writeAudits(Connection connection, String operator, Instant now,
            MajorTransferRepository.ApplicationRow application,
            MajorTransferRepository.OptionRow option,
            MajorTransferBatchPlanner.Assignment assignment) {
        transfers.insertExecution(connection, new MajorTransferRepository.ExecutionRow(
                UUID.randomUUID().toString(), application.applicationId(),
                assignment.targetClass().classId(), assignment.targetClass().className(),
                option.targetMajorId(), option.targetMajorName(), option.targetDepartmentId(),
                option.targetDepartmentName(), "PENDING", operator, LocalDate.now(), now));
        transfers.insertReview(connection, new MajorTransferRepository.ReviewRow(
                UUID.randomUUID().toString(), application.applicationId(),
                MajorTransferReviewStage.EXECUTION, MajorTransferDecision.APPROVE,
                operator, "转专业已生效", null, null, null, now));
    }

    private int classStudentCount(Connection connection, String classId) {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblStudent WHERE classId=?")) {
            statement.setString(1, classId);
            try (var result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        } catch (SQLException exception) {
            throw new PersistenceException("Cannot count class students", exception);
        }
    }

    private int targetCohortYear(Connection connection, String batchId, int sourceYear) {
        return transfers.findBatch(connection, batchId).map(batch -> {
            LocalDate start = batch.applicationStart()
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
            int grade = start.getYear() - (start.getMonthValue() < 9 ? 1 : 0) - sourceYear + 1;
            return grade == 2 ? sourceYear + 1 : sourceYear;
        }).orElse(sourceYear == 2025 ? 2026 : sourceYear);
    }

    private static MajorTransferException error(String code, String message) {
        return new MajorTransferException(code, message);
    }
}
