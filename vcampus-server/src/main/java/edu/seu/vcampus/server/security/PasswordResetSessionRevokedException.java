package edu.seu.vcampus.server.security;

/** Indicates that an administrator password initialization revoked this session. */
public final class PasswordResetSessionRevokedException extends SessionExpiredException {
    /** Creates the stable password-reset session-revocation error. */
    public PasswordResetSessionRevokedException() {
        super("AUTH_SESSION_REVOKED_PASSWORD_RESET");
    }
}
