package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;

/** Authorization rules shared by operations that can mutate buyer state. */
public final class BuyerGuard {
    private BuyerGuard() { }

    /**
     * Performs the require buyer operation.
     * @param user the user
     * @return the operation result
     */
    public static ShopUser requireBuyer(ShopUser user) {
        if (!user.active() || user.kind() == ShopUserKind.ADMINISTRATOR) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_BUYER_FORBIDDEN,
                    "Buyer role required");
        }
        return user;
    }

    /**
     * Performs the require different owner operation.
     * @param buyerId the buyer identifier
     * @param ownerUserId the owner user identifier
     */
    public static void requireDifferentOwner(String buyerId, String ownerUserId) {
        if (buyerId.equals(ownerUserId)) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SELF_PURCHASE_FORBIDDEN,
                    "Shop owners cannot purchase their own products");
        }
    }
}
