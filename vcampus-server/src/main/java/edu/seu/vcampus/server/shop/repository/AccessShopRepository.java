package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
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

/** JDBC repository for the Access shop tables. */
public final class AccessShopRepository implements ShopRepository {
    private static final int MAX_CATALOG_PAGE_SIZE = 100;
    private static final long MAX_CATALOG_PAGE_OFFSET = 10_000_000L;
    @Override
    public Optional<SellerApplication> findApplicationById(
            Connection connection, String applicationId) throws Exception {
        return findApplication(connection, "applicationId", applicationId);
    }

    @Override
    public Optional<SellerApplication> findApplicationByApplicant(
            Connection connection, String applicantUserId) throws Exception {
        return findApplication(connection, "applicantUserId", applicantUserId);
    }

    private Optional<SellerApplication> findApplication(
            Connection connection, String column, String value) throws Exception {
        String sql = "SELECT * FROM tblSellerApplication WHERE " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapApplication(result)) : Optional.empty();
            }
        }
    }

    @Override
    public SellerApplication insertApplication(
            Connection connection, SellerApplication application) throws Exception {
        String sql = "INSERT INTO tblSellerApplication (applicationId, applicantUserId, shopName, "
                + "description, category, contact, applicationStatement, applicationStatus, reviewReason, reviewerUserId, "
                + "submittedAt, reviewedAt, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindApplication(statement, application);
            statement.executeUpdate();
            return application;
        }
    }

    @Override
    public SellerApplication updateApplication(Connection connection,
            SellerApplication application, long expectedVersion) throws Exception {
        String sql = "UPDATE tblSellerApplication SET shopName = ?, description = ?, category = ?, "
                + "contact = ?, applicationStatement = ?, applicationStatus = ?, reviewReason = ?, reviewerUserId = ?, "
                + "submittedAt = ?, reviewedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE applicationId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, application.shopName());
            statement.setString(2, application.description());
            statement.setString(3, application.category());
            statement.setString(4, application.contact());
            statement.setString(5, application.applicationStatement());
            statement.setString(6, application.status().name());
            statement.setString(7, application.reviewReason());
            statement.setString(8, application.reviewerUserId());
            setInstant(statement, 9, application.submittedAt());
            setInstant(statement, 10, application.reviewedAt());
            statement.setString(11, application.applicationId());
            statement.setLong(12, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw invalidApplicationState("Stale seller application version");
            }
        }
        return findApplicationById(connection, application.applicationId()).orElseThrow();
    }

    @Override
    public PageResult<SellerApplication> searchApplications(Connection connection,
            SellerApplicationQuery query) throws Exception {
        if (query.pageNumber() < 0 || query.pageSize() <= 0) {
            throw new IllegalArgumentException("Invalid page");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM tblSellerApplication WHERE 1 = 1");
        List<String> values = new ArrayList<>();
        if (query.applicantUserId() != null && !query.applicantUserId().isBlank()) {
            sql.append(" AND applicantUserId = ?");
            values.add(query.applicantUserId());
        }
        if (query.status() != null) {
            sql.append(" AND applicationStatus = ?");
            values.add(query.status().name());
        }
        sql.append(" ORDER BY applicationId");
        List<SellerApplication> all = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    all.add(mapApplication(result));
                }
            }
        }
        int from = Math.min(query.pageNumber() * query.pageSize(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return new PageResult<>(all.subList(from, to), query.pageNumber(), query.pageSize(), all.size());
    }

    @Override
    public Optional<Shop> findShopById(Connection connection, String shopId) throws Exception {
        return findShop(connection, "shopId", shopId);
    }

    @Override
    public Optional<Shop> findShopByOwner(Connection connection, String ownerUserId) throws Exception {
        return findShop(connection, "ownerUserId", ownerUserId);
    }

    @Override
    public Optional<Shop> findShopByNormalizedName(Connection connection,
            String normalizedShopName) throws Exception {
        return findShop(connection, "normalizedShopName", normalizedShopName);
    }

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

    private static long countProducts(Connection connection, String shopId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblProduct WHERE shopId = ?")) {
            statement.setString(1, shopId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private Optional<Shop> findShop(Connection connection, String column, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblShop WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapShop(result)) : Optional.empty();
            }
        }
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

    @Override
    public ProductSku updateSku(Connection connection, ProductSku sku,
            long expectedVersion) throws Exception {
        String sql = "UPDATE tblProductSku SET skuName = ?, unitPrice = ?, stockQuantity = ?, "
                + "isActive = ?, rowVersion = rowVersion + 1 WHERE skuId = ? AND productId = ? "
                + "AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sku.skuName());
            statement.setBigDecimal(2, sku.unitPrice());
            statement.setLong(3, sku.stockQuantity());
            statement.setBoolean(4, sku.active());
            statement.setString(5, sku.skuId());
            statement.setString(6, sku.productId());
            statement.setLong(7, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ShopException(ShopErrorCode.SHOP_SKU_UNAVAILABLE, "Stale or foreign SKU");
            }
        }
        return findSkusByProduct(connection, sku.productId()).stream()
                .filter(candidate -> candidate.skuId().equals(sku.skuId())).findFirst().orElseThrow();
    }

    @Override
    public PageResult<ProductSummary> searchCatalog(Connection connection,
            ProductSearchQuery query, String shopId) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT p.productId, p.shopId, s.shopName, "
                + "p.productName, p.category, p.coverImageUrl, MIN(k.unitPrice) AS minimumPrice, "
                + "p.salesCount, p.createdAt FROM (tblProduct p INNER JOIN tblShop s "
                + "ON p.shopId = s.shopId) INNER JOIN tblProductSku k ON p.productId = k.productId "
                + "WHERE s.shopStatus = 'ACTIVE' AND p.productStatus = 'ACTIVE' "
                + "AND k.isActive = TRUE AND k.stockQuantity - k.reservedQuantity > 0");
        List<Object> values = new ArrayList<>();
        if (shopId != null) {
            sql.append(" AND p.shopId = ?");
            values.add(shopId);
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" AND (p.productName LIKE ? OR s.shopName LIKE ? OR p.category LIKE ? "
                    + "OR p.description LIKE ? OR EXISTS (SELECT 1 FROM tblProductSku matched "
                    + "WHERE matched.productId = p.productId AND matched.skuName LIKE ?))");
            String keyword = "%" + query.keyword().strip() + "%";
            for (int index = 0; index < 5; index++) {
                values.add(keyword);
            }
        }
        if (query.category() != null && !query.category().isBlank()) {
            sql.append(" AND p.category = ?");
            values.add(query.category().strip());
        }
        sql.append(" GROUP BY p.productId, p.shopId, s.shopName, p.productName, p.category, p.coverImageUrl, "
                + "p.salesCount, p.createdAt HAVING 1 = 1");
        if (query.minPrice() != null) {
            sql.append(" AND MIN(k.unitPrice) >= ?");
            values.add(query.minPrice());
        }
        if (query.maxPrice() != null) {
            sql.append(" AND MIN(k.unitPrice) <= ?");
            values.add(query.maxPrice());
        }
        ProductSortMode sort = query.sortMode() == null ? ProductSortMode.SALES_DESC : query.sortMode();
        sql.append(sort == ProductSortMode.PRICE_DESC
                ? " ORDER BY MIN(k.unitPrice) DESC, p.createdAt DESC, p.productId"
                : " ORDER BY p.salesCount DESC, p.createdAt DESC, p.productId");
        List<ProductSummary> all = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                Object value = values.get(index);
                if (value instanceof java.math.BigDecimal money) {
                    statement.setBigDecimal(index + 1, money);
                } else {
                    statement.setString(index + 1, value.toString());
                }
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    all.add(new ProductSummary(result.getString("productId"),
                            result.getString("shopId"), result.getString("shopName"),
                            result.getString("productName"), result.getString("category"),
                            result.getString("coverImageUrl"), result.getBigDecimal("minimumPrice"), result.getLong("salesCount"),
                            instant(result, "createdAt")));
                }
            }
        }
        long offset = validateCatalogPage(query.pageNumber(), query.pageSize());
        int from = (int) Math.min(offset, (long) all.size());
        int to = (int) Math.min(offset + (long) query.pageSize(), (long) all.size());
        return new PageResult<>(all.subList(from, to), query.pageNumber(), query.pageSize(), all.size());
    }

    private static long validateCatalogPage(int pageNumber, int pageSize) {
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

    private static List<SellerOrderItemView> findSellerOrderItems(Connection connection,
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

    @Override
    public Optional<CartItem> findCartItemById(Connection connection,
            String cartItemId) throws Exception {
        return findCartItem(connection, "cartItemId = ?", cartItemId);
    }

    private Optional<CartItem> findCartItem(Connection connection, String predicate,
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

    @Override
    public List<PaidOrderView> findPaidOrders(Connection connection,
            String buyerUserId) throws Exception {
        String sql = "SELECT o.orderId, o.orderNumber, o.shopId, s.shopName, "
                + "o.orderAmount, o.paidAt FROM (tblOrder o INNER JOIN tblOrderGroup g "
                + "ON o.orderGroupId = g.orderGroupId) INNER JOIN tblShop s "
                + "ON o.shopId = s.shopId WHERE g.buyerUserId = ? "
                + "AND g.groupStatus = 'PAID' AND o.orderStatus = 'PAID' "
                + "AND o.paidAt IS NOT NULL ORDER BY o.paidAt DESC, o.orderId";
        List<PaidOrderHeader> headers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, buyerUserId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    headers.add(new PaidOrderHeader(result.getString("orderId"),
                            result.getString("orderNumber"), result.getString("shopId"),
                            result.getString("shopName"), result.getBigDecimal("orderAmount"),
                            instant(result, "paidAt")));
                }
            }
        }
        List<PaidOrderView> orders = new ArrayList<>();
        for (PaidOrderHeader header : headers) {
            orders.add(new PaidOrderView(header.orderId(), header.orderNumber(),
                    header.shopId(), header.shopName(), header.totalAmount(), header.paidAt(),
                    OrderStatus.PAID, findPaidOrderItems(connection, header.orderId())));
        }
        return List.copyOf(orders);
    }

    private static List<PaidOrderItemView> findPaidOrderItems(Connection connection,
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

    private record PaidOrderHeader(String orderId, String orderNumber, String shopId,
            String shopName, java.math.BigDecimal totalAmount, Instant paidAt) { }

    private static void touchCart(Connection connection, String cartId, Instant updatedAt)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblCart SET updatedAt = ? WHERE cartId = ?")) {
            setInstant(statement, 1, updatedAt);
            statement.setString(2, cartId);
            statement.executeUpdate();
        }
    }

    private static void bindApplication(PreparedStatement statement,
            SellerApplication application) throws Exception {
        statement.setString(1, application.applicationId());
        statement.setString(2, application.applicantUserId());
        statement.setString(3, application.shopName());
        statement.setString(4, application.description());
        statement.setString(5, application.category());
        statement.setString(6, application.contact());
        statement.setString(7, application.applicationStatement());
        statement.setString(8, application.status().name());
        statement.setString(9, application.reviewReason());
        statement.setString(10, application.reviewerUserId());
        setInstant(statement, 11, application.submittedAt());
        setInstant(statement, 12, application.reviewedAt());
        statement.setLong(13, application.rowVersion());
    }

    private static void bindSku(PreparedStatement statement, ProductSku sku) throws Exception {
        statement.setString(1, sku.skuId());
        statement.setString(2, sku.productId());
        statement.setString(3, sku.skuName());
        statement.setBigDecimal(4, sku.unitPrice());
        statement.setLong(5, sku.stockQuantity());
        statement.setLong(6, sku.reservedQuantity());
        statement.setBoolean(7, sku.active());
        statement.setLong(8, sku.rowVersion());
    }

    private static SellerApplication mapApplication(ResultSet result) throws Exception {
        return new SellerApplication(result.getString("applicationId"),
                result.getString("applicantUserId"), result.getString("shopName"),
                result.getString("description"), result.getString("category"),
                result.getString("contact"), result.getString("applicationStatement"), SellerApplicationStatus.valueOf(
                        result.getString("applicationStatus")), result.getString("reviewReason"),
                result.getString("reviewerUserId"), instant(result, "submittedAt"),
                instant(result, "reviewedAt"), result.getLong("rowVersion"));
    }

    private static Shop mapShop(ResultSet result) throws Exception {
        return new Shop(result.getString("shopId"), result.getString("ownerUserId"),
                result.getString("shopName"), result.getString("normalizedShopName"), result.getString("description"),
                result.getString("category"), result.getString("contact"),
                ShopStatus.valueOf(result.getString("shopStatus")),
                result.getString("suspensionReason"), result.getString("suspendedByUserId"),
                instant(result, "suspendedAt"), result.getLong("rowVersion"),
                instant(result, "createdAt"), instant(result, "updatedAt"));
    }

    private static Product mapProduct(ResultSet result) throws Exception {
        return new Product(result.getString("productId"), result.getString("shopId"),
                result.getString("productName"), result.getString("normalizedProductName"),
                result.getString("category"), result.getString("description"),
                result.getString("coverImageUrl"), ProductStatus.valueOf(
                        result.getString("productStatus")), result.getLong("salesCount"),
                result.getLong("rowVersion"), instant(result, "createdAt"),
                instant(result, "updatedAt"));
    }

    private static ProductSku mapSku(ResultSet result) throws Exception {
        return new ProductSku(result.getString("skuId"), result.getString("productId"),
                result.getString("skuName"), result.getBigDecimal("unitPrice"),
                result.getLong("stockQuantity"), result.getLong("reservedQuantity"),
                result.getBoolean("isActive"), result.getLong("rowVersion"));
    }

    private static CartItem mapCartItem(ResultSet result) throws Exception {
        return new CartItem(result.getString("cartItemId"), result.getString("cartId"),
                result.getString("skuId"), result.getLong("quantity"),
                result.getLong("rowVersion"), instant(result, "createdAt"),
                instant(result, "updatedAt"));
    }

    private static Instant instant(ResultSet result, String column) throws Exception {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value)
            throws Exception {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    private static ShopException invalidApplicationState(String message) {
        return new ShopException(ShopErrorCode.SHOP_SELLER_APPLICATION_STATUS_INVALID, message);
    }
}
