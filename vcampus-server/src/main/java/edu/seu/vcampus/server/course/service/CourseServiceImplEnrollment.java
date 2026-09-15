package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.RetakeNotEligibleException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseAttempt;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Student enrollment, retake and adjustment mutation flows. */
abstract class CourseServiceImplEnrollment extends CourseServiceImplEnrollmentRules {

    /**
     * Creates the enrollment segment.
     * @param authorization the authorization
     * @param students the students
     * @param repository the repository
     * @param curricula the curricula
     * @param locks the locks
     * @param transactions the transactions
     * @param windows the windows
     * @param conflicts the conflicts
     * @param clock the clock
     */
    protected CourseServiceImplEnrollment(CourseAuthorizationGateway authorization,
                             CourseStudentGateway students,
                             CourseRepository repository,
                             edu.seu.vcampus.server.course.repository.CurriculumRepository curricula,
                             ResourceLockManager locks,
                             TransactionManager transactions,
                             TermWindowPolicy windows,
                             ScheduleConflictPolicy conflicts,
                             Clock clock) {
        super(authorization, students, repository, curricula, locks, transactions, windows, conflicts, clock);
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
                    enrollLocked(connection, current, command.offeringId(), operationTime));
        });
    }

    @Override
    public EnrollmentView addDuringAdjustment(String sessionToken, LateAddCommand command) {
        return adjustments.add(sessionToken, command);
    }

    @Override
    public void drop(String sessionToken, DropCommand command) {
        adjustments.drop(sessionToken, command);
    }

    @Deprecated
    @Override
    public void dropDuringAdjustment(String sessionToken, DropCommand command) {
        drop(sessionToken, command);
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
                boolean eligible = !failedIds.isEmpty()
                        && !repository.existsPassedAttempt(connection, current.studentId(), courseId);
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
                    enrollLocked(connection, current, command.offeringId(),
                            operationTime, "RETAKE", true));
        });
    }
}
