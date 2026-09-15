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

/** Shared query helpers for the Access shop repository. */
abstract class AccessShopRepositorySegment0 extends AccessShopRepositoryMappings {
    private static final int MAX_CATALOG_PAGE_SIZE = 100;
    private static final long MAX_CATALOG_PAGE_OFFSET = 10_000_000L;

    protected Optional<SellerApplication> findApplication(
            Connection connection, String column, String value) throws Exception {
        String sql = "SELECT * FROM tblSellerApplication WHERE " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapApplication(result)) : Optional.empty();
            }
        }
    }

    protected static long countProducts(Connection connection, String shopId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblProduct WHERE shopId = ?")) {
            statement.setString(1, shopId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    protected Optional<Shop> findShop(Connection connection, String column, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblShop WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapShop(result)) : Optional.empty();
            }
        }
    }

    protected static long validateCatalogPage(int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_CATALOG_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_CATALOG_PAGE_SIZE);
        }
        long offset = (long) pageNumber * (long) pageSize;
        if (offset > MAX_CATALOG_PAGE_OFFSET) {
            throw new IllegalArgumentException(
                    "page offset must not exceed " + MAX_CATALOG_PAGE_OFFSET);
        }
        return offset;
    }

    protected static List<SellerOrderItemView> findSellerOrderItems(Connection connection,
            String orderId) throws Exception {
        String sql = "SELECT k.productId, i.productNameSnapshot, i.skuId, i.skuNameSnapshot, "
                + "i.quantity, i.unitPrice, i.lineAmount FROM tblOrderItem i LEFT JOIN "
                + "tblProductSku k ON i.skuId = k.skuId WHERE i.orderId = ? ORDER BY i.orderItemId";
        List<SellerOrderItemView> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) items.add(new SellerOrderItemView(
                        result.getString("productId"), result.getString("productNameSnapshot"),
                        result.getString("skuId"), result.getString("skuNameSnapshot"),
                        Math.toIntExact(result.getLong("quantity")), result.getBigDecimal("unitPrice"),
                        result.getBigDecimal("lineAmount")));
            }
        }
        return List.copyOf(items);
    }

    protected Optional<CartItem> findCartItem(Connection connection, String predicate,
            String... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblCartItem WHERE " + predicate)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapCartItem(result)) : Optional.empty();
            }
        }
    }

    protected static List<PaidOrderItemView> findPaidOrderItems(Connection connection,
            String orderId) throws Exception {
        String sql = "SELECT k.productId, i.productNameSnapshot, i.skuId, "
                + "i.skuNameSnapshot, i.quantity, i.unitPrice, i.lineAmount "
                + "FROM tblOrderItem i LEFT JOIN tblProductSku k ON i.skuId = k.skuId "
                + "WHERE i.orderId = ? ORDER BY i.orderItemId";
        List<PaidOrderItemView> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String skuId = result.getString("skuId");
                    String productId = result.getString("productId");
                    if (productId == null) {
                        throw new IllegalStateException(
                                "Paid order item references missing SKU: " + skuId);
                    }
                    items.add(new PaidOrderItemView(productId,
                            result.getString("productNameSnapshot"), skuId,
                            result.getString("skuNameSnapshot"),
                            Math.toIntExact(result.getLong("quantity")),
                            result.getBigDecimal("unitPrice"), result.getBigDecimal("lineAmount")));
                }
            }
        }
        return List.copyOf(items);
    }

    /** Holds the order fields shared while mapping paid order items. */
    protected record PaidOrderHeader(String orderId, String orderNumber, String shopId,
            String shopName, java.math.BigDecimal totalAmount, Instant paidAt) { }

    protected static void touchCart(Connection connection, String cartId, Instant updatedAt)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblCart SET updatedAt = ? WHERE cartId = ?")) {
            setInstant(statement, 1, updatedAt);
            statement.setString(2, cartId);
            statement.executeUpdate();
        }
    }
}
