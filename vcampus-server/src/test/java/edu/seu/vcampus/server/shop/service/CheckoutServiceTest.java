package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.server.shop.ShopException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutServiceTest extends CheckoutServiceTestSupport {
    @Test
    void crossShopCheckoutCreatesOneGroupTwoOrdersSnapshotsAndReservations() {
        seedShop("shop-1", "owner-1", "文具店");
        seedShop("shop-2", "stranger-1", "书店");
        seedProductAndSku("product-1", "签字笔", "shop-1", "sku-1", "黑色", "2.50", 10);
        seedProductAndSku("product-2", "教材", "shop-2", "sku-2", "新版", "10.00", 5);
        carts.addToCart("buyer-token", new AddCartItemCommand("sku-1", 1));
        carts.addToCart("buyer-token", new AddCartItemCommand("sku-2", 1));

        var result = checkout.checkout("buyer-token", checkoutCommand("buyer-token", false));

        assertThat(result.orders()).hasSize(2);
        assertThat(result.totalAmount()).isEqualByComparingTo("12.50");
        assertThat(scalarLong("SELECT COUNT(*) FROM tblOrderGroup")).isEqualTo(1);
        assertThat(scalarLong("SELECT COUNT(*) FROM tblOrder")).isEqualTo(2);
        assertThat(scalarLong("SELECT COUNT(*) FROM tblOrderItem")).isEqualTo(2);
        assertThat(scalarLong("SELECT COUNT(*) FROM tblInventoryReservation")).isEqualTo(2);
        assertThat(scalarMoney("SELECT SUM(orderAmount) FROM tblOrder"))
                .isEqualByComparingTo(result.totalAmount());
        assertThat(scalarMoney("SELECT SUM(lineAmount) FROM tblOrderItem"))
                .isEqualByComparingTo(result.totalAmount());
        assertThat(scalarLong("SELECT SUM(reservedQuantity) FROM tblProductSku")).isEqualTo(2);
    }

    @Test
    void changedPriceWithoutAcceptanceCreatesNoOrderRows() {
        seedShop("shop-1", "owner-1", "文具店");
        seedProductAndSku("product-1", "签字笔", "shop-1", "sku-1", "黑色", "2.50", 10);
        carts.addToCart("buyer-token", new AddCartItemCommand("sku-1", 1));
        var command = checkoutCommand("buyer-token", false);
        transactions.inTransaction(connection -> {
            connection.createStatement().executeUpdate(
                    "UPDATE tblProductSku SET unitPrice = 3.00 WHERE skuId = 'sku-1'");
            return null;
        });

        assertThatThrownBy(() -> checkout.checkout("buyer-token", command))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_PRICE_CHANGED));
        assertThat(scalarLong("SELECT COUNT(*) FROM tblOrderGroup")).isZero();
        assertThat(scalarLong("SELECT SUM(reservedQuantity) FROM tblProductSku")).isZero();
    }

    @Test
    void administratorAndOwnerCannotCheckoutBuyerMutations() {
        seedShop("shop-1", "owner-1", "文具店");
        seedProductAndSku("product-1", "签字笔", "shop-1", "sku-1", "黑色", "2.50", 10);
        seedCartItem("owner-1", "owner-cart-item", "sku-1", 1);

        assertThatThrownBy(() -> checkout.checkout("admin-token", new edu.seu.vcampus.common.shop.CheckoutCommand(List.of(), false)))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_BUYER_FORBIDDEN));
        assertThatThrownBy(() -> checkout.checkout("owner-token",
                new edu.seu.vcampus.common.shop.CheckoutCommand(List.of(
                        new edu.seu.vcampus.common.shop.CheckoutItem("owner-cart-item", new BigDecimal("2.50"))), false)))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_SELF_PURCHASE_FORBIDDEN));
        assertThat(scalarLong("SELECT SUM(reservedQuantity) FROM tblProductSku")).isZero();
    }
}
