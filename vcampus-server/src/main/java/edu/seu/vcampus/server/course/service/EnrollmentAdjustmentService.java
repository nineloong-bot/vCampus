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
import edu.seu.vcampus.server.course.domain.SelectionPhasePolicy;
import edu.seu.vcampus.server.course.domain.CourseAlreadyPassedException;
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
import java.util.function.Supplier;

/** Locked, transactional adjustment workflows kept separate from normal enrollment. */
final class EnrollmentAdjustmentService {
    private final CourseAuthorizationGateway authorization;
    private final CourseStudentGateway students;
    private final CourseRepository repository;
    private final ResourceLockManager locks;
    private final TransactionManager transactions;
    private final SelectionPhasePolicy phases;
    private final Clock clock;
    private final AdjustmentEnrollmentRules rules;

    EnrollmentAdjustmentService(CourseAuthorizationGateway authorization, CourseStudentGateway students,
                                CourseRepository repository, ResourceLockManager locks,
                                TransactionManager transactions, SelectionPhasePolicy phases,
                                ScheduleConflictPolicy conflicts, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.students = Objects.requireNonNull(students, "students");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.phases = Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(conflicts, "conflicts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rules = new AdjustmentEnrollmentRules(repository, conflicts);
    }

    EnrollmentView add(String token, LateAddCommand command) {
        Objects.requireNonNull(command, "command");
        Actor initial = initialActor(token);
        return locks.withLocks(List.of(studentKey(initial.studentId()), offeringKey(command.offeringId())), () -> {
            Instant now = clock.instant();
            return auditBusiness(initial, "ADD", new SourceReference(), command.offeringId(), now, () -> {
                revalidate(token, initial);
                return transactions.inTransaction(c -> addInside(c, initial.studentId(), command.offeringId(), now));
            });
        });
    }

    void drop(String token, DropCommand command) {
        Objects.requireNonNull(command, "command");
        Actor initial = initialActor(token);
        locks.withLocks(List.of(studentKey(initial.studentId())), () -> {
            Instant now = clock.instant();
            SourceReference sourceRef = new SourceReference();
            return auditBusiness(initial, "DROP", sourceRef, null, now, () -> {
                Enrollment preliminary = sourceForLock(command.enrollmentId(), initial);
                sourceRef.offeringId = preliminary.offeringId();
                return locks.withLocks(offeringKeys(preliminary.offeringId(), null), () -> {
                    revalidate(token, initial);
                    return transactions.inTransaction(c -> {
                        Enrollment source = rules.requireOwnedActive(c, command.enrollmentId(), initial.studentId(), command.expectedVersion());
                        Offering offering = repository.requireOffering(c, source.offeringId());
                        phases.requireDropOpen(c, offering.termId());
                        repository.updateEnrollment(c, dropped(source, now), command.expectedVersion());
                        repository.changeEnrolledCount(c, source.offeringId(), -1);
                        repository.insertAdjustment(c, adjustment(initial.studentId(), "DROP", source.offeringId(), null, "SUCCEEDED", null, now));
                        return null;
                    });
                });
            });
        });
    }

    EnrollmentView change(String token, ChangeOfferingCommand command) {
        Objects.requireNonNull(command, "command");
        Actor initial = initialActor(token);
        return locks.withLocks(List.of(studentKey(initial.studentId())), () -> {
            Instant now = clock.instant();
            SourceReference sourceRef = new SourceReference();
            return auditBusiness(initial, "CHANGE", sourceRef, command.targetOfferingId(), now, () -> {
                Enrollment preliminary = sourceForLock(command.sourceEnrollmentId(), initial);
                sourceRef.offeringId = preliminary.offeringId();
                return locks.withLocks(offeringKeys(preliminary.offeringId(), command.targetOfferingId()), () -> {
                    revalidate(token, initial);
                    return transactions.inTransaction(c -> changeInside(c, initial.studentId(), command, now));
                });
            });
        });
    }

    private EnrollmentView addInside(Connection c, String studentId, String targetId, Instant now) {
        Offering target = repository.requireOffering(c, targetId);
        if (repository.existsPassedAttempt(c, studentId, target.courseId())) throw new CourseAlreadyPassedException();
        requireOpenForAdd(target);
        phases.requireAdjustmentOpen(c, target.termId());
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
        phases.requireAdjustmentOpen(c, sourceOffering.termId());
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

    private List<ResourceKey> offeringKeys(String sourceId, String targetId) {
        List<String> offeringIds = new ArrayList<>(List.of(sourceId));
        if (targetId != null && !sourceId.equals(targetId)) offeringIds.add(targetId);
        return offeringIds.stream().distinct().sorted(Comparator.naturalOrder())
                .map(this::offeringKey).toList();
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

    private Enrollment sourceForLock(String enrollmentId, Actor actor) {
        Enrollment source;
        try {
            source = transactions.inTransaction(c -> repository.requireEnrollment(c, enrollmentId));
        } catch (IllegalStateException unavailable) {
            throw new CourseForbiddenException();
        }
        if (!actor.studentId().equals(source.studentId())) throw new CourseForbiddenException();
        return source;
    }

    private <T> T auditBusiness(Actor actor, String type, SourceReference source, String target,
                                Instant now, Supplier<T> action) {
        try {
            return action.get();
        } catch (CourseRuleException failure) {
            try {
                transactions.inTransaction(c -> repository.insertAdjustment(c, adjustment(actor.studentId(), type,
                        source.offeringId, target, "FAILED", failure.code(), now)));
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw failure;
        }
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

    private static final class SourceReference {
        private String offeringId;
    }
}
