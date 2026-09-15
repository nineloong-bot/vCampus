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
abstract class AccessShopRepositorySegment3 extends AccessShopRepositorySegment2 {

    @Override
    public Optional<Product> findProductById(Connection connection, String productId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblProduct WHERE productId = ?")) {
            statement.setString(1, productId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProduct(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Product> findProductByNormalizedName(Connection connection, String shopId,
            String normalizedName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblProduct WHERE shopId = ? AND normalizedProductName = ?")) {
            statement.setString(1, shopId);
            statement.setString(2, normalizedName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProduct(result)) : Optional.empty();
            }
        }
    }

    @Override
    public List<ProductSku> findSkusByProduct(Connection connection, String productId) throws Exception {
        List<ProductSku> skus = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblProductSku WHERE productId = ? ORDER BY skuId")) {
            statement.setString(1, productId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    skus.add(mapSku(result));
                }
            }
        }
        return skus;
    }

    @Override
    public Product insertProduct(Connection connection, Product product) throws Exception {
        String sql = "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, "
                + "category, description, coverImageUrl, productStatus, salesCount, rowVersion, "
                + "createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.productId());
            statement.setString(2, product.shopId());
            statement.setString(3, product.productName());
            statement.setString(4, product.normalizedProductName());
            statement.setString(5, product.category());
            statement.setString(6, product.description());
            statement.setString(7, product.coverImageUrl());
            statement.setString(8, product.status().name());
            statement.setLong(9, product.salesCount());
            statement.setLong(10, product.rowVersion());
            setInstant(statement, 11, product.createdAt());
            setInstant(statement, 12, product.updatedAt());
            statement.executeUpdate();
        }
        return product;
    }

    @Override
    public Product updateProduct(Connection connection, Product product,
            long expectedVersion) throws Exception {
        String sql = "UPDATE tblProduct SET productName = ?, normalizedProductName = ?, category = ?, "
                + "description = ?, coverImageUrl = ?, updatedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE productId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.productName());
            statement.setString(2, product.normalizedProductName());
            statement.setString(3, product.category());
            statement.setString(4, product.description());
            statement.setString(5, product.coverImageUrl());
            setInstant(statement, 6, product.updatedAt());
            statement.setString(7, product.productId());
            statement.setLong(8, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ShopException(ShopErrorCode.SHOP_PRODUCT_INACTIVE, "Stale product version");
            }
        }
        return findProductById(connection, product.productId()).orElseThrow();
    }

    @Override
    public Product updateProductStatus(Connection connection, String productId,
            ProductStatus status, Instant updatedAt, long expectedVersion) throws Exception {
        String sql = "UPDATE tblProduct SET productStatus = ?, updatedAt = ?, "
                + "rowVersion = rowVersion + 1 WHERE productId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            setInstant(statement, 2, updatedAt);
            statement.setString(3, productId);
            statement.setLong(4, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ShopException(ShopErrorCode.SHOP_PRODUCT_INACTIVE, "Stale product version");
            }
        }
        return findProductById(connection, productId).orElseThrow();
    }

    @Override
    public ProductSku insertSku(Connection connection, ProductSku sku) throws Exception {
        String sql = "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, "
                + "stockQuantity, reservedQuantity, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSku(statement, sku);
            statement.executeUpdate();
        }
        return sku;
    }
}
