package edu.seu.vcampus.client.user.service;

/** Signals that an administrator permission change revoked the local session. */
public final class PermissionChangeSessionClientException extends RuntimeException {
    /** Creates a client-internal signal without sensitive session data. */
    public PermissionChangeSessionClientException() {
        super("Session revoked after permission change");
    }
}
