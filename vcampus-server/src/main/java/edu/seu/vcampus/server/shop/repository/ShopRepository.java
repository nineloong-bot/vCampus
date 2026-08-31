package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.PaidOrderView;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.ShopAdminSummary;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.shop.ProductManagementSummary;
import edu.seu.vcampus.common.shop.SellerOrderQuery;
import edu.seu.vcampus.common.shop.SellerOrderView;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.ProductSku;
import edu.seu.vcampus.server.shop.domain.CartItem;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

/** Persistence boundary owned by the shop module. */
public interface ShopRepository {
    Optional<SellerApplication> findApplicationById(Connection connection, String applicationId) throws Exception;

    Optional<SellerApplication> findApplicationByApplicant(Connection connection, String applicantUserId) throws Exception;

    SellerApplication insertApplication(Connection connection, SellerApplication application) throws Exception;

    SellerApplication updateApplication(Connection connection, SellerApplication application,
            long expectedVersion) throws Exception;

    PageResult<SellerApplication> searchApplications(Connection connection,
            SellerApplicationQuery query) throws Exception;

    Optional<Shop> findShopById(Connection connection, String shopId) throws Exception;

    Optional<Shop> findShopByOwner(Connection connection, String ownerUserId) throws Exception;

    Optional<Shop> findShopByNormalizedName(Connection connection, String normalizedShopName) throws Exception;

    PageResult<ShopAdminSummary> searchShops(Connection connection, ShopAdminQuery query) throws Exception;

    Shop insertShop(Connection connection, Shop shop) throws Exception;

    Shop updateShopStatus(Connection connection, String shopId, ShopStatus expectedStatus,
            ShopStatus targetStatus, String suspensionReason, String suspendedByUserId,
            Instant suspendedAt, Instant updatedAt, long expectedVersion) throws Exception;

    long countShopsByOwner(Connection connection, String ownerUserId) throws Exception;

    Shop updateShopProfile(Connection connection, Shop shop, long expectedVersion) throws Exception;

    Optional<Product> findProductById(Connection connection, String productId) throws Exception;

    Optional<Product> findProductByNormalizedName(Connection connection, String shopId,
            String normalizedName) throws Exception;

    List<ProductSku> findSkusByProduct(Connection connection, String productId) throws Exception;

    Product insertProduct(Connection connection, Product product) throws Exception;

    Product updateProduct(Connection connection, Product product, long expectedVersion) throws Exception;

    Product updateProductStatus(Connection connection, String productId, ProductStatus status,
            Instant updatedAt, long expectedVersion) throws Exception;

    ProductSku insertSku(Connection connection, ProductSku sku) throws Exception;

    ProductSku updateSku(Connection connection, ProductSku sku, long expectedVersion) throws Exception;

    PageResult<ProductSummary> searchCatalog(Connection connection,
            ProductSearchQuery query, String shopId) throws Exception;

    PageResult<ProductManagementSummary> searchManagedProducts(Connection connection,
            ProductManagementQuery query) throws Exception;

    List<SellerOrderView> findOrdersByShop(Connection connection, String shopId,
            SellerOrderQuery query) throws Exception;

    Optional<ProductSku> findSellableSku(Connection connection, String skuId) throws Exception;

    Optional<String> findShopOwnerBySku(Connection connection, String skuId) throws Exception;

    Optional<String> findCartIdByUser(Connection connection, String userId) throws Exception;

    String insertCart(Connection connection, String cartId, String userId, Instant updatedAt) throws Exception;

    Optional<CartItem> findCartItemBySku(Connection connection, String cartId, String skuId) throws Exception;

    Optional<CartItem> findCartItemById(Connection connection, String cartItemId) throws Exception;

    CartItem insertCartItem(Connection connection, CartItem item) throws Exception;

    CartItem updateCartItemQuantity(Connection connection, String cartItemId,
            long quantity, Instant updatedAt, long expectedVersion) throws Exception;

    void deleteCartItem(Connection connection, String cartItemId, String cartId) throws Exception;

    CartView loadCart(Connection connection, String userId) throws Exception;

    List<PaidOrderView> findPaidOrders(Connection connection, String buyerUserId) throws Exception;
}
