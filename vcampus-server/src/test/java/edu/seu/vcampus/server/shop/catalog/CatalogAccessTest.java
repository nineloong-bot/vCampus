package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CatalogAccessTest {
    @TempDir Path directory;
    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:ucanaccess://" + directory.resolve("catalog.accdb") + ";newDatabaseVersion=V2010");
    }
    private CatalogService setup() throws Exception {
        try (var c = open(); var s = c.createStatement()) {
            s.execute("CREATE TABLE tblUser(userId VARCHAR(36) PRIMARY KEY)");
            for (String user : List.of("seller", "buyer", "other")) s.execute("INSERT INTO tblUser VALUES('" + user + "')");
            for (String name : List.of("050_shop.sql", "052_shop_catalog.sql"))
                for (String sql : Files.readString(Path.of("../vcampus-database/schema/" + name)).split(";"))
                    if (!sql.isBlank()) s.execute(sql);
            s.execute("INSERT INTO tblShop(shopId,ownerUserId,shopName,normalizedShopName,description,category,contact,"
                    + "shopStatus,createdAt,updatedAt) VALUES('shop','seller','校园店','shop','介绍','ordinary','',"
                    + "'ACTIVE',Now(),Now())");
        }
        var tx = new TransactionManager(() -> {
            try { return open(); } catch (java.sql.SQLException ex) { throw ex; }
            catch (Exception ex) { throw new java.sql.SQLException(ex); }
        });
        return new CatalogService(tx, new StripedResourceLockManager(), Clock.systemUTC(), new ProductPolicy() {
            public boolean mayPublish(Connection c, String shop, String category) { return category.equals("ordinary"); }
            public boolean mayBuy(Connection c, String product) { return true; }
        });
    }
    @Test void nameOnlyDraftPublicationAndStableInventory() throws Exception {
        var service = setup();
        var draft = service.save("seller", new SaveProduct("draft", "", "名称草稿", "", "", "", "", List.of()));
        assertEquals("DRAFT", draft.status());
        assertThrows(CatalogException.class, () -> service.action("seller", new ProductAction("publish", draft.id(), "PUBLISH")));
        assertThrows(CatalogException.class, () -> service.ownedDetail("other", draft.id()));
        var saved = service.save("seller", new SaveProduct("edit", draft.id(), "名称草稿", "介绍", "ordinary", "book", "sku",
                List.of(new Sku("sku", "标准款", new BigDecimal("12.50"), 0, 0, true))));
        assertEquals("sku", saved.defaultSkuId());
        service.action("seller", new ProductAction("publish2", draft.id(), "PUBLISH"));
        assertEquals(1, service.list(new Query("", "", "DEFAULT", 1, 10)).total());
        assertThrows(CatalogException.class, () -> service.action("seller", new ProductAction("delete", draft.id(), "DELETE")));
        service.action("seller", new ProductAction("off", draft.id(), "OFF"));
        service.action("seller", new ProductAction("delete2", draft.id(), "DELETE"));
        assertThrows(CatalogException.class, () -> service.action("seller", new ProductAction("revive", draft.id(), "PUBLISH")));
        try (var c = open(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM tblProductSku")) {
            r.next(); assertEquals(1, r.getInt(1));
        }
    }
    @Test void importRevalidationDeduplicationAndCartDefaultMerge() throws Exception {
        var service = setup();
        var imports = new ImportService(service);
        var rows = List.of(new ImportRow(2, "A", "书", "介绍", "ordinary", "book", "标准", "2", "2", "是"),
                new ImportRow(3, "A", "书", "介绍", "ordinary", "book", "豪华", "4", "5", "否"),
                new ImportRow(4, "B", "错误", "介绍", "ordinary", "book", "标准", "-1", "1", "是"));
        assertEquals(1, imports.preview("seller", new ImportCommand("preview", rows)).validGroups());
        assertEquals(0, service.ownedList("seller", new Query("", "", "DEFAULT", 1, 10)).total());
        var command = new ImportCommand("import", rows);
        var imported = imports.confirm("seller", command);
        assertEquals(imported, imports.confirm("seller", command));
        assertEquals(1, imported.productIds().size());
        assertThrows(CatalogException.class, () -> imports.confirm("seller", new ImportCommand("import", rows.subList(0, 1))));
        String id = imported.productIds().getFirst();
        service.action("seller", new ProductAction("publish", id, "PUBLISH"));
        var cart = new CartService(service);
        var added = cart.bulk("buyer", new BulkAdd("add", List.of(id)));
        assertEquals(1, added.lines().getFirst().quantity());
        var line = added.lines().getFirst();
        var capped = cart.change("buyer", new CartChange("cap", line.id(), line.skuId(), 20));
        assertEquals(2, capped.lines().getFirst().quantity());
        assertFalse(capped.notices().isEmpty());
        String otherSku = line.product().skus().stream().filter(s -> !s.id().equals(line.skuId())).findFirst().orElseThrow().id();
        cart.change("buyer", new CartChange("other", "", otherSku, 2));
        var merged = cart.change("buyer", new CartChange("merge", capped.lines().getFirst().id(), otherSku, 4));
        assertEquals(1, merged.lines().size());
        assertEquals(5, merged.lines().getFirst().quantity());
        service.action("seller", new ProductAction("off", id, "OFF"));
        assertTrue(cart.get("buyer").lines().isEmpty());
    }
    @Test void stableSkuEditsCannotReduceTotalBelowReservations() throws Exception {
        var service=setup();
        var p=service.save("seller",new SaveProduct("create","","库存书","介绍","ordinary","book","stock-sku",
                List.of(new Sku("stock-sku","标准",new BigDecimal("3.00"),8,0,true))));
        try(var c=open();var s=c.createStatement()){
            s.executeUpdate("UPDATE tblProductSku SET reservedQuantity=5 WHERE skuId='stock-sku'");
        }
        assertThrows(CatalogException.class,()->service.save("seller",new SaveProduct("reduce",p.id(),"库存书","介绍","ordinary","book","stock-sku",
                List.of(new Sku("stock-sku","标准",new BigDecimal("3.00"),4,0,true)))));
        assertEquals(8,service.ownedDetail("seller",p.id()).skus().getFirst().totalStock());
        var changed=service.save("seller",new SaveProduct("safe",p.id(),"库存书","介绍","ordinary","book","stock-sku",
                List.of(new Sku("stock-sku","改名",new BigDecimal("4.00"),5,0,true))));
        assertEquals("stock-sku",changed.skus().getFirst().id());
        assertEquals(5,changed.skus().getFirst().reservedStock());
        assertEquals(new BigDecimal("4.00"),changed.skus().getFirst().price());
    }
    @Test void unfinishedOrderBlocksDeletionButCompletedHistoryAndSkuSurvive() throws Exception {
        var service=setup();
        var p=service.save("seller",new SaveProduct("create","","历史商品","介绍","ordinary","book","history-sku",
                List.of(new Sku("history-sku","标准",new BigDecimal("3.00"),8,0,true))));
        try(var c=open();var s=c.createStatement()){
            s.executeUpdate("INSERT INTO tblOrderGroup(orderGroupId,buyerUserId,totalAmount,groupStatus,createdAt) "
                    + "VALUES('group','buyer',3,'PENDING_PAYMENT',Now())");
            s.executeUpdate("INSERT INTO tblOrder(orderId,orderGroupId,shopId,orderNumber,orderAmount,orderStatus,createdAt) "
                    + "VALUES('order','group','shop','TEST-ORDER',3,'PENDING_PAYMENT',Now())");
            s.executeUpdate("INSERT INTO tblOrderItem(orderItemId,orderId,skuId,productNameSnapshot,skuNameSnapshot,shopNameSnapshot,unitPrice,quantity,lineAmount) "
                    + "VALUES('line','order','history-sku','历史商品','标准','校园店',3,1,3)");
        }
        assertThrows(CatalogException.class,()->service.action("seller",new ProductAction("delete-pending",p.id(),"DELETE")));
        try(var c=open();var s=c.createStatement()){
            s.executeUpdate("UPDATE tblOrder SET orderStatus='PAID' WHERE orderId='order'");
        }
        assertThrows(CatalogException.class,()->service.action("seller",new ProductAction("delete-paid",p.id(),"DELETE")));
        try(var c=open();var s=c.createStatement()){
            s.executeUpdate("UPDATE tblOrder SET orderStatus='COMPLETED' WHERE orderId='order'");
        }
        assertTrue(service.action("seller",new ProductAction("delete-complete",p.id(),"DELETE")).deleted());
        try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM tblOrderItem WHERE skuId='history-sku'")){
            r.next();assertEquals(1,r.getInt(1));
        }
        assertEquals(1,service.adminDetail(p.id()).skus().size());
    }
    @Test void importedOrdinaryDraftRequiresImageAndDefaultOutOfStockIsSkipped() throws Exception {
        var service=setup();var imports=new ImportService(service);
        var rows=List.of(new ImportRow(2,"A","普通商品","介绍","普通白名单商品","","缺货默认","1.00","0","是"),
                new ImportRow(3,"A","普通商品","介绍","普通白名单商品","","有货其他","2.00","8","否"));
        var result=imports.confirm("seller",new ImportCommand("import",rows));
        String id=result.productIds().getFirst();
        assertEquals("ordinary",service.ownedDetail("seller",id).category());
        assertThrows(CatalogException.class,()->service.action("seller",new ProductAction("missing-image",id,"PUBLISH")));
        service.images("seller",new Images("images",List.of(id),"book"));
        service.action("seller",new ProductAction("publish",id,"PUBLISH"));
        var cart=new CartService(service).bulk("buyer",new BulkAdd("bulk",List.of(id)));
        assertTrue(cart.lines().isEmpty());assertTrue(cart.notices().stream().anyMatch(n->n.contains("缺货")));
        assertEquals(1,service.list(new Query("普通",null,"PRICE_ASC",1,10)).total());
        var special=List.of(new ImportRow(5,"B","许可商品","介绍","licensed","book","标准","2","1","是"));
        assertEquals(0,imports.preview("seller",new ImportCommand("preview-special",special)).validGroups());
    }
}
