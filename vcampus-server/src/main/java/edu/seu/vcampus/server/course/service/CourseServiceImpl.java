package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.OutcomeImportInvalidException;
import edu.seu.vcampus.server.course.domain.RetakeNotEligibleException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.CourseAttempt;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
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

    @Override
    public RetakeEligibility checkRetakeEligibility(String sessionToken, String courseId) {
        if (courseId == null || courseId.isBlank()) throw new IllegalArgumentException("courseId");
        CourseSessionIdentity identity = requireStudentSession(sessionToken);
        StudentEnrollmentEligibility initial = requireEligible(
                students.getEnrollmentEligibility(identity.userId()));
        return locks.withLocks(List.of(new ResourceKey("STUDENT", initial.studentId())), () -> {
            StudentEnrollmentEligibility current = revalidateStudent(sessionToken, identity, initial);
            return transactions.inTransaction(connection -> {
                List<String> failedIds = repository.findAttempts(connection, current.studentId(), courseId)
                        .stream()
                        .filter(attempt -> CourseOutcome.FAILED.name().equals(attempt.outcome()))
                        .map(CourseAttempt::attemptId)
                        .toList();
                boolean eligible = !failedIds.isEmpty();
                return new RetakeEligibility(courseId, eligible, failedIds,
                        eligible ? null : RetakeNotEligibleException.CODE);
            });
        });
    }

    @Override
    public EnrollmentView enrollRetake(String sessionToken, RetakeCommand command) {
        Objects.requireNonNull(command, "command");
        CourseSessionIdentity identity = requireStudentSession(sessionToken);
        StudentEnrollmentEligibility initial = requireEligible(
                students.getEnrollmentEligibility(identity.userId()));
        List<ResourceKey> orderedKeys = List.of(
                new ResourceKey("STUDENT", initial.studentId()),
                new ResourceKey("OFFERING", command.offeringId()));
        return locks.withLocks(orderedKeys, () -> {
            StudentEnrollmentEligibility current = revalidateStudent(sessionToken, identity, initial);
            Instant operationTime = clock.instant();
            return transactions.inTransaction(connection ->
                    enrollLocked(connection, current.studentId(), command.offeringId(),
                            operationTime, "RETAKE", true));
        });
    }

    @Override
    public void importCourseOutcomes(ImportCourseOutcomesCommand command) {
        if (command == null || command.outcomes() == null || command.outcomes().isEmpty()) {
            throw new OutcomeImportInvalidException();
        }
        List<ResourceKey> sourceKeys = command.outcomes().stream()
                .map(entry -> new ResourceKey("COURSE_OUTCOME", entry.sourceReference()))
                .distinct()
                .sorted(Comparator.comparing(ResourceKey::resourceType)
                        .thenComparing(ResourceKey::resourceId))
                .toList();
        locks.withLocks(sourceKeys, () -> {
            try {
                transactions.inTransaction(connection -> {
                    Instant importedAt = clock.instant();
                    for (ImportCourseOutcomesCommand.OutcomeEntry entry : command.outcomes()) {
                        CourseAttempt incoming = new CourseAttempt(UUID.randomUUID().toString(),
                                entry.studentId(), entry.courseId(), entry.termId(),
                                entry.outcome().name(), entry.sourceReference(), importedAt);
                        var existing = repository.findAttemptBySourceReference(
                                connection, entry.sourceReference());
                        if (existing.isPresent()) {
                            requireSameImport(existing.orElseThrow(), incoming);
                        } else {
                            boolean inserted = repository.insertAttemptIfAbsent(connection, incoming);
                            if (!inserted) {
                                CourseAttempt concurrent = repository.findAttemptBySourceReference(
                                                connection, entry.sourceReference())
                                        .orElseThrow(OutcomeImportInvalidException::new);
                                requireSameImport(concurrent, incoming);
                            }
                        }
                    }
                    return null;
                });
            } catch (OutcomeImportInvalidException error) {
                throw error;
            } catch (RuntimeException error) {
                throw new OutcomeImportInvalidException(error);
            }
            return null;
        });
    }

    private EnrollmentView enrollLocked(Connection connection, String studentId, String offeringId,
                                        Instant operationTime) {
        return enrollLocked(connection, studentId, offeringId, operationTime, "NORMAL", false);
    }

    private EnrollmentView enrollLocked(Connection connection, String studentId, String offeringId,
                                        Instant operationTime, String enrollmentType,
                                        boolean requireFailedAttempt) {
        Offering offering = repository.requireOffering(connection, offeringId);
        if (requireFailedAttempt
                && !repository.existsFailedAttempt(connection, studentId, offering.courseId())) {
            throw new RetakeNotEligibleException();
        }
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
                UUID.randomUUID().toString(), offeringId, studentId, enrollmentType, "ACTIVE",
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

    private StudentEnrollmentEligibility revalidateStudent(String sessionToken,
                                                            CourseSessionIdentity identity,
                                                            StudentEnrollmentEligibility initial) {
        CourseSessionIdentity currentIdentity = requireStudentSession(sessionToken);
        if (!identity.userId().equals(currentIdentity.userId())) throw new CourseForbiddenException();
        StudentEnrollmentEligibility current = requireEligible(
                students.getEnrollmentEligibility(currentIdentity.userId()));
        if (!initial.studentId().equals(current.studentId())) throw new StudentIneligibleException();
        return current;
    }

    private static void requireSameImport(CourseAttempt existing, CourseAttempt incoming) {
        if (!existing.studentId().equals(incoming.studentId())
                || !existing.courseId().equals(incoming.courseId())
                || !existing.termId().equals(incoming.termId())
                || !existing.outcome().equals(incoming.outcome())) {
            throw new OutcomeImportInvalidException();
        }
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
