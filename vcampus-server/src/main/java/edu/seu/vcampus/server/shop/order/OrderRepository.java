package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.OrderItem;
import edu.seu.vcampus.common.shop.order.OrderView;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class OrderRepository {
    record Stored(String id, String group, String shop, String buyer, String seller, String state,
                  BigDecimal amount, Instant created, Instant expires, String reason) { }
    Stored get(Connection c, String id) throws SQLException {
        var found = OrderSql.list(c, "SELECT o.*, g.buyerUserId, s.ownerUserId, x.lifecycle, x.expiresAt, "
                + "x.refundReason FROM ((tblOrder o INNER JOIN tblOrderGroup g ON o.orderGroupId=g.orderGroupId) "
                + "INNER JOIN tblShop s ON o.shopId=s.shopId) INNER JOIN tblShopOrderState x ON o.orderId=x.orderId "
                + "WHERE o.orderId=?", r -> new Stored(r.getString("orderId"),r.getString("orderGroupId"),
                r.getString("shopId"),r.getString("buyerUserId"),r.getString("ownerUserId"),r.getString("lifecycle"),
                r.getBigDecimal("orderAmount"),r.getTimestamp("createdAt").toInstant(),
                r.getTimestamp("expiresAt").toInstant(),r.getString("refundReason")), id);
        OrderSql.require(!found.isEmpty(), "ORDER_NOT_FOUND");
        return found.getFirst();
    }
    List<String> owned(Connection c, String user, boolean seller, String shop) throws SQLException {
        String sql = "SELECT o.orderId FROM ((tblOrder o INNER JOIN tblOrderGroup g "
                + "ON o.orderGroupId=g.orderGroupId) INNER JOIN tblShop s ON o.shopId=s.shopId) "
                + "INNER JOIN tblShopOrderState x ON o.orderId=x.orderId WHERE "
                + (seller ? "s.ownerUserId" : "g.buyerUserId") + "=?";
        if (shop != null && !shop.isBlank()) return OrderSql.list(c, sql + " AND o.shopId=? ORDER BY o.createdAt DESC",
                r -> r.getString(1), user, shop);
        return OrderSql.list(c, sql + " ORDER BY o.createdAt DESC", r -> r.getString(1), user);
    }
    OrderView view(Connection c, String id) throws SQLException {
        var o = get(c, id);
        var items = OrderSql.list(c, "SELECT i.*, x.lineState FROM tblOrderItem i INNER JOIN tblShopOrderLineState x "
                + "ON i.orderItemId=x.orderItemId WHERE i.orderId=? ORDER BY i.orderItemId", r -> new OrderItem(
                r.getString("orderItemId"),r.getString("skuId"),r.getString("productNameSnapshot"),
                r.getString("skuNameSnapshot"),r.getBigDecimal("unitPrice"),r.getInt("quantity"),
                r.getBigDecimal("lineAmount"),!r.getString("lineState").equals("INVALID")), id);
        var names = OrderSql.list(c, "SELECT shopNameSnapshot FROM tblOrderItem WHERE orderId=?",
                r -> r.getString(1), id);
        return new OrderView(o.id,o.group,o.shop,names.isEmpty()?"":names.getFirst(),"校园用户",o.state,
                o.amount,o.created,o.expires,o.reason == null ? "" : o.reason,items);
    }
    void state(Connection c, Stored o, String next, String reason, Instant now) throws SQLException {
        OrderSql.require(OrderSql.update(c, "UPDATE tblShopOrderState SET lifecycle=?, refundReason=? "
                + "WHERE orderId=? AND lifecycle=?",next,reason,o.id,o.state)==1,"ORDER_STATE_CHANGED");
        String legacy = switch(next) { case "REFUND_PENDING" -> "PAID"; case "REFUNDED" -> "CANCELLED"; default -> next; };
        OrderSql.update(c,"UPDATE tblOrder SET orderStatus=?, rowVersion=rowVersion+1 WHERE orderId=?",legacy,o.id);
        String time = switch(next) { case "PAID" -> "paidAt"; case "SHIPPED" -> "shippedAt";
            case "COMPLETED" -> "completedAt"; default -> null; };
        if(time != null && !(next.equals("PAID") && o.state.equals("REFUND_PENDING")))
            OrderSql.update(c,"UPDATE tblOrder SET " + time + "=? WHERE orderId=?",now,o.id);
        refreshGroup(c,o.group);
    }
    void refreshGroup(Connection c, String group) throws SQLException {
        var states = OrderSql.list(c,"SELECT orderStatus FROM tblOrder WHERE orderGroupId=?",r -> r.getString(1),group);
        String state = states.stream().allMatch("CANCELLED"::equals)?"CANCELLED"
                :states.stream().anyMatch("PENDING_PAYMENT"::equals)?"PENDING_PAYMENT"
                :states.stream().allMatch(s -> s.equals("CANCELLED") || s.equals("COMPLETED"))?"COMPLETED":"PAID";
        var amounts=OrderSql.list(c,"SELECT orderAmount FROM tblOrder WHERE orderGroupId=?",r->r.getBigDecimal(1),group);
        BigDecimal total=amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        OrderSql.update(c,"UPDATE tblOrderGroup SET totalAmount=?,groupStatus=?,rowVersion=rowVersion+1 WHERE orderGroupId=?",
                total,state,group);
    }
}
