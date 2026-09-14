package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

final class ProductWriter {
    private final CatalogStore store;
    private final ProductPolicy policy;
    ProductWriter(CatalogStore store, ProductPolicy policy) { this.store = store; this.policy = policy; }
    Product save(Connection c, String user, SaveProduct command, Instant now) throws SQLException {
        ProductRules.draft(command);
        String shop = store.ownerShop(c, user);
        boolean create = text(command.id()).isEmpty();
        String id = create ? UUID.randomUUID().toString() : command.id();
        Product old = create ? null : store.owned(c, user, id);
        String status = create ? "DRAFT" : old.status();
        String normalized = text(command.name()).toLowerCase(Locale.ROOT) + "#" + id;
        if (create) {
            update(c, "INSERT INTO tblProduct(productId,shopId,productName,normalizedProductName,category,description,"
                    + "productStatus,salesCount,rowVersion,createdAt,updatedAt) VALUES(?,?,?,?,?,?,?,0,0,?,?)",
                    id, shop, text(command.name()), normalized, text(command.category()), text(command.description()),
                    status, Timestamp.from(now), Timestamp.from(now));
        } else update(c, "UPDATE tblProduct SET productName=?,normalizedProductName=?,category=?,description=?,"
                + "updatedAt=?,rowVersion=rowVersion+1 WHERE productId=?", text(command.name()), normalized,
                text(command.category()), text(command.description()), Timestamp.from(now), id);
        var present = new HashSet<String>();
        for (var s : command.skus()) {
            present.add(s.id());
            String existingProduct = scalar(c, "SELECT productId FROM tblProductSku WHERE skuId=?", s.id());
            require(existingProduct == null || existingProduct.equals(id), "规格不属于该商品");
            String reservedText = scalar(c, "SELECT reservedQuantity FROM tblProductSku WHERE skuId=?", s.id());
            int reserved = reservedText == null ? 0 : Integer.parseInt(reservedText);
            int stock = s.totalStock() == null ? 0 : s.totalStock();
            require(stock >= reserved, "总库存不能小于已预占库存");
            BigDecimal price = s.price() == null ? BigDecimal.ZERO : s.price();
            if (existingProduct == null) update(c, "INSERT INTO tblProductSku(skuId,productId,skuName,unitPrice,"
                    + "stockQuantity,reservedQuantity,isActive,rowVersion) VALUES(?,?,?,?,?,0,?,0)",
                    s.id(), id, text(s.name()), price, stock, s.active());
            else update(c, "UPDATE tblProductSku SET skuName=?,unitPrice=?,stockQuantity=?,isActive=?,"
                    + "rowVersion=rowVersion+1 WHERE skuId=?", text(s.name()), price, stock, s.active(), s.id());
            if (scalar(c, "SELECT skuId FROM tblSkuDraftFields WHERE skuId=?", s.id()) == null)
                update(c, "INSERT INTO tblSkuDraftFields(skuId,priceMissing,stockMissing) VALUES(?,?,?)",
                        s.id(), s.price() == null, s.totalStock() == null);
            else update(c, "UPDATE tblSkuDraftFields SET priceMissing=?,stockMissing=? WHERE skuId=?",
                    s.price() == null, s.totalStock() == null, s.id());
        }
        if (old != null) for (var s : old.skus()) if (!present.contains(s.id())) {
            update(c, "UPDATE tblProductSku SET isActive=FALSE,rowVersion=rowVersion+1 WHERE skuId=?", s.id());
        }
        store.metadata(c, id, text(command.defaultSkuId()), text(command.imageId()), false);
        Product result = store.get(c, id);
        if (status.equals("ACTIVE")) publishCheck(c, result);
        return result;
    }
    void publishCheck(Connection c, Product p) throws SQLException {
        ProductRules.publish(p);
        require("ACTIVE".equals(scalar(c, "SELECT shopStatus FROM tblShop WHERE shopId=?", p.shopId())), "店铺暂停营业");
        require(policy.mayPublish(c, p.shopId(), p.category()), "类目经营资质未通过或已到期");
        require(policy.mayBuy(c, p.id()), "商品受到管理限制，请先完成复核");
    }
    Product action(Connection c, String user, ProductAction a, Instant now) throws SQLException {
        Product p = store.owned(c, user, a.productId());
        switch (text(a.action())) {
            case "PUBLISH" -> {
                update(c, "UPDATE tblProduct SET productStatus='ACTIVE',updatedAt=?,rowVersion=rowVersion+1 WHERE productId=?",
                        Timestamp.from(now), p.id());
                publishCheck(c, store.get(c, p.id()));
            }
            case "OFF" -> {
                update(c, "UPDATE tblProduct SET productStatus='INACTIVE',updatedAt=?,rowVersion=rowVersion+1 WHERE productId=?",
                        Timestamp.from(now), p.id());
                policy.manualOff(c, p.id());
                clearCart(c, p.id());
            }
            case "DELETE" -> {
                boolean expiredAdmission = "ACTIVE".equals(scalar(c, "SELECT shopStatus FROM tblShop WHERE shopId=?", p.shopId()))
                        && !policy.mayPublish(c, p.shopId(), p.category());
                require(!p.status().equals("ACTIVE") || expiredAdmission, "商品在售，请先下架后再删除");
                String unfinished = scalar(c, "SELECT i.orderItemId FROM (tblOrderItem i INNER JOIN tblOrder o "
                        + "ON i.orderId=o.orderId) INNER JOIN tblProductSku s ON i.skuId=s.skuId "
                        + "WHERE s.productId=? AND o.orderStatus IN ('PENDING_PAYMENT','PAID','PREPARING','SHIPPED')", p.id());
                require(unfinished == null, "该商品尚有未完成订单，无法删除，请等待订单全部完结。");
                store.metadata(c, p.id(), p.defaultSkuId(), p.imageId(), true);
                clearCart(c, p.id());
            }
            default -> throw new CatalogException("未知商品操作");
        }
        return store.get(c, p.id());
    }
    private void clearCart(Connection c, String id) throws SQLException {
        for (var sku : store.skus(c, id)) update(c, "DELETE FROM tblCartItem WHERE skuId=?", sku.id());
    }
}
