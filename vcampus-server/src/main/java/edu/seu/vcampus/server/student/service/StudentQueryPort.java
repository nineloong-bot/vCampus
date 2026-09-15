package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.StudentEligibility;
import edu.seu.vcampus.common.student.StudentIdentity;
/** Defines the student query port contract. */
public interface StudentQueryPort {
    /**
     * Performs the find by user identifier operation.
     * @param userId the user identifier
     * @return the operation result
     */
    StudentIdentity findByUserId(String userId);
    /**
     * Performs the get enrollment eligibility operation.
     * @param userId the user identifier
     * @return the operation result
     */
    StudentEligibility getEnrollmentEligibility(String userId);
    /**
     * Performs the get enrollment eligibility by student number operation.
     * @param studentNumber the student number
     * @return the operation result
     */
    StudentEligibility getEnrollmentEligibilityByStudentNumber(String studentNumber);
    /**
     * Performs the exists active student operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    boolean existsActiveStudent(String studentId);
}
