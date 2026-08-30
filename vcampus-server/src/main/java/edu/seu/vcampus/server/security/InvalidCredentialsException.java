package edu.seu.vcampus.server.security;

/** Indicates an unknown login identifier or an incorrect password. */
public final class InvalidCredentialsException extends RuntimeException {
    /** Creates the stable safe authentication error. */
    public InvalidCredentialsException() { super("AUTH_INVALID_CREDENTIALS"); }
}
