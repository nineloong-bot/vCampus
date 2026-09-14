package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class OrderCheckout {
    private final OrderInventory stock=new OrderInventory();
    private final OrderAvailability availability;
    OrderCheckout(OrderAvailability availability){this.availability=availability;}
    CheckoutQuote quote(Connection c,CheckoutRequest request) throws SQLException {
        List<OrderLine> lines=new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO;
        for(var wanted:request.lines()) {
            try {
                var sku=stock.sku(c,wanted.skuId());
                if(!sku.active() || !availability.purchasable(c,sku.product()) || sku.available()<1) continue;
                int quantity=Math.min(wanted.quantity(),sku.available());
                lines.add(new OrderLine(sku.id(),quantity,sku.price()));
                total=total.add(sku.price().multiply(BigDecimal.valueOf(quantity)));
            } catch(OrderException ignored) { /* Missing SKU is unavailable in a quote. */ }
        }
        return new CheckoutQuote(lines,total,same(lines,request.lines())?"":"库存或价格发生变化，请确认最新数量和金额");
    }
    private boolean same(List<OrderLine> actual,List<OrderLine> expected) {
        if(actual.size()!=expected.size())return false;
        for(int i=0;i<actual.size();i++) {
            var a=actual.get(i);var e=expected.get(i);
            if(!a.skuId().equals(e.skuId()) || a.quantity()!=e.quantity()
                    || a.expectedUnitPrice().compareTo(e.expectedUnitPrice())!=0)return false;
        }
        return true;
    }
    List<String> create(Connection c,String buyer,CheckoutRequest request,Instant now) throws SQLException {
        var quote=quote(c,request);
        OrderSql.require(same(quote.lines(),request.lines()),"ORDER_QUOTE_CHANGED");
        if(request.fromCart()) for(var line:request.lines()) {
            var quantities=OrderSql.list(c,"SELECT i.quantity FROM tblCartItem i INNER JOIN tblCart t "
                    + "ON i.cartId=t.cartId WHERE t.userId=? AND i.skuId=?",r->r.getInt(1),buyer,line.skuId());
            OrderSql.require(!quantities.isEmpty() && quantities.getFirst()>=line.quantity(),"ORDER_CART_CHANGED");
        }
        for(var line:request.lines()) {
            var owners=OrderSql.list(c,"SELECT s.ownerUserId FROM (tblProductSku k INNER JOIN tblProduct p "
                    + "ON k.productId=p.productId) INNER JOIN tblShop s ON p.shopId=s.shopId WHERE k.skuId=?",
                    r->r.getString(1),line.skuId());
            OrderSql.require(!owners.getFirst().equals(buyer),"ORDER_SELF_PURCHASE_FORBIDDEN");
        }
        String group=OrderSql.id();
        OrderSql.update(c,"INSERT INTO tblOrderGroup (orderGroupId,buyerUserId,totalAmount,groupStatus,createdAt,rowVersion) "
                + "VALUES (?,?,?,'PENDING_PAYMENT',?,0)",group,buyer,quote.amount(),now);
        var grouped=new LinkedHashMap<String,List<OrderLine>>();
        for(var line:request.lines().stream().sorted(java.util.Comparator.comparing(OrderLine::skuId)).toList()) {
            var sku=stock.sku(c,line.skuId());
            grouped.computeIfAbsent(sku.shop(),key->new ArrayList<>()).add(line);
        }
        List<String> result=new ArrayList<>();
        for(var entry:grouped.entrySet()) {
            String order=OrderSql.id();
            BigDecimal amount=entry.getValue().stream().map(l->l.expectedUnitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
            OrderSql.update(c,"INSERT INTO tblOrder (orderId,orderGroupId,shopId,orderNumber,orderAmount,orderStatus,createdAt,rowVersion) "
                    + "VALUES (?,?,?,?,?,'PENDING_PAYMENT',?,0)",order,group,entry.getKey(),order.replace("-",""),amount,now);
            OrderSql.update(c,"INSERT INTO tblShopOrderState (orderId,lifecycle,expiresAt,refundReason) "
                    + "VALUES (?,'PENDING_PAYMENT',?,'')",order,now.plusSeconds(1800));
            for(var line:entry.getValue()) {
                var sku=stock.sku(c,line.skuId());
                OrderSql.require(sku.active() && availability.purchasable(c,sku.product()),"ORDER_PRODUCT_UNAVAILABLE");
                String item=OrderSql.id();
                OrderSql.update(c,"INSERT INTO tblOrderItem (orderItemId,orderId,skuId,productNameSnapshot,skuNameSnapshot,"
                        + "shopNameSnapshot,unitPrice,quantity,lineAmount) VALUES (?,?,?,?,?,?,?,?,?)",
                        item,order,sku.id(),sku.productName(),sku.name(),sku.shopName(),sku.price(),line.quantity(),
                        sku.price().multiply(BigDecimal.valueOf(line.quantity())));
                OrderSql.update(c,"INSERT INTO tblShopOrderLineState (orderItemId,productId,lineState) VALUES (?,?,'RESERVED')",
                        item,sku.product());
                stock.reserve(c,sku.id(),item,line.quantity(),now);
                if(request.fromCart()) OrderSql.update(c,"DELETE FROM tblCartItem WHERE skuId=? AND cartId IN "
                        + "(SELECT cartId FROM tblCart WHERE userId=?)",sku.id(),buyer);
            }
            result.add(order);
        }
        return result;
    }
}
