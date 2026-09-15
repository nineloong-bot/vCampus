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
interface ShopRepositorySegment1 {
    /**
     * Performs the find application by identifier operation.
     * @param connection the connection
     * @param applicationId the application identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<SellerApplication> findApplicationById(Connection connection, String applicationId) throws Exception;

    /**
     * Performs the find application by applicant operation.
     * @param connection the connection
     * @param applicantUserId the applicant user identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<SellerApplication> findApplicationByApplicant(Connection connection, String applicantUserId) throws Exception;

    /**
     * Performs the insert application operation.
     * @param connection the connection
     * @param application the application
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    SellerApplication insertApplication(Connection connection, SellerApplication application) throws Exception;

    /**
     * Performs the update application operation.
     * @param connection the connection
     * @param application the application
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    SellerApplication updateApplication(Connection connection, SellerApplication application,
            long expectedVersion) throws Exception;

    /**
     * Performs the search applications operation.
     * @param connection the connection
     * @param query the query
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    PageResult<SellerApplication> searchApplications(Connection connection,
            SellerApplicationQuery query) throws Exception;

    /**
     * Performs the find shop by identifier operation.
     * @param connection the connection
     * @param shopId the shop identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<Shop> findShopById(Connection connection, String shopId) throws Exception;

    /**
     * Performs the find shop by owner operation.
     * @param connection the connection
     * @param ownerUserId the owner user identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<Shop> findShopByOwner(Connection connection, String ownerUserId) throws Exception;

    /**
     * Performs the find shop by normalized name operation.
     * @param connection the connection
     * @param normalizedShopName the normalized shop name
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<Shop> findShopByNormalizedName(Connection connection, String normalizedShopName) throws Exception;

    /**
     * Performs the search shops operation.
     * @param connection the connection
     * @param query the query
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    PageResult<ShopAdminSummary> searchShops(Connection connection, ShopAdminQuery query) throws Exception;

    /**
     * Performs the insert shop operation.
     * @param connection the connection
     * @param shop the shop
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Shop insertShop(Connection connection, Shop shop) throws Exception;

    /**
     * Performs the update shop status operation.
     * @param connection the connection
     * @param shopId the shop identifier
     * @param expectedStatus the expected status
     * @param targetStatus the target status
     * @param suspensionReason the suspension reason
     * @param suspendedByUserId the suspended by user identifier
     * @param suspendedAt the suspended at
     * @param updatedAt the updated at
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Shop updateShopStatus(Connection connection, String shopId, ShopStatus expectedStatus,
            ShopStatus targetStatus, String suspensionReason, String suspendedByUserId,
            Instant suspendedAt, Instant updatedAt, long expectedVersion) throws Exception;
}
