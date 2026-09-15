package edu.seu.vcampus.server.library.handler;

/** Authorization seam implemented by the user module when it is integrated. */
public interface LibraryAccessPort {
    /**
     * Performs the require session operation.
     * @param sessionToken the session token
     */
    void requireSession(String sessionToken);

    /**
     * Performs the require permission operation.
     * @param sessionToken the session token
     * @param permissionCode the permission code
     */
    void requirePermission(String sessionToken, String permissionCode);
}
