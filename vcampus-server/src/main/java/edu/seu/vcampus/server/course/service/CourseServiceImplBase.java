package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.CurriculumSelectionPolicy;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.SelectionPhasePolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;
import java.util.Objects;

/** Shared collaborators, constructors and session helpers for the course-service segments. */
abstract class CourseServiceImplBase implements CourseService, CourseQueryPort {
    final CourseAuthorizationGateway authorization;
    final CourseStudentGateway students;
    final CourseRepository repository;
    final ResourceLockManager locks;
    final TransactionManager transactions;
    final TermWindowPolicy windows;
    final ScheduleConflictPolicy conflicts;
    final Clock clock;
    final EnrollmentAdjustmentService adjustments;
    final SelectionPhaseService selectionPhases;
    final SelectionPhasePolicy phasePolicy;
    final CurriculumSelectionPolicy curriculumPolicy;
    final AdminEnrollmentService adminEnrollments;

    /**
     * Creates the shared course-service segment.
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
    protected CourseServiceImplBase(CourseAuthorizationGateway authorization,
                             CourseStudentGateway students,
                             CourseRepository repository,
                             edu.seu.vcampus.server.course.repository.CurriculumRepository curricula,
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
        this.curriculumPolicy = curricula == null ? null : new CurriculumSelectionPolicy(curricula, repository);
        this.phasePolicy = new SelectionPhasePolicy(repository);
        this.adjustments = new EnrollmentAdjustmentService(authorization, students, repository, locks,
                transactions, phasePolicy, conflicts, clock);
        this.selectionPhases = new SelectionPhaseService(repository, locks, transactions);
        this.adminEnrollments = new AdminEnrollmentService(
                students, repository, locks, transactions, conflicts, clock);
    }

    CourseSessionIdentity requireStudentSession(String sessionToken) {
        CourseSessionIdentity identity = authorization.requireSession(sessionToken);
        if (identity == null || !"STUDENT".equals(identity.role())) {
            throw new CourseForbiddenException();
        }
        return identity;
    }

    StudentEnrollmentEligibility revalidateStudent(String sessionToken,
                                                            CourseSessionIdentity identity,
                                                            StudentEnrollmentEligibility initial) {
        CourseSessionIdentity currentIdentity = requireStudentSession(sessionToken);
        if (!identity.userId().equals(currentIdentity.userId())) throw new CourseForbiddenException();
        StudentEnrollmentEligibility current = requireEligible(
                students.getEnrollmentEligibility(currentIdentity.userId()));
        if (!initial.studentId().equals(current.studentId())) throw new StudentIneligibleException();
        return current;
    }

    static StudentEnrollmentEligibility requireEligible(
            StudentEnrollmentEligibility eligibility) {
        if (eligibility == null || !"ACTIVE".equals(eligibility.status())) {
            throw new StudentIneligibleException();
        }
        return eligibility;
    }

    static boolean blank(String value){return value==null||value.isBlank();}

    static EnrollmentView toView(Enrollment enrollment) {
        return new EnrollmentView(enrollment.enrollmentId(), enrollment.offeringId(),
                enrollment.studentId(), enrollment.enrollmentType(), enrollment.enrollmentStatus(),
                enrollment.enrolledAt(), enrollment.droppedAt(), enrollment.rowVersion());
    }
}
