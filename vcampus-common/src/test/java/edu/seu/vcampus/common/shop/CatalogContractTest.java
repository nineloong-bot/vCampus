package edu.seu.vcampus.common.shop;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogContractTest {
    @Test
    void exposesExactlyFiveCategories() {
        assertThat(ShopCategories.ALL)
                .containsExactly("文具", "图书", "生活用品", "药品", "其他");
    }

    @Test
    void productSummaryCarriesCoverUrlThroughSerialization() throws Exception {
        ProductSummary value = new ProductSummary("p", "s", "店", "中性笔", "文具",
                "https://img.example/pen.png", new BigDecimal("2.80"), 9, Instant.EPOCH);

        assertThat(roundTrip(value)).isEqualTo(value);
    }

    @Test
    void legacyProductDtosDefaultCoverUrlToNull() {
        ProductSkuView sku = new ProductSkuView("sku", "黑色", new BigDecimal("2.80"),
                9, true, 0);

        assertThat(new ProductSummary("p", "s", "店", "中性笔", "文具",
                new BigDecimal("2.80"), 9, Instant.EPOCH).coverImageUrl()).isNull();
        assertThat(new ProductDetail("p", "中性笔", "文具", "简介", ProductStatus.ACTIVE,
                0, new ShopSummary("s", "店"), List.of(sku), Instant.EPOCH)
                .coverImageUrl()).isNull();
        assertThat(new ProductView("p", "中性笔", "文具", "简介", ProductStatus.ACTIVE,
                0, 0, List.of(sku)).coverImageUrl()).isNull();
        assertThat(new CreateProductCommand("中性笔", "文具", "简介", List.of(
                new CreateSkuCommand("黑色", new BigDecimal("2.80"), 9, true)))
                .coverImageUrl()).isNull();
        assertThat(new UpdateProductCommand("p", "中性笔", "文具", "简介", List.of(
                new UpsertSkuCommand("sku", "黑色", new BigDecimal("2.80"), 9, true, 0)), 0)
                .coverImageUrl()).isNull();
    }

    private static Object roundTrip(Object value) throws Exception {
        byte[] bytes;
        try (var buffer = new ByteArrayOutputStream();
             var output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
            bytes = buffer.toByteArray();
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }
}
