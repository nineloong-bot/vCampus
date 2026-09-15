package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.OrderItem;
import edu.seu.vcampus.common.shop.order.OrderView;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class LegacyOrderReader {
    static final String NOTICE="历史版本订单仅供查询，未迁移资金不得重复支付或结算";
    List<OrderView> list(Connection c,String actor,boolean seller,String shop,String filter) throws SQLException {
        String sql="SELECT o.orderId FROM ((tblOrder o INNER JOIN tblOrderGroup g ON o.orderGroupId=g.orderGroupId) "
                + "INNER JOIN tblShop s ON o.shopId=s.shopId) LEFT JOIN tblShopOrderState x ON o.orderId=x.orderId "
                + "WHERE x.orderId IS NULL AND "+(seller?"s.ownerUserId":"g.buyerUserId")+"=?";
        var ids=shop==null || shop.isBlank()?OrderSql.list(c,sql,r->r.getString(1),actor)
                :OrderSql.list(c,sql+" AND o.shopId=?",r->r.getString(1),actor,shop);
        List<OrderView> result=new ArrayList<>();
        for(String id:ids) {
            var row=view(c,id);
            String state=row.state().substring("LEGACY_".length());
            if(filter.equals("ALL") || filter.equals(state) || filter.equals("CLOSED") && state.equals("CANCELLED"))
                result.add(row);
        }
        return result;
    }
    private OrderView view(Connection c,String id) throws SQLException {
        var items=OrderSql.list(c,"SELECT * FROM tblOrderItem WHERE orderId=? ORDER BY orderItemId",r->new OrderItem(
                r.getString("orderItemId"),r.getString("skuId"),r.getString("productNameSnapshot"),r.getString("skuNameSnapshot"),
                r.getBigDecimal("unitPrice"),r.getInt("quantity"),r.getBigDecimal("lineAmount"),true),id);
        var names=OrderSql.list(c,"SELECT shopNameSnapshot FROM tblOrderItem WHERE orderId=?",r->r.getString(1),id);
        return OrderSql.list(c,"SELECT * FROM tblOrder WHERE orderId=?",r->{
            String state=r.getString("orderStatus");
            if(state.equals("PREPARING"))state="PAID";
            var created=r.getTimestamp("createdAt").toInstant();
            return new OrderView(id,r.getString("orderGroupId"),r.getString("shopId"),names.isEmpty()?"":names.getFirst(),
                    "校园用户","LEGACY_"+state,r.getBigDecimal("orderAmount"),created,created.plusSeconds(1800),"",items);
        },id).getFirst();
    }
}
