package edu.seu.vcampus.common.student.governance;
import java.io.Serializable;
/** Atomically transfers one preset college administrator between departments. */
public record TransferStudentCollegeAdministratorCommand(String userId, String sourceDepartmentId,
        String targetDepartmentId, long expectedAssignmentVersion,
        long expectedTargetDepartmentVersion) implements Serializable { }
