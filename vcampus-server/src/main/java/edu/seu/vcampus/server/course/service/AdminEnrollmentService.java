package edu.seu.vcampus.server.course.service;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import edu.seu.vcampus.common.course.AdminEnrollStudentCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseAlreadyPassedException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.RetakeNotEligibleException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.EnrollmentAdjustment;
import edu.seu.vcampus.server.persistence.TransactionManager;

/** Locked administrator placement for exceptional retake enrollment. */
final class AdminEnrollmentService {
    private final CourseStudentGateway students;
    private final CourseRepository repository;
    private final ResourceLockManager locks;
    private final TransactionManager transactions;
    private final Clock clock;
    private final AdjustmentEnrollmentRules rules;

    AdminEnrollmentService(CourseStudentGateway students, CourseRepository repository,
                           ResourceLockManager locks, TransactionManager transactions,
                           ScheduleConflictPolicy conflicts, Clock clock) {
        this.students = Objects.requireNonNull(students);
        this.repository = Objects.requireNonNull(repository);
        this.locks = Objects.requireNonNull(locks);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
        this.rules = new AdjustmentEnrollmentRules(repository, conflicts);
    }

    EnrollmentView enroll(AdminEnrollStudentCommand command) {
        Objects.requireNonNull(command);
        StudentEnrollmentEligibility initial = requireActive(
                students.findActiveByStudentNumber(command.studentNumber()));
        return locks.withLocks(List.of(
                new ResourceKey("STUDENT", initial.studentId()),
                new ResourceKey("OFFERING", command.offeringId())), () -> {
            StudentEnrollmentEligibility current = requireActive(
                    students.findActiveByStudentNumber(command.studentNumber()));
            if (!initial.studentId().equals(current.studentId())) throw new StudentIneligibleException();
            return transactions.inTransaction(connection -> enrollInside(
                    connection, current.studentId(), command.offeringId(), clock.instant()));
        });
    }

    private EnrollmentView enrollInside(Connection connection, String studentId,
                                        String offeringId, Instant now) {
        var offering = repository.requireOffering(connection, offeringId);
        if (!"OPEN".equals(offering.offeringStatus())) throw new EnrollmentClosedException();
        if (repository.existsPassedAttempt(connection, studentId, offering.courseId())) {
            throw new CourseAlreadyPassedException();
        }
        if (!repository.existsFailedAttempt(connection, studentId, offering.courseId())) {
            throw new RetakeNotEligibleException();
        }
        rules.requireNoDuplicateOrConflict(connection,
                repository.findActiveByStudentAndTerm(connection, studentId, offering.termId()), offering, null);
        var quota = repository.findRetakeQuota(connection, offeringId);
        if (quota.enrolledCount() >= quota.capacity()) {
            repository.saveRetakeCapacity(connection, offeringId, quota.enrolledCount() + 1);
        }
        Enrollment saved = repository.insertEnrollment(connection, new Enrollment(
                UUID.randomUUID().toString(), offeringId, studentId, "RETAKE", "ACTIVE",
                now, null, 0, null, null));
        repository.changeEnrolledCount(connection, offeringId, "RETAKE", 1);
        repository.insertAdjustment(connection, new EnrollmentAdjustment(
                UUID.randomUUID().toString(), studentId, "ADMIN_RETAKE_ADD", null,
                offeringId, "SUCCEEDED", null, now));
        return new EnrollmentView(saved.enrollmentId(), saved.offeringId(), saved.studentId(),
                saved.enrollmentType(), saved.enrollmentStatus(), saved.enrolledAt(),
                saved.droppedAt(), saved.rowVersion());
    }

    private static StudentEnrollmentEligibility requireActive(StudentEnrollmentEligibility student) {
        if (student == null || !"ACTIVE".equals(student.status())) throw new StudentIneligibleException();
        return student;
    }
}
