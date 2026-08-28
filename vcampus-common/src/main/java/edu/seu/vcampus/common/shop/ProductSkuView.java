package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record ProductSkuView(String skuId, String skuName, BigDecimal unitPrice,
        long availableQuantity, boolean active, long rowVersion) implements Serializable { }
