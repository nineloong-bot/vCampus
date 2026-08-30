package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import edu.seu.vcampus.common.paging.PageResult;

/** Transactional profile service and privacy-safe enrollment eligibility port. */
public final class StudentServiceImpl implements StudentService, StudentQueryPort {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final StudentRepository students;
    private final StudentChangeRepository changes;
    private final OrganizationRepository organizations;
    private final UserQueryPort users;
    private final String operatorUserId;

    public StudentServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        this.transactions = Objects.requireNonNull(transactions); this.locks = Objects.requireNonNull(locks);
        this.students = Objects.requireNonNull(students); this.changes = Objects.requireNonNull(changes);
        this.organizations = Objects.requireNonNull(organizations); this.users = Objects.requireNonNull(users);
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
    }

    @Override public StudentView getStudent(String studentId) {
        return transactions.inTransaction(connection -> view(requireById(connection, studentId)));
    }

    @Override public StudentView getCurrentStudent(String userId) {
        return transactions.inTransaction(connection -> view(students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new)));
    }

    @Override public PageResult<StudentSummary> searchStudents(StudentSearchQuery query) {
        if (query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new IllegalArgumentException("Invalid page");
        String keyword = blankToNull(query.keyword());
        var matches = transactions.inTransaction(students::findAll).stream()
                .filter(s -> keyword == null || s.studentName().contains(keyword)
                        || s.studentNumber().contains(keyword)
                        || loginId(s.userId()).contains(keyword))
                .filter(s -> query.departmentId() == null || query.departmentId().equals(
                        transactions.inTransaction(connection -> organizations.findMajor(connection,
                                s.majorId()).map(value -> value.departmentId()).orElse(null))))
                .filter(s -> query.majorId() == null || query.majorId().equals(s.majorId()))
                .filter(s -> query.classId() == null || query.classId().equals(s.classId()))
                .filter(s -> query.status() == null || query.status() == s.status()).toList();
        int from = Math.min((query.page() - 1) * query.pageSize(), matches.size());
        int to = Math.min(from + query.pageSize(), matches.size());
        var summaries = matches.subList(from, to).stream().map(s -> new StudentSummary(s.studentId(),
                loginId(s.userId()), s.studentNumber(), s.studentName(),
                s.majorId(), s.classId(), s.status())).toList();
        return new PageResult<>(summaries, query.page(), query.pageSize(), matches.size());
    }

    @Override public StudentView updateContact(UpdateStudentContactCommand command) {
        return withStudent(command.studentId(), () -> transactions.inTransaction(connection -> {
            requireById(connection, command.studentId());
            students.updateContact(connection, command.studentId(), normalizeEmail(command.email()),
                    blankToNull(command.phone()), command.expectedVersion(), Instant.now());
            return view(requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentView changeStatus(ChangeStudentStatusCommand command) {
        return changeStatus(command, operatorUserId);
    }

    @Override public StudentView changeStatus(ChangeStudentStatusCommand command, String auditUserId) {
        Objects.requireNonNull(command.status()); Objects.requireNonNull(command.effectiveDate());
        requireReason(command.reason());
        return withStudent(command.studentId(), () -> transactions.inTransaction(connection -> {
            Student before = requireById(connection, command.studentId());
            if (!validTransition(before.status(), command.status())) {
                throw new StudentAdmissionException("STUDENT_STATUS_TRANSITION_INVALID",
                        "Invalid student status transition");
            }
            students.updateStatus(connection, command.studentId(), command.status().name(),
                    command.expectedVersion(), Instant.now());
            changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                    "STATUS_CHANGE", before.status().name(), command.status().name(), command.reason(),
                    auditUserId, command.effectiveDate(), Instant.now());
            return view(requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentView updateEnrollment(UpdateStudentEnrollmentCommand command) {
        return updateEnrollment(command, operatorUserId);
    }

    @Override public StudentView updateEnrollment(UpdateStudentEnrollmentCommand command,
            String auditUserId) {
        Objects.requireNonNull(command.effectiveDate()); requireReason(command.reason());
        var target = transactions.inTransaction(connection -> organizations.findClass(connection, command.classId())
                .filter(value -> value.active()).orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_CLASS_INACTIVE", "Target class is unavailable")));
        var major = transactions.inTransaction(connection -> organizations.findMajor(connection, target.majorId())
                .filter(value -> value.active()).orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_CLASS_INACTIVE", "Target major is unavailable")));
        String sequenceKey = "STUDENT_NUMBER:" + major.majorCode() + ":"
                + String.format("%02d", target.enrollmentYear() % 100) + ":" + target.classNumber();
        return locks.withLocks(List.of(new ResourceKey("NUMBER_SEQUENCE", sequenceKey),
                new ResourceKey("STUDENT", command.studentId())), () -> transactions.inTransaction(connection -> {
            Student before = requireById(connection, command.studentId());
            String nextNumber = new AccessStudentNumberGenerator(new NumberSequenceRepository()).next(
                    new TransactionContext(connection, auditUserId, "student-service"), major.majorCode(),
                    target.enrollmentYear(), target.classNumber());
            students.updateEnrollment(connection, command.studentId(), target.classId(), nextNumber,
                    command.expectedVersion(), Instant.now());
            changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                    "CLASS_CHANGE", before.classId() + ":" + before.studentNumber(),
                    target.classId() + ":" + nextNumber, command.reason(), auditUserId,
                    command.effectiveDate(), Instant.now());
            return view(requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentEligibility getEnrollmentEligibility(String userId) {
        Student student = transactions.inTransaction(connection -> students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new));
        boolean eligible = student.status() == StudentStatus.ACTIVE;
        return new StudentEligibility(student.studentId(), student.status(), eligible,
                eligible ? "ELIGIBLE" : "STATUS_" + student.status());
    }

    @Override public StudentIdentity findByUserId(String userId) {
        Student student = transactions.inTransaction(connection -> students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new));
        return new StudentIdentity(student.studentId(), student.userId(),
                loginId(student.userId()), student.studentNumber(),
                student.studentType(), student.majorId(), student.classId(), student.status());
    }

    @Override public boolean existsActiveStudent(String studentId) {
        return transactions.inTransaction(connection -> students.findById(connection, studentId)
                .map(student -> student.status() == StudentStatus.ACTIVE).orElse(false));
    }

    @Override public List<StudentChangeView> listChanges(String studentId) {
        return transactions.inTransaction(connection -> {
            requireById(connection, studentId);
            return changes.listByStudentId(connection, studentId);
        });
    }

    private Student requireById(java.sql.Connection connection, String studentId) {
        return students.findById(connection, studentId).orElseThrow(StudentNotFoundException::new);
    }

    private StudentView view(Student student) {
        return new StudentView(student.studentId(), student.userId(), loginId(student.userId()),
                student.studentNumber(), student.studentType(), student.studentName(), student.gender(),
                student.email(), student.phone(), student.majorId(), student.classId(), student.enrollmentDate(),
                student.status(), student.rowVersion());
    }

    private String loginId(String userId) {
        return users.findByUserId(userId).orElseThrow(() ->
                new IllegalStateException("STUDENT_USER_ACCOUNT_NOT_FOUND")).loginId();
    }

    private <T> T withStudent(String studentId, java.util.function.Supplier<T> action) {
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), action);
    }

    private static String normalizeEmail(String email) {
        String value = blankToNull(email);
        if (value != null && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Invalid email");
        return value;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void requireReason(String reason) { if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required"); }
    private static boolean validTransition(StudentStatus from, StudentStatus to) {
        return switch (from) {
            case ACTIVE -> to == StudentStatus.SUSPENDED || to == StudentStatus.GRADUATED
                    || to == StudentStatus.WITHDRAWN;
            case SUSPENDED -> to == StudentStatus.ACTIVE || to == StudentStatus.WITHDRAWN;
            case GRADUATED, WITHDRAWN -> false;
        };
    }
}
