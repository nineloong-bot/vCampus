package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/** Password-change request that copies and clears caller-owned password arrays. */
public record ChangePasswordCommand(char[] oldPassword, char[] newPassword) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Takes defensive copies and clears the arrays supplied by the caller. */
    public ChangePasswordCommand {
        oldPassword = copyAndClear(Objects.requireNonNull(oldPassword, "oldPassword"));
        newPassword = copyAndClear(Objects.requireNonNull(newPassword, "newPassword"));
    }

    /** Returns a defensive copy of the previous password. */
    @Override public char[] oldPassword() { return oldPassword.clone(); }

    /** Returns a defensive copy of the replacement password. */
    @Override public char[] newPassword() { return newPassword.clone(); }

    /** Clears both passwords retained by this request; repeated calls are harmless. */
    public void clearPasswords() {
        Arrays.fill(oldPassword, '\0');
        Arrays.fill(newPassword, '\0');
    }

    private static char[] copyAndClear(char[] value) {
        char[] copy = value.clone();
        Arrays.fill(value, '\0');
        return copy;
    }
}
