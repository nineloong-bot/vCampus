package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/** Public request for applying for a teacher account without choosing a role. */
public record TeacherAccountApplicationCommand(
        String loginId,
        char[] password) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Copies the password into the command and clears the caller-owned array. */
    public TeacherAccountApplicationCommand {
        Objects.requireNonNull(loginId, "loginId");
        char[] submittedPassword = Objects.requireNonNull(password, "password");
        password = submittedPassword.clone();
        Arrays.fill(submittedPassword, '\0');
    }

    /** Returns a defensive copy of the password. */
    @Override public char[] password() { return password.clone(); }

    /** Clears the password retained by this request; repeated calls are harmless. */
    public void clearPassword() { Arrays.fill(password, '\0'); }
}
