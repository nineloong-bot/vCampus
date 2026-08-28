package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Administrator request to change an account role. */
public record UpdateUserRoleCommand(String userId, UserRole newRole, long expectedVersion)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
