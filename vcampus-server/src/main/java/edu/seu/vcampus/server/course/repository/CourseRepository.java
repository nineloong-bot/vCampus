package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Transaction-scoped persistence operations for every course-owned table. */
public interface CourseRepository {
    /** Inserts a term with an initial version and audit timestamps. */
    Term insertTerm(Connection connection, Term term);

    /** Returns the requested term or signals that it no longer exists. */
    Term requireTerm(Connection connection, String termId);

    /** Lists all configured terms, newest code first. */
    List<Term> findTerms(Connection connection);

    /** Replaces a term when its optimistic-lock version matches. */
    Term updateTerm(Connection connection, Term term, long expectedVersion);

    /** Inserts a catalog course with an initial version and audit timestamps. */
    Course insertCourse(Connection connection, Course course);

    /** Returns the requested catalog course or signals that it no longer exists. */
    Course requireCourse(Connection connection, String courseId);

    /** Lists catalog courses by code. */
    List<Course> findCourses(Connection connection);

    /** Replaces a catalog course when its optimistic-lock version matches. */
    Course updateCourse(Connection connection, Course course, long expectedVersion);

    /** Inserts an offering and all of its schedule rows atomically in the caller transaction. */
    Offering insertOffering(Connection connection, Offering offering, List<Schedule> schedules);

    /** Returns the requested offering or signals that it no longer exists. */
    Offering requireOffering(Connection connection, String offeringId);

    /** Lists offerings belonging to a term. */
    List<Offering> findOfferingsByTerm(Connection connection, String termId);

    /** Replaces an offering and its complete schedule collection under optimistic locking. */
    Offering updateOffering(Connection connection, Offering offering, long expectedVersion,
                            List<Schedule> schedules);

    /** Lists schedule rows for one offering in display order. */
    List<Schedule> findSchedules(Connection connection, String offeringId);

    /** Finds an enrollment by its natural student/offering key. */
    Optional<Enrollment> findEnrollment(Connection connection, String studentId, String offeringId);

    /** Returns an enrollment by its stable identifier or signals that it no longer exists. */
    Enrollment requireEnrollment(Connection connection, String enrollmentId);

    /** Returns active enrollments for a student in a term. */
    List<Enrollment> findActiveByStudentAndTerm(Connection connection, String studentId,
                                                 String termId);

    /** Inserts an enrollment or reactivates the retained dropped row for the same natural key. */
    Enrollment insertEnrollment(Connection connection, Enrollment enrollment);

    /** Updates an enrollment when its optimistic-lock version matches. */
    Enrollment updateEnrollment(Connection connection, Enrollment enrollment, long expectedVersion);

    /** Applies one enrollment-count delta and increments the offering version. */
    Offering changeEnrolledCount(Connection connection, String offeringId, int delta);

    /** Writes an immutable adjustment audit row. */
    EnrollmentAdjustment insertAdjustment(Connection connection, EnrollmentAdjustment adjustment);

    /** Lists a student's adjustment audit rows, newest first. */
    List<EnrollmentAdjustment> findAdjustmentsByStudent(Connection connection, String studentId);

    /** Inserts an imported outcome unless its source reference was already processed. */
    boolean insertAttemptIfAbsent(Connection connection, CourseAttempt attempt);

    /** Lists imported outcomes for one student's course. */
    List<CourseAttempt> findAttempts(Connection connection, String studentId, String courseId);

    /** Determines whether a student has at least one imported failed outcome for a course. */
    boolean existsFailedAttempt(Connection connection, String studentId, String courseId);
}
