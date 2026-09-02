package edu.seu.vcampus.client.shop;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import java.time.Instant;

import java.math.BigDecimal;
import java.util.List;

public final class ShopClientFixtures {
    private ShopClientFixtures() { }

    public static CartView cartView() {
        CartItemView item = new CartItemView("cart-item-1", "product-1", "签字笔",
                "sku-1", "黑色", "shop-1", "校园文具店", new BigDecimal("3.00"), 2, 0);
        return new CartView("cart-1", List.of(item), new BigDecimal("6.00"));
    }

    public static ProductSummary productSummary() {
        return new ProductSummary("product-1", "shop-1", "校园文具店", "签字笔",
                "文具", new BigDecimal("3.00"), 4, Instant.parse("2026-08-29T00:00:00Z"));
    }

    public static <T extends java.io.Serializable> PageResult<T> page(T value) {
        return new PageResult<>(List.of(value), 0, 20, 1);
    }

    public static ProductDetail productDetail() {
        return new ProductDetail("product-1", "签字笔", "文具", "description",
                ProductStatus.ACTIVE, 4, new ShopSummary("shop-1", "校园文具店"), List.of(),
                Instant.parse("2026-08-29T00:00:00Z"));
    }

    public static ShopDetail shopDetail() {
        return new ShopDetail("shop-1", "校园文具店", "description", "文具", "contact",
                ShopStatus.ACTIVE);
    }

    public static CheckoutResult checkoutResult() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        OrderSummary order = new OrderSummary("order-1", "group-1", "O0001", "shop-1",
                "校园文具店", new BigDecimal("6.00"), OrderStatus.PENDING_PAYMENT, now);
        return new CheckoutResult("group-1", "payment-1", "P0001", new BigDecimal("6.00"),
                now.plusSeconds(900), List.of(order));
    }

    public static PaymentView paymentView() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        return new PaymentView("payment-1", "group-1", "P0001", new BigDecimal("6.00"),
                PaymentStatus.PENDING, null, now.plusSeconds(900), null, 0);
    }
}
