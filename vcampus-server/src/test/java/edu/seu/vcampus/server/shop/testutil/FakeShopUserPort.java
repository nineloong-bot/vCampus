package edu.seu.vcampus.server.shop.testutil;

import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeShopUserPort implements ShopUserPort {
    private final Map<String, ShopUser> sessions = new ConcurrentHashMap<>();
    private volatile ShopUser administrator = new ShopUser("admin-1", ShopUserKind.ADMINISTRATOR, true);

    public void add(String token, String userId, ShopUserKind kind, boolean active) {
        sessions.put(token, new ShopUser(userId, kind, active));
    }

    public void administrator(String userId) {
        administrator = new ShopUser(userId, ShopUserKind.ADMINISTRATOR, true);
    }

    @Override
    public ShopUser requireUser(String sessionToken) {
        ShopUser user = sessions.get(sessionToken);
        if (user == null) {
            throw new SecurityException("Unknown session");
        }
        return user;
    }

    @Override
    public ShopUser requireAdministrator() {
        return administrator;
    }
}
