package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentCheckoutTest extends CheckoutServiceTestSupport {
    @Test
    void fiveUnitsCannotBeOversoldByTwentyCheckouts() throws Exception {
        seedShop("shop-1", "owner-1", "文具店");
        seedProductAndSku("product-1", "限量本", "shop-1", "sku-1", "标准", "10.00", 5);
        List<String> tokens = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String token = "buyer-" + index + "-token";
            addUser("buyer-" + index, token);
            tokens.add(token);
            carts.addToCart(token, new AddCartItemCommand("sku-1", 1));
        }

        var start = new CountDownLatch(1);
        List<Boolean> outcomes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(20)) {
            var futures = tokens.stream().map(token -> executor.submit(() -> {
                start.await();
                try {
                    checkout.checkout(token, checkoutCommand(token, true));
                    return true;
                } catch (RuntimeException error) {
                    return false;
                }
            })).toList();
            start.countDown();
            for (var future : futures) {
                outcomes.add(future.get());
            }
        }

        assertThat(outcomes).filteredOn(Boolean::booleanValue).hasSize(5);
        assertThat(scalarLong("SELECT reservedQuantity FROM tblProductSku WHERE skuId = 'sku-1'"))
                .isEqualTo(5);
        assertThat(scalarLong("SELECT COUNT(*) FROM tblOrderGroup")).isEqualTo(5);
    }
}
