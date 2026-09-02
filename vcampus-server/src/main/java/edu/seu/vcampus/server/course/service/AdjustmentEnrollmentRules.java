package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.server.course.domain.ChangeTargetInvalidException;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentNotActiveException;
import edu.seu.vcampus.server.course.domain.EnrollmentVersionMismatchException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/** Transaction-local ownership and target validation for enrollment adjustments. */
final class AdjustmentEnrollmentRules {
    private final CourseRepository repository;
    private final ScheduleConflictPolicy conflicts;

    AdjustmentEnrollmentRules(CourseRepository repository, ScheduleConflictPolicy conflicts) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.conflicts = Objects.requireNonNull(conflicts, "conflicts");
    }

    Enrollment requireOwnedActive(Connection c, String enrollmentId, String studentId, long expectedVersion) {
        Enrollment source = repository.requireEnrollment(c, enrollmentId);
        if (!studentId.equals(source.studentId())) throw new CourseForbiddenException();
        if (!"ACTIVE".equals(source.enrollmentStatus())) throw new EnrollmentNotActiveException();
        if (source.rowVersion() != expectedVersion) throw new EnrollmentVersionMismatchException();
        return source;
    }

    Offering requireChangeTarget(Connection c, Offering source, String targetId) {
        Offering target;
        try {
            target = repository.requireOffering(c, targetId);
        } catch (IllegalStateException missing) {
            throw new ChangeTargetInvalidException();
        }
        if (!source.termId().equals(target.termId()) || !"OPEN".equals(target.offeringStatus())) {
            throw new ChangeTargetInvalidException();
        }
        return target;
    }

    void requireTargetAllowed(Connection c, List<Enrollment> active, Offering target, String ignoredEnrollmentId) {
        for (Enrollment enrollment : active) {
            if (enrollment.enrollmentId().equals(ignoredEnrollmentId)) continue;
            Offering selected = repository.requireOffering(c, enrollment.offeringId());
            if (target.courseId().equals(selected.courseId())) throw new DuplicateEnrollmentException();
            if (schedulesConflict(c, selected.offeringId(), target.offeringId())) throw new ScheduleConflictException();
        }
        if (target.enrolledCount() >= target.capacity()) throw new OfferingFullException();
    }

    private boolean schedulesConflict(Connection c, String selectedId, String targetId) {
        for (Schedule selected : repository.findSchedules(c, selectedId)) {
            for (Schedule target : repository.findSchedules(c, targetId)) {
                if (conflicts.conflicts(selected, target)) return true;
            }
        }
        return false;
    }
}
