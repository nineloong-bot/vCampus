package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.wallet.service.WalletPosting;
import edu.seu.vcampus.server.wallet.service.WalletPostingPort;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.math.BigDecimal;

final class OrderLifecycle {
    private final OrderRepository orders=new OrderRepository();
    private final OrderInventory stock=new OrderInventory();
    private final OrderAvailability availability;
    private final WalletPostingPort wallet;
    OrderLifecycle(OrderAvailability availability,WalletPostingPort wallet){this.availability=availability;this.wallet=wallet;}
    boolean validate(Connection c,String id,Instant now) throws SQLException {
        var order=orders.get(c,id);
        if(!order.state().equals("PENDING_PAYMENT")) return false;
        if(!now.isBefore(order.expires())) {cancel(c,order,now);return true;}
        boolean changed=false;
        for(var line:stock.lines(c,id)) {
            if(!line.state().equals("RESERVED")) continue;
            var sku=stock.sku(c,line.sku());
            if(!sku.active() || !availability.purchasable(c,line.product())) {
                stock.change(c,line,"INVALID",now);changed=true;
            }
        }
        if(changed) {
            var totals=OrderSql.list(c,"SELECT i.lineAmount FROM tblOrderItem i INNER JOIN tblShopOrderLineState x "
                    + "ON i.orderItemId=x.orderItemId WHERE i.orderId=? AND x.lineState='RESERVED'",r->r.getBigDecimal(1),id);
            BigDecimal total=totals.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            OrderSql.update(c,"UPDATE tblOrder SET orderAmount=?,rowVersion=rowVersion+1 WHERE orderId=?",total,id);
            if(totals.isEmpty()) cancel(c,order,now);
            orders.refreshGroup(c,order.group());
        }
        return changed;
    }
    void cancel(Connection c,OrderRepository.Stored order,Instant now) throws SQLException {
        OrderSql.require(order.state().equals("PENDING_PAYMENT"),"ORDER_STATE_CHANGED");
        for(var line:stock.lines(c,order.id())) stock.change(c,line,"RELEASE",now);
        orders.state(c,order,"CANCELLED",order.reason(),now);
        event(c,order,"CANCEL",null,order.reason(),"CANCELLED",now);
    }
    void action(Connection c,String actor,String kind,String id,String reason,Instant now) throws SQLException {
        var o=orders.get(c,id);
        boolean seller=kind.equals("SHIP") || kind.startsWith("REFUND_A") || kind.equals("REFUND_REJECT");
        OrderSql.require((seller?o.seller():o.buyer()).equals(actor),"ORDER_FORBIDDEN");
        switch(kind) {
            case "PAY" -> {
                require(o,"PENDING_PAYMENT");
                OrderSql.require(now.isBefore(o.expires()),"ORDER_EXPIRED");
                post(c,o,WalletPosting.Kind.HOLD);
                for(var line:stock.lines(c,id)) stock.change(c,line,"PAY",now);
                orders.state(c,o,"PAID",o.reason(),now);
            }
            case "CANCEL" -> cancel(c,o,now);
            case "REFUND_REQUEST" -> {
                require(o,"PAID");
                orders.state(c,o,"REFUND_PENDING",reason,now);
            }
            case "REFUND_REJECT" -> {
                require(o,"REFUND_PENDING");
                OrderSql.require(!reason.isBlank(),"ORDER_REASON_REQUIRED");
                orders.state(c,o,"PAID",reason,now);
            }
            case "REFUND_APPROVE" -> {
                require(o,"REFUND_PENDING");post(c,o,WalletPosting.Kind.REFUND);
                for(var line:stock.lines(c,id)) stock.change(c,line,"REFUND",now);
                orders.state(c,o,"REFUNDED",o.reason(),now);
            }
            case "SHIP" -> {require(o,"PAID");orders.state(c,o,"SHIPPED",o.reason(),now);}
            case "RECEIVE" -> {
                require(o,"SHIPPED");post(c,o,WalletPosting.Kind.SETTLE);
                orders.state(c,o,"COMPLETED",o.reason(),now);
            }
            default -> throw new OrderException("ORDER_INVALID_REQUEST");
        }
        if(!kind.equals("CANCEL")) event(c,o,kind,actor,reason,orders.get(c,id).state(),now);
    }
    private void event(Connection c,OrderRepository.Stored o,String action,String actor,String reason,
                       String next,Instant now) throws SQLException {
        OrderSql.update(c,"INSERT INTO tblShopOrderEvent (eventId,orderId,actionName,actorUserId,reason,"
                + "previousState,nextState,createdAt) VALUES (?,?,?,?,?,?,?,?)",
                OrderSql.id(),o.id(),action,actor,reason,o.state(),next,now);
    }
    private void post(Connection c,OrderRepository.Stored o,WalletPosting.Kind kind) throws SQLException {
        wallet.post(new TransactionContext(c),new WalletPosting(kind+":"+o.id(),o.id(),o.buyer(),o.seller(),
                o.amount().movePointRight(2).longValueExact(),kind));
    }
    private void require(OrderRepository.Stored order,String state) {
        OrderSql.require(order.state().equals(state),"ORDER_STATE_CHANGED");
    }
}
