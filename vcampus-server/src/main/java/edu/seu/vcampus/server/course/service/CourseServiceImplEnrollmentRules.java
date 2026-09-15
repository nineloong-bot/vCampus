package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseAlreadyPassedException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.RetakeNotEligibleException;
import edu.seu.vcampus.server.course.domain.RetakeRequiredException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
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
import java.util.UUID;

/** Shared enrollment rule enforcement invoked by the enrollment flows. */
abstract class CourseServiceImplEnrollmentRules extends CourseServiceImplSelection {

    /**
     * Creates the enrollment-rules segment.
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
    protected CourseServiceImplEnrollmentRules(CourseAuthorizationGateway authorization,
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

    EnrollmentView enrollLocked(Connection connection, StudentEnrollmentEligibility student,
                                        String offeringId,
                                        Instant operationTime) {
        return enrollLocked(connection, student, offeringId, operationTime, "NORMAL", false);
    }

    EnrollmentView enrollLocked(Connection connection, StudentEnrollmentEligibility student,
                                        String offeringId,
                                        Instant operationTime, String enrollmentType,
                                        boolean requireFailedAttempt) {
        Offering offering = repository.requireOffering(connection, offeringId);
        if (curriculumPolicy != null) {
            curriculumPolicy.resolve(connection, student,
                    repository.requireTerm(connection, offering.termId())).requireAllowed(offering.courseId());
        }
        String studentId = student.studentId();
        boolean passed = repository.existsPassedAttempt(connection, studentId, offering.courseId());
        boolean failed = repository.existsFailedAttempt(connection, studentId, offering.courseId());
        if (passed) throw new CourseAlreadyPassedException();
        if (requireFailedAttempt && !failed) throw new RetakeNotEligibleException();
        if (!requireFailedAttempt && failed) throw new RetakeRequiredException();
        if (!"OPEN".equals(offering.offeringStatus())) {
            throw new EnrollmentClosedException();
        }
        phasePolicy.requireEnrollmentOpen(connection, offering.termId());
        List<Enrollment> active = repository.findActiveByStudentAndTerm(
                connection, studentId, offering.termId());
        requireNoDuplicate(connection, active, offering);
        requireNoScheduleConflict(connection, active, offering);
        boolean full = "RETAKE".equals(enrollmentType)
                ? repository.findRetakeQuota(connection, offeringId).enrolledCount()
                    >= repository.findRetakeQuota(connection, offeringId).capacity()
                : offering.enrolledCount() >= offering.capacity();
        if (full) {
            throw new OfferingFullException();
        }
        Enrollment saved = repository.insertEnrollment(connection, new Enrollment(
                UUID.randomUUID().toString(), offeringId, studentId, enrollmentType, "ACTIVE",
                operationTime, null, 0, null, null));
        repository.changeEnrolledCount(connection, offeringId, enrollmentType, 1);
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
}
