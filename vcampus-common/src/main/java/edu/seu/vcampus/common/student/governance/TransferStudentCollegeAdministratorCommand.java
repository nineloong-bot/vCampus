package edu.seu.vcampus.common.student.governance;
import java.io.Serializable;
/** Atomically transfers one preset college administrator between departments. */
/**
 * Carries immutable transfer student college administrator command data.
 * @param userId the user identifier
 * @param sourceDepartmentId the source department identifier
 * @param targetDepartmentId the target department identifier
 * @param expectedAssignmentVersion the expected assignment version
 * @param expectedTargetDepartmentVersion the expected target department version
 */
public record TransferStudentCollegeAdministratorCommand(String userId, String sourceDepartmentId,
        String targetDepartmentId, long expectedAssignmentVersion,
        long expectedTargetDepartmentVersion) implements Serializable { }
