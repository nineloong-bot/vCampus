package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.ChangeTargetInvalidException;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.CourseRuleException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.EnrollmentAdjustment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Locked, transactional adjustment workflows kept separate from normal enrollment. */
final class EnrollmentAdjustmentService {
    private final CourseAuthorizationGateway authorization;
    private final CourseStudentGateway students;
    private final CourseRepository repository;
    private final ResourceLockManager locks;
    private final TransactionManager transactions;
    private final TermWindowPolicy windows;
    private final Clock clock;
    private final AdjustmentEnrollmentRules rules;

    EnrollmentAdjustmentService(CourseAuthorizationGateway authorization, CourseStudentGateway students,
                                CourseRepository repository, ResourceLockManager locks,
                                TransactionManager transactions, TermWindowPolicy windows,
                                ScheduleConflictPolicy conflicts, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.students = Objects.requireNonNull(students, "students");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.windows = Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(conflicts, "conflicts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rules = new AdjustmentEnrollmentRules(repository, conflicts);
    }

    EnrollmentView add(String token, LateAddCommand command) {
        Objects.requireNonNull(command, "command");
        Actor initial = initialActor(token);
        return locks.withLocks(List.of(studentKey(initial.studentId()), offeringKey(command.offeringId())), () -> {
            Actor current = revalidate(token, initial);
            Instant now = clock.instant();
            try {
                return transactions.inTransaction(c -> addInside(c, current.studentId(), command.offeringId(), now));
            } catch (CourseRuleException failure) {
                recordFailure(current.studentId(), "ADD", null, command.offeringId(), now, failure);
                throw failure;
            }
        });
    }

    void drop(String token, DropCommand command) {
        Objects.requireNonNull(command, "command");
        Actor initial = initialActor(token);
        Enrollment preliminary = sourceForLock(command.enrollmentId());
        returnAfterLocks(token, initial, preliminary.offeringId(), null, () -> {
            Instant now = clock.instant();
            try {
                transactions.inTransaction(c -> {
                    Enrollment source = rules.requireOwnedActive(c, command.enrollmentId(), initial.studentId(), command.expectedVersion());
                    Offering offering = repository.requireOffering(c, source.offeringId());
                    windows.requireAdjustmentOpen(repository.requireTerm(c, offering.termId()), now);
                    repository.updateEnrollment(c, dropped(source, now), command.expectedVersion());
                    repository.changeEnrolledCount(c, source.offeringId(), -1);
                    repository.insertAdjustment(c, adjustment(initial.studentId(), "DROP", source.offeringId(), null, "SUCCEEDED", null, now));
                    return null;
                });
            } catch (CourseRuleException failure) {
                recordFailure(initial.studentId(), "DROP", preliminary.offeringId(), null, now, failure);
                throw failure;
            }
            return null;
        });
    }

    EnrollmentView change(String token, ChangeOfferingCommand command) {
        Objects.requireNonNull(command, "command");
        Actor initial = initialActor(token);
        Enrollment preliminary = sourceForLock(command.sourceEnrollmentId());
        return returnAfterLocks(token, initial, preliminary.offeringId(), command.targetOfferingId(), () -> {
            Instant now = clock.instant();
            try {
                return transactions.inTransaction(c -> changeInside(c, initial.studentId(), command, now));
            } catch (CourseRuleException failure) {
                recordFailure(initial.studentId(), "CHANGE", preliminary.offeringId(), command.targetOfferingId(), now, failure);
                throw failure;
            }
        });
    }

    private EnrollmentView addInside(Connection c, String studentId, String targetId, Instant now) {
        Offering target = repository.requireOffering(c, targetId);
        requireOpenForAdd(target);
        windows.requireAdjustmentOpen(repository.requireTerm(c, target.termId()), now);
        List<Enrollment> active = repository.findActiveByStudentAndTerm(c, studentId, target.termId());
        rules.requireTargetAllowed(c, active, target, null);
        Enrollment saved = repository.insertEnrollment(c, new Enrollment(UUID.randomUUID().toString(), targetId,
                studentId, "LATE_ADD", "ACTIVE", now, null, 0, null, null));
        repository.changeEnrolledCount(c, targetId, 1);
        repository.insertAdjustment(c, adjustment(studentId, "ADD", null, targetId, "SUCCEEDED", null, now));
        return view(saved);
    }

    private EnrollmentView changeInside(Connection c, String studentId, ChangeOfferingCommand command, Instant now) {
        Enrollment source = rules.requireOwnedActive(c, command.sourceEnrollmentId(), studentId, command.expectedVersion());
        if (source.offeringId().equals(command.targetOfferingId())) throw new ChangeTargetInvalidException();
        Offering sourceOffering = repository.requireOffering(c, source.offeringId());
        windows.requireAdjustmentOpen(repository.requireTerm(c, sourceOffering.termId()), now);
        Offering target = rules.requireChangeTarget(c, sourceOffering, command.targetOfferingId());
        List<Enrollment> active = repository.findActiveByStudentAndTerm(c, studentId, target.termId());
        rules.requireTargetAllowed(c, active, target, source.enrollmentId());
        Enrollment saved = repository.insertEnrollment(c, new Enrollment(UUID.randomUUID().toString(), target.offeringId(),
                studentId, source.enrollmentType(), "ACTIVE", now, null, 0, null, null));
        repository.updateEnrollment(c, dropped(source, now), command.expectedVersion());
        repository.changeEnrolledCount(c, source.offeringId(), -1);
        repository.changeEnrolledCount(c, target.offeringId(), 1);
        repository.insertAdjustment(c, adjustment(studentId, "CHANGE", source.offeringId(), target.offeringId(),
                "SUCCEEDED", null, now));
        return view(saved);
    }


    private <T> T returnAfterLocks(String token, Actor initial, String sourceId,
                                   String targetId, java.util.function.Supplier<T> action) {
        List<ResourceKey> keys = new ArrayList<>();
        keys.add(studentKey(initial.studentId()));
        List<String> offeringIds = new ArrayList<>(List.of(sourceId));
        if (targetId != null && !sourceId.equals(targetId)) offeringIds.add(targetId);
        offeringIds.stream().distinct().sorted(Comparator.naturalOrder()).map(this::offeringKey).forEach(keys::add);
        return locks.withLocks(keys, () -> {
            revalidate(token, initial);
            return action.get();
        });
    }

    private Actor initialActor(String token) {
        CourseSessionIdentity identity = requireStudentSessionInstance(token);
        StudentEnrollmentEligibility eligibility = requireEligible(students.getEnrollmentEligibility(identity.userId()));
        return new Actor(identity.userId(), eligibility.studentId());
    }

    private Actor revalidate(String token, Actor original) {
        CourseSessionIdentity identity = requireStudentSessionInstance(token);
        if (!original.userId().equals(identity.userId())) throw new CourseForbiddenException();
        StudentEnrollmentEligibility current = requireEligible(students.getEnrollmentEligibility(identity.userId()));
        if (!original.studentId().equals(current.studentId())) throw new StudentIneligibleException();
        return original;
    }

    private Enrollment sourceForLock(String enrollmentId) {
        return transactions.inTransaction(c -> repository.requireEnrollment(c, enrollmentId));
    }

    private void recordFailure(String studentId, String type, String source, String target, Instant now,
                               CourseRuleException failure) {
        transactions.inTransaction(c -> repository.insertAdjustment(c, adjustment(studentId, type, source, target,
                "FAILED", failure.code(), now)));
    }

    private static void requireOpenForAdd(Offering offering) {
        if (!"OPEN".equals(offering.offeringStatus())) throw new EnrollmentClosedException();
    }

    private static Enrollment dropped(Enrollment source, Instant now) {
        return new Enrollment(source.enrollmentId(), source.offeringId(), source.studentId(), source.enrollmentType(),
                "DROPPED", source.enrolledAt(), now, source.rowVersion(), source.createdAt(), source.updatedAt());
    }

    private static EnrollmentAdjustment adjustment(String studentId, String type, String source, String target,
                                                   String result, String failure, Instant now) {
        return new EnrollmentAdjustment(UUID.randomUUID().toString(), studentId, type, source, target, result, failure, now);
    }

    private static EnrollmentView view(Enrollment enrollment) {
        return new EnrollmentView(enrollment.enrollmentId(), enrollment.offeringId(), enrollment.studentId(),
                enrollment.enrollmentType(), enrollment.enrollmentStatus(), enrollment.enrolledAt(),
                enrollment.droppedAt(), enrollment.rowVersion());
    }

    private CourseSessionIdentity requireStudentSessionInstance(String token) {
        CourseSessionIdentity identity = authorization.requireSession(token);
        if (identity == null || !"STUDENT".equals(identity.role())) throw new CourseForbiddenException();
        return identity;
    }

    private StudentEnrollmentEligibility requireEligible(StudentEnrollmentEligibility eligibility) {
        if (eligibility == null || !"ACTIVE".equals(eligibility.status())) throw new StudentIneligibleException();
        return eligibility;
    }

    private ResourceKey studentKey(String id) { return new ResourceKey("STUDENT", id); }
    private ResourceKey offeringKey(String id) { return new ResourceKey("OFFERING", id); }

    private record Actor(String userId, String studentId) { }
}
