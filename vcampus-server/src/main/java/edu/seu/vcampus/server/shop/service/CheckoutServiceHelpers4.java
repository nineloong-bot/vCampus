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
abstract class CheckoutServiceHelpers4 extends CheckoutServiceSupport {
    protected CheckoutServiceHelpers4(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    protected static void reserveSku(Connection connection, String skuId, long quantity)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblProductSku SET reservedQuantity = reservedQuantity + ?, "
                        + "rowVersion = rowVersion + 1 WHERE skuId = ? "
                        + "AND stockQuantity - reservedQuantity >= ?")) {
            statement.setLong(1, quantity);
            statement.setString(2, skuId);
            statement.setLong(3, quantity);
            if (statement.executeUpdate() != 1) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_INSUFFICIENT_STOCK,
                        "A selected SKU has insufficient stock");
            }
        }
    }

    protected static void insertReservation(Connection connection, String paymentId,
            String skuId, long quantity, Instant expiresAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblInventoryReservation (reservationId, paymentId, skuId, "
                        + "quantity, reservationStatus, expiresAt) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, paymentId);
            statement.setString(3, skuId);
            statement.setLong(4, quantity);
            statement.setTimestamp(5, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    protected static void deleteCartItems(Connection connection, Set<String> itemIds,
            String buyerId) throws Exception {
        String sql = "DELETE FROM tblCartItem WHERE cartItemId = ? AND cartId IN "
                + "(SELECT cartId FROM tblCart WHERE userId = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String itemId : itemIds) {
                statement.setString(1, itemId);
                statement.setString(2, buyerId);
                if (statement.executeUpdate() != 1) {
                    throw emptyCart("Selected cart item changed during checkout");
                }
            }
        }
    }

    protected static String number(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 31);
    }

    protected static RuntimeException emptyCart(String message) {
        return SellerApplicationService.error(ShopErrorCode.SHOP_CART_EMPTY, message);
    }

    /** Provides checkout line behavior. */
    protected record CheckoutLine(String cartItemId, String skuId, String productId,
            String shopId, String productName, String ownerUserId, String skuName, String shopName,
            BigDecimal unitPrice, long quantity, long stockQuantity,
            long reservedQuantity, boolean skuActive, String productStatus,
            String shopStatus) {
        BigDecimal lineAmount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
