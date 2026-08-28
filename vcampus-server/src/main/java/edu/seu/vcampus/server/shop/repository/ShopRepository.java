package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.ProductSku;

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

    Shop insertShop(Connection connection, Shop shop) throws Exception;

    Shop updateShopStatus(Connection connection, String shopId, ShopStatus expectedStatus,
            ShopStatus targetStatus, String suspensionReason, String suspendedByUserId,
            Instant suspendedAt, Instant updatedAt, long expectedVersion) throws Exception;

    long countShopsByOwner(Connection connection, String ownerUserId) throws Exception;

    Shop updateShopProfile(Connection connection, Shop shop, long expectedVersion) throws Exception;

    Optional<Product> findProductById(Connection connection, String productId) throws Exception;

    List<ProductSku> findSkusByProduct(Connection connection, String productId) throws Exception;

    Product insertProduct(Connection connection, Product product) throws Exception;

    Product updateProduct(Connection connection, Product product, long expectedVersion) throws Exception;

    Product updateProductStatus(Connection connection, String productId, ProductStatus status,
            Instant updatedAt, long expectedVersion) throws Exception;

    ProductSku insertSku(Connection connection, ProductSku sku) throws Exception;

    ProductSku updateSku(Connection connection, ProductSku sku, long expectedVersion) throws Exception;

    PageResult<ProductSummary> searchCatalog(Connection connection,
            ProductSearchQuery query, String shopId) throws Exception;
}
