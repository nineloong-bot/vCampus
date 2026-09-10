package edu.seu.vcampus.common.student.governance;
import java.io.Serializable;
/** Deactivates one college-administrator assignment by optimistic-lock version. */
public record DeactivateStudentCollegeAdministratorCommand(String departmentId, String userId,
        long expectedAssignmentVersion) implements Serializable { }
