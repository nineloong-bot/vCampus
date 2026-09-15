package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.StudentEligibility;
import edu.seu.vcampus.common.student.StudentIdentity;
import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentSummary;
import edu.seu.vcampus.common.student.StudentView;

/** Read-only student data available to other server modules. */
public interface StudentQueryPort {
    /** Finds the student identity linked to a user account. */
    StudentIdentity findByUserId(String userId);

    /** Resolves current enrollment eligibility from a user account. */
    StudentEligibility getEnrollmentEligibility(String userId);

    /** Resolves current enrollment eligibility from a student number. */
    StudentEligibility getEnrollmentEligibilityByStudentNumber(String studentNumber);

    /** Checks whether an internal student identifier belongs to an active student. */
    boolean existsActiveStudent(String studentId);

    /** Loads one student profile for server-side display projection. */
    StudentView getStudent(String studentId);

    /** Searches students without exposing the student repository. */
    PageResult<StudentSummary> searchStudents(StudentSearchQuery query);
}
