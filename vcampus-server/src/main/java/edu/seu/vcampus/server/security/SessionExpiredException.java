package edu.seu.vcampus.server.security;

/** Indicates that a session token is unknown, expired, or revoked. */
public class SessionExpiredException extends RuntimeException {
    /** Creates the stable expired-session error. */
    public SessionExpiredException() { super("AUTH_SESSION_EXPIRED"); }

    /** Creates a specialized stable session-expiry error for server-internal subclasses. */
    protected SessionExpiredException(String code) { super(code); }
}
