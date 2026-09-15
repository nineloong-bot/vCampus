package edu.seu.vcampus.server.shop.testutil;

import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provides fake shop user port behavior. */
public final class FakeShopUserPort implements ShopUserPort {
    private final Map<String, ShopUser> sessions = new ConcurrentHashMap<>();

    /**
     * Performs the add operation.
     * @param token the token
     * @param userId the user identifier
     * @param kind the kind
     * @param active the active
     */
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
