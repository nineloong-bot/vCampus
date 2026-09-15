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

/** Defines one cohesive portion of shop persistence operations. */
interface ShopRepositorySegment2 {

    /**
     * Performs the count shops by owner operation.
     * @param connection the connection
     * @param ownerUserId the owner user identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    long countShopsByOwner(Connection connection, String ownerUserId) throws Exception;

    /**
     * Performs the update shop profile operation.
     * @param connection the connection
     * @param shop the shop
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Shop updateShopProfile(Connection connection, Shop shop, long expectedVersion) throws Exception;

    /**
     * Performs the find product by identifier operation.
     * @param connection the connection
     * @param productId the product identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<Product> findProductById(Connection connection, String productId) throws Exception;

    /**
     * Performs the find product by normalized name operation.
     * @param connection the connection
     * @param shopId the shop identifier
     * @param normalizedName the normalized name
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<Product> findProductByNormalizedName(Connection connection, String shopId,
            String normalizedName) throws Exception;

    /**
     * Performs the find skus by product operation.
     * @param connection the connection
     * @param productId the product identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    List<ProductSku> findSkusByProduct(Connection connection, String productId) throws Exception;

    /**
     * Performs the insert product operation.
     * @param connection the connection
     * @param product the product
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Product insertProduct(Connection connection, Product product) throws Exception;

    /**
     * Performs the update product operation.
     * @param connection the connection
     * @param product the product
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Product updateProduct(Connection connection, Product product, long expectedVersion) throws Exception;

    /**
     * Performs the update product status operation.
     * @param connection the connection
     * @param productId the product identifier
     * @param status the status
     * @param updatedAt the updated at
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Product updateProductStatus(Connection connection, String productId, ProductStatus status,
            Instant updatedAt, long expectedVersion) throws Exception;

    /**
     * Performs the insert sku operation.
     * @param connection the connection
     * @param sku the sku
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    ProductSku insertSku(Connection connection, ProductSku sku) throws Exception;

    /**
     * Performs the update sku operation.
     * @param connection the connection
     * @param sku the sku
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    ProductSku updateSku(Connection connection, ProductSku sku, long expectedVersion) throws Exception;

    /**
     * Performs the search catalog operation.
     * @param connection the connection
     * @param query the query
     * @param shopId the shop identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    PageResult<ProductSummary> searchCatalog(Connection connection,
            ProductSearchQuery query, String shopId) throws Exception;
}
