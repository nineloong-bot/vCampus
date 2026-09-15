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
interface ShopRepositorySegment3 {

    /**
     * Performs the search managed products operation.
     * @param connection the connection
     * @param query the query
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    PageResult<ProductManagementSummary> searchManagedProducts(Connection connection,
            ProductManagementQuery query) throws Exception;

    /**
     * Performs the find orders by shop operation.
     * @param connection the connection
     * @param shopId the shop identifier
     * @param query the query
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    List<SellerOrderView> findOrdersByShop(Connection connection, String shopId,
            SellerOrderQuery query) throws Exception;

    /**
     * Performs the find sellable sku operation.
     * @param connection the connection
     * @param skuId the sku identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<ProductSku> findSellableSku(Connection connection, String skuId) throws Exception;

    /**
     * Performs the find shop owner by sku operation.
     * @param connection the connection
     * @param skuId the sku identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<String> findShopOwnerBySku(Connection connection, String skuId) throws Exception;

    /**
     * Performs the find cart identifier by user operation.
     * @param connection the connection
     * @param userId the user identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<String> findCartIdByUser(Connection connection, String userId) throws Exception;

    /**
     * Performs the insert cart operation.
     * @param connection the connection
     * @param cartId the cart identifier
     * @param userId the user identifier
     * @param updatedAt the updated at
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    String insertCart(Connection connection, String cartId, String userId, Instant updatedAt) throws Exception;

    /**
     * Performs the find cart item by sku operation.
     * @param connection the connection
     * @param cartId the cart identifier
     * @param skuId the sku identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<CartItem> findCartItemBySku(Connection connection, String cartId, String skuId) throws Exception;

    /**
     * Performs the find cart item by identifier operation.
     * @param connection the connection
     * @param cartItemId the cart item identifier
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    Optional<CartItem> findCartItemById(Connection connection, String cartItemId) throws Exception;

    /**
     * Performs the insert cart item operation.
     * @param connection the connection
     * @param item the item
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    CartItem insertCartItem(Connection connection, CartItem item) throws Exception;

    /**
     * Performs the update cart item quantity operation.
     * @param connection the connection
     * @param cartItemId the cart item identifier
     * @param quantity the quantity
     * @param updatedAt the updated at
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws Exception when the operation cannot be completed
     */
    CartItem updateCartItemQuantity(Connection connection, String cartItemId,
            long quantity, Instant updatedAt, long expectedVersion) throws Exception;

    /**
     * Performs the delete cart item operation.
     * @param connection the connection
     * @param cartItemId the cart item identifier
     * @param cartId the cart identifier
     * @throws Exception when the operation cannot be completed
     */
    void deleteCartItem(Connection connection, String cartItemId, String cartId) throws Exception;
}
