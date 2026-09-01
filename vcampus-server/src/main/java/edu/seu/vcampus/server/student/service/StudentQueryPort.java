package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.StudentEligibility;
import edu.seu.vcampus.common.student.StudentIdentity;
public interface StudentQueryPort {
    StudentIdentity findByUserId(String userId);
    StudentEligibility getEnrollmentEligibility(String userId);
    boolean existsActiveStudent(String studentId);
}
