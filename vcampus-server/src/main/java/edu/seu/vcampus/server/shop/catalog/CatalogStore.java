package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Product;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Sku;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Shop;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

final class CatalogStore {
    Product get(Connection c, String id) throws SQLException {
        try (var s = prepare(c, "SELECT p.*,s.shopName,m.defaultSkuId,m.imageId,m.isDeleted "
                + "FROM (tblProduct p INNER JOIN tblShop s ON p.shopId=s.shopId) "
                + "LEFT JOIN tblProductCatalog m ON p.productId=m.productId WHERE p.productId=?", id);
             var r = s.executeQuery()) {
            require(r.next(), "商品不存在");
            var skus = skus(c, id);
            String defaultId = r.getString("defaultSkuId");
            if (defaultId == null && !skus.isEmpty()) defaultId = skus.getFirst().id();
            String image = r.getString("imageId");
            if (image == null) image = r.getString("coverImageUrl");
            return new Product(id, r.getString("shopId"), r.getString("shopName"),
                    r.getString("productName"), r.getString("description"), r.getString("category"),
                    image, r.getString("productStatus"), defaultId, r.getBoolean("isDeleted"),
                    r.getInt("salesCount"), skus);
        }
    }
    List<Sku> skus(Connection c, String product) throws SQLException {
        var result = new ArrayList<Sku>();
        try (var s = prepare(c, "SELECT s.*,d.priceMissing,d.stockMissing FROM tblProductSku s "
                + "LEFT JOIN tblSkuDraftFields d ON s.skuId=d.skuId WHERE s.productId=? ORDER BY s.skuId", product);
             var r = s.executeQuery()) {
            while (r.next()) result.add(new Sku(r.getString("skuId"), r.getString("skuName"),
                    r.getBoolean("priceMissing") ? null : r.getBigDecimal("unitPrice"),
                    r.getBoolean("stockMissing") ? null : r.getInt("stockQuantity"),
                    r.getInt("reservedQuantity"), r.getBoolean("isActive")));
        }
        return result;
    }
    List<String> ids(Connection c) throws SQLException {
        var result = new ArrayList<String>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT productId FROM tblProduct ORDER BY createdAt DESC,productId")) {
            while (r.next()) result.add(r.getString(1));
        }
        return result;
    }
    String ownerShop(Connection c, String owner) throws SQLException {
        String id = scalar(c, "SELECT shopId FROM tblShop WHERE ownerUserId=?", owner);
        require(id != null, "请先申请开店并通过审核");
        return id;
    }
    Product owned(Connection c, String owner, String id) throws SQLException {
        Product p = get(c, id);
        require(p.shopId().equals(ownerShop(c, owner)), "无权修改此商品");
        require(!p.deleted(), "商品已删除");
        return p;
    }
    List<Shop> shops(Connection c) throws SQLException {
        var result = new ArrayList<Shop>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT shopId,shopName,description,shopStatus FROM tblShop ORDER BY shopName")) {
            while (r.next()) result.add(new Shop(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
        }
        return result;
    }
    void metadata(Connection c, String id, String defaultSku, String image, boolean deleted) throws SQLException {
        if (scalar(c, "SELECT productId FROM tblProductCatalog WHERE productId=?", id) == null) {
            update(c, "INSERT INTO tblProductCatalog(productId,defaultSkuId,imageId,isDeleted) VALUES(?,?,?,?)",
                    id, defaultSku, image, deleted);
        } else update(c, "UPDATE tblProductCatalog SET defaultSkuId=?,imageId=?,isDeleted=? WHERE productId=?",
                defaultSku, image, deleted, id);
    }
}
