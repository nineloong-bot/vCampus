package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.server.library.handler.LibraryAccessPort;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.UserIdentity;

import java.util.Objects;

/** Connects library identity and access checks to the shared authorization service. */
public final class LibraryAuthorizationAdapter
        implements LibraryIdentityPort, LibraryAccessPort {
    private final AuthorizationPort authorization;

    public LibraryAuthorizationAdapter(AuthorizationPort authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override public BorrowerIdentity requireBorrower(String sessionToken) {
        UserIdentity identity = authorization.requireSession(sessionToken);
        return new BorrowerIdentity(identity.userId(), identity.role().name());
    }

    @Override public void requireSession(String sessionToken) {
        authorization.requireSession(sessionToken);
    }

    @Override public void requirePermission(String sessionToken, String permissionCode) {
        authorization.requirePermission(sessionToken, permissionCode);
    }
}
