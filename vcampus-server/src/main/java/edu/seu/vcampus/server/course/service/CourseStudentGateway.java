package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.CourseStudentCandidate;
import edu.seu.vcampus.common.course.CourseStudentCandidateQuery;
import edu.seu.vcampus.common.paging.PageResult;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Course-owned adapter boundary for minimal student enrollment eligibility. */
@FunctionalInterface
public interface CourseStudentGateway {
    /** Resolves a user to a student identifier and current academic status. */
    StudentEnrollmentEligibility getEnrollmentEligibility(String userId);

    /** Checks an imported stable student id without exposing the student repository. */
    default boolean existsActiveStudent(String studentId) {
        throw new IllegalStateException("Active-student lookup is not configured");
    }

    /** Resolves an active student by the student number entered by an administrator. */
    default StudentEnrollmentEligibility findActiveByStudentNumber(String studentNumber) {
        throw new IllegalStateException("Student-number lookup is not configured");
    }

    /** Searches active students by student number for administrator selection. */
    default PageResult<CourseStudentCandidate> searchActiveStudents(CourseStudentCandidateQuery query) {
        throw new IllegalStateException("Student search is not configured");
    }

    /** Resolves an internal student identifier to its stable student number. */
    default String findStudentNumber(String studentId) {
        throw new IllegalStateException("Student display lookup is not configured");
    }

    /** Adapts the two read-only student-module queries to the course boundary. */
    static CourseStudentGateway of(Function<String, StudentEnrollmentEligibility> eligibility,
                                   Predicate<String> activeStudentExists) {
        return of(eligibility, activeStudentExists, number -> null);
    }

    /** Adapts all read-only student-module queries needed by course enrollment. */
    static CourseStudentGateway of(Function<String, StudentEnrollmentEligibility> eligibility,
                                   Predicate<String> activeStudentExists,
                                   Function<String, StudentEnrollmentEligibility> studentNumberLookup) {
        Objects.requireNonNull(eligibility);
        Objects.requireNonNull(activeStudentExists);
        Objects.requireNonNull(studentNumberLookup);
        return new CourseStudentGateway() {
            @Override public StudentEnrollmentEligibility getEnrollmentEligibility(String userId) {
                return eligibility.apply(userId);
            }

            @Override public boolean existsActiveStudent(String studentId) {
                return activeStudentExists.test(studentId);
            }

            @Override public StudentEnrollmentEligibility findActiveByStudentNumber(String studentNumber) {
                return studentNumberLookup.apply(studentNumber);
            }
        };
    }
}
