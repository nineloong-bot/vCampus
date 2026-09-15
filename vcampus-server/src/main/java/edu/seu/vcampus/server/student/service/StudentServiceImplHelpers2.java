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

/** Provides focused helper operations for {@link StudentServiceImpl}. */
abstract class StudentServiceImplHelpers2 extends StudentServiceImplSupport {
    protected StudentServiceImplHelpers2(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        super(transactions, locks, students, changes, organizations, users, operatorUserId);
    }

    protected static boolean validTransition(StudentStatus from, StudentStatus to) {
        return switch (from) {
            case ACTIVE -> to == StudentStatus.SUSPENDED || to == StudentStatus.GRADUATED
                    || to == StudentStatus.WITHDRAWN;
            case SUSPENDED -> to == StudentStatus.ACTIVE || to == StudentStatus.WITHDRAWN;
            case GRADUATED, WITHDRAWN -> false;
        };
    }
}
