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

/** Provides focused helper operations for {@link CheckoutService}. */
abstract class CheckoutServiceHelpers1 extends CheckoutServiceHelpers2 {
    protected CheckoutServiceHelpers1(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }

    protected static final Duration RESERVATION_DURATION = Duration.ofMinutes(15);

    protected CheckoutResult createCheckout(Connection connection, String buyerId,
            Map<String, CheckoutItem> requested, boolean acceptLatestPrice) throws Exception {
        List<CheckoutLine> lines = loadLines(connection, buyerId, requested.keySet());
        if (lines.size() != requested.size()) {
            throw emptyCart("A selected cart item no longer belongs to the buyer");
        }
        Map<String, Long> quantitiesBySku = new HashMap<>();
        for (CheckoutLine line : lines) {
            BuyerGuard.requireDifferentOwner(buyerId, line.ownerUserId());
            requireSellable(line);
            BigDecimal displayed = requested.get(line.cartItemId()).displayedUnitPrice();
            if (!acceptLatestPrice && displayed.compareTo(line.unitPrice()) != 0) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_PRICE_CHANGED,
                        "A selected SKU price has changed");
            }
            quantitiesBySku.merge(line.skuId(), line.quantity(), Long::sum);
        }
        for (CheckoutLine line : uniqueSkuLines(lines)) {
            long quantity = quantitiesBySku.get(line.skuId());
            if (quantity > line.stockQuantity() - line.reservedQuantity()) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_INSUFFICIENT_STOCK,
                        "A selected SKU has insufficient stock");
            }
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(RESERVATION_DURATION);
        String orderGroupId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();
        String paymentNumber = number("P");
        Map<String, List<CheckoutLine>> byShop = lines.stream()
                .collect(Collectors.groupingBy(CheckoutLine::shopId,
                        LinkedHashMap::new, Collectors.toList()));
        BigDecimal total = lines.stream().map(CheckoutLine::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        insertOrderGroup(connection, orderGroupId, buyerId, total, now);
        List<OrderSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<CheckoutLine>> entry : byShop.entrySet()) {
            String orderId = UUID.randomUUID().toString();
            String orderNumber = number("O");
            List<CheckoutLine> shopLines = entry.getValue();
            BigDecimal orderAmount = shopLines.stream().map(CheckoutLine::lineAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            insertOrder(connection, orderId, orderGroupId, entry.getKey(), orderNumber,
                    orderAmount, now);
            for (CheckoutLine line : shopLines) {
                insertOrderItem(connection, orderId, line);
            }
            summaries.add(new OrderSummary(orderId, orderGroupId, orderNumber,
                    entry.getKey(), shopLines.getFirst().shopName(), orderAmount,
                    OrderStatus.PENDING_PAYMENT, now));
        }
        insertPayment(connection, paymentId, orderGroupId, paymentNumber, total);
        for (CheckoutLine line : uniqueSkuLines(lines)) {
            long quantity = quantitiesBySku.get(line.skuId());
            reserveSku(connection, line.skuId(), quantity);
            insertReservation(connection, paymentId, line.skuId(), quantity, expiresAt);
        }
        deleteCartItems(connection, requested.keySet(), buyerId);
        return new CheckoutResult(orderGroupId, paymentId, paymentNumber,
                total, expiresAt, summaries);
    }

    protected List<String> resolveOwnedSkuIds(Connection connection, String buyerId,
            Set<String> cartItemIds) throws Exception {
        String cartId = repository.findCartIdByUser(connection, buyerId)
                .orElseThrow(() -> emptyCart("No active cart"));
        List<String> skuIds = new ArrayList<>();
        for (String cartItemId : cartItemIds) {
            CartItem item = repository.findCartItemById(connection, cartItemId)
                    .orElseThrow(() -> emptyCart("Selected cart item does not exist"));
            if (!cartId.equals(item.cartId())) {
                throw emptyCart("Selected cart item is not owned by buyer");
            }
            skuIds.add(item.skuId());
        }
        return skuIds;
    }
}
