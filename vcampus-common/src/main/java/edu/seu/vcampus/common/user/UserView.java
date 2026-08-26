package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Safe account identity returned to the client. */
public record UserView(
        String userId,
        String loginId,
        UserRole role) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
