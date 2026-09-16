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

    @Test
    void restartRecoveryReconcilesReservationAlreadyReleasedByCanonicalOrder() {
        seedCheckout(2);
        transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE tblShopOrderState ("
                        + "orderId VARCHAR(36) PRIMARY KEY,lifecycle VARCHAR(24) NOT NULL,"
                        + "expiresAt DATETIME NOT NULL,refundReason VARCHAR(500))");
                statement.executeUpdate("INSERT INTO tblShopOrderState "
                        + "(orderId,lifecycle,expiresAt,refundReason) "
                        + "SELECT orderId,'CANCELLED',createdAt,'' FROM tblOrder");
                statement.executeUpdate("UPDATE tblProductSku SET reservedQuantity=0");
            }
            return null;
        });

        assertThat(expiry.expirePendingPayments()).isEqualTo(1);
        assertThat(scalarString("SELECT paymentStatus FROM tblPayment")).isEqualTo("EXPIRED");
        assertThat(scalarString("SELECT reservationStatus FROM tblInventoryReservation"))
                .isEqualTo("RELEASED");
    }
}
