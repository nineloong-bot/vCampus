package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Concurrency-safe implementation of course enrollment application rules. */
public final class CourseServiceImpl implements CourseService {
    private final CourseAuthorizationGateway authorization;
    private final CourseStudentGateway students;
    private final CourseRepository repository;
    private final ResourceLockManager locks;
    private final TransactionManager transactions;
    private final TermWindowPolicy windows;
    private final ScheduleConflictPolicy conflicts;
    private final Clock clock;
    private final EnrollmentAdjustmentService adjustments;

    /** Creates an enrollment service from course-owned infrastructure and gateway boundaries. */
    public CourseServiceImpl(CourseAuthorizationGateway authorization,
                             CourseStudentGateway students,
                             CourseRepository repository,
                             ResourceLockManager locks,
                             TransactionManager transactions,
                             TermWindowPolicy windows,
                             ScheduleConflictPolicy conflicts,
                             Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.students = Objects.requireNonNull(students, "students");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.conflicts = Objects.requireNonNull(conflicts, "conflicts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.adjustments = new EnrollmentAdjustmentService(authorization, students, repository, locks,
                transactions, windows, conflicts, clock);
    }

    /** Uses the declared student-then-offering lock order and repeats mutable validation. */
    @Override
    public EnrollmentView enroll(String sessionToken, EnrollCommand command) {
        Objects.requireNonNull(command, "command");
        CourseSessionIdentity identity = requireStudentSession(sessionToken);
        StudentEnrollmentEligibility initial = requireEligible(
                students.getEnrollmentEligibility(identity.userId()));
        List<ResourceKey> orderedKeys = List.of(
                new ResourceKey("STUDENT", initial.studentId()),
                new ResourceKey("OFFERING", command.offeringId()));
        return locks.withLocks(orderedKeys, () -> {
            CourseSessionIdentity currentIdentity = requireStudentSession(sessionToken);
            if (!identity.userId().equals(currentIdentity.userId())) {
                throw new CourseForbiddenException();
            }
            StudentEnrollmentEligibility current = requireEligible(
                    students.getEnrollmentEligibility(currentIdentity.userId()));
            if (!initial.studentId().equals(current.studentId())) {
                throw new StudentIneligibleException();
            }
            Instant operationTime = clock.instant();
            return transactions.inTransaction(connection ->
                    enrollLocked(connection, current.studentId(), command.offeringId(), operationTime));
        });
    }

    @Override
    public EnrollmentView addDuringAdjustment(String sessionToken, LateAddCommand command) {
        return adjustments.add(sessionToken, command);
    }

    @Override
    public void dropDuringAdjustment(String sessionToken, DropCommand command) {
        adjustments.drop(sessionToken, command);
    }

    @Override
    public EnrollmentView changeDuringAdjustment(String sessionToken, ChangeOfferingCommand command) {
        return adjustments.change(sessionToken, command);
    }

    private EnrollmentView enrollLocked(Connection connection, String studentId, String offeringId,
                                        Instant operationTime) {
        Offering offering = repository.requireOffering(connection, offeringId);
        if (!"OPEN".equals(offering.offeringStatus())) {
            throw new EnrollmentClosedException();
        }
        windows.requireEnrollmentOpen(repository.requireTerm(connection, offering.termId()), operationTime);
        List<Enrollment> active = repository.findActiveByStudentAndTerm(
                connection, studentId, offering.termId());
        requireNoDuplicate(connection, active, offering);
        requireNoScheduleConflict(connection, active, offering);
        if (offering.enrolledCount() >= offering.capacity()) {
            throw new OfferingFullException();
        }
        Enrollment saved = repository.insertEnrollment(connection, new Enrollment(
                UUID.randomUUID().toString(), offeringId, studentId, "NORMAL", "ACTIVE",
                operationTime, null, 0, null, null));
        repository.changeEnrolledCount(connection, offeringId, 1);
        return toView(saved);
    }

    private void requireNoDuplicate(Connection connection, List<Enrollment> active, Offering target) {
        for (Enrollment enrollment : active) {
            Offering selected = repository.requireOffering(connection, enrollment.offeringId());
            if (target.courseId().equals(selected.courseId())) {
                throw new DuplicateEnrollmentException();
            }
        }
    }

    private void requireNoScheduleConflict(Connection connection, List<Enrollment> active,
                                           Offering target) {
        List<Schedule> targetSchedules = repository.findSchedules(connection, target.offeringId());
        for (Enrollment enrollment : active) {
            for (Schedule selected : repository.findSchedules(connection, enrollment.offeringId())) {
                for (Schedule candidate : targetSchedules) {
                    if (conflicts.conflicts(selected, candidate)) {
                        throw new ScheduleConflictException();
                    }
                }
            }
        }
    }

    private CourseSessionIdentity requireStudentSession(String sessionToken) {
        CourseSessionIdentity identity = authorization.requireSession(sessionToken);
        if (identity == null || !"STUDENT".equals(identity.role())) {
            throw new CourseForbiddenException();
        }
        return identity;
    }

    private static StudentEnrollmentEligibility requireEligible(
            StudentEnrollmentEligibility eligibility) {
        if (eligibility == null || !"ACTIVE".equals(eligibility.status())) {
            throw new StudentIneligibleException();
        }
        return eligibility;
    }

    private static EnrollmentView toView(Enrollment enrollment) {
        return new EnrollmentView(enrollment.enrollmentId(), enrollment.offeringId(),
                enrollment.studentId(), enrollment.enrollmentType(), enrollment.enrollmentStatus(),
                enrollment.enrolledAt(), enrollment.droppedAt(), enrollment.rowVersion());
    }
}
