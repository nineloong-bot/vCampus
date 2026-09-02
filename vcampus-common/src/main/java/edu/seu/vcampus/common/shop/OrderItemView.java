package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record OrderItemView(String orderItemId, String productName,
        String skuName, String shopName, BigDecimal unitPrice,
        int quantity, BigDecimal lineAmount) implements Serializable { }
