package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationTest {
    @Test
    void cancellingAnOrderFromCartReleasesStockWithoutRecreatingCartItems() throws Exception {
        try (var f = new OrderFixture()) {
            f.sql("INSERT INTO tblCart (cartId,userId,updatedAt) VALUES ('cart','student-1',NOW())");
            f.sql("INSERT INTO tblCartItem (cartItemId,cartId,skuId,quantity,createdAt,updatedAt) "
                    + "VALUES ('item','cart','k1',1,NOW(),NOW())");
            var request = f.request("k1");
            var created = f.orders.checkout("student-1", "create",
                    new CheckoutRequest(request.lines(), true));
            assertThat(f.number("SELECT COUNT(*) FROM tblCartItem")).isZero();
            assertThat(f.number("SELECT reservedQuantity FROM tblProductSku WHERE skuId='k1'")).isEqualTo(1);
            var cancelled = f.orders.act("student-1", "cancel", "CANCEL", f.action(created));
            assertThat(cancelled.orders().getFirst().state()).isEqualTo("CANCELLED");
            assertThat(f.orders.act("student-1", "cancel", "CANCEL", f.action(created))).isEqualTo(cancelled);
            assertThat(f.number("SELECT COUNT(*) FROM tblCartItem")).isZero();
            assertThat(f.number("SELECT reservedQuantity FROM tblProductSku WHERE skuId='k1'")).isZero();
            assertThat(f.number("SELECT COUNT(*) FROM tblOrderItem")).isEqualTo(1);
            assertThat(f.number("SELECT COUNT(*) FROM tblWalletOperation")).isZero();
        }
    }

    @Test
    void concurrentCancellationAndPaymentCannotBothComplete() throws Exception {
        try (var f = new OrderFixture(); var pool = Executors.newFixedThreadPool(2)) {
            f.wallet.recharge("student-1", "fund", new BigDecimal("100"));
            var order = f.action(f.orders.checkout("student-1", "create", f.request("k1")));
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var cancel = pool.submit(() -> attempt(f, order, "CANCEL", ready, start));
            var pay = pool.submit(() -> attempt(f, order, "PAY", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            boolean cancelled = cancel.get(30, TimeUnit.SECONDS);
            boolean paid = pay.get(30, TimeUnit.SECONDS);
            assertThat(cancelled ^ paid).isTrue();
            var state = f.orders.list("student-1", false, new OrderQuery("ALL", null))
                    .orders().getFirst().state();
            assertThat(state).isEqualTo(cancelled ? "CANCELLED" : "PAID");
            assertThat(f.wallet.getBalance("student-1").balanceCents()).isEqualTo(cancelled ? 10000 : 9000);
            assertThat(f.wallet.getBalance("owner-1").pendingCents()).isEqualTo(cancelled ? 0 : 1000);
            assertThat(f.number("SELECT COUNT(*) FROM tblCartItem")).isZero();
            assertThat(f.number("SELECT reservedQuantity FROM tblProductSku WHERE skuId='k1'")).isZero();
        }
    }

    private boolean attempt(OrderFixture f, OrderAction order, String action,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
        try { f.orders.act("student-1", action, action, order); return true; }
        catch (OrderException expected) { return false; }
    }
}
