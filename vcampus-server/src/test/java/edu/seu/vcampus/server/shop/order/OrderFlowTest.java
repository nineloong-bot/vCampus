package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.server.wallet.service.WalletException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderFlowTest {
    @Test void multiShopPaymentIsAtomicAndReceiptSurvivesCancellation() throws Exception {
        try(var f=new OrderFixture()) {
            var created=f.orders.checkout("student-1","create",f.request("k1","k2"));
            f.wallet.recharge("student-1","fund",new BigDecimal("15"));
            assertThrows(WalletException.class,()->f.orders.act("student-1","pay","PAY",f.action(created)));
            assertEquals(1500,f.wallet.getBalance("student-1").balanceCents());
            assertEquals(2,f.number("SELECT COUNT(*) FROM tblShopOrderState WHERE lifecycle='PENDING_PAYMENT'"));
            assertEquals(0,f.number("SELECT COUNT(*) FROM tblWalletEscrow"));
            f.orders.act("student-1","cancel","CANCEL",f.action(created));
            assertEquals(created,f.orders.checkout("student-1","create",f.request("k1","k2")));
            assertEquals(0,f.number("SELECT SUM(reservedQuantity) FROM tblProductSku"));
        }
    }
    @Test void refundsRestoreInventoryAndBlockShipmentUntilReviewed() throws Exception {
        try(var f=new OrderFixture()) {
            f.wallet.recharge("student-1","fund",new BigDecimal("100"));
            var created=f.orders.checkout("student-1","create",f.request("k1"));
            var action=f.action(created);
            f.orders.act("student-1","pay","PAY",action);
            f.orders.act("student-1","ask","REFUND_REQUEST",action);
            assertThrows(OrderException.class,()->f.orders.act("owner-1","ship","SHIP",action));
            var reject=new OrderAction(action.orderIds(),"理由");
            f.orders.act("owner-1","reject","REFUND_REJECT",reject);
            assertEquals("理由",f.orders.list("student-1",false,new OrderQuery("ALL",null)).orders().getFirst().refundReason());
            f.orders.act("student-1","ask2","REFUND_REQUEST",action);
            var refunded=f.orders.act("owner-1","refund","REFUND_APPROVE",action);
            assertEquals(refunded,f.orders.act("owner-1","refund","REFUND_APPROVE",action));
            assertEquals(10000,f.wallet.getBalance("student-1").balanceCents());
            assertEquals(5,f.number("SELECT stockQuantity FROM tblProductSku WHERE skuId='k1'"));
            assertEquals(0,f.number("SELECT salesCount FROM tblProduct WHERE productId='p1'"));
            assertEquals(2,f.number("SELECT COUNT(*) FROM tblShopOrderEvent WHERE actionName='REFUND_REQUEST'"));
            assertEquals(1,f.number("SELECT COUNT(*) FROM tblShopOrderEvent WHERE actionName='REFUND_REJECT'"));
        }
    }
    @Test void receiptSettlesOnceAndSellerProjectionNeverIncludesBuyerIdentity() throws Exception {
        try(var f=new OrderFixture()) {
            f.wallet.recharge("student-1","fund",new BigDecimal("100"));
            var created=f.orders.checkout("student-1","create",f.request("k1"));
            var action=f.action(created);
            f.orders.act("student-1","pay","PAY",action);
            f.orders.act("owner-1","ship","SHIP",action);
            assertThrows(OrderException.class,()->f.orders.act("student-1","refund","REFUND_REQUEST",action));
            var done=f.orders.act("student-1","receive","RECEIVE",action);
            assertEquals(done,f.orders.act("student-1","receive","RECEIVE",action));
            assertEquals(1000,f.wallet.getBalance("owner-1").balanceCents());
            assertFalse(f.orders.list("owner-1",true,new OrderQuery("ALL",null)).toString().contains("student-1"));
        }
    }
    @Test void lazyInvalidationRetainsPriceAndRequiresNewPaymentConfirmation() throws Exception {
        try(var f=new OrderFixture()) {
            f.sql("UPDATE tblProduct SET shopId='s1' WHERE productId='p2'");
            f.wallet.recharge("student-1","fund",new BigDecimal("100"));
            var created=f.orders.checkout("student-1","create",f.request("k1","k2"));
            f.sql("UPDATE tblProduct SET productStatus='INACTIVE' WHERE productId='p1'");
            f.sql("UPDATE tblProductSku SET unitPrice=99 WHERE skuId='k2'");
            var adjusted=f.orders.act("student-1","pay","PAY",f.action(created));
            assertFalse(adjusted.notice().isBlank());
            assertEquals(new BigDecimal("10.00"),adjusted.orders().getFirst().amount());
            assertEquals(10000,f.wallet.getBalance("student-1").balanceCents());
            f.sql("UPDATE tblProduct SET productStatus='ACTIVE' WHERE productId='p1'");
            f.orders.act("student-1","confirm","PAY",f.action(created));
            assertEquals(9000,f.wallet.getBalance("student-1").balanceCents());
            assertEquals(5,f.number("SELECT stockQuantity FROM tblProductSku WHERE skuId='k1'"));
        }
    }
    @Test void timeoutRecoveryAndSuspensionReleaseExactlyOnce() throws Exception {
        try(var f=new OrderFixture()) {
            var created=f.orders.checkout("student-1","create",f.request("k1","k2"));
            f.transactions.inTransaction(c->{f.orders.cancelPendingForShop(new edu.seu.vcampus.server.persistence.TransactionContext(c),"s1");return null;});
            f.clock.now=f.clock.now.plusSeconds(1800);
            assertEquals(1,f.orders.expirePending());assertEquals(0,f.orders.expirePending());
            assertEquals(0,f.number("SELECT SUM(reservedQuantity) FROM tblProductSku"));
            assertThrows(OrderException.class,()->f.orders.act("student-1","pay","PAY",f.action(created)));
        }
    }
}
