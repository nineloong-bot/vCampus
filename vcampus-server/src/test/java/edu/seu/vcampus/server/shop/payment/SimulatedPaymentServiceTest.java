package edu.seu.vcampus.server.shop.payment;

import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.server.shop.ShopException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentServiceTest extends PaymentServiceTestSupport {
    @Test
    void failedAttemptCanRetryAndSuccessConsumesReservationAndCountsSalesOnce() {
        var checkout = seedCheckout(2);

        var failed = payments.simulatePayment("buyer-token", new SimulatePaymentCommand(
                checkout.paymentId(), PaymentChannel.ALIPAY, PaymentAttemptStatus.FAILED));
        assertThat(failed.status()).isEqualTo(PaymentStatus.PENDING);

        var succeeded = payments.simulatePayment("buyer-token", new SimulatePaymentCommand(
                checkout.paymentId(), PaymentChannel.WECHAT, PaymentAttemptStatus.SUCCEEDED));

        assertThat(succeeded.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(succeeded.successfulChannel()).isEqualTo(PaymentChannel.WECHAT);
        assertThat(scalarLong("SELECT stockQuantity FROM tblProductSku WHERE skuId = 'sku-1'"))
                .isEqualTo(8);
        assertThat(scalarLong("SELECT reservedQuantity FROM tblProductSku WHERE skuId = 'sku-1'"))
                .isZero();
        assertThat(scalarLong("SELECT salesCount FROM tblProduct WHERE productId = 'product-1'"))
                .isEqualTo(2);
        assertThat(scalarLong("SELECT COUNT(*) FROM tblPaymentAttempt")).isEqualTo(2);
        assertThat(scalarString("SELECT groupStatus FROM tblOrderGroup")).isEqualTo("PAID");
        assertThat(scalarString("SELECT orderStatus FROM tblOrder")).isEqualTo("PAID");

        assertThatThrownBy(() -> payments.simulatePayment("buyer-token",
                new SimulatePaymentCommand(checkout.paymentId(), PaymentChannel.WECHAT,
                        PaymentAttemptStatus.SUCCEEDED)))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.PAYMENT_ALREADY_COMPLETED));
        assertThat(scalarLong("SELECT salesCount FROM tblProduct WHERE productId = 'product-1'"))
                .isEqualTo(2);
    }

    @Test
    void amountMismatchDoesNotMutateInventoryOrReservation() {
        var checkout = seedCheckout(2);
        transactions.inTransaction(connection -> {
            connection.createStatement().executeUpdate(
                    "UPDATE tblOrderGroup SET totalAmount = totalAmount + 1");
            return null;
        });

        assertThatThrownBy(() -> payments.simulatePayment("buyer-token",
                new SimulatePaymentCommand(checkout.paymentId(), PaymentChannel.BANK_CARD,
                        PaymentAttemptStatus.SUCCEEDED)))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.PAYMENT_AMOUNT_MISMATCH));
        assertThat(scalarLong("SELECT stockQuantity FROM tblProductSku")).isEqualTo(10);
        assertThat(scalarLong("SELECT reservedQuantity FROM tblProductSku")).isEqualTo(2);
        assertThat(scalarLong("SELECT COUNT(*) FROM tblPaymentAttempt")).isZero();
    }

    @Test
    void explicitCancellationReleasesReservationWithoutReducingStock() {
        var checkout = seedCheckout(2);

        var cancelled = payments.simulatePayment("buyer-token", new SimulatePaymentCommand(
                checkout.paymentId(), PaymentChannel.ALIPAY, PaymentAttemptStatus.CANCELLED));

        assertThat(cancelled.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(scalarLong("SELECT stockQuantity FROM tblProductSku")).isEqualTo(10);
        assertThat(scalarLong("SELECT reservedQuantity FROM tblProductSku")).isZero();
        assertThat(scalarString("SELECT groupStatus FROM tblOrderGroup")).isEqualTo("CANCELLED");
        assertThat(scalarString("SELECT orderStatus FROM tblOrder")).isEqualTo("CANCELLED");
    }
}
