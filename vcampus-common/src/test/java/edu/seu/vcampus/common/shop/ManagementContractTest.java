package edu.seu.vcampus.common.shop;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementContractTest {
    @Test
    void managementQueriesAndAdminCommandsRoundTrip() throws Exception {
        ProductManagementQuery query = new ProductManagementQuery("shop-1", ProductStatus.ACTIVE,
                "笔", 2, 20);
        CreateProductCommand create = new CreateProductCommand("签字笔", "错误类别", "说明",
                "https://img.example/pen.png", List.of(
                new CreateSkuCommand("黑色", new BigDecimal("3.00"), 10, true)));
        UpdateProductCommand update = new UpdateProductCommand("product-1", "签字笔", "错误类别",
                "说明", "https://img.example/pen.png", List.of(
                new UpsertSkuCommand("sku-1", "黑色", new BigDecimal("3.00"), 10, true, 4)), 5);

        assertThat(roundTrip(query)).isEqualTo(query);
        assertThat(roundTrip(new AdminCreateProductCommand("shop-1", create)))
                .isEqualTo(new AdminCreateProductCommand("shop-1", create));
        assertThat(roundTrip(new AdminUpdateProductCommand("shop-1", update)))
                .isEqualTo(new AdminUpdateProductCommand("shop-1", update));
        assertThat(roundTrip(new AdminChangeProductStatusCommand("shop-1",
                new ChangeProductStatusCommand("product-1", ProductStatus.INACTIVE, 5))))
                .isEqualTo(new AdminChangeProductStatusCommand("shop-1",
                        new ChangeProductStatusCommand("product-1", ProductStatus.INACTIVE, 5)));
    }

    @Test
    void sellerOrderHistoryDefensivelyCopiesItemsAndOrders() throws Exception {
        List<SellerOrderItemView> sourceItems = new ArrayList<>(List.of(
                item("product-1", "sku-1"), item("product-2", "sku-2")));
        SellerOrderView order = new SellerOrderView("order-1", "O0001", "buyer-1", "shop-1",
                "文具店", new BigDecimal("6.00"), Instant.EPOCH, OrderStatus.PAID, sourceItems);
        List<SellerOrderView> sourceOrders = new ArrayList<>(List.of(order, new SellerOrderView(
                "order-2", "O0002", "buyer-2", "shop-1", "文具店",
                new BigDecimal("6.00"), Instant.EPOCH.plusSeconds(1), OrderStatus.PAID,
                List.of(item("product-3", "sku-3")))));
        SellerOrderHistory history = new SellerOrderHistory(sourceOrders);

        sourceItems.clear();
        sourceOrders.clear();

        assertThat(order.items()).hasSize(2);
        assertThat(history.orders()).hasSize(2);
        assertThat(roundTrip(history)).isEqualTo(history);
    }

    @Test
    void productManagementSummaryUsesExactAggregateTypes() throws Exception {
        ProductManagementSummary summary = new ProductManagementSummary("product-1", "签字笔",
                ProductStatus.ACTIVE, 2, new BigDecimal("2.50"), 30L, 4L, 99L, 7L);
        assertThat(roundTrip(summary)).isEqualTo(summary);
        assertThat(new SellerOrderQuery(OrderStatus.PAID, 0, 50).pageSize()).isEqualTo(50);
    }

    private static SellerOrderItemView item(String productId, String skuId) {
        return new SellerOrderItemView(productId, "商品", skuId, "规格", 2,
                new BigDecimal("3.00"), new BigDecimal("6.00"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(value); }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) { return (T) input.readObject(); }
    }
}
