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
abstract class AccessShopRepositorySegment2 extends AccessShopRepositorySegment1 {

    @Override
    public PageResult<ShopAdminSummary> searchShops(Connection connection,
            ShopAdminQuery query) throws Exception {
        if (query.pageNumber() < 0 || query.pageSize() <= 0) {
            throw new IllegalArgumentException("Invalid page");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM tblShop WHERE 1 = 1");
        List<String> values = new ArrayList<>();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" AND shopName LIKE ?");
            values.add("%" + query.keyword().strip() + "%");
        }
        if (query.status() != null) {
            sql.append(" AND shopStatus = ?");
            values.add(query.status().name());
        }
        sql.append(" ORDER BY shopName, shopId");
        List<ShopAdminSummary> all = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String shopId = result.getString("shopId");
                    all.add(new ShopAdminSummary(shopId, result.getString("ownerUserId"),
                            result.getString("shopName"), result.getString("category"),
                            ShopStatus.valueOf(result.getString("shopStatus")),
                            countProducts(connection, shopId), result.getLong("rowVersion")));
                }
            }
        }
        int from = Math.min(query.pageNumber() * query.pageSize(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return new PageResult<>(all.subList(from, to), query.pageNumber(), query.pageSize(), all.size());
    }

    @Override
    public Shop insertShop(Connection connection, Shop shop) throws Exception {
        String sql = "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                + "normalizedShopName, contact, shopStatus, suspensionReason, suspendedByUserId, suspendedAt, "
                + "rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, shop.shopId());
            statement.setString(2, shop.ownerUserId());
            statement.setString(3, shop.shopName());
            statement.setString(4, shop.description());
            statement.setString(5, shop.category());
            statement.setString(6, shop.normalizedShopName());
            statement.setString(7, shop.contact());
            statement.setString(8, shop.status().name());
            statement.setString(9, shop.suspensionReason());
            statement.setString(10, shop.suspendedByUserId());
            setInstant(statement, 11, shop.suspendedAt());
            statement.setLong(12, shop.rowVersion());
            setInstant(statement, 13, shop.createdAt());
            setInstant(statement, 14, shop.updatedAt());
            statement.executeUpdate();
            return shop;
        }
    }

    @Override
    public Shop updateShopStatus(Connection connection, String shopId,
            ShopStatus expectedStatus, ShopStatus targetStatus, String suspensionReason,
            String suspendedByUserId, Instant suspendedAt, Instant updatedAt,
            long expectedVersion) throws Exception {
        String sql = "UPDATE tblShop SET shopStatus = ?, suspensionReason = ?, suspendedByUserId = ?, "
                + "suspendedAt = ?, updatedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE shopId = ? AND shopStatus = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetStatus.name());
            statement.setString(2, suspensionReason);
            statement.setString(3, suspendedByUserId);
            setInstant(statement, 4, suspendedAt);
            setInstant(statement, 5, updatedAt);
            statement.setString(6, shopId);
            statement.setString(7, expectedStatus.name());
            statement.setLong(8, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ShopException(ShopErrorCode.SHOP_CONCURRENT_MODIFICATION,
                        "Shop status or version changed before this transition");
            }
        }
        return findShopById(connection, shopId).orElseThrow();
    }

    @Override
    public long countShopsByOwner(Connection connection, String ownerUserId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblShop WHERE ownerUserId = ?")) {
            statement.setString(1, ownerUserId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    @Override
    public Shop updateShopProfile(Connection connection, Shop shop, long expectedVersion) throws Exception {
        String sql = "UPDATE tblShop SET shopName = ?, normalizedShopName = ?, description = ?, category = ?, contact = ?, "
                + "updatedAt = ?, rowVersion = rowVersion + 1 WHERE shopId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, shop.shopName());
            statement.setString(2, shop.normalizedShopName());
            statement.setString(3, shop.description());
            statement.setString(4, shop.category());
            statement.setString(5, shop.contact());
            setInstant(statement, 6, shop.updatedAt());
            statement.setString(7, shop.shopId());
            statement.setLong(8, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ShopException(ShopErrorCode.SHOP_STATUS_INVALID, "Stale shop version");
            }
        }
        return findShopById(connection, shop.shopId()).orElseThrow();
    }
}
