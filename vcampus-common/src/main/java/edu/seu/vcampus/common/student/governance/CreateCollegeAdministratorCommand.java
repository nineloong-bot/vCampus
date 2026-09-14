package edu.seu.vcampus.common.student.governance;

import java.io.Serializable;

/** Command to provision a new college administrator account. */
public record CreateCollegeAdministratorCommand(
        String loginId,
        String password,
        String departmentId
) implements Serializable {
    public CreateCollegeAdministratorCommand {
        loginId = loginId == null ? null : loginId.trim();
        password = password == null ? null : password.trim();
        departmentId = departmentId == null || departmentId.isBlank() ? null : departmentId.trim();
    }
}
