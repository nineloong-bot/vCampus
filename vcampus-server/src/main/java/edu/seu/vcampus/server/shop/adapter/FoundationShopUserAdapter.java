package edu.seu.vcampus.server.shop.adapter;

import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.util.Objects;

/** Projects authenticated Foundation sessions into shop users. */
public final class FoundationShopUserAdapter implements ShopUserPort {
    private final AuthorizationPort authorization;

    public FoundationShopUserAdapter(AuthorizationPort authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public ShopUser requireUser(String sessionToken) {
        try {
            UserIdentity identity = authorization.requireSession(sessionToken);
            if (identity.restricted()) {
                throw new ShopAccessException("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
            }
            ShopUserKind kind = switch (identity.role()) {
                case STUDENT -> ShopUserKind.STUDENT;
                case TEACHER -> ShopUserKind.TEACHER;
                case ADMIN -> ShopUserKind.ADMINISTRATOR;
            };
            return new ShopUser(identity.userId(), kind, true);
        } catch (SessionExpiredException error) {
            throw new ShopAccessException("AUTH_SESSION_EXPIRED");
        }
    }

    @Override
    public ShopUser requireAdministrator() {
        throw new ShopAccessException("AUTH_FORBIDDEN");
    }
}
