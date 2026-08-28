package edu.seu.vcampus.server.library.handler;

/** Authorization seam implemented by the user module when it is integrated. */
public interface LibraryAccessPort {
    void requireSession(String sessionToken);

    void requirePermission(String sessionToken, String permissionCode);
}
