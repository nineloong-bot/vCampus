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
abstract class CheckoutServiceHelpers2 extends CheckoutServiceHelpers3 {
    protected CheckoutServiceHelpers2(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    protected static List<CheckoutLine> loadLines(Connection connection, String buyerId,
            Set<String> cartItemIds) throws Exception {
        String sql = "SELECT ci.cartItemId, ci.skuId, ci.quantity, s.skuName, s.unitPrice, "
                + "s.stockQuantity, s.reservedQuantity, s.isActive, p.productId, "
                + "p.productName, p.productStatus, sh.shopId, sh.ownerUserId, sh.shopName, sh.shopStatus "
                + "FROM (((tblCartItem ci INNER JOIN tblCart c ON ci.cartId = c.cartId) "
                + "INNER JOIN tblProductSku s ON ci.skuId = s.skuId) "
                + "INNER JOIN tblProduct p ON s.productId = p.productId) "
                + "INNER JOIN tblShop sh ON p.shopId = sh.shopId "
                + "WHERE ci.cartItemId = ? AND c.userId = ?";
        List<CheckoutLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String cartItemId : cartItemIds) {
                statement.setString(1, cartItemId);
                statement.setString(2, buyerId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        lines.add(new CheckoutLine(result.getString("cartItemId"),
                                result.getString("skuId"), result.getString("productId"),
                                result.getString("shopId"), result.getString("productName"),
                                result.getString("ownerUserId"), result.getString("skuName"), result.getString("shopName"),
                                result.getBigDecimal("unitPrice"), result.getLong("quantity"),
                                result.getLong("stockQuantity"),
                                result.getLong("reservedQuantity"), result.getBoolean("isActive"),
                                result.getString("productStatus"),
                                result.getString("shopStatus")));
                    }
                }
            }
        }
        return lines;
    }

    protected static Map<String, CheckoutItem> validate(CheckoutCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.items().isEmpty()) {
            throw emptyCart("At least one cart item must be selected");
        }
        Map<String, CheckoutItem> requested = new LinkedHashMap<>();
        for (CheckoutItem item : command.items()) {
            Objects.requireNonNull(item, "checkout item");
            SellerApplicationService.requireId(item.cartItemId(), "cartItemId");
            if (item.displayedUnitPrice() == null || item.displayedUnitPrice().signum() < 0) {
                throw new IllegalArgumentException("displayedUnitPrice must be non-negative");
            }
            if (requested.putIfAbsent(item.cartItemId(), item) != null) {
                throw new IllegalArgumentException("Duplicate cartItemId");
            }
        }
        return requested;
    }

    protected ShopUser requireActiveUser(String sessionToken) {
        ShopUser user = users.requireUser(sessionToken);
        if (!user.active()) {
            throw new SecurityException("Active account required");
        }
        return user;
    }

    protected ShopUser requireBuyer(String sessionToken) {
        return BuyerGuard.requireBuyer(requireActiveUser(sessionToken));
    }

    protected static void requireSellable(CheckoutLine line) {
        if (!"ACTIVE".equals(line.shopStatus())) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SUSPENDED,
                    "Shop is suspended");
        }
        if (!"ACTIVE".equals(line.productStatus())) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_PRODUCT_INACTIVE,
                    "Product is inactive");
        }
        if (!line.skuActive()) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                    "SKU is unavailable");
        }
    }
}
