package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.CartItem;
import edu.seu.vcampus.server.shop.domain.ProductSku;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistent buyer cart with per-user serialization. */
public final class CartService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;

    public CartService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CartView getCart(String sessionToken) {
        ShopUser actor = requireBuyer(sessionToken);
        return transactions.inTransaction(connection -> repository.loadCart(connection, actor.userId()));
    }

    public CartView addToCart(String sessionToken, AddCartItemCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.quantity());
        SellerApplicationService.requireId(command.skuId(), "skuId");
        ShopUser actor = requireBuyer(sessionToken);
        return locks.withLocks(List.of(SellerApplicationService.key("CART", actor.userId())), () ->
                transactions.inTransaction(connection -> {
                    ProductSku sku = repository.findSellableSku(connection, command.skuId())
                            .orElseThrow(() -> SellerApplicationService.error(
                                    ShopErrorCode.SHOP_SKU_UNAVAILABLE, "SKU is unavailable"));
                    requireDifferentOwner(connection, actor.userId(), sku.skuId());
                    String cartId = repository.findCartIdByUser(connection, actor.userId())
                            .orElseGet(() -> repositoryInsertCart(connection, actor.userId()));
                    var existing = repository.findCartItemBySku(connection, cartId, sku.skuId());
                    long quantity = command.quantity() + existing.map(CartItem::quantity).orElse(0L);
                    requireAvailable(sku, quantity);
                    if (existing.isPresent()) {
                        CartItem item = existing.get();
                        repository.updateCartItemQuantity(connection, item.cartItemId(), quantity,
                                clock.instant(), item.rowVersion());
                    } else {
                        var now = clock.instant();
                        repository.insertCartItem(connection, new CartItem(UUID.randomUUID().toString(),
                                cartId, sku.skuId(), quantity, 0, now, now));
                    }
                    return repository.loadCart(connection, actor.userId());
                }));
    }

    public CartView updateCartItem(String sessionToken, UpdateCartItemCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.quantity());
        SellerApplicationService.requireId(command.cartItemId(), "cartItemId");
        ShopUser actor = requireBuyer(sessionToken);
        return locks.withLocks(List.of(SellerApplicationService.key("CART", actor.userId())), () ->
                transactions.inTransaction(connection -> {
                    String cartId = repository.findCartIdByUser(connection, actor.userId())
                            .orElseThrow(() -> new SecurityException("Cart is not owned by user"));
                    CartItem item = repository.findCartItemById(connection, command.cartItemId())
                            .orElseThrow(() -> new SecurityException("Cart item is not owned by user"));
                    if (!item.cartId().equals(cartId)) {
                        throw new SecurityException("Cart item is not owned by user");
                    }
                    ProductSku sku = repository.findSellableSku(connection, item.skuId())
                            .orElseThrow(() -> SellerApplicationService.error(
                                    ShopErrorCode.SHOP_SKU_UNAVAILABLE, "SKU is unavailable"));
                    requireDifferentOwner(connection, actor.userId(), sku.skuId());
                    requireAvailable(sku, command.quantity());
                    repository.updateCartItemQuantity(connection, item.cartItemId(), command.quantity(),
                            clock.instant(), command.expectedVersion());
                    return repository.loadCart(connection, actor.userId());
                }));
    }

    public CartView removeCartItem(String sessionToken, String cartItemId) {
        SellerApplicationService.requireId(cartItemId, "cartItemId");
        ShopUser actor = requireBuyer(sessionToken);
        return locks.withLocks(List.of(SellerApplicationService.key("CART", actor.userId())), () ->
                transactions.inTransaction(connection -> {
                    String cartId = repository.findCartIdByUser(connection, actor.userId())
                            .orElseThrow(() -> new SecurityException("Cart is not owned by user"));
                    CartItem item = repository.findCartItemById(connection, cartItemId)
                            .orElseThrow(() -> new SecurityException("Cart item is not owned by user"));
                    if (!item.cartId().equals(cartId)) {
                        throw new SecurityException("Cart item is not owned by user");
                    }
                    repository.deleteCartItem(connection, cartItemId, cartId);
                    return repository.loadCart(connection, actor.userId());
                }));
    }

    private String repositoryInsertCart(java.sql.Connection connection, String userId) {
        try {
            return repository.insertCart(connection, UUID.randomUUID().toString(),
                    userId, clock.instant());
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Unable to create cart", error);
        }
    }

    private ShopUser requireActiveUser(String sessionToken) {
        ShopUser actor = users.requireUser(sessionToken);
        if (!actor.active()) {
            throw new SecurityException("Active account required");
        }
        return actor;
    }

    private ShopUser requireBuyer(String sessionToken) {
        return BuyerGuard.requireBuyer(requireActiveUser(sessionToken));
    }

    private void requireDifferentOwner(java.sql.Connection connection, String buyerId, String skuId)
            throws Exception {
        String ownerId = repository.findShopOwnerBySku(connection, skuId)
                .orElseThrow(() -> SellerApplicationService.error(
                        ShopErrorCode.SHOP_SKU_UNAVAILABLE, "SKU is unavailable"));
        BuyerGuard.requireDifferentOwner(buyerId, ownerId);
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static void requireAvailable(ProductSku sku, long quantity) {
        if (quantity > sku.availableQuantity()) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                    "Requested quantity exceeds available stock");
        }
    }
}
