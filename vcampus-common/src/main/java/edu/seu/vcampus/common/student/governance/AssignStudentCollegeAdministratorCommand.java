package edu.seu.vcampus.common.student.governance;
import java.io.Serializable;
/** Assigns a preset college administrator to one department without credentials. */
public record AssignStudentCollegeAdministratorCommand(String departmentId, String userId,
        long expectedDepartmentVersion) implements Serializable { }
