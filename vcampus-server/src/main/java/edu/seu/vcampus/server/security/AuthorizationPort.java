package edu.seu.vcampus.server.security;

/** Verifies session validity and permissions at protected-command boundaries. */
public interface AuthorizationPort {
    /** Returns the authenticated identity for a valid session. */
    UserIdentity requireSession(String sessionToken);

    /** Requires a non-restricted session that holds the requested permission. */
    void requirePermission(String sessionToken, String permissionCode);
}
