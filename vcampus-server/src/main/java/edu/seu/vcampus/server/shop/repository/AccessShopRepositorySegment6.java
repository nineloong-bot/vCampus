package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationListMode;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.CartItemView;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.OrderStatus;
import edu.seu.vcampus.common.shop.PaidOrderItemView;
import edu.seu.vcampus.common.shop.PaidOrderView;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.ShopAdminSummary;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.shop.ProductManagementSummary;
import edu.seu.vcampus.common.shop.SellerOrderItemView;
import edu.seu.vcampus.common.shop.SellerOrderQuery;
import edu.seu.vcampus.common.shop.SellerOrderView;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.ProductSku;
import edu.seu.vcampus.server.shop.domain.CartItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implements one focused group of Access shop queries and writes. */
abstract class AccessShopRepositorySegment6 extends AccessShopRepositorySegment5 {

    @Override
    public Optional<CartItem> findCartItemById(Connection connection,
            String cartItemId) throws Exception {
        return findCartItem(connection, "cartItemId = ?", cartItemId);
    }

    @Override
    public CartItem insertCartItem(Connection connection, CartItem item) throws Exception {
        String sql = "INSERT INTO tblCartItem (cartItemId, cartId, skuId, quantity, rowVersion, "
                + "createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.cartItemId());
            statement.setString(2, item.cartId());
            statement.setString(3, item.skuId());
            statement.setLong(4, item.quantity());
            statement.setLong(5, item.rowVersion());
            setInstant(statement, 6, item.createdAt());
            setInstant(statement, 7, item.updatedAt());
            statement.executeUpdate();
        }
        touchCart(connection, item.cartId(), item.updatedAt());
        return item;
    }

    @Override
    public CartItem updateCartItemQuantity(Connection connection, String cartItemId,
            long quantity, Instant updatedAt, long expectedVersion) throws Exception {
        CartItem existing = findCartItemById(connection, cartItemId)
                .orElseThrow(() -> new IllegalStateException("Cart item does not exist"));
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblCartItem SET quantity = ?, updatedAt = ?, rowVersion = rowVersion + 1 "
                        + "WHERE cartItemId = ? AND rowVersion = ?")) {
            statement.setLong(1, quantity);
            setInstant(statement, 2, updatedAt);
            statement.setString(3, cartItemId);
            statement.setLong(4, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Stale cart item version");
            }
        }
        touchCart(connection, existing.cartId(), updatedAt);
        return findCartItemById(connection, cartItemId).orElseThrow();
    }

    @Override
    public void deleteCartItem(Connection connection, String cartItemId, String cartId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM tblCartItem WHERE cartItemId = ? AND cartId = ?")) {
            statement.setString(1, cartItemId);
            statement.setString(2, cartId);
            if (statement.executeUpdate() != 1) {
                throw new SecurityException("Cart item is not owned by user");
            }
        }
        touchCart(connection, cartId, Instant.now());
    }

    @Override
    public CartView loadCart(Connection connection, String userId) throws Exception {
        Optional<String> cartId = findCartIdByUser(connection, userId);
        if (cartId.isEmpty()) {
            return new CartView(null, List.of(), java.math.BigDecimal.ZERO.setScale(2));
        }
        String sql = "SELECT i.cartItemId, p.productId, p.productName, k.skuId, k.skuName, "
                + "s.shopId, s.shopName, k.unitPrice, i.quantity, i.rowVersion "
                + "FROM ((tblCartItem i INNER JOIN tblProductSku k ON i.skuId = k.skuId) "
                + "INNER JOIN tblProduct p ON k.productId = p.productId) "
                + "INNER JOIN tblShop s ON p.shopId = s.shopId WHERE i.cartId = ? "
                + "ORDER BY i.createdAt, i.cartItemId";
        List<CartItemView> items = new ArrayList<>();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cartId.get());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    java.math.BigDecimal price = result.getBigDecimal("unitPrice");
                    int quantity = Math.toIntExact(result.getLong("quantity"));
                    items.add(new CartItemView(result.getString("cartItemId"),
                            result.getString("productId"), result.getString("productName"),
                            result.getString("skuId"), result.getString("skuName"),
                            result.getString("shopId"), result.getString("shopName"),
                            price, quantity, result.getLong("rowVersion")));
                    total = total.add(price.multiply(java.math.BigDecimal.valueOf(quantity)));
                }
            }
        }
        return new CartView(cartId.get(), items, total);
    }

    @Override
    public Optional<String> findShopOwnerBySku(Connection connection, String skuId) throws Exception {
        String sql = "SELECT sh.ownerUserId FROM (tblProductSku k INNER JOIN tblProduct p "
                + "ON k.productId = p.productId) INNER JOIN tblShop sh ON p.shopId = sh.shopId "
                + "WHERE k.skuId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skuId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        }
    }
}
