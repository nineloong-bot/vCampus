package edu.seu.vcampus.server.shop.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRecoveryTest extends PaymentServiceTestSupport {
    @Test
    void restartRecoveryExpiresPendingPaymentOnlyOnce() {
        seedCheckout(2);

        assertThat(expiry.expirePendingPayments()).isEqualTo(1);
        assertThat(expiry.expirePendingPayments()).isZero();

        assertThat(scalarString("SELECT paymentStatus FROM tblPayment")).isEqualTo("EXPIRED");
        assertThat(scalarLong("SELECT reservedQuantity FROM tblProductSku")).isZero();
        assertThat(scalarLong("SELECT stockQuantity FROM tblProductSku")).isEqualTo(10);
        assertThat(scalarLong("SELECT salesCount FROM tblProduct")).isZero();
        assertThat(scalarString("SELECT reservationStatus FROM tblInventoryReservation"))
                .isEqualTo("RELEASED");
    }
}
