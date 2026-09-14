package edu.seu.vcampus.server.course.service;

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
