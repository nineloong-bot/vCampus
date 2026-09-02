package edu.seu.vcampus.server.shop.payment;

import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentExpiryRaceTest extends PaymentServiceTestSupport {
    @Test
    void successAndExpiryRaceHasExactlyOneInventoryOutcome() throws Exception {
        var checkout = seedCheckout(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var success = executor.submit(() -> {
                start.await();
                try {
                    payments.simulatePayment("buyer-token", new SimulatePaymentCommand(
                            checkout.paymentId(), PaymentChannel.WECHAT,
                            PaymentAttemptStatus.SUCCEEDED));
                } catch (RuntimeException ignored) {
                    // Expiry may win the shared payment lock.
                }
                return null;
            });
            var expiration = executor.submit(() -> {
                start.await();
                expiry.expirePendingPayments();
                return null;
            });
            start.countDown();
            success.get();
            expiration.get();
        }

        String status = scalarString("SELECT paymentStatus FROM tblPayment");
        assertThat(status).isIn("SUCCEEDED", "EXPIRED");
        assertThat(scalarLong("SELECT reservedQuantity FROM tblProductSku")).isZero();
        long stock = scalarLong("SELECT stockQuantity FROM tblProductSku");
        long sales = scalarLong("SELECT salesCount FROM tblProduct");
        if ("SUCCEEDED".equals(status)) {
            assertThat(stock).isEqualTo(8);
            assertThat(sales).isEqualTo(2);
        } else {
            assertThat(stock).isEqualTo(10);
            assertThat(sales).isZero();
        }
    }
}
