package edu.seu.vcampus.server.course.integration;

import edu.seu.vcampus.common.course.AcademicSeason;
import edu.seu.vcampus.server.course.domain.CurriculumNotConfiguredException;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.AccessCurriculumRepository;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.CurriculumRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.EnrollmentAdjustment;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferEnrollmentPort;

import java.sql.Connection;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Access-backed course enrollment adapter used by major-transfer finalization. */
public final class AccessMajorTransferEnrollmentAdapter implements MajorTransferEnrollmentPort {
    private final CourseRepository courses;
    private final CurriculumRepository curricula;

    /** Creates an adapter backed by the production Access repositories. */
    public AccessMajorTransferEnrollmentAdapter() {
        this(new AccessCourseRepository(), new AccessCurriculumRepository());
    }

    AccessMajorTransferEnrollmentAdapter(CourseRepository courses, CurriculumRepository curricula) {
        this.courses = Objects.requireNonNull(courses);
        this.curricula = Objects.requireNonNull(curricula);
    }

    /** {@inheritDoc} */
    @Override
    public void validate(Connection connection, String targetMajorCode, int cohortYear) {
        curricula.findPublishedPlan(connection, targetMajorCode, cohortYear)
                .orElseThrow(CurriculumNotConfiguredException::new);
    }

    /** {@inheritDoc} */
    @Override
    public Reconciliation reconcile(Connection connection, String studentId, String targetMajorCode,
                                    int cohortYear, String operatorUserId, Instant occurredAt) {
        Objects.requireNonNull(connection);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(targetMajorCode);
        Objects.requireNonNull(operatorUserId);
        Objects.requireNonNull(occurredAt);
        var plan = curricula.findPublishedPlan(connection, targetMajorCode, cohortYear)
                .orElseThrow(CurriculumNotConfiguredException::new);
        Set<String> allowedCourseIds = curriculumCourseIds(connection, plan.planId());
        int dropped = 0;
        for (Enrollment enrollment : courses.findActiveByStudent(connection, studentId)) {
            var offering = courses.requireOffering(connection, enrollment.offeringId());
            if (!"ACTIVE".equals(courses.requireTerm(connection, offering.termId()).termStatus())
                    || allowedCourseIds.contains(offering.courseId())) {
                continue;
            }
            courses.updateEnrollment(connection, dropped(enrollment, occurredAt),
                    enrollment.rowVersion());
            courses.changeEnrolledCount(connection, offering.offeringId(),
                    enrollment.enrollmentType(), -1);
            courses.insertAdjustment(connection, new EnrollmentAdjustment(
                    UUID.randomUUID().toString(), studentId, "MAJOR_TRANSFER_AUTO_DROP",
                    offering.offeringId(), null, "SUCCEEDED", null, occurredAt));
            dropped++;
        }
        return new Reconciliation(dropped);
    }

    private Set<String> curriculumCourseIds(Connection connection, String planId) {
        Set<String> courseIds = new HashSet<>();
        for (int academicYear = 1; academicYear <= 4; academicYear++) {
            for (AcademicSeason season : AcademicSeason.values()) {
                curricula.findScheduledCourses(connection, planId, academicYear, season)
                        .forEach(course -> courseIds.add(course.courseId()));
            }
        }
        return courseIds;
    }

    private Enrollment dropped(Enrollment enrollment, Instant occurredAt) {
        return new Enrollment(enrollment.enrollmentId(), enrollment.offeringId(),
                enrollment.studentId(), enrollment.enrollmentType(), "DROPPED",
                enrollment.enrolledAt(), occurredAt, enrollment.rowVersion(),
                enrollment.createdAt(), enrollment.updatedAt());
    }
}
