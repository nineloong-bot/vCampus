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
abstract class AccessShopRepositorySegment4 extends AccessShopRepositorySegment3 {

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
}
