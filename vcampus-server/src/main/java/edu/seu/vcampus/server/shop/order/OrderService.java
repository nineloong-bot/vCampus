package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.wallet.service.WalletPostingPort;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Authoritative orders, inventory and wallet postings sharing one transaction manager. */
public final class OrderService {
    private final TransactionManager transactions;
    private final Clock clock;
    private final OrderRepository orders=new OrderRepository();
    private final OrderReceipts receipts=new OrderReceipts();
    private final OrderCheckout checkout;
    private final OrderLifecycle lifecycle;
    /** Composes order work with the runtime's shared transaction manager and availability rule. */
    public OrderService(TransactionManager transactions,WalletPostingPort wallet,Clock clock,OrderAvailability availability) {
        this.transactions=Objects.requireNonNull(transactions);this.clock=Objects.requireNonNull(clock);
        this.checkout=new OrderCheckout(Objects.requireNonNull(availability));
        this.lifecycle=new OrderLifecycle(availability,Objects.requireNonNull(wallet));
    }
    /** Returns a fresh quote to display before explicit buyer confirmation. */
    public CheckoutQuote quote(String buyer,CheckoutRequest request) {
        identity(buyer);Objects.requireNonNull(request);
        return transactions.inTransaction(c->checkout.quote(c,request));
    }
    /** Creates per-shop orders and inventory reservations, replaying the original receipt. */
    public OrderResult checkout(String buyer,String requestId,CheckoutRequest request) {
        String key=receipts.key(buyer,requestId,"CHECKOUT"),digest=receipts.digest(request);
        return transactions.inTransaction(c->{
            var replay=receipts.replay(c,key,digest);if(replay!=null)return replay;
            var result=result(c,checkout.create(c,buyer,request,clock.instant()),"");
            receipts.save(c,key,digest,result);return result;
        });
    }
    /** Lists only this buyer's or seller's orders and lazily invalidates unpaid products. */
    public OrderResult list(String actor,boolean seller,OrderQuery query) {
        identity(actor);Objects.requireNonNull(query);
        String filter=query.state()==null?"ALL":query.state();
        OrderSql.require(List.of("ALL","CLOSED","PENDING_PAYMENT","PAID","SHIPPED","COMPLETED",
                "REFUND_PENDING","REFUNDED","CANCELLED").contains(filter),"ORDER_INVALID_REQUEST");
        return transactions.inTransaction(c->{
            List<String> ids=orders.owned(c,actor,seller,query.shopId());
            boolean changed=false;List<OrderView> views=new ArrayList<>();
            for(String id:ids) {
                changed=lifecycle.validate(c,id,clock.instant()) || changed;
                var view=orders.view(c,id);
                if(filter.equals("ALL") || filter.equals(view.state()) || filter.equals("CLOSED")
                        && List.of("REFUND_PENDING","REFUNDED","CANCELLED").contains(view.state())) views.add(view);
            }
            var legacy=new LegacyOrderReader().list(c,actor,seller,query.shopId(),filter);
            views.addAll(legacy);
            views.sort(java.util.Comparator.comparing(OrderView::createdAt).reversed());
            String notice=changed?"订单商品或付款期限已变化，请查看订单状态":"";
            if(!legacy.isEmpty())notice+=(notice.isEmpty()?"":"；")+LegacyOrderReader.NOTICE;
            return new OrderResult(views,notice);
        });
    }
    /** Rechecks selected buyer orders without charging and commits permanent invalidations. */
    public OrderResult validate(String buyer,OrderAction action) {
        identity(buyer);
        return transactions.inTransaction(c->{
            authorize(c,buyer,action.orderIds(),false);
            boolean changed=validate(c,action.orderIds());
            var snapshot=result(c,action.orderIds(),"");
            if(!changed)return snapshot;
            boolean invalid=snapshot.orders().stream().flatMap(o->o.items().stream()).anyMatch(i->!i.valid());
            boolean valid=snapshot.orders().stream().flatMap(o->o.items().stream()).anyMatch(OrderItem::valid);
            String notice=invalid?(valid?"订单内部分商品已下架，付款仅购买剩余有效商品":"订单商品均已下架，订单已取消")
                    :"订单已超时，订单已取消";
            return new OrderResult(snapshot.orders(),notice);
        });
    }
    /** Executes a selected whole-order transition with durable request replay. */
    public OrderResult act(String actor,String requestId,String kind,OrderAction action) {
        String key=receipts.key(actor,requestId,kind),digest=receipts.digest(action);
        OrderSql.require(List.of("PAY","CANCEL","REFUND_REQUEST","REFUND_APPROVE","REFUND_REJECT","SHIP","RECEIVE")
                .contains(kind),"ORDER_INVALID_REQUEST");
        return transactions.inTransaction(c->{
            var replay=receipts.replay(c,key,digest);if(replay!=null)return replay;
            boolean seller=List.of("SHIP","REFUND_APPROVE","REFUND_REJECT").contains(kind);
            authorize(c,actor,action.orderIds(),seller);
            if(kind.equals("PAY") && validate(c,action.orderIds())) {
                var adjusted=result(c,action.orderIds(),"订单金额或状态已调整，请重新确认后付款");
                receipts.save(c,key,digest,adjusted);return adjusted;
            }
            for(String id:action.orderIds().stream().sorted().toList())
                lifecycle.action(c,actor,kind,id,action.reason(),clock.instant());
            var result=result(c,action.orderIds(),"");receipts.save(c,key,digest,result);return result;
        });
    }
    /** Cancels all expired unpaid orders; safe on startup and repeated scheduled runs. */
    public int expirePending() {
        return transactions.inTransaction(c->{
            var ids=OrderSql.list(c,"SELECT orderId FROM tblShopOrderState WHERE lifecycle='PENDING_PAYMENT' AND expiresAt<=?",
                    r->r.getString(1),clock.instant());
            for(String id:ids)lifecycle.cancel(c,orders.get(c,id),clock.instant());
            return ids.size();
        });
    }
    /** Participates in the caller's shop suspension transaction without committing separately. */
    public void cancelPendingForShop(TransactionContext transaction,String shopId) throws SQLException {
        var c=transaction.connection();OrderSql.require(!c.getAutoCommit(),"ORDER_TRANSACTION_REQUIRED");
        var ids=OrderSql.list(c,"SELECT o.orderId FROM tblOrder o INNER JOIN tblShopOrderState x "
                + "ON o.orderId=x.orderId WHERE o.shopId=? AND x.lifecycle='PENDING_PAYMENT'",r->r.getString(1),shopId);
        for(String id:ids)lifecycle.cancel(c,orders.get(c,id),clock.instant());
    }
    private boolean validate(Connection c,List<String> ids) throws SQLException {
        boolean changed=false;
        for(String id:ids)changed=lifecycle.validate(c,id,clock.instant()) || changed;
        return changed;
    }
    private void authorize(Connection c,String actor,List<String> ids,boolean seller) throws SQLException {
        for(String id:ids) {
            var order=orders.get(c,id);
            OrderSql.require(actor.equals(seller?order.seller():order.buyer()),"ORDER_FORBIDDEN");
        }
    }
    private OrderResult result(Connection c,List<String> ids,String notice) throws SQLException {
        List<OrderView> result=new ArrayList<>();
        for(String id:ids)result.add(orders.view(c,id));
        return new OrderResult(result,notice);
    }
    private void identity(String user) {OrderSql.require(user!=null && !user.isBlank() && user.length()<=36,"ORDER_FORBIDDEN");}
}
