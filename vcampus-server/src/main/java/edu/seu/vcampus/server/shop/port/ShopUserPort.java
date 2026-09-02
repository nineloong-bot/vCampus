package edu.seu.vcampus.server.shop.port;

/** Adapter boundary for Foundation session and authorization services. */
public interface ShopUserPort {
    ShopUser requireUser(String sessionToken);

    default ShopUser requireAdministrator(String sessionToken) {
        ShopUser user = requireUser(sessionToken);
        if (!user.active() || user.kind() != ShopUserKind.ADMINISTRATOR) {
            throw new ShopAccessException("AUTH_FORBIDDEN");
        }
        return user;
    }
}
