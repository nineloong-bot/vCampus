package edu.seu.vcampus.server.security;

/** Indicates that an administrator permission change revoked the current session. */
public final class PermissionChangeSessionRevokedException extends SessionExpiredException {
    /** Creates the stable permission-change revocation signal. */
    public PermissionChangeSessionRevokedException() {
        super("AUTH_SESSION_REVOKED_PERMISSION_CHANGE");
    }
}
