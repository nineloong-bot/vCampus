package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Credentials submitted to the public USER_LOGIN command. */
public record LoginCommand(
        String loginId,
        char[] password,
        String clientInstanceId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
