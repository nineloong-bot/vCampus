package edu.seu.vcampus.server.course.service;

/** Course-owned adapter boundary for minimal student enrollment eligibility. */
@FunctionalInterface
public interface CourseStudentGateway {
    /** Resolves a user to a student identifier and current academic status. */
    StudentEnrollmentEligibility getEnrollmentEligibility(String userId);
}
