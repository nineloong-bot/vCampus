package edu.seu.vcampus.client.user.service;

/** Identifies an explicit server-side session expiry without carrying sensitive data. */
public final class SessionExpiredClientException extends RuntimeException {
    /** Creates the client-internal session-expiry signal. */
    public SessionExpiredClientException() {
        super("Session expired");
    }
}
