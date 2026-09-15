package edu.seu.vcampus.common.student.governance;
import java.io.Serializable;
/** Deactivates one college-administrator assignment by optimistic-lock version. */
/**
 * Carries immutable deactivate student college administrator command data.
 * @param departmentId the department identifier
 * @param userId the user identifier
 * @param expectedAssignmentVersion the expected assignment version
 */
public record DeactivateStudentCollegeAdministratorCommand(String departmentId, String userId,
        long expectedAssignmentVersion) implements Serializable { }
