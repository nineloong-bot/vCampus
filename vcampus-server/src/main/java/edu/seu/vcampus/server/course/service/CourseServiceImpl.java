package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;

/**
 * Concurrency-safe implementation of course enrollment application rules.
 *
 * <p>Term management, catalog queries, offering maintenance, student selection views,
 * enrollment mutations and outcome imports are implemented by the package-private
 * segment chain this class extends, keeping the public surface unchanged.</p>
 */
public final class CourseServiceImpl extends CourseServiceImplOutcomes {

    /**
     * Creates an enrollment service from course-owned infrastructure and gateway boundaries.
     * @param authorization the authorization
     * @param students the students
     * @param repository the repository
     * @param locks the locks
     * @param transactions the transactions
     * @param windows the windows
     * @param conflicts the conflicts
     * @param clock the clock
     */
    public CourseServiceImpl(CourseAuthorizationGateway authorization,
                             CourseStudentGateway students,
                             CourseRepository repository,
                             ResourceLockManager locks,
                             TransactionManager transactions,
                             TermWindowPolicy windows,
                             ScheduleConflictPolicy conflicts,
                             Clock clock) {
        this(authorization, students, repository, null, locks, transactions, windows, conflicts, clock);
    }

    /**
     * Creates a course service impl with its required collaborators.
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
    public CourseServiceImpl(CourseAuthorizationGateway authorization,
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
}
