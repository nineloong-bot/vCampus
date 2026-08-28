package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record CreateSkuCommand(String skuName, BigDecimal unitPrice,
        long stockQuantity, boolean active) implements Serializable { }
