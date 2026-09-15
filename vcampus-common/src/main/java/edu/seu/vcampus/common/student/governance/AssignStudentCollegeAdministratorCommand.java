package edu.seu.vcampus.common.student.governance;
import java.io.Serializable;
/** Assigns a preset college administrator to one department without credentials. */
/**
 * Carries immutable assign student college administrator command data.
 * @param departmentId the department identifier
 * @param userId the user identifier
 * @param expectedDepartmentVersion the expected department version
 */
public record AssignStudentCollegeAdministratorCommand(String departmentId, String userId,
        long expectedDepartmentVersion) implements Serializable { }
