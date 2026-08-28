package edu.seu.vcampus.server.security;

/** Indicates that an authenticated user lacks a required permission. */
public final class ForbiddenException extends RuntimeException {
    /** Creates the stable forbidden error. */
    public ForbiddenException() { super("AUTH_FORBIDDEN"); }
}
