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
abstract class AccessShopRepositorySegment5 extends AccessShopRepositorySegment4 {

    @Override
    public PageResult<ProductManagementSummary> searchManagedProducts(Connection connection,
            ProductManagementQuery query) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT p.productId, p.productName, p.productStatus, "
                + "COUNT(k.skuId) AS skuCount, MIN(k.unitPrice) AS minimumPrice, "
                + "SUM(k.stockQuantity) AS totalStock, SUM(k.reservedQuantity) AS reservedStock, "
                + "p.salesCount, p.rowVersion FROM tblProduct p INNER JOIN tblProductSku k "
                + "ON p.productId = k.productId WHERE p.shopId = ?");
        List<Object> values = new ArrayList<>();
        values.add(query.shopId());
        if (query.status() != null) {
            sql.append(" AND p.productStatus = ?");
            values.add(query.status().name());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" AND (p.productName LIKE ? OR EXISTS (SELECT 1 FROM tblProductSku matched "
                    + "WHERE matched.productId = p.productId AND matched.skuName LIKE ?))");
            String keyword = "%" + query.keyword().strip() + "%";
            values.add(keyword); values.add(keyword);
        }
        sql.append(" GROUP BY p.productId, p.productName, p.productStatus, p.salesCount, p.rowVersion "
                + "ORDER BY p.productName, p.productId");
        List<ProductManagementSummary> all = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index).toString());
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    all.add(new ProductManagementSummary(result.getString("productId"),
                            result.getString("productName"),
                            ProductStatus.valueOf(result.getString("productStatus")),
                            result.getLong("skuCount"), result.getBigDecimal("minimumPrice"),
                            result.getLong("totalStock"), result.getLong("reservedStock"),
                            result.getLong("salesCount"), result.getLong("rowVersion")));
                }
            }
        }
        long offset = validateCatalogPage(query.pageNumber(), query.pageSize());
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + query.pageSize(), all.size());
        return new PageResult<>(all.subList(from, to), query.pageNumber(), query.pageSize(), all.size());
    }

    @Override
    public List<SellerOrderView> findOrdersByShop(Connection connection, String shopId,
            SellerOrderQuery query) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT o.orderId, o.orderNumber, g.buyerUserId, "
                + "o.shopId, s.shopName, o.orderAmount, o.paidAt, o.orderStatus "
                + "FROM (tblOrder o INNER JOIN tblOrderGroup g ON o.orderGroupId = g.orderGroupId) "
                + "INNER JOIN tblShop s ON o.shopId = s.shopId WHERE o.shopId = ?");
        if (query.status() != null) sql.append(" AND o.orderStatus = ?");
        sql.append(" ORDER BY o.paidAt DESC, o.createdAt DESC, o.orderId");
        List<SellerOrderView> all = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, shopId);
            if (query.status() != null) statement.setString(2, query.status().name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String orderId = result.getString("orderId");
                    all.add(new SellerOrderView(orderId, result.getString("orderNumber"),
                            result.getString("buyerUserId"), result.getString("shopId"),
                            result.getString("shopName"), result.getBigDecimal("orderAmount"),
                            instant(result, "paidAt"), OrderStatus.valueOf(result.getString("orderStatus")),
                            findSellerOrderItems(connection, orderId)));
                }
            }
        }
        long offset = validateCatalogPage(query.pageNumber(), query.pageSize());
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + query.pageSize(), all.size());
        return List.copyOf(all.subList(from, to));
    }

    @Override
    public Optional<ProductSku> findSellableSku(Connection connection, String skuId) throws Exception {
        String sql = "SELECT k.* FROM (tblProductSku k INNER JOIN tblProduct p "
                + "ON k.productId = p.productId) INNER JOIN tblShop s ON p.shopId = s.shopId "
                + "WHERE k.skuId = ? AND k.isActive = TRUE AND p.productStatus = 'ACTIVE' "
                + "AND s.shopStatus = 'ACTIVE' AND k.stockQuantity - k.reservedQuantity > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skuId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapSku(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<String> findCartIdByUser(Connection connection, String userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT cartId FROM tblCart WHERE userId = ?")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        }
    }

    @Override
    public String insertCart(Connection connection, String cartId,
            String userId, Instant updatedAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCart (cartId, userId, updatedAt) VALUES (?, ?, ?)")) {
            statement.setString(1, cartId);
            statement.setString(2, userId);
            setInstant(statement, 3, updatedAt);
            statement.executeUpdate();
            return cartId;
        }
    }

    @Override
    public Optional<CartItem> findCartItemBySku(Connection connection,
            String cartId, String skuId) throws Exception {
        return findCartItem(connection, "cartId = ? AND skuId = ?", cartId, skuId);
    }
}
