package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.SellerOrderHistory;
import edu.seu.vcampus.common.shop.SellerOrderQuery;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.util.Objects;

/** Read-only order history scoped from the authenticated seller's owned shop. */
public final class SellerOrderService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;

    public SellerOrderService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public SellerOrderHistory getOwnedOrders(String sessionToken, SellerOrderQuery query) {
        Objects.requireNonNull(query, "query");
        var actor = users.requireUser(sessionToken);
        return transactions.inTransaction(connection -> {
            Shop shop = repository.findShopByOwner(connection, actor.userId())
                    .orElseThrow(() -> SellerApplicationService.error(
                            ShopErrorCode.SHOP_SELLER_NOT_APPROVED, "Approved shop required"));
            return new SellerOrderHistory(repository.findOrdersByShop(
                    connection, shop.shopId(), query));
        });
    }
}
