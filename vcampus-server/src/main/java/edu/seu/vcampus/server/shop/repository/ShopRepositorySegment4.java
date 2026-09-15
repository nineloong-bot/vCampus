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
interface ShopRepositorySegment4 {

    /**
     * Performs the load cart operation.
     * @param connection the connection
     * @param userId the user identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    CartView loadCart(Connection connection, String userId) throws Exception;

    /**
     * Performs the find paid orders operation.
     * @param connection the connection
     * @param buyerUserId the buyer user identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    List<PaidOrderView> findPaidOrders(Connection connection, String buyerUserId) throws Exception;
}
