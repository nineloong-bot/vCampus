package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record CartItemView(String cartItemId, String productId, String productName,
        String skuId, String skuName, String shopId, String shopName,
        BigDecimal displayedUnitPrice, int quantity,
        long rowVersion) implements Serializable { }
