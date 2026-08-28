package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Request to replace the password for the current session. */
public record ChangePasswordCommand(char[] oldPassword, char[] newPassword)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
