package edu.seu.vcampus.common.shop;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaidOrderHistoryTest {
    private static final Instant PAID_AT = Instant.parse("2026-08-30T08:00:00Z");

    @Test
    void roundTripsPaidOrderHistoryThroughJavaSerialization() throws Exception {
        PaidOrderHistory source = history();

        byte[] bytes;
        try (var buffer = new ByteArrayOutputStream();
             var output = new ObjectOutputStream(buffer)) {
            output.writeObject(source);
            bytes = buffer.toByteArray();
        }

        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            assertThat(input.readObject()).isEqualTo(source);
        }
    }

    @Test
    void defensivelyCopiesOrderAndItemLists() {
        PaidOrderItemView item = item();
        List<PaidOrderItemView> mutableItems = new ArrayList<>(List.of(item));
        PaidOrderView order = order(mutableItems);
        List<PaidOrderView> mutableOrders = new ArrayList<>(List.of(order));
        PaidOrderHistory history = new PaidOrderHistory(mutableOrders);

        mutableItems.clear();
        mutableOrders.clear();

        assertThat(order.items()).containsExactly(item);
        assertThat(history.orders()).containsExactly(order);
        assertThatThrownBy(() -> order.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> history.orders().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullRequiredValuesAndNullListElements() {
        assertThatThrownBy(() -> new PaidOrderItemView(null, "签字笔", "sku-1", "黑色",
                2, money("2.50"), money("5.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", null, "sku-1", "黑色",
                2, money("2.50"), money("5.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", null, "黑色",
                2, money("2.50"), money("5.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", null,
                2, money("2.50"), money("5.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                2, null, money("5.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                2, money("2.50"), null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new PaidOrderView(null, "O-1", "shop-1", "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", null, "shop-1", "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", null, "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", null,
                money("5.00"), PAID_AT, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                null, PAID_AT, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                money("5.00"), null, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                money("5.00"), PAID_AT, null, List.of(item())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PAID, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> order(Arrays.asList(item(), null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderHistory(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaidOrderHistory(Arrays.asList(order(List.of(item())), null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidQuantitiesAmountsAndNonPaidStatus() {
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                0, money("2.50"), money("0.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                -1, money("2.50"), money("-2.50"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                1, money("-0.01"), money("0.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                1, money("0.00"), money("-0.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                money("-0.01"), PAID_AT, OrderStatus.PAID, List.of(item())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PENDING_PAYMENT, List.of(item())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLineAmountsThatDoNotEqualUnitPriceTimesQuantity() {
        assertThatThrownBy(() -> new PaidOrderItemView(
                "product-1", "签字笔", "sku-1", "黑色",
                2, money("2.50"), money("4.99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineAmount");
    }

    @Test
    void rejectsOrderTotalsThatDoNotEqualTheSumOfItemAmounts() {
        PaidOrderItemView first = new PaidOrderItemView(
                "product-1", "签字笔", "sku-1", "黑色",
                2, money("2.50"), money("5.00"));
        PaidOrderItemView second = new PaidOrderItemView(
                "product-2", "练习本", "sku-2", "A5",
                1, money("2.00"), money("2.00"));

        assertThatThrownBy(() -> new PaidOrderView(
                "order-1", "O-1", "shop-1", "校园文具店",
                money("6.99"), PAID_AT, OrderStatus.PAID,
                List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalAmount");
    }

    @Test
    void acceptsMathematicallyEqualAmountsWithDifferentDecimalScales() {
        PaidOrderItemView item = new PaidOrderItemView(
                "product-1", "签字笔", "sku-1", "黑色",
                2, money("2.5"), money("5.000"));

        PaidOrderView order = new PaidOrderView(
                "order-1", "O-1", "shop-1", "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PAID, List.of(item));

        assertThat(order.items()).containsExactly(item);
    }

    private static PaidOrderHistory history() {
        return new PaidOrderHistory(List.of(order(List.of(item()))));
    }

    private static PaidOrderView order(List<PaidOrderItemView> items) {
        return new PaidOrderView("order-1", "O-1", "shop-1", "校园文具店",
                money("5.00"), PAID_AT, OrderStatus.PAID, items);
    }

    private static PaidOrderItemView item() {
        return new PaidOrderItemView("product-1", "签字笔", "sku-1", "黑色",
                2, money("2.50"), money("5.00"));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
