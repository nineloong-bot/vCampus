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
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import edu.seu.vcampus.common.paging.PageResult;

/** Implements focused public operations for {@link StudentServiceImpl}. */
abstract class StudentServiceImplOperations1 extends StudentServiceImplOperations2 {
    protected StudentServiceImplOperations1(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        super(transactions, locks, students, changes, organizations, users, operatorUserId);
    }


    @Override public StudentView getStudent(String studentId) {
        return getStudent(studentId, null);
    }

    @Override public StudentView getStudent(String studentId, String trustedDepartmentId) {
        return transactions.inTransaction(connection ->
                view(connection, requireById(connection, studentId, trustedDepartmentId)));
    }

    @Override public StudentView getCurrentStudent(String userId) {
        return transactions.inTransaction(connection -> view(connection, students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new)));
    }

    @Override public PageResult<StudentSummary> searchStudents(StudentSearchQuery query) {
        return searchStudents(query, null);
    }

    @Override public PageResult<StudentSummary> searchStudents(StudentSearchQuery query,
            String trustedDepartmentId) {
        if (query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new IllegalArgumentException("Invalid page");
        String keyword = blankToNull(query.keyword());
        var candidates = transactions.inTransaction(connection -> trustedDepartmentId == null
                ? students.findAll(connection) : students.findAll(connection, trustedDepartmentId));
        var matches = candidates.stream()
                .filter(s -> keyword == null || s.studentName().contains(keyword)
                        || s.studentNumber().contains(keyword)
                        || loginId(s.userId()).contains(keyword))
                .filter(s -> trustedDepartmentId != null || query.departmentId() == null
                        || query.departmentId().equals(
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
        return updateContact(command, null);
    }

    @Override public StudentView updateContact(UpdateStudentContactCommand command,
            String trustedDepartmentId) {
        return withStudent(command.studentId(), () -> transactions.inTransaction(connection -> {
            requireById(connection, command.studentId(), trustedDepartmentId);
            students.updateContact(connection, command.studentId(), normalizeEmail(command.email()),
                    blankToNull(command.phone()), command.expectedVersion(), Instant.now());
            return view(connection, requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentView changeStatus(ChangeStudentStatusCommand command) {
        return changeStatus(command, operatorUserId);
    }

    @Override public StudentView changeStatus(ChangeStudentStatusCommand command, String auditUserId) {
        return changeStatus(command, auditUserId, null);
    }
}
