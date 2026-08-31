package edu.seu.vcampus.common.shop;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Instant;

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
