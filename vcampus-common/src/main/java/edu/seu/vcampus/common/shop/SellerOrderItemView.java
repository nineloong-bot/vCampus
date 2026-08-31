package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record SellerOrderItemView(String productId, String productName, String skuId,
        String skuName, int quantity, BigDecimal unitPrice,
        BigDecimal lineAmount) implements Serializable { }
