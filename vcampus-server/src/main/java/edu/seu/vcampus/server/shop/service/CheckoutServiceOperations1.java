package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.OrderStatus;
import edu.seu.vcampus.common.shop.OrderSummary;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.CartItem;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Implements focused public operations for {@link CheckoutService}. */
abstract class CheckoutServiceOperations1 extends CheckoutServiceHelpers1 {
    protected CheckoutServiceOperations1(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    /**
     * Performs the checkout operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    public CheckoutResult checkout(String sessionToken, CheckoutCommand command) {
        ShopUser buyer = requireBuyer(sessionToken);
        Map<String, CheckoutItem> requested = validate(command);
        List<String> skuIds = transactions.inTransaction(connection ->
                resolveOwnedSkuIds(connection, buyer.userId(), requested.keySet()));

        List<ResourceKey> keys = skuIds.stream().distinct().sorted()
                .map(id -> SellerApplicationService.key("SKU", id))
                .collect(Collectors.toCollection(ArrayList::new));
        keys.add(SellerApplicationService.key("CART", buyer.userId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection ->
                createCheckout(connection, buyer.userId(), requested,
                        command.acceptLatestPrice())));
    }
}
