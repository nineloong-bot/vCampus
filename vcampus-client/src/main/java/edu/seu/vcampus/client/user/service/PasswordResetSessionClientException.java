package edu.seu.vcampus.client.user.service;

/** Signals that administrator password initialization revoked the local session. */
public final class PasswordResetSessionClientException extends RuntimeException {
    /** Creates a client-internal signal without sensitive session data. */
    public PasswordResetSessionClientException() {
        super("Session revoked after password reset");
    }
}
