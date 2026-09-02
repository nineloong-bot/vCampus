package edu.seu.vcampus.server.shop.testutil;

import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeShopUserPort implements ShopUserPort {
    private final Map<String, ShopUser> sessions = new ConcurrentHashMap<>();

    public void add(String token, String userId, ShopUserKind kind, boolean active) {
        sessions.put(token, new ShopUser(userId, kind, active));
    }

    @Override
    public ShopUser requireUser(String sessionToken) {
        ShopUser user = sessions.get(sessionToken);
        if (user == null) {
            throw new SecurityException("Unknown session");
        }
        return user;
    }
}
