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

/** Holds dependencies and shared state for {@link StudentServiceImpl}. */
abstract class StudentServiceImplSupport implements StudentService, StudentQueryPort {
    protected final TransactionManager transactions;
    protected final ResourceLockManager locks;
    protected final StudentRepository students;
    protected final StudentChangeRepository changes;
    protected final OrganizationRepository organizations;
    protected final UserQueryPort users;
    protected final String operatorUserId;

    /**
     * Creates a student service impl with its required collaborators.
     * @param transactions the transactions
     * @param locks the locks
     * @param students the students
     * @param changes the changes
     * @param organizations the organizations
     * @param users the users
     * @param operatorUserId the operator user identifier
     */
    protected StudentServiceImplSupport(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        this.transactions = Objects.requireNonNull(transactions); this.locks = Objects.requireNonNull(locks);
        this.students = Objects.requireNonNull(students); this.changes = Objects.requireNonNull(changes);
        this.organizations = Objects.requireNonNull(organizations); this.users = Objects.requireNonNull(users);
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
    }
}
