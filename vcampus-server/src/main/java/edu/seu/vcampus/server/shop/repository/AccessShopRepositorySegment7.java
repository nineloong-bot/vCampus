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
abstract class AccessShopRepositorySegment7 extends AccessShopRepositorySegment6 {

    @Override
    public List<PaidOrderView> findPaidOrders(Connection connection,
            String buyerUserId) throws Exception {
        String sql = "SELECT o.orderId, o.orderNumber, o.shopId, s.shopName, "
                + "o.orderAmount, o.paidAt FROM (tblOrder o INNER JOIN tblOrderGroup g "
                + "ON o.orderGroupId = g.orderGroupId) INNER JOIN tblShop s "
                + "ON o.shopId = s.shopId WHERE g.buyerUserId = ? "
                + "AND g.groupStatus = 'PAID' AND o.orderStatus = 'PAID' "
                + "AND o.paidAt IS NOT NULL ORDER BY o.paidAt DESC, o.orderId";
        List<PaidOrderHeader> headers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, buyerUserId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    headers.add(new PaidOrderHeader(result.getString("orderId"),
                            result.getString("orderNumber"), result.getString("shopId"),
                            result.getString("shopName"), result.getBigDecimal("orderAmount"),
                            instant(result, "paidAt")));
                }
            }
        }
        List<PaidOrderView> orders = new ArrayList<>();
        for (PaidOrderHeader header : headers) {
            orders.add(new PaidOrderView(header.orderId(), header.orderNumber(),
                    header.shopId(), header.shopName(), header.totalAmount(), header.paidAt(),
                    OrderStatus.PAID, findPaidOrderItems(connection, header.orderId())));
        }
        return List.copyOf(orders);
    }
}
