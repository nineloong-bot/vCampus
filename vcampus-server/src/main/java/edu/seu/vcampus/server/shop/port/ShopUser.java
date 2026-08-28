package edu.seu.vcampus.server.shop.port;

import java.util.Objects;

/** Minimal identity projection consumed by the shop module. */
public record ShopUser(String userId, ShopUserKind kind, boolean active) {
    public ShopUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(kind, "kind");
    }

    public boolean sellerEligible() {
        return active && (kind == ShopUserKind.STUDENT || kind == ShopUserKind.TEACHER);
    }
}
