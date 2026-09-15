package edu.seu.vcampus.server.shop.order;

import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class OrderInventory {
    record Sku(String id,String product,String shop,String name,String productName,String shopName,
               BigDecimal price,int available,boolean active) { }
    record Line(String id,String sku,String product,int quantity,String state) { }
    Sku sku(Connection c,String id) throws SQLException {
        var rows=OrderSql.list(c,"SELECT k.*,p.shopId,p.productName,p.productStatus,s.shopName,s.shopStatus "
                + "FROM (tblProductSku k INNER JOIN tblProduct p ON k.productId=p.productId) "
                + "INNER JOIN tblShop s ON p.shopId=s.shopId WHERE k.skuId=?",r->new Sku(id,
                r.getString("productId"),r.getString("shopId"),r.getString("skuName"),r.getString("productName"),
                r.getString("shopName"),r.getBigDecimal("unitPrice"),r.getInt("stockQuantity")-r.getInt("reservedQuantity"),
                r.getBoolean("isActive") && r.getString("productStatus").equals("ACTIVE")
                        && r.getString("shopStatus").equals("ACTIVE")),id);
        OrderSql.require(!rows.isEmpty(),"ORDER_PRODUCT_UNAVAILABLE");
        return rows.getFirst();
    }
    List<Line> lines(Connection c,String order) throws SQLException {
        return OrderSql.list(c,"SELECT i.orderItemId,i.skuId,i.quantity,x.productId,x.lineState "
                + "FROM tblOrderItem i INNER JOIN tblShopOrderLineState x ON i.orderItemId=x.orderItemId "
                + "WHERE i.orderId=? ORDER BY i.skuId",r->new Line(r.getString(1),r.getString(2),
                r.getString(4),r.getInt(3),r.getString(5)),order);
    }
    void reserve(Connection c,String sku,String line,int qty,Instant now) throws SQLException {
        OrderSql.require(OrderSql.update(c,"UPDATE tblProductSku SET reservedQuantity=reservedQuantity+?, "
                + "rowVersion=rowVersion+1 WHERE skuId=? AND isActive=TRUE "
                + "AND stockQuantity-reservedQuantity>=?",qty,sku,qty)==1,"ORDER_STOCK_CHANGED");
        movement(c,line,sku,"RESERVE",qty,now);
    }
    void change(Connection c,Line line,String kind,Instant now) throws SQLException {
        String expected=kind.equals("REFUND")?"CONSUMED":"RESERVED";
        if(!line.state.equals(expected)) return;
        String target=switch(kind){case "PAY" -> "CONSUMED";case "INVALID" -> "INVALID";
            case "REFUND" -> "REFUNDED";default -> "RELEASED";};
        OrderSql.require(OrderSql.update(c,"UPDATE tblShopOrderLineState SET lineState=? "
                + "WHERE orderItemId=? AND lineState=?",target,line.id,expected)==1,"ORDER_STATE_CHANGED");
        String sql=switch(kind){
            case "PAY" -> "UPDATE tblProductSku SET stockQuantity=stockQuantity-?,reservedQuantity=reservedQuantity-?,"
                    + "rowVersion=rowVersion+1 WHERE skuId=? AND reservedQuantity>=? AND stockQuantity>=?";
            case "REFUND" -> "UPDATE tblProductSku SET stockQuantity=stockQuantity+?,rowVersion=rowVersion+1 WHERE skuId=?";
            default -> "UPDATE tblProductSku SET reservedQuantity=reservedQuantity-?,rowVersion=rowVersion+1 "
                    + "WHERE skuId=? AND reservedQuantity>=?";};
        int changed=switch(kind){case "PAY" -> OrderSql.update(c,sql,line.quantity,line.quantity,line.sku,line.quantity,line.quantity);
            case "REFUND" -> OrderSql.update(c,sql,line.quantity,line.sku);
            default -> OrderSql.update(c,sql,line.quantity,line.sku,line.quantity);};
        OrderSql.require(changed==1,"ORDER_INVENTORY_CONFLICT");
        if(kind.equals("PAY") || kind.equals("REFUND")) {
            int delta=kind.equals("PAY")?line.quantity:-line.quantity;
            OrderSql.require(OrderSql.update(c,"UPDATE tblProduct SET salesCount=salesCount+?,rowVersion=rowVersion+1 "
                    + "WHERE productId=? AND salesCount+?>=0",delta,line.product,delta)==1,"ORDER_INVENTORY_CONFLICT");
        }
        movement(c,line.id,line.sku,kind,line.quantity,now);
    }
    private void movement(Connection c,String line,String sku,String kind,int qty,Instant now) throws SQLException {
        OrderSql.update(c,"INSERT INTO tblShopInventoryMovement "
                + "(movementId,orderItemId,skuId,movementKind,quantity,createdAt) VALUES (?,?,?,?,?,?)",
                OrderSql.id(),line,sku,kind,qty,now);
    }
}
