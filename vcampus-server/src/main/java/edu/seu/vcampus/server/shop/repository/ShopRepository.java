package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

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
}
