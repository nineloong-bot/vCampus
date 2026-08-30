package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/** Public login request that protects its mutable password value. */
public record LoginCommand(
        String loginId,
        char[] password,
        String clientInstanceId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Copies the password into the command and clears the caller-owned array. */
    public LoginCommand {
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(clientInstanceId, "clientInstanceId");
        char[] submittedPassword = Objects.requireNonNull(password, "password");
        password = submittedPassword.clone();
        Arrays.fill(submittedPassword, '\0');
    }

    /** Returns a defensive copy of the password. */
    @Override
    public char[] password() {
        return password.clone();
    }

    /** Clears the password retained by this request; repeated calls are harmless. */
    public void clearPassword() {
        Arrays.fill(password, '\0');
    }
}
