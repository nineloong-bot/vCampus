package edu.seu.vcampus.server.security;

/** Indicates that a session token is unknown, expired, or revoked. */
public final class SessionExpiredException extends RuntimeException {
    /** Creates the stable expired-session error. */
    public SessionExpiredException() { super("AUTH_SESSION_EXPIRED"); }
}
