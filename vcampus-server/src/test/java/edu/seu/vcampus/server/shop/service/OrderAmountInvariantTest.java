package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAmountInvariantTest extends CheckoutServiceTestSupport {
    @Test
    void groupAmountEqualsOrdersAndOrderItems() {
        seedShop("shop-1", "owner-1", "文具店");
        seedProductAndSku("product-1", "笔", "shop-1", "sku-1", "黑色", "1.25", 20);
        carts.addToCart("buyer-token", new AddCartItemCommand("sku-1", 4));
        checkout.checkout("buyer-token", checkoutCommand("buyer-token", false));

        var group = scalarMoney("SELECT totalAmount FROM tblOrderGroup");
        assertThat(scalarMoney("SELECT SUM(orderAmount) FROM tblOrder")).isEqualByComparingTo(group);
        assertThat(scalarMoney("SELECT SUM(lineAmount) FROM tblOrderItem")).isEqualByComparingTo(group);
    }
}
