package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.server.wallet.service.WalletPostingService;
import java.util.List;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderBoundaryTest {
    @Test void lastUnitCannotBeReservedByTwoConcurrentBuyers() throws Exception {
        try(var f=new OrderFixture();var pool=Executors.newFixedThreadPool(2)) {
            f.sql("UPDATE tblProductSku SET stockQuantity=1 WHERE skuId='k1'");
            var gate=new java.util.concurrent.CountDownLatch(1);
            var attempts=List.of("student-1","other-1").stream().map(buyer->pool.submit(()->{
                gate.await();
                try {f.orders.checkout(buyer,"create",f.request("k1"));return true;}
                catch(OrderException error){return false;}
            })).toList();
            gate.countDown();int winners=0;
            for(var attempt:attempts)if(attempt.get())winners++;
            assertEquals(1,winners);
            assertEquals(1,f.number("SELECT reservedQuantity FROM tblProductSku WHERE skuId='k1'"));
        }
    }
    @Test void allInvalidStaysCancelledAfterRelistingAndSchemaRestartIsSafe() throws Exception {
        try(var f=new OrderFixture()) {
            var created=f.orders.checkout("student-1","create",f.request("k1"));
            f.sql("UPDATE tblProduct SET productStatus='INACTIVE' WHERE productId='p1'");
            assertEquals("CANCELLED",f.orders.validate("student-1",f.action(created)).orders().getFirst().state());
            f.sql("UPDATE tblProduct SET productStatus='ACTIVE' WHERE productId='p1'");
            assertEquals("CANCELLED",f.orders.validate("student-1",f.action(created)).orders().getFirst().state());
            var migration=new OrderSchemaInitializer(Path.of("../vcampus-database/schema/053_shop_orders.sql"));
            migration.initialize(f.database.connections());migration.initialize(f.database.connections());
            assertEquals(1,f.number("SELECT COUNT(*) FROM tblShopInventoryMovement WHERE movementKind='INVALID'"));
        }
    }
    @Test void startupRecoveryBeatsPaymentAtExactDeadline() throws Exception {
        try(var f=new OrderFixture();var pool=Executors.newFixedThreadPool(2)) {
            f.wallet.recharge("student-1","fund",new BigDecimal("100"));
            var created=f.orders.checkout("student-1","create",f.request("k1"));
            f.clock.now=f.clock.now.plusSeconds(1800);
            var payment=pool.submit(()->{
                try{return f.orders.act("student-1","pay","PAY",f.action(created));}
                catch(OrderException error){return null;}
            });
            var recovery=pool.submit(()->{
                try(var job=new OrderExpiryJob(new OrderService(f.transactions,new WalletPostingService(f.clock),f.clock,(c,p)->true))) {
                    return true;
                }
            });
            payment.get();assertTrue(recovery.get());
            assertEquals(10000,f.wallet.getBalance("student-1").balanceCents());
            assertEquals(0,f.number("SELECT reservedQuantity FROM tblProductSku WHERE skuId='k1'"));
            assertEquals("CANCELLED",f.orders.list("student-1",false,new OrderQuery("ALL",null)).orders().getFirst().state());
        }
    }
    @Test void selectedCartRowsOnlyAreRemovedAndStalePriceIsQuoted() throws Exception {
        try(var f=new OrderFixture()) {
            f.sql("INSERT INTO tblCart (cartId,userId,updatedAt) VALUES ('cart','student-1',NOW())");
            for(int i=1;i<=2;i++)f.sql("INSERT INTO tblCartItem (cartItemId,cartId,skuId,quantity,createdAt,updatedAt) "
                    + "VALUES ('i"+i+"','cart','k"+i+"',1,NOW(),NOW())");
            f.sql("UPDATE tblCartItem SET quantity=5 WHERE skuId='k1'");
            f.sql("UPDATE tblProductSku SET stockQuantity=1 WHERE skuId='k1'");
            var request=new CheckoutRequest(List.of(new OrderLine("k1",5,new BigDecimal("9"))),true);
            assertThrows(OrderException.class,()->f.orders.checkout("student-1","old",request));
            var quote=f.orders.quote("student-1",request);
            assertFalse(quote.notice().isBlank());
            f.orders.checkout("student-1","create",new CheckoutRequest(quote.lines(),true));
            assertEquals(1,f.number("SELECT COUNT(*) FROM tblCartItem"));
            assertThrows(OrderException.class,()->f.orders.checkout("student-1","create",f.request("k2")));
        }
    }
    @Test void legacyPaidHistoryIsVisibleButCannotBePaidOrSettled() throws Exception {
        try(var f=new OrderFixture()) {
            f.sql("INSERT INTO tblOrderGroup (orderGroupId,buyerUserId,totalAmount,groupStatus,createdAt) "
                    + "VALUES ('legacy-group','student-1',10,'PAID',NOW())");
            f.sql("INSERT INTO tblOrder (orderId,orderGroupId,shopId,orderNumber,orderAmount,orderStatus,createdAt) "
                    + "VALUES ('legacy','legacy-group','s1','legacy',10,'PAID',NOW())");
            f.sql("INSERT INTO tblOrderItem (orderItemId,orderId,skuId,productNameSnapshot,skuNameSnapshot,shopNameSnapshot,"
                    + "unitPrice,quantity,lineAmount) VALUES ('legacy-item','legacy','k1','Old product','Old sku','Old shop',10,1,10)");
            var buyer=f.orders.list("student-1",false,new OrderQuery("ALL",null));
            assertEquals("LEGACY_PAID",buyer.orders().getFirst().state());
            assertEquals("Old shop",buyer.orders().getFirst().shopName());
            assertTrue(buyer.notice().contains("仅供查询"));
            assertEquals(1,f.orders.list("owner-1",true,new OrderQuery("PAID",null)).orders().size());
            assertThrows(OrderException.class,()->f.orders.act("student-1","pay","PAY",new OrderAction(List.of("legacy"),"")));
            assertThrows(OrderException.class,()->f.orders.act("student-1","settle","RECEIVE",new OrderAction(List.of("legacy"),"")));
            assertEquals(0,f.number("SELECT COUNT(*) FROM tblWalletEscrow"));
        }
    }
    @Test void forbiddenActorCannotMutateOrInspectAnotherOrder() throws Exception {
        try(var f=new OrderFixture()) {
            var created=f.orders.checkout("student-1","create",f.request("k1"));
            assertThrows(OrderException.class,()->f.orders.validate("other-1",f.action(created)));
            assertThrows(OrderException.class,()->f.orders.act("other-1","cancel","CANCEL",f.action(created)));
            assertTrue(f.orders.list("other-1",false,new OrderQuery("ALL",null)).orders().isEmpty());
            assertThrows(OrderException.class,()->f.orders.checkout("owner-1","self",f.request("k1")));
        }
    }
}
