package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.wallet.service.WalletService;
import edu.seu.vcampus.server.wallet.service.WalletPostingService;
import edu.seu.vcampus.common.shop.order.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

final class OrderFixture implements AutoCloseable {
    final ShopTestDatabase database=new ShopTestDatabase();
    final TransactionManager transactions=new TransactionManager(database.connections());
    final MutableClock clock=new MutableClock();
    final WalletService wallet=new WalletService(transactions,new StripedResourceLockManager(),clock);
    final OrderService orders=new OrderService(transactions,new WalletPostingService(clock),clock,(c,p)->true);
    OrderFixture() throws Exception {
        try(var c=database.connections().open();var s=c.createStatement()) {
            for(String file:List.of("051_shop_wallet.sql","053_shop_orders.sql"))
                for(String sql:Files.readString(Path.of("../vcampus-database/schema/"+file)).replace("\uFEFF", "").split(";"))
                    if(!sql.isBlank())s.execute(sql);
            for(int i=1;i<=2;i++) {
                String owner=i==1?"owner-1":"teacher-1";
                s.execute("INSERT INTO tblShop (shopId,ownerUserId,shopName,normalizedShopName,description,category,contact,shopStatus,createdAt,updatedAt) "
                        + "VALUES ('s"+i+"','"+owner+"','Shop "+i+"','shop "+i+"','test','test','test','ACTIVE',NOW(),NOW())");
                s.execute("INSERT INTO tblProduct (productId,shopId,productName,normalizedProductName,category,description,productStatus,createdAt,updatedAt) "
                        + "VALUES ('p"+i+"','s"+i+"','Product "+i+"','product "+i+"','test','test','ACTIVE',NOW(),NOW())");
                s.execute("INSERT INTO tblProductSku (skuId,productId,skuName,unitPrice,stockQuantity) "
                        + "VALUES ('k"+i+"','p"+i+"','Standard',10.00,5)");
            }
        }
    }
    CheckoutRequest request(String... skus) {
        return new CheckoutRequest(java.util.Arrays.stream(skus).map(s->new OrderLine(s,1,new BigDecimal("10.00"))).toList(),false);
    }
    OrderAction action(OrderResult result) {return new OrderAction(result.orders().stream().map(OrderView::orderId).toList(),"");}
    long number(String sql) throws Exception {
        try(var c=database.connections().open();var s=c.createStatement();var r=s.executeQuery(sql)) {r.next();return r.getLong(1);}
    }
    void sql(String sql) throws Exception {try(var c=database.connections().open();var s=c.createStatement()){s.execute(sql);}}
    @Override public void close() throws Exception {database.close();}
    static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-09-14T01:00:00Z");
        @Override public ZoneId getZone(){return java.time.ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
