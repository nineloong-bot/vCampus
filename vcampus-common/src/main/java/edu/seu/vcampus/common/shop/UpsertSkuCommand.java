package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record UpsertSkuCommand(String skuId, String skuName, BigDecimal unitPrice,
        long stockQuantity, boolean active, long expectedVersion) implements Serializable { }
