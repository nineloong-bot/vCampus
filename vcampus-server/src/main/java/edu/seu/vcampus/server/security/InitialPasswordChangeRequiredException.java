package edu.seu.vcampus.server.security;

/** Indicates that a restricted initial-password session attempted a business command. */
public final class InitialPasswordChangeRequiredException extends RuntimeException {
    /** Creates the stable restricted-session error. */
    public InitialPasswordChangeRequiredException() { super("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED"); }
}
