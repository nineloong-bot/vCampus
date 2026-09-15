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

/** Maps Access rows and binds shop aggregate values. */
abstract class AccessShopRepositoryMappings implements ShopRepository {

    protected static void bindApplication(PreparedStatement statement,
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

    protected static void bindSku(PreparedStatement statement, ProductSku sku) throws Exception {
        statement.setString(1, sku.skuId());
        statement.setString(2, sku.productId());
        statement.setString(3, sku.skuName());
        statement.setBigDecimal(4, sku.unitPrice());
        statement.setLong(5, sku.stockQuantity());
        statement.setLong(6, sku.reservedQuantity());
        statement.setBoolean(7, sku.active());
        statement.setLong(8, sku.rowVersion());
    }

    protected static SellerApplication mapApplication(ResultSet result) throws Exception {
        return new SellerApplication(result.getString("applicationId"),
                result.getString("applicantUserId"), result.getString("shopName"),
                result.getString("description"), result.getString("category"),
                result.getString("contact"), result.getString("applicationStatement"), SellerApplicationStatus.valueOf(
                        result.getString("applicationStatus")), result.getString("reviewReason"),
                result.getString("reviewerUserId"), instant(result, "submittedAt"),
                instant(result, "reviewedAt"), result.getLong("rowVersion"));
    }

    protected static Shop mapShop(ResultSet result) throws Exception {
        return new Shop(result.getString("shopId"), result.getString("ownerUserId"),
                result.getString("shopName"), result.getString("normalizedShopName"), result.getString("description"),
                result.getString("category"), result.getString("contact"),
                ShopStatus.valueOf(result.getString("shopStatus")),
                result.getString("suspensionReason"), result.getString("suspendedByUserId"),
                instant(result, "suspendedAt"), result.getLong("rowVersion"),
                instant(result, "createdAt"), instant(result, "updatedAt"));
    }

    protected static Product mapProduct(ResultSet result) throws Exception {
        return new Product(result.getString("productId"), result.getString("shopId"),
                result.getString("productName"), result.getString("normalizedProductName"),
                result.getString("category"), result.getString("description"),
                result.getString("coverImageUrl"), ProductStatus.valueOf(
                        result.getString("productStatus")), result.getLong("salesCount"),
                result.getLong("rowVersion"), instant(result, "createdAt"),
                instant(result, "updatedAt"));
    }

    protected static ProductSku mapSku(ResultSet result) throws Exception {
        return new ProductSku(result.getString("skuId"), result.getString("productId"),
                result.getString("skuName"), result.getBigDecimal("unitPrice"),
                result.getLong("stockQuantity"), result.getLong("reservedQuantity"),
                result.getBoolean("isActive"), result.getLong("rowVersion"));
    }

    protected static CartItem mapCartItem(ResultSet result) throws Exception {
        return new CartItem(result.getString("cartItemId"), result.getString("cartId"),
                result.getString("skuId"), result.getLong("quantity"),
                result.getLong("rowVersion"), instant(result, "createdAt"),
                instant(result, "updatedAt"));
    }

    protected static Instant instant(ResultSet result, String column) throws Exception {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    protected static void setInstant(PreparedStatement statement, int index, Instant value)
            throws Exception {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    protected static ShopException invalidApplicationState(String message) {
        return new ShopException(ShopErrorCode.SHOP_SELLER_APPLICATION_STATUS_INVALID, message);
    }
}
