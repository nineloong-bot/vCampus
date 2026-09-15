package edu.seu.vcampus.server.shop.port;

import java.util.Objects;

/** Minimal identity projection consumed by the shop module. */
public record ShopUser(String userId, ShopUserKind kind, boolean active) {
    /**
     * Creates a shop user with its required collaborators.
     * @param userId the user identifier
     * @param kind the kind
     * @param active the active
     */
    public ShopUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * Performs the seller eligible operation.
     * @return the operation result
     */
    public boolean sellerEligible() {
        return active && (kind == ShopUserKind.STUDENT || kind == ShopUserKind.TEACHER);
    }
}
