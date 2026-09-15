package edu.seu.vcampus.server.shop.port;

/** Adapter boundary for Foundation session and authorization services. */
public interface ShopUserPort {
    /**
     * Performs the require user operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    ShopUser requireUser(String sessionToken);

    /**
     * Performs the require administrator operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    default ShopUser requireAdministrator(String sessionToken) {
        ShopUser user = requireUser(sessionToken);
        if (!user.active() || user.kind() != ShopUserKind.ADMINISTRATOR) {
            throw new ShopAccessException("AUTH_FORBIDDEN");
        }
        return user;
    }
}
