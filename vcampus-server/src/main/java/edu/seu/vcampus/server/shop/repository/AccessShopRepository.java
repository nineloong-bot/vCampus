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
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
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
                + "description, category, contact, applicationStatus, reviewReason, reviewerUserId, "
                + "submittedAt, reviewedAt, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                + "contact = ?, applicationStatus = ?, reviewReason = ?, reviewerUserId = ?, "
                + "submittedAt = ?, reviewedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE applicationId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, application.shopName());
            statement.setString(2, application.description());
            statement.setString(3, application.category());
            statement.setString(4, application.contact());
            statement.setString(5, application.status().name());
            statement.setString(6, application.reviewReason());
            statement.setString(7, application.reviewerUserId());
            setInstant(statement, 8, application.submittedAt());
            setInstant(statement, 9, application.reviewedAt());
            statement.setString(10, application.applicationId());
            statement.setLong(11, expectedVersion);
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
                + "contact, shopStatus, suspensionReason, suspendedByUserId, suspendedAt, "
                + "rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, shop.shopId());
            statement.setString(2, shop.ownerUserId());
            statement.setString(3, shop.shopName());
            statement.setString(4, shop.description());
            statement.setString(5, shop.category());
            statement.setString(6, shop.contact());
            statement.setString(7, shop.status().name());
            statement.setString(8, shop.suspensionReason());
            statement.setString(9, shop.suspendedByUserId());
            setInstant(statement, 10, shop.suspendedAt());
            statement.setLong(11, shop.rowVersion());
            setInstant(statement, 12, shop.createdAt());
            setInstant(statement, 13, shop.updatedAt());
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
                throw new ShopException(ShopErrorCode.SHOP_STATUS_INVALID,
                        "Shop status or version does not allow this transition");
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
        String sql = "UPDATE tblShop SET shopName = ?, description = ?, category = ?, contact = ?, "
                + "updatedAt = ?, rowVersion = rowVersion + 1 WHERE shopId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, shop.shopName());
            statement.setString(2, shop.description());
            statement.setString(3, shop.category());
            statement.setString(4, shop.contact());
            setInstant(statement, 5, shop.updatedAt());
            statement.setString(6, shop.shopId());
            statement.setLong(7, expectedVersion);
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
        String sql = "INSERT INTO tblProduct (productId, shopId, productName, category, description, "
                + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.productId());
            statement.setString(2, product.shopId());
            statement.setString(3, product.productName());
            statement.setString(4, product.category());
            statement.setString(5, product.description());
            statement.setString(6, product.status().name());
            statement.setLong(7, product.salesCount());
            statement.setLong(8, product.rowVersion());
            setInstant(statement, 9, product.createdAt());
            setInstant(statement, 10, product.updatedAt());
            statement.executeUpdate();
        }
        return product;
    }

    @Override
    public Product updateProduct(Connection connection, Product product,
            long expectedVersion) throws Exception {
        String sql = "UPDATE tblProduct SET productName = ?, category = ?, description = ?, "
                + "updatedAt = ?, rowVersion = rowVersion + 1 WHERE productId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.productName());
            statement.setString(2, product.category());
            statement.setString(3, product.description());
            setInstant(statement, 4, product.updatedAt());
            statement.setString(5, product.productId());
            statement.setLong(6, expectedVersion);
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
                + "p.productName, p.category, MIN(k.unitPrice) AS minimumPrice, "
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
        sql.append(" GROUP BY p.productId, p.shopId, s.shopName, p.productName, p.category, "
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
                ? " ORDER BY MIN(k.unitPrice) DESC, p.createdAt DESC"
                : " ORDER BY p.salesCount DESC, p.createdAt DESC");
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
                            result.getBigDecimal("minimumPrice"), result.getLong("salesCount"),
                            instant(result, "createdAt")));
                }
            }
        }
        int from = Math.min(query.pageNumber() * query.pageSize(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return new PageResult<>(all.subList(from, to), query.pageNumber(), query.pageSize(), all.size());
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
        statement.setString(7, application.status().name());
        statement.setString(8, application.reviewReason());
        statement.setString(9, application.reviewerUserId());
        setInstant(statement, 10, application.submittedAt());
        setInstant(statement, 11, application.reviewedAt());
        statement.setLong(12, application.rowVersion());
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
                result.getString("contact"), SellerApplicationStatus.valueOf(
                        result.getString("applicationStatus")), result.getString("reviewReason"),
                result.getString("reviewerUserId"), instant(result, "submittedAt"),
                instant(result, "reviewedAt"), result.getLong("rowVersion"));
    }

    private static Shop mapShop(ResultSet result) throws Exception {
        return new Shop(result.getString("shopId"), result.getString("ownerUserId"),
                result.getString("shopName"), result.getString("description"),
                result.getString("category"), result.getString("contact"),
                ShopStatus.valueOf(result.getString("shopStatus")),
                result.getString("suspensionReason"), result.getString("suspendedByUserId"),
                instant(result, "suspendedAt"), result.getLong("rowVersion"),
                instant(result, "createdAt"), instant(result, "updatedAt"));
    }

    private static Product mapProduct(ResultSet result) throws Exception {
        return new Product(result.getString("productId"), result.getString("shopId"),
                result.getString("productName"), result.getString("category"),
                result.getString("description"), ProductStatus.valueOf(
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
