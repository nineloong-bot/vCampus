package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.shop.PaymentStatus;

import java.math.BigDecimal;
import java.nio.file.Path;

/** Observable result of the persistent Shop demo scenario. */
public record ShopDemoResult(Path databasePath, int catalogProductCount,
        int orderCount, String paymentNumber, BigDecimal totalAmount,
        PaymentStatus paymentStatus) { }
