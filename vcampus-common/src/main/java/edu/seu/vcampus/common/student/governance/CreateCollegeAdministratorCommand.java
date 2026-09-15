package edu.seu.vcampus.common.student.governance;

import java.io.Serializable;

/** Command to provision a new college administrator account. */
/**
 * Carries immutable create college administrator command data.
 * @param loginId the login identifier
 * @param password the password
 * @param departmentId the department identifier
 */
public record CreateCollegeAdministratorCommand(
        String loginId,
        String password,
        String departmentId
) implements Serializable {
    /**
     * Validates and creates a create college administrator command.
     * @param loginId the login id
     * @param password the password
     * @param departmentId the department id
     */
    public CreateCollegeAdministratorCommand {
        loginId = loginId == null ? null : loginId.trim();
        password = password == null ? null : password.trim();
        departmentId = departmentId == null || departmentId.isBlank() ? null : departmentId.trim();
    }
}
