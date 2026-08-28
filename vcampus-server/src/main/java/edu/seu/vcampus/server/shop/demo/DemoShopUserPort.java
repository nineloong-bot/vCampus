package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.util.Map;

/** Demo-only identity adapter; production user/session infrastructure remains external. */
final class DemoShopUserPort implements ShopUserPort {
    private static final ShopUser BUYER =
            new ShopUser("demo-buyer", ShopUserKind.STUDENT, true);
    private static final ShopUser ADMIN =
            new ShopUser("demo-admin", ShopUserKind.ADMINISTRATOR, true);
    private final Map<String, ShopUser> sessions = Map.of("demo-buyer-token", BUYER);

    @Override
    public ShopUser requireUser(String sessionToken) {
        ShopUser user = sessions.get(sessionToken);
        if (user == null) {
            throw new SecurityException("Unknown demo session");
        }
        return user;
    }

    @Override
    public ShopUser requireAdministrator() {
        return ADMIN;
    }
}
