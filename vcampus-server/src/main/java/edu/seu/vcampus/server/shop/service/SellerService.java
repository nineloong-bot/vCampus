package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopView;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.util.Objects;

/** Seller capability boundary derived from owned approved shop state. */
public final class SellerService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;

    public SellerService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public ShopView getOwnedShop(String sessionToken) {
        ShopUser actor = users.requireUser(sessionToken);
        return transactions.inTransaction(connection -> toView(repository
                .findShopByOwner(connection, actor.userId())
                .orElseThrow(() -> SellerApplicationService.error(
                        ShopErrorCode.SHOP_SELLER_NOT_APPROVED,
                        "User does not own an approved shop"))));
    }

    public ShopView requireOwnedActiveShop(String sessionToken) {
        ShopView shop = getOwnedShop(sessionToken);
        if (shop.status() == ShopStatus.SUSPENDED) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SUSPENDED,
                    "Owned shop is suspended");
        }
        return shop;
    }

    private static ShopView toView(Shop shop) {
        return new ShopView(shop.shopId(), shop.ownerUserId(), shop.shopName(),
                shop.description(), shop.category(), shop.contact(), shop.status(),
                shop.suspensionReason(), shop.suspendedByUserId(), shop.suspendedAt(),
                shop.rowVersion());
    }
}
