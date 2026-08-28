package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentCartUpdateTest extends CartServiceTestSupport {
    @Test
    void concurrentAddsPreserveEveryQuantityIncrement() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(
                    executor.submit(() -> addAfter(start, 7)),
                    executor.submit(() -> addAfter(start, 11)));
            start.countDown();
            for (var task : tasks) {
                task.get();
            }
        }
        assertThat(service().getCart("token-1").items()).singleElement()
                .extracting(item -> item.quantity()).isEqualTo(18);
    }

    private void addAfter(CountDownLatch start, int quantity) {
        try {
            start.await();
            service().addToCart("token-1", new AddCartItemCommand("sku-1", quantity));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
